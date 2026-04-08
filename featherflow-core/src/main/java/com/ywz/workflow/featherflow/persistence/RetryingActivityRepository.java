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
    public void saveAttempt(
        String activityId,
        String workflowId,
        String activityName,
        String executedNode,
        String input,
        String output,
        ActivityExecutionStatus status,
        Instant modifiedAt
    ) {
        retrier.run(
            "activityRepository.saveAttempt",
            () -> delegate.saveAttempt(activityId, workflowId, activityName, executedNode, input, output, status, modifiedAt)
        );
    }

    @Override
    public List<ActivityInstance> findByWorkflowId(String workflowId) {
        return delegate.findByWorkflowId(workflowId);
    }

    @Override
    public ActivityInstance findLatestByWorkflowIdAndActivityName(String workflowId, String activityName) {
        return delegate.findLatestByWorkflowIdAndActivityName(workflowId, activityName);
    }

    @Override
    public ActivityInstance findByActivityId(String activityId) {
        return delegate.findByActivityId(activityId);
    }

    @Override
    public long countByWorkflowIdAndActivityNameAndStatus(String workflowId, String activityName, ActivityExecutionStatus status) {
        return delegate.countByWorkflowIdAndActivityNameAndStatus(workflowId, activityName, status);
    }
}
