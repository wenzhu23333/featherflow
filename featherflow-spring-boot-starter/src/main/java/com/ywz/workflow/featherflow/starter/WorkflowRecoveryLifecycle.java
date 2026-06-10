package com.ywz.workflow.featherflow.starter;

import com.ywz.workflow.featherflow.service.StaleRunningWorkflowRecoveryService;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.SmartLifecycle;

/**
 * Runs low-frequency recovery scans only during the startup window.
 */
public class WorkflowRecoveryLifecycle implements SmartLifecycle {

    private static final Logger log = LoggerFactory.getLogger(WorkflowRecoveryLifecycle.class);

    private final StaleRunningWorkflowRecoveryService recoveryService;
    private final FeatherFlowProperties properties;
    private ScheduledExecutorService scheduler;
    private Instant stopAt;
    private volatile boolean running;

    public WorkflowRecoveryLifecycle(StaleRunningWorkflowRecoveryService recoveryService, FeatherFlowProperties properties) {
        this.recoveryService = recoveryService;
        this.properties = properties;
    }

    @Override
    public void start() {
        if (!properties.isAutoRecoverRunningWorkflows()) {
            log.info("Startup workflow recovery scheduler is disabled");
            return;
        }
        scheduler = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "featherflow-recovery");
            thread.setDaemon(true);
            return thread;
        });
        stopAt = Instant.now().plusMillis(properties.getRunningWorkflowRecoveryWindowMillis());
        log.info(
            "Start startup workflow recovery scheduler, delayMillis={}, intervalMillis={}, windowMillis={}, staleMillis={}, batchSize={}, stopAt={}",
            Long.valueOf(properties.getRunningWorkflowRecoveryDelayMillis()),
            Long.valueOf(properties.getRunningWorkflowRecoveryIntervalMillis()),
            Long.valueOf(properties.getRunningWorkflowRecoveryWindowMillis()),
            Long.valueOf(properties.getRunningWorkflowRecoveryStaleMillis()),
            Integer.valueOf(properties.getRunningWorkflowRecoveryBatchSize()),
            stopAt
        );
        scheduler.scheduleWithFixedDelay(
            this::recoverOnceWithinStartupWindowSafely,
            properties.getRunningWorkflowRecoveryDelayMillis(),
            properties.getRunningWorkflowRecoveryIntervalMillis(),
            TimeUnit.MILLISECONDS
        );
        running = true;
    }

    @Override
    public void stop() {
        if (scheduler != null) {
            scheduler.shutdownNow();
            log.info("Stop startup workflow recovery scheduler");
        }
        running = false;
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    @Override
    public int getPhase() {
        return Integer.MAX_VALUE - 1;
    }

    @Override
    public boolean isAutoStartup() {
        return properties.isAutoRecoverRunningWorkflows();
    }

    @Override
    public void stop(Runnable callback) {
        stop();
        callback.run();
    }

    private void recoverOnceWithinStartupWindow() {
        if (Instant.now().isAfter(stopAt)) {
            log.info("Stop startup workflow recovery scheduler because startup window elapsed, stopAt={}", stopAt);
            stop();
            return;
        }
        recoveryService.recover(
            Duration.ofMillis(properties.getRunningWorkflowRecoveryStaleMillis()),
            properties.getRunningWorkflowRecoveryBatchSize()
        );
    }

    private void recoverOnceWithinStartupWindowSafely() {
        try {
            recoverOnceWithinStartupWindow();
        } catch (Throwable throwable) {
            log.error("Failed to run startup workflow recovery scan, next scan will continue within startup window", throwable);
        }
    }
}
