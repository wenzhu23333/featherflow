package com.ywz.workflow.featherflow.persistence;

import com.ywz.workflow.featherflow.model.ActivityExecutionStatus;
import com.ywz.workflow.featherflow.model.ActivityInstance;
import com.ywz.workflow.featherflow.repository.ActivityRepository;
import java.time.Instant;
import java.util.List;

/**
 * Retries activity snapshot writes that belong to the framework runtime.
 */
public class RetryingActivityRepository implements ActivityRepository {

    private final ActivityRepository delegate;
    private final PersistenceWriteRetrier retrier;

    public RetryingActivityRepository(ActivityRepository delegate, PersistenceWriteRetrier retrier) {
        this.delegate = delegate;
        this.retrier = retrier;
    }

    @Override
    public void saveAll(List<ActivityInstance> activityInstances) {
        retrier.run("activityRepository.saveAll", () -> delegate.saveAll(activityInstances));
    }

    @Override
    public void saveOrUpdateResult(
        String activityId,
        String workflowId,
        String activityName,
        String input,
        String output,
        ActivityExecutionStatus status,
        Instant modifiedAt
    ) {
        retrier.run(
            "activityRepository.saveOrUpdateResult",
            () -> delegate.saveOrUpdateResult(activityId, workflowId, activityName, input, output, status, modifiedAt)
        );
    }

    @Override
    public List<ActivityInstance> findByWorkflowId(String workflowId) {
        return delegate.findByWorkflowId(workflowId);
    }

    @Override
    public ActivityInstance findByWorkflowIdAndActivityName(String workflowId, String activityName) {
        return delegate.findByWorkflowIdAndActivityName(workflowId, activityName);
    }

    @Override
    public ActivityInstance findByActivityId(String activityId) {
        return delegate.findByActivityId(activityId);
    }

    @Override
    public void update(ActivityInstance activityInstance) {
        retrier.run("activityRepository.update", () -> delegate.update(activityInstance));
    }

    @Override
    public void markSuccessful(String workflowId, String activityName, String output, Instant modifiedAt) {
        retrier.run("activityRepository.markSuccessful", () -> delegate.markSuccessful(workflowId, activityName, output, modifiedAt));
    }

    @Override
    public void updateResult(String activityId, String input, String output, ActivityExecutionStatus status, Instant modifiedAt) {
        retrier.run("activityRepository.updateResult", () -> delegate.updateResult(activityId, input, output, status, modifiedAt));
    }
}
