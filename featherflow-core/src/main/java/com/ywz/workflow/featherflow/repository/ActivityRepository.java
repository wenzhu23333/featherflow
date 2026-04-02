package com.ywz.workflow.featherflow.repository;

import com.ywz.workflow.featherflow.model.ActivityExecutionStatus;
import com.ywz.workflow.featherflow.model.ActivityInstance;
import java.time.Instant;
import java.util.List;

public interface ActivityRepository {

    void saveAll(List<ActivityInstance> activityInstances);

    void saveOrUpdateResult(
        String activityId,
        String workflowId,
        String activityName,
        String input,
        String output,
        ActivityExecutionStatus status,
        Instant modifiedAt
    );

    List<ActivityInstance> findByWorkflowId(String workflowId);

    ActivityInstance findByWorkflowIdAndActivityName(String workflowId, String activityName);

    ActivityInstance findByActivityId(String activityId);

    void update(ActivityInstance activityInstance);

    void markSuccessful(String workflowId, String activityName, String output, Instant modifiedAt);

    void updateResult(String activityId, String input, String output, ActivityExecutionStatus status, Instant modifiedAt);
}
