package com.ywz.workflow.featherflow.engine;

import com.ywz.workflow.featherflow.definition.ActivityDefinition;
import com.ywz.workflow.featherflow.definition.WorkflowDefinition;
import com.ywz.workflow.featherflow.definition.WorkflowDefinitionRegistry;
import com.ywz.workflow.featherflow.handler.WorkflowActivityHandler;
import com.ywz.workflow.featherflow.handler.WorkflowActivityHandlerRegistry;
import com.ywz.workflow.featherflow.lock.WorkflowLockService;
import com.ywz.workflow.featherflow.logging.WorkflowLogContext;
import com.ywz.workflow.featherflow.model.ActivityExecutionStatus;
import com.ywz.workflow.featherflow.model.ActivityInstance;
import com.ywz.workflow.featherflow.model.WorkflowInstance;
import com.ywz.workflow.featherflow.model.WorkflowStatus;
import com.ywz.workflow.featherflow.repository.ActivityRepository;
import com.ywz.workflow.featherflow.repository.WorkflowRepository;
import com.ywz.workflow.featherflow.support.WorkflowContextSerializer;
import com.ywz.workflow.featherflow.support.WorkflowNodeIdentity;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Core linear workflow engine.
 *
 * <p>One workflow execution thread drives the full lifecycle: it restores the current context, acquires the
 * activity lock, executes business logic, persists activity snapshots, evaluates retry policy, and decides
 * whether to continue to the next activity.
 */
public class WorkflowEngine {

    private static final Logger log = LoggerFactory.getLogger(WorkflowEngine.class);
    private static final String SKIP_MARKER_KEY = "_featherflowSkip";

    private final WorkflowDefinitionRegistry definitionRegistry;
    private final WorkflowRepository workflowRepository;
    private final ActivityRepository activityRepository;
    private final WorkflowActivityHandlerRegistry handlerRegistry;
    private final WorkflowLockService lockService;
    private final WorkflowContextSerializer serializer;
    private final Clock clock;
    private final WorkflowRetryScheduler workflowRetryScheduler;
    private final String nodeIdentity;

    public WorkflowEngine(
        WorkflowDefinitionRegistry definitionRegistry,
        WorkflowRepository workflowRepository,
        ActivityRepository activityRepository,
        WorkflowActivityHandlerRegistry handlerRegistry,
        WorkflowLockService lockService,
        WorkflowContextSerializer serializer,
        Clock clock,
        WorkflowRetryScheduler workflowRetryScheduler,
        String nodeIdentity
    ) {
        this.definitionRegistry = definitionRegistry;
        this.workflowRepository = workflowRepository;
        this.activityRepository = activityRepository;
        this.handlerRegistry = handlerRegistry;
        this.lockService = lockService;
        this.serializer = serializer;
        this.clock = clock;
        this.workflowRetryScheduler = workflowRetryScheduler;
        this.nodeIdentity = nodeIdentity;
    }

    public WorkflowEngine(
        WorkflowDefinitionRegistry definitionRegistry,
        WorkflowRepository workflowRepository,
        ActivityRepository activityRepository,
        WorkflowActivityHandlerRegistry handlerRegistry,
        WorkflowLockService lockService,
        WorkflowContextSerializer serializer,
        Clock clock
    ) {
        this(
            definitionRegistry,
            workflowRepository,
            activityRepository,
            handlerRegistry,
            lockService,
            serializer,
            clock,
            (workflowId, delay) -> {
            },
            WorkflowNodeIdentity.currentInstanceId()
        );
    }

    public WorkflowEngine(
        WorkflowDefinitionRegistry definitionRegistry,
        WorkflowRepository workflowRepository,
        ActivityRepository activityRepository,
        WorkflowActivityHandlerRegistry handlerRegistry,
        WorkflowLockService lockService,
        WorkflowContextSerializer serializer,
        Clock clock,
        WorkflowRetryScheduler workflowRetryScheduler
    ) {
        this(
            definitionRegistry,
            workflowRepository,
            activityRepository,
            handlerRegistry,
            lockService,
            serializer,
            clock,
            workflowRetryScheduler,
            WorkflowNodeIdentity.currentInstanceId()
        );
    }

