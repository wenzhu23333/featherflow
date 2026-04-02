package com.ywz.workflow.featherflow.engine;

import com.ywz.workflow.featherflow.logging.WorkflowLogContext;
import com.ywz.workflow.featherflow.model.WorkflowInstance;
import com.ywz.workflow.featherflow.model.WorkflowStatus;
import com.ywz.workflow.featherflow.repository.WorkflowRepository;
import java.time.Clock;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Wraps workflow tasks so rejected submissions and uncaught crashes are handled consistently.
 */
public class DefaultWorkflowExecutionScheduler implements WorkflowExecutionScheduler {

    private static final Logger log = LoggerFactory.getLogger(DefaultWorkflowExecutionScheduler.class);

    private final WorkflowEngine workflowEngine;
    private final WorkflowRepository workflowRepository;
    private final ExecutorService executorService;
    private final Clock clock;

    public DefaultWorkflowExecutionScheduler(
        WorkflowEngine workflowEngine,
        WorkflowRepository workflowRepository,
        ExecutorService executorService,
        Clock clock
    ) {
        this.workflowEngine = workflowEngine;
        this.workflowRepository = workflowRepository;
        this.executorService = executorService;
        this.clock = clock;
    }

    @Override
    public void schedule(String workflowId) {
        WorkflowInstance workflowInstance = workflowRepository.findRequired(workflowId);
        Map<String, String> logContext = WorkflowLogContext.snapshot(workflowInstance);
        try (WorkflowLogContext.Scope ignored = WorkflowLogContext.open(logContext)) {
            log.info("Submit workflow execution task");
            try {
                executorService.execute(() -> runWorkflow(workflowId, logContext));
                log.info("Workflow execution task submitted");
            } catch (RejectedExecutionException rejectedExecutionException) {
                log.error("Workflow execution task rejected by executor", rejectedExecutionException);
                throw new IllegalStateException("Workflow execution task rejected", rejectedExecutionException);
            }
        }
    }

    public void shutdown() {
        executorService.shutdownNow();
    }

    private void runWorkflow(String workflowId, Map<String, String> logContext) {
        try (WorkflowLogContext.Scope ignored = WorkflowLogContext.open(logContext)) {
            log.info("Start workflow execution task");
            workflowEngine.continueWorkflow(workflowId);
            log.info("Finish workflow execution task");
        } catch (Throwable throwable) {
            log.error("Workflow execution task crashed", throwable);
            try {
                correctWorkflowStatusAfterCrash(workflowId);
            } catch (RuntimeException correctionFailure) {
                log.error(
                    "Failed to correct workflow status after task crash; workflow may remain RUNNING without active execution, workflowId={}",
                    workflowId,
                    correctionFailure
                );
            }
        }
    }

    private void correctWorkflowStatusAfterCrash(String workflowId) {
        WorkflowInstance latest = workflowRepository.findRequired(workflowId);
        if (latest.getStatus() == WorkflowStatus.RUNNING) {
            latest.setStatus(WorkflowStatus.HUMAN_PROCESSING);
            latest.setGmtModified(clock.instant());
            workflowRepository.update(latest);
            log.warn("Workflow status corrected to HUMAN_PROCESSING after task crash");
        }
    }
}
