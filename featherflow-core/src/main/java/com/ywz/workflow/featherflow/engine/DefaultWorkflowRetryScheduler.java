package com.ywz.workflow.featherflow.engine;

import com.ywz.workflow.featherflow.logging.WorkflowLogContext;
import com.ywz.workflow.featherflow.model.WorkflowInstance;
import com.ywz.workflow.featherflow.repository.WorkflowRepository;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Internal retry scheduler used only for automatic activity retries inside the engine.
 */
public class DefaultWorkflowRetryScheduler implements WorkflowRetryScheduler {

    private static final Logger log = LoggerFactory.getLogger(DefaultWorkflowRetryScheduler.class);

    private final Supplier<WorkflowExecutionScheduler> workflowExecutionSchedulerSupplier;
    private final WorkflowRepository workflowRepository;
    private final ScheduledExecutorService scheduledExecutorService;

    public DefaultWorkflowRetryScheduler(
        Supplier<WorkflowExecutionScheduler> workflowExecutionSchedulerSupplier,
        WorkflowRepository workflowRepository,
        ScheduledExecutorService scheduledExecutorService
    ) {
        this.workflowExecutionSchedulerSupplier = workflowExecutionSchedulerSupplier;
        this.workflowRepository = workflowRepository;
        this.scheduledExecutorService = scheduledExecutorService;
    }

    @Override
    public void scheduleRetry(String workflowId, Duration delay) {
        WorkflowInstance workflowInstance = workflowRepository.findRequired(workflowId);
        Map<String, String> logContext = WorkflowLogContext.snapshot(workflowInstance);
        try (WorkflowLogContext.Scope ignored = WorkflowLogContext.open(logContext)) {
            if (delay == null || delay.isZero() || delay.isNegative()) {
                log.info("Dispatch workflow retry immediately");
                dispatchRetry(workflowId);
                return;
            }
            log.info("Schedule internal workflow retry, delay={}", delay);
            try {
                scheduledExecutorService.schedule(
                    () -> dispatchRetry(workflowId),
                    delay.toMillis(),
                    TimeUnit.MILLISECONDS
                );
            } catch (RejectedExecutionException rejectedExecutionException) {
                log.error("Internal workflow retry rejected by scheduler", rejectedExecutionException);
                throw new IllegalStateException("Internal workflow retry rejected", rejectedExecutionException);
            }
        }
    }

    public void shutdown() {
        scheduledExecutorService.shutdownNow();
    }

    private void dispatchRetry(String workflowId) {
        workflowExecutionSchedulerSupplier.get().schedule(workflowId);
    }
}