    /**
     * Continue one workflow until it either finishes all activities or reaches a stopping condition.
     */
    public void continueWorkflow(String workflowId) {
        WorkflowInstance workflowInstance = workflowRepository.findRequired(workflowId);
        try (WorkflowLogContext.Scope ignored = WorkflowLogContext.open(workflowInstance)) {
            WorkflowDefinition definition = loadDefinition(workflowInstance);
            log.info(
                "Continue workflow execution, definitionName={}, activityCount={}",
                definition.getName(),
                Integer.valueOf(definition.getActivities().size())
            );

            String workflowContext = workflowInstance.getInput();
            int activitySequence = 1;
            for (ActivityDefinition activityDefinition : definition.getActivities()) {
                workflowContext = continueWithActivity(workflowId, activityDefinition, workflowContext, activitySequence);
                activitySequence++;
            }

            markWorkflowSuccessfulIfStillRunning(workflowId);
        } catch (StopWorkflowExecutionException ignoredStop) {
            // The stop reason is already recorded in logs or persisted state.
        }
    }

    /**
     * Mark the latest persisted activity as skipped and reopen the workflow to continue from the next step.
     */
    public void skipActivity(String workflowId, String skipInput) {
        WorkflowInstance workflowInstance = workflowRepository.findRequired(workflowId);
        try (WorkflowLogContext.Scope ignored = WorkflowLogContext.open(workflowInstance)) {
            ActivityInstance target = requireLatestSkippableActivity(workflowInstance);
            WorkflowDefinition definition = loadDefinition(workflowInstance);
            String baseContext = resolveContextBefore(workflowId, target.getActivityName(), workflowInstance.getInput());
            String skipOutput = buildSkipOutput(baseContext, skipInput, target);
            int activitySequence = resolveActivitySequence(definition, target.getActivityName());
            long attempt = nextAttempt(findLatestActivityAttempt(workflowId, target.getActivityName()));
            String activityId = buildActivityId(workflowId, activitySequence, attempt);

            activityRepository.saveAttempt(
                activityId,
                workflowId,
                target.getActivityName(),
                currentNode(),
                target.getInput(),
                skipOutput,
                ActivityExecutionStatus.SUCCESSFUL,
                clock.instant()
            );
            log.info("Skip activity by manual operation, activityId={}, activityName={}", activityId, target.getActivityName());

            workflowInstance.setStatus(WorkflowStatus.RUNNING);
            workflowInstance.setGmtModified(clock.instant());
            workflowRepository.update(workflowInstance);
        }
    }

    private WorkflowDefinition loadDefinition(WorkflowInstance workflowInstance) {
        return definitionRegistry.getRequired(getDefinitionName(workflowInstance));
    }

    private String continueWithActivity(String workflowId, ActivityDefinition activityDefinition, String workflowContext, int activitySequence) {
        requireRunningWorkflow(workflowId, "before starting the next activity");

        ActivityInstance latestAttempt = findLatestActivityAttempt(workflowId, activityDefinition.getName());
        if (isSuccessful(latestAttempt)) {
            return reuseSuccessfulActivityContext(workflowContext, latestAttempt, activityDefinition.getName(), "before lock acquisition");
        }

        return continueWithActivityLock(workflowId, activityDefinition, workflowContext, activitySequence);
    }

    private String continueWithActivityLock(
        String workflowId,
        ActivityDefinition activityDefinition,
        String workflowContext,
        int activitySequence
    ) {
        String lockKey = buildLockKey(workflowId, activityDefinition);
        if (!lockService.tryLock(lockKey)) {
            log.info("Skip activity execution because workflow lock is already held, activityName={}, lockKey={}", activityDefinition.getName(), lockKey);
            throw new StopWorkflowExecutionException();
        }

        try {
            WorkflowInstance workflowInstance = requireRunningWorkflow(workflowId, "after acquiring the activity lock");
            ActivityInstance latestAttempt = findLatestActivityAttempt(workflowId, activityDefinition.getName());
            if (isSuccessful(latestAttempt)) {
                return reuseSuccessfulActivityContext(workflowContext, latestAttempt, activityDefinition.getName(), "after lock acquisition");
            }
            long attempt = nextAttempt(latestAttempt);
            String activityId = buildActivityId(workflowId, activitySequence, attempt);
            log.info(
                "Acquire activity lock, activityName={}, activityId={}, sequence={}, attempt={}",
                activityDefinition.getName(),
                activityId,
                Integer.valueOf(activitySequence),
                Long.valueOf(attempt)
            );
            return executeActivity(workflowInstance, activityId, activityDefinition, workflowContext);
        } finally {
            try {
                lockService.unlock(lockKey);
            } finally {
                log.info("Release activity lock, activityName={}", activityDefinition.getName());
            }
        }
    }

    private WorkflowInstance requireRunningWorkflow(String workflowId, String phase) {
        WorkflowInstance workflowInstance = workflowRepository.findRequired(workflowId);
        if (workflowInstance.getStatus() != WorkflowStatus.RUNNING) {
            log.info("Stop workflow execution because workflow is not RUNNING, phase={}, status={}", phase, workflowInstance.getStatus());
            throw new StopWorkflowExecutionException();
        }
        return workflowInstance;
    }

