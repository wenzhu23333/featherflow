package com.ywz.workflow.featherflow.service;

import com.ywz.workflow.featherflow.logging.WorkflowLogContext;
import com.ywz.workflow.featherflow.model.WorkflowInstance;
import com.ywz.workflow.featherflow.repository.WorkflowRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Lightweight restart recovery for workflows that stayed RUNNING after a process or pod exit.
 *
 * <p>The service does not change workflow state. It only resubmits stale RUNNING workflows to the
 * local execution scheduler. Activity-level DB locks and idempotency still decide whether a step
 * really executes or is skipped because another node already completed it.</p>
 */
public class StaleRunningWorkflowRecoveryService {

    private static final Logger log = LoggerFactory.getLogger(StaleRunningWorkflowRecoveryService.class);

    private final WorkflowRepository workflowRepository;
    private final WorkflowRuntimeService workflowRuntimeService;
    private final Clock clock;

    public StaleRunningWorkflowRecoveryService(
        WorkflowRepository workflowRepository,
        WorkflowRuntimeService workflowRuntimeService,
        Clock clock
    ) {
        this.workflowRepository = workflowRepository;
        this.workflowRuntimeService = workflowRuntimeService;
        this.clock = clock;
    }

    public int recover(Duration staleTimeout, int batchSize) {
        if (staleTimeout == null || staleTimeout.isNegative() || staleTimeout.isZero()) {
            throw new IllegalArgumentException("staleTimeout must be positive");
        }
        if (batchSize <= 0) {
            throw new IllegalArgumentException("batchSize must be positive");
        }

        Instant modifiedBefore = clock.instant().minus(staleTimeout);
        List<WorkflowInstance> workflows = workflowRepository.findRunningModifiedBefore(modifiedBefore, batchSize);
        if (workflows.isEmpty()) {
            log.info("No stale RUNNING workflows found for recovery, modifiedBefore={}", modifiedBefore);
            return 0;
        }

        int submitted = 0;
        for (WorkflowInstance workflow : workflows) {
            try (WorkflowLogContext.Scope ignored = WorkflowLogContext.open(workflow)) {
                log.info("Recover stale RUNNING workflow by resubmitting execution, modifiedBefore={}", modifiedBefore);
                workflowRuntimeService.dispatchWorkflow(workflow.getWorkflowId());
                submitted++;
            } catch (RuntimeException runtimeException) {
                log.error("Failed to submit stale RUNNING workflow for recovery", runtimeException);
            }
        }
        log.info("Submitted stale RUNNING workflows for recovery, submitted={}, scanned={}", submitted, workflows.size());
        return submitted;
    }
}
