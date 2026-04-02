package com.ywz.workflow.featherflow.starter;

import com.ywz.workflow.featherflow.daemon.WorkflowOperationDaemon;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.springframework.context.SmartLifecycle;

public class WorkflowOperationDaemonLifecycle implements SmartLifecycle {

    private final WorkflowOperationDaemon workflowOperationDaemon;
    private final FeatherFlowProperties properties;
    private ScheduledExecutorService scheduler;
    private volatile boolean running;

    public WorkflowOperationDaemonLifecycle(WorkflowOperationDaemon workflowOperationDaemon, FeatherFlowProperties properties) {
        this.workflowOperationDaemon = workflowOperationDaemon;
        this.properties = properties;
    }

    @Override
    public void start() {
        if (!properties.isAutoStartDaemon()) {
            return;
        }
        scheduler = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "featherflow-operation-daemon");
            thread.setDaemon(true);
            return thread;
        });
        scheduler.scheduleWithFixedDelay(
            workflowOperationDaemon::pollOnce,
            0L,
            properties.getPollIntervalMillis(),
            TimeUnit.MILLISECONDS
        );
        running = true;
    }

    @Override
    public void stop() {
        if (scheduler != null) {
            scheduler.shutdownNow();
        }
        running = false;
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    @Override
    public int getPhase() {
        return Integer.MAX_VALUE;
    }

    @Override
    public boolean isAutoStartup() {
        return properties.isAutoStartDaemon();
    }

    @Override
    public void stop(Runnable callback) {
        stop();
        callback.run();
    }
}