    private boolean isSuccessful(ActivityInstance activityInstance) {
        return activityInstance != null && activityInstance.getStatus() == ActivityExecutionStatus.SUCCESSFUL;
    }

    private String reuseSuccessfulActivityContext(
        String workflowContext,
        ActivityInstance activityInstance,
        String activityName,
        String phase
    ) {
        log.info(
            "Reuse successful activity result for idempotency, activityName={}, activityId={}, phase={}",
            activityName,
            activityInstance.getActivityId(),
            phase
        );
        return activityInstance.getOutput() == null ? workflowContext : activityInstance.getOutput();
    }

    private void markWorkflowSuccessfulIfStillRunning(String workflowId) {
        WorkflowInstance completedWorkflow = workflowRepository.findRequired(workflowId);
        if (completedWorkflow.getStatus() == WorkflowStatus.RUNNING) {
            completedWorkflow.setStatus(WorkflowStatus.SUCCESSFUL);
            completedWorkflow.setGmtModified(clock.instant());
            workflowRepository.update(completedWorkflow);
            log.info("Workflow completed successfully");
        }
    }

    private ActivityInstance requireLatestSkippableActivity(WorkflowInstance workflowInstance) {
        if (workflowInstance.getStatus() != WorkflowStatus.TERMINATED) {
            throw new IllegalStateException("Skip is only allowed when workflow is TERMINATED");
        }

        ActivityInstance latestActivity = findLatestActivity(workflowInstance.getWorkflowId());
        if (latestActivity == null) {
            throw new IllegalStateException("Skip requires the latest recorded activity");
        }
        if (latestActivity.getStatus() == ActivityExecutionStatus.SUCCESSFUL) {
            throw new IllegalStateException("Latest activity is already SUCCESSFUL, skip is not needed");
        }
        return latestActivity;
    }

    private String executeActivity(
        WorkflowInstance workflowInstance,
        String activityId,
        ActivityDefinition activityDefinition,
        String workflowContext
    ) {
        Instant executeTime = clock.instant();
        Map<String, Object> activityContext = deserializeWorkflowContext(workflowContext);
        try {
            WorkflowActivityHandler handler = handlerRegistry.getRequired(activityDefinition.getHandler());
            log.info(
                "Start activity execution, activityId={}, activityName={}, handler={}",
                activityId,
                activityDefinition.getName(),
                activityDefinition.getHandler()
            );
            Map<String, Object> outputContext = handler.handle(activityContext);
            return persistSuccessfulActivity(workflowInstance, activityId, activityDefinition, workflowContext, outputContext, executeTime);
        } catch (Throwable throwable) {
            persistFailedActivity(workflowInstance, activityId, activityDefinition, workflowContext, throwable, executeTime);
            handleRetry(workflowInstance, activityDefinition);
            throw new StopWorkflowExecutionException();
        }
    }

    private Map<String, Object> deserializeWorkflowContext(String workflowContext) {
        return new LinkedHashMap<String, Object>(serializer.deserialize(workflowContext));
    }

    private String persistSuccessfulActivity(
        WorkflowInstance workflowInstance,
        String activityId,
        ActivityDefinition activityDefinition,
        String workflowContext,
        Map<String, Object> outputContext,
        Instant executeTime
    ) {
        String output = serializer.serialize(outputContext);
        activityRepository.saveAttempt(
            activityId,
            workflowInstance.getWorkflowId(),
            activityDefinition.getName(),
            currentNode(),
            workflowContext,
            output,
            ActivityExecutionStatus.SUCCESSFUL,
            executeTime
        );
        log.info("Activity executed successfully, activityId={}, activityName={}", activityId, activityDefinition.getName());
        return output;
    }

    private void persistFailedActivity(
        WorkflowInstance workflowInstance,
        String activityId,
        ActivityDefinition activityDefinition,
        String workflowContext,
        Throwable throwable,
        Instant executeTime
    ) {
        String failureOutput = serializer.failureOutput(throwable);
        activityRepository.saveAttempt(
            activityId,
            workflowInstance.getWorkflowId(),
            activityDefinition.getName(),
            currentNode(),
            workflowContext,
            failureOutput,
            ActivityExecutionStatus.FAILED,
            executeTime
        );
        log.warn(
            "Activity execution failed, activityId={}, activityName={}, message={}",
            activityId,
            activityDefinition.getName(),
            throwable.getMessage(),
            throwable
        );
    }

