package com.ywz.workflow.featherflow.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.CannotGetJdbcConnectionException;

class DefaultPersistenceWriteRetrierTest {

    @Test
    void shouldRetryTransientPersistenceFailureUntilSuccess() {
        AtomicInteger attempts = new AtomicInteger();
        List<Duration> delays = new ArrayList<Duration>();
        DefaultPersistenceWriteRetrier retrier = new DefaultPersistenceWriteRetrier(
            4,
            Duration.ofMillis(100),
            Duration.ofMillis(500),
            delays::add
        );

        retrier.run("persist activity failure", () -> {
            if (attempts.incrementAndGet() < 3) {
                throw new CannotGetJdbcConnectionException("db down");
            }
        });

        assertThat(attempts.get()).isEqualTo(3);
        assertThat(delays).containsExactly(Duration.ofMillis(100), Duration.ofMillis(200));
    }

    @Test
    void shouldNotRetryNonTransientPersistenceFailure() {
        AtomicInteger attempts = new AtomicInteger();
        DefaultPersistenceWriteRetrier retrier = new DefaultPersistenceWriteRetrier(
            4,
            Duration.ofMillis(100),
            Duration.ofMillis(500),
            delay -> {
            }
        );

        assertThatThrownBy(() -> retrier.run("persist workflow", () -> {
            attempts.incrementAndGet();
            throw new IllegalArgumentException("bad sql mapping");
        }))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("bad sql mapping");

        assertThat(attempts.get()).isEqualTo(1);
    }

    @Test
    void shouldStopRetryingAfterConfiguredAttempts() {
        AtomicInteger attempts = new AtomicInteger();
        List<Duration> delays = new ArrayList<Duration>();
        DefaultPersistenceWriteRetrier retrier = new DefaultPersistenceWriteRetrier(
            3,
            Duration.ofMillis(100),
            Duration.ofMillis(500),
            delays::add
        );

        assertThatThrownBy(() -> retrier.run("persist workflow", () -> {
            attempts.incrementAndGet();
            throw new CannotGetJdbcConnectionException("db down");
        }))
            .isInstanceOf(CannotGetJdbcConnectionException.class)
            .hasMessageContaining("db down");

        assertThat(attempts.get()).isEqualTo(3);
        assertThat(delays).containsExactly(Duration.ofMillis(100), Duration.ofMillis(200));
    }
}
