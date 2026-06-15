package com.ywz.workflow.featherflow.service;

import com.ywz.workflow.featherflow.logging.WorkflowLogContext;
import com.ywz.workflow.featherflow.lock.WorkflowLockService;
import com.ywz.workflow.featherflow.model.ActivityExecutionStatus;
import com.ywz.workflow.featherflow.model.ActivityInstance;
import com.ywz.workflow.featherflow.model.WorkflowInstance;
import com.ywz.workflow.featherflow.repository.ActivityRepository;
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
    private final ActivityRepository activityRepository;
    private final WorkflowLockService workflowLockService;

    public StaleRunningWorkflowRecoveryService(
        WorkflowRepository workflowRepository,
        WorkflowRuntimeService workflowRuntimeService,
        Clock clock
    ) {
        this(workflowRepository, workflowRuntimeService, clock, null, new NoOpWorkflowLockService());
    }

    public StaleRunningWorkflowRecoveryService(
        WorkflowRepository workflowRepository,
        WorkflowRuntimeService workflowRuntimeService,
        Clock clock,
        WorkflowLockService workflowLockService
    ) {
        this(workflowRepository, workflowRuntimeService, clock, null, workflowLockService);
    }

    public StaleRunningWorkflowRecoveryService(
        WorkflowRepository workflowRepository,
        WorkflowRuntimeService workflowRuntimeService,
        Clock clock,
        ActivityRepository activityRepository,
        WorkflowLockService workflowLockService
    ) {
        this.workflowRepository = workflowRepository;
        this.workflowRuntimeService = workflowRuntimeService;
        this.clock = clock;
        this.activityRepository = activityRepository;
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
            RecoveryActivityMetadata latestActivity = latestActivityMetadata(workflow.getWorkflowId());
            try (WorkflowLogContext.Scope ignored = WorkflowLogContext.open(workflow)) {
                log.info(
                    "Recover stale RUNNING workflow by resubmitting execution, workflowId={}, bizId={}, bizKey={}, workflowName={}, startNode={}, gmtModified={}, modifiedBefore={}, latestActivityId={}, latestActivityName={}, latestActivityStatus={}, latestActivityModified={}, failedAttemptCount={}",
                    workflow.getWorkflowId(),
                    workflow.getBizId(),
                    workflow.getBizKey(),
                    workflow.getWorkflowName(),
                    workflow.getStartNode(),
                    workflow.getGmtModified(),
                    modifiedBefore,
                    latestActivity.activityId(),
                    latestActivity.activityName(),
                    latestActivity.activityStatus(),
                    latestActivity.activityModified(),
                    Long.valueOf(latestActivity.failedAttemptCount())
                );
                workflowRuntimeService.dispatchWorkflow(workflow.getWorkflowId());
                submitted++;
            } catch (RuntimeException runtimeException) {
                log.error(
                    "Failed to submit stale RUNNING workflow for recovery, workflowId={}, bizId={}, bizKey={}, workflowName={}, startNode={}, gmtModified={}, latestActivityId={}, latestActivityName={}, latestActivityStatus={}, failedAttemptCount={}",
                    workflow.getWorkflowId(),
                    workflow.getBizId(),
                    workflow.getBizKey(),
                    workflow.getWorkflowName(),
                    workflow.getStartNode(),
                    workflow.getGmtModified(),
                    latestActivity.activityId(),
                    latestActivity.activityName(),
                    latestActivity.activityStatus(),
                    Long.valueOf(latestActivity.failedAttemptCount()),
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

    private RecoveryActivityMetadata latestActivityMetadata(String workflowId) {
        if (activityRepository == null) {
            return RecoveryActivityMetadata.empty();
        }
        try {
            List<ActivityInstance> activities = activityRepository.findByWorkflowId(workflowId);
            if (activities.isEmpty()) {
                return RecoveryActivityMetadata.empty();
            }
            ActivityInstance latestActivity = activities.get(activities.size() - 1);
            long failedAttemptCount = activityRepository.countByWorkflowIdAndActivityNameAndStatus(
                workflowId,
                latestActivity.getActivityName(),
                ActivityExecutionStatus.FAILED
            );
            return new RecoveryActivityMetadata(
                latestActivity.getActivityId(),
                latestActivity.getActivityName(),
                latestActivity.getStatus() == null ? "" : latestActivity.getStatus().name(),
                latestActivity.getGmtModified(),
                failedAttemptCount
            );
        } catch (RuntimeException runtimeException) {
            log.warn("Failed to load latest activity metadata for startup recovery, workflowId={}", workflowId, runtimeException);
            return RecoveryActivityMetadata.empty();
        }
    }

    private static final class RecoveryActivityMetadata {

        private final String activityId;
        private final String activityName;
        private final String activityStatus;
        private final Instant activityModified;
        private final long failedAttemptCount;

        RecoveryActivityMetadata(
            String activityId,
            String activityName,
            String activityStatus,
            Instant activityModified,
            long failedAttemptCount
        ) {
            this.activityId = activityId;
            this.activityName = activityName;
            this.activityStatus = activityStatus;
            this.activityModified = activityModified;
            this.failedAttemptCount = failedAttemptCount;
        }

        static RecoveryActivityMetadata empty() {
            return new RecoveryActivityMetadata("", "", "", null, 0L);
        }

        String activityId() {
            return activityId;
        }

        String activityName() {
            return activityName;
        }

        String activityStatus() {
            return activityStatus;
        }

        Instant activityModified() {
            return activityModified;
        }

        long failedAttemptCount() {
            return failedAttemptCount;
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