    private void handleRetry(WorkflowInstance workflowInstance, ActivityDefinition activityDefinition) {
        Instant now = clock.instant();
        long failedAttemptCount = activityRepository.countByWorkflowIdAndActivityNameAndStatus(
            workflowInstance.getWorkflowId(),
            activityDefinition.getName(),
            ActivityExecutionStatus.FAILED
        );
        workflowInstance.setGmtModified(now);

        if (failedAttemptCount <= activityDefinition.getMaxRetryTimes()) {
            log.info(
                "Schedule workflow retry, activityName={}, failedAttemptCount={}, maxRetryTimes={}, nextRetryAt={}",
                activityDefinition.getName(),
                Long.valueOf(failedAttemptCount),
                Integer.valueOf(activityDefinition.getMaxRetryTimes()),
                now.plus(activityDefinition.getRetryInterval())
            );
            workflowRetryScheduler.scheduleRetry(workflowInstance.getWorkflowId(), activityDefinition.getRetryInterval());
        } else {
            workflowInstance.setStatus(WorkflowStatus.HUMAN_PROCESSING);
            log.warn(
                "Workflow moved to HUMAN_PROCESSING because retries are exhausted, activityName={}, failedAttemptCount={}, maxRetryTimes={}",
                activityDefinition.getName(),
                Long.valueOf(failedAttemptCount),
                Integer.valueOf(activityDefinition.getMaxRetryTimes())
            );
        }
        workflowRepository.update(workflowInstance);
    }

    private String getDefinitionName(WorkflowInstance workflowInstance) {
        if (workflowInstance.getWorkflowName() == null || workflowInstance.getWorkflowName().trim().isEmpty()) {
            throw new IllegalStateException("Workflow definition name missing from workflow_name");
        }
        return workflowInstance.getWorkflowName();
    }

    private String buildSkipOutput(String baseContext, String skipInput, ActivityInstance target) {
        Map<String, Object> mergedContext = serializer.deserialize(serializer.merge(baseContext, skipInput));
        Map<String, Object> skipMetadata = new LinkedHashMap<String, Object>();
        skipMetadata.put("skipped", Boolean.TRUE);
        skipMetadata.put("activityId", target.getActivityId());
        skipMetadata.put("activityName", target.getActivityName());
        mergedContext.put(SKIP_MARKER_KEY, skipMetadata);
        return serializer.serialize(mergedContext);
    }

    private String resolveContextBefore(String workflowId, String activityName, String defaultContext) {
        String workflowContext = defaultContext;
        for (ActivityInstance activityInstance : activityRepository.findByWorkflowId(workflowId)) {
            if (activityName.equals(activityInstance.getActivityName())) {
                break;
            }
            if (isSuccessful(activityInstance) && activityInstance.getOutput() != null) {
                workflowContext = activityInstance.getOutput();
            }
        }
        return workflowContext;
    }

    private ActivityInstance findLatestActivity(String workflowId) {
        List<ActivityInstance> activityInstances = activityRepository.findByWorkflowId(workflowId);
        return activityInstances.isEmpty() ? null : activityInstances.get(activityInstances.size() - 1);
    }

    private ActivityInstance findLatestActivityAttempt(String workflowId, String activityName) {
        return activityRepository.findLatestByWorkflowIdAndActivityName(workflowId, activityName);
    }

    private String buildLockKey(String workflowId, ActivityDefinition activityDefinition) {
        return workflowId + ":" + activityDefinition.getName();
    }

    private int resolveActivitySequence(WorkflowDefinition definition, String activityName) {
        int sequence = 1;
        for (ActivityDefinition activityDefinition : definition.getActivities()) {
            if (activityName.equals(activityDefinition.getName())) {
                return sequence;
            }
            sequence++;
        }
        throw new IllegalStateException("Unknown activity in workflow definition: " + activityName);
    }

    private long nextAttempt(ActivityInstance latestAttempt) {
        if (latestAttempt == null) {
            return 1L;
        }
        return parseAttemptNumber(latestAttempt.getActivityId()) + 1L;
    }

    private long parseAttemptNumber(String activityId) {
        int attemptSeparator = activityId.lastIndexOf('-');
        if (attemptSeparator < 0 || attemptSeparator == activityId.length() - 1) {
            throw new IllegalStateException("Unexpected activity id format: " + activityId);
        }
        return Long.parseLong(activityId.substring(attemptSeparator + 1));
    }

    private String currentNode() {
        return nodeIdentity;
    }

    private String buildActivityId(String workflowId, int sequence, long attempt) {
        return workflowId + "-" + String.format("%02d", Integer.valueOf(sequence)) + "-" + String.format("%02d", Long.valueOf(attempt));
    }

    private static final class StopWorkflowExecutionException extends RuntimeException {
        private static final long serialVersionUID = 1L;
    }
}
