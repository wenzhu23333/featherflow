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
        this.definitionRegistry = definitionRegistry;
        this.workflowRepository = workflowRepository;
        this.activityRepository = activityRepository;
        this.handlerRegistry = handlerRegistry;
        this.lockService = lockService;
        this.serializer = serializer;
        this.clock = clock;
        this.workflowRetryScheduler = workflowRetryScheduler;
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
            }
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
    public void skipActivity(String workflowId, String activityId, String skipInput) {
        WorkflowInstance workflowInstance = workflowRepository.findRequired(workflowId);
        try (WorkflowLogContext.Scope ignored = WorkflowLogContext.open(workflowInstance)) {
            ActivityInstance target = requireLatestSkippableActivity(workflowInstance, activityId);
            String baseContext = resolveContextBefore(workflowId, target.getActivityName(), workflowInstance.getInput());
            String skipOutput = buildSkipOutput(baseContext, skipInput, target);

            activityRepository.updateResult(target.getActivityId(), target.getInput(), skipOutput, ActivityExecutionStatus.SUCCESSFUL, clock.instant());
            log.info("Skip activity by manual operation, activityId={}, activityName={}", target.getActivityId(), target.getActivityName());

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

        String activityId = buildActivityId(workflowId, activitySequence);
        ActivityInstance persistedActivity = activityRepository.findByWorkflowIdAndActivityName(workflowId, activityDefinition.getName());
        if (isSuccessful(persistedActivity)) {
            return reuseSuccessfulActivityContext(workflowContext, persistedActivity, activityId, activityDefinition.getName(), "before lock acquisition");
        }

        return continueWithActivityLock(workflowId, activityDefinition, workflowContext, activityId, activitySequence);
    }

    private String continueWithActivityLock(
        String workflowId,
        ActivityDefinition activityDefinition,
        String workflowContext,
        String activityId,
        int activitySequence
    ) {
        String lockKey = buildLockKey(workflowId, activityDefinition);
        if (!lockService.tryLock(lockKey)) {
            log.info("Skip activity execution because workflow lock is already held, activityName={}, lockKey={}", activityDefinition.getName(), lockKey);
            throw new StopWorkflowExecutionException();
        }

        try {
            log.info(
                "Acquire activity lock, activityName={}, activityId={}, sequence={}",
                activityDefinition.getName(),
                activityId,
                Integer.valueOf(activitySequence)
            );
            WorkflowInstance workflowInstance = requireRunningWorkflow(workflowId, "after acquiring the activity lock");
            ActivityInstance latestActivity = activityRepository.findByWorkflowIdAndActivityName(workflowId, activityDefinition.getName());
            if (isSuccessful(latestActivity)) {
                return reuseSuccessfulActivityContext(workflowContext, latestActivity, activityId, activityDefinition.getName(), "after lock acquisition");
            }
            return executeActivity(workflowInstance, activityId, activityDefinition, workflowContext);
        } finally {
            lockService.unlock(lockKey);
            log.info("Release activity lock, activityName={}, activityId={}", activityDefinition.getName(), activityId);
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
        String activityId,
        String activityName,
        String phase
    ) {
        log.info(
            "Reuse successful activity result for idempotency, activityName={}, activityId={}, phase={}",
            activityName,
            activityId,
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

    private ActivityInstance requireLatestSkippableActivity(WorkflowInstance workflowInstance, String activityId) {
        if (workflowInstance.getStatus() != WorkflowStatus.TERMINATED) {
            throw new IllegalStateException("Skip is only allowed when workflow is TERMINATED");
        }

        ActivityInstance target = activityRepository.findByActivityId(activityId);
        if (target == null || !workflowInstance.getWorkflowId().equals(target.getWorkflowId())) {
            throw new IllegalArgumentException("Activity not found: " + activityId);
        }

        ActivityInstance latestActivity = findLatestActivity(workflowInstance.getWorkflowId());
        if (latestActivity == null || !target.getActivityId().equals(latestActivity.getActivityId())) {
            throw new IllegalStateException("Skip only supports the latest recorded activity");
        }
        if (target.getStatus() == ActivityExecutionStatus.SUCCESSFUL) {
            throw new IllegalStateException("Latest activity is already SUCCESSFUL, skip is not needed");
        }
        return target;
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
        activityRepository.saveOrUpdateResult(
            activityId,
            workflowInstance.getWorkflowId(),
            activityDefinition.getName(),
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
        activityRepository.saveOrUpdateResult(
            activityId,
            workflowInstance.getWorkflowId(),
            activityDefinition.getName(),
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
        Map<String, Object> ext = serializer.deserialize(workflowInstance.getExtCol());
        Map<String, Object> retryCounts = readRetryCounts(ext);
        int nextRetryCount = retryCounts.containsKey(activityDefinition.getName())
            ? ((Number) retryCounts.get(activityDefinition.getName())).intValue() + 1
            : 1;

        retryCounts.put(activityDefinition.getName(), Integer.valueOf(nextRetryCount));
        ext.put("retryCounts", retryCounts);
        workflowInstance.setExtCol(serializer.serialize(ext));
        workflowInstance.setGmtModified(now);

        if (nextRetryCount <= activityDefinition.getMaxRetryTimes()) {
            log.info(
                "Schedule workflow retry, activityName={}, retryCount={}, maxRetryTimes={}, nextRetryAt={}",
                activityDefinition.getName(),
                Integer.valueOf(nextRetryCount),
                Integer.valueOf(activityDefinition.getMaxRetryTimes()),
                now.plus(activityDefinition.getRetryInterval())
            );
            workflowRetryScheduler.scheduleRetry(workflowInstance.getWorkflowId(), activityDefinition.getRetryInterval());
        } else {
            workflowInstance.setStatus(WorkflowStatus.HUMAN_PROCESSING);
            log.warn(
                "Workflow moved to HUMAN_PROCESSING because retries are exhausted, activityName={}, retryCount={}, maxRetryTimes={}",
                activityDefinition.getName(),
                Integer.valueOf(nextRetryCount),
                Integer.valueOf(activityDefinition.getMaxRetryTimes())
            );
        }
        workflowRepository.update(workflowInstance);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> readRetryCounts(Map<String, Object> ext) {
        Object retryCounts = ext.get("retryCounts");
        if (retryCounts instanceof Map) {
            return new LinkedHashMap<String, Object>((Map<String, Object>) retryCounts);
        }
        return new LinkedHashMap<String, Object>();
    }

    private String getDefinitionName(WorkflowInstance workflowInstance) {
        Object definitionName = serializer.deserialize(workflowInstance.getExtCol()).get("definitionName");
        if (definitionName == null) {
            throw new IllegalStateException("Workflow definition name missing from ext_col");
        }
        return definitionName.toString();
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

    private String buildLockKey(String workflowId, ActivityDefinition activityDefinition) {
        return workflowId + ":" + activityDefinition.getName();
    }

    private String buildActivityId(String workflowId, int sequence) {
        return workflowId + "-" + String.format("%02d", Integer.valueOf(sequence));
    }

    private static final class StopWorkflowExecutionException extends RuntimeException {
        private static final long serialVersionUID = 1L;
    }
}
