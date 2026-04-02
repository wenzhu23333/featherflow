package com.ywz.workflow.featherflow.persistence;

import java.sql.SQLRecoverableException;
import java.sql.SQLTransientException;
import java.time.Duration;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.dao.RecoverableDataAccessException;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.jdbc.CannotGetJdbcConnectionException;

/**
 * Default bounded retry policy for framework persistence writes.
 */
public class DefaultPersistenceWriteRetrier implements PersistenceWriteRetrier {

    private static final Logger log = LoggerFactory.getLogger(DefaultPersistenceWriteRetrier.class);

    private final int maxAttempts;
    private final Duration initialDelay;
    private final Duration maxDelay;
    private final PersistenceRetrySleeper sleeper;

    public DefaultPersistenceWriteRetrier(int maxAttempts, Duration initialDelay, Duration maxDelay) {
        this(maxAttempts, initialDelay, maxDelay, delay -> Thread.sleep(delay.toMillis()));
    }

    public DefaultPersistenceWriteRetrier(int maxAttempts, Duration initialDelay, Duration maxDelay, PersistenceRetrySleeper sleeper) {
        if (maxAttempts < 1) {
            throw new IllegalArgumentException("maxAttempts must be at least 1");
        }
        this.maxAttempts = maxAttempts;
        this.initialDelay = normalizeDelay(initialDelay);
        this.maxDelay = normalizeMaxDelay(maxDelay, this.initialDelay);
        this.sleeper = sleeper;
    }

    @Override
    public void run(String operationName, Runnable action) {
        call(operationName, () -> {
            action.run();
            return null;
        });
    }

    @Override
    public <T> T call(String operationName, Supplier<T> supplier) {
        Duration delay = initialDelay;
        RuntimeException lastFailure = null;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                return supplier.get();
            } catch (RuntimeException runtimeException) {
                lastFailure = runtimeException;
                if (!isRetryable(runtimeException) || attempt == maxAttempts) {
                    log.error(
                        "Persistence write failed after {} attempt(s), operationName={}, retryable={}",
                        Integer.valueOf(attempt),
                        operationName,
                        Boolean.valueOf(isRetryable(runtimeException)),
                        runtimeException
                    );
                    throw runtimeException;
                }
                log.warn(
                    "Persistence write failed, retrying operationName={}, attempt={}, maxAttempts={}, nextDelay={}",
                    operationName,
                    Integer.valueOf(attempt),
                    Integer.valueOf(maxAttempts),
                    delay,
                    runtimeException
                );
                pauseBeforeRetry(delay);
                delay = nextDelay(delay);
            }
        }
        throw lastFailure == null ? new IllegalStateException("Persistence retry exited unexpectedly") : lastFailure;
    }

    private boolean isRetryable(RuntimeException runtimeException) {
        return runtimeException instanceof CannotGetJdbcConnectionException
            || runtimeException instanceof QueryTimeoutException
            || runtimeException instanceof CannotAcquireLockException
            || runtimeException instanceof RecoverableDataAccessException
            || runtimeException instanceof TransientDataAccessException
            || containsRetryableSqlCause(runtimeException);
    }

    private boolean containsRetryableSqlCause(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof SQLTransientException || current instanceof SQLRecoverableException) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private void pauseBeforeRetry(Duration delay) {
        try {
            sleeper.sleep(delay);
        } catch (InterruptedException interruptedException) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Persistence retry interrupted", interruptedException);
        }
    }

    private Duration nextDelay(Duration currentDelay) {
        Duration doubled = currentDelay.multipliedBy(2L);
        return doubled.compareTo(maxDelay) > 0 ? maxDelay : doubled;
    }

    private static Duration normalizeDelay(Duration delay) {
        if (delay == null || delay.isNegative() || delay.isZero()) {
            return Duration.ofMillis(100L);
        }
        return delay;
    }

    private static Duration normalizeMaxDelay(Duration maxDelay, Duration fallback) {
        if (maxDelay == null || maxDelay.isNegative() || maxDelay.isZero()) {
            return fallback;
        }
        return maxDelay.compareTo(fallback) < 0 ? fallback : maxDelay;
    }
}
