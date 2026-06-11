package com.ywz.workflow.featherflow.service;

import com.ywz.workflow.featherflow.logging.WorkflowLogContext;
import com.ywz.workflow.featherflow.lock.WorkflowLockService;
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
    private final WorkflowLockService workflowLockService;

    public StaleRunningWorkflowRecoveryService(
        WorkflowRepository workflowRepository,
        WorkflowRuntimeService workflowRuntimeService,
        Clock clock
    ) {
        this(workflowRepository, workflowRuntimeService, clock, new NoOpWorkflowLockService());
    }

    public StaleRunningWorkflowRecoveryService(
        WorkflowRepository workflowRepository,
        WorkflowRuntimeService workflowRuntimeService,
        Clock clock,
        WorkflowLockService workflowLockService
    ) {
        this.workflowRepository = workflowRepository;
        this.workflowRuntimeService = workflowRuntimeService;
        this.clock = clock;
        this.workflowLockService = workflowLockService;
    }

    public int recover(Duration staleTimeout, int batchSize) {
        if (staleTimeout == null || staleTimeout.isNegative() || staleTimeout.isZero()) {
            throw new IllegalArgumentException("staleTimeout must be positive");
        }
        if (batchSize <= 0) {
            throw new IllegalArgumentException("batchSize must be positive");
        }

        Instant modifiedBefore = clock.instant().minus(staleTimeout);
        cleanExpiredWorkflowLocks(modifiedBefore);
        log.info(
            "Scan stale RUNNING workflows for startup recovery, modifiedBefore={}, staleTimeoutMillis={}, batchSize={}",
            modifiedBefore,
            Long.valueOf(staleTimeout.toMillis()),
            Integer.valueOf(batchSize)
        );
        List<WorkflowInstance> workflows = workflowRepository.findRunningModifiedBefore(modifiedBefore, batchSize);
        if (workflows.isEmpty()) {
            log.info("No stale RUNNING workflows found for recovery, modifiedBefore={}, batchSize={}", modifiedBefore, Integer.valueOf(batchSize));
            return 0;
        }

        int submitted = 0;
        for (WorkflowInstance workflow : workflows) {
            try (WorkflowLogContext.Scope ignored = WorkflowLogContext.open(workflow)) {
                log.info(
                    "Recover stale RUNNING workflow by resubmitting execution, workflowId={}, bizId={}, bizKey={}, workflowName={}, startNode={}, gmtModified={}, modifiedBefore={}",
                    workflow.getWorkflowId(),
                    workflow.getBizId(),
                    workflow.getBizKey(),
                    workflow.getWorkflowName(),
                    workflow.getStartNode(),
                    workflow.getGmtModified(),
                    modifiedBefore
                );
                workflowRuntimeService.dispatchWorkflow(workflow.getWorkflowId());
                submitted++;
            } catch (RuntimeException runtimeException) {
                log.error(
                    "Failed to submit stale RUNNING workflow for recovery, workflowId={}, bizId={}, bizKey={}, workflowName={}, startNode={}, gmtModified={}",
                    workflow.getWorkflowId(),
                    workflow.getBizId(),
                    workflow.getBizKey(),
                    workflow.getWorkflowName(),
                    workflow.getStartNode(),
                    workflow.getGmtModified(),
                    runtimeException
                );
            }
        }
        log.info(
            "Submitted stale RUNNING workflows for recovery, submitted={}, scanned={}, modifiedBefore={}, batchSize={}",
            Integer.valueOf(submitted),
            Integer.valueOf(workflows.size()),
            modifiedBefore,
            Integer.valueOf(batchSize)
        );
        return submitted;
    }

    private void cleanExpiredWorkflowLocks(Instant modifiedBefore) {
        int cleaned = workflowLockService.cleanExpiredLocks(modifiedBefore);
        if (cleaned > 0) {
            log.warn(
                "Clean expired workflow locks before startup recovery, cleaned={}, modifiedBefore={}",
                Integer.valueOf(cleaned),
                modifiedBefore
            );
        } else {
            log.info("No expired workflow locks found before startup recovery, modifiedBefore={}", modifiedBefore);
        }
    }

    private static final class NoOpWorkflowLockService implements WorkflowLockService {

        @Override
        public boolean tryLock(String key) {
            return true;
        }

        @Override
        public void unlock(String key) {
        }
    }
}
