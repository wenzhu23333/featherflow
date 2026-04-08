package com.ywz.workflow.featherflow.repository;

import com.ywz.workflow.featherflow.model.ActivityExecutionStatus;
import com.ywz.workflow.featherflow.model.ActivityInstance;
import java.time.Instant;
import java.util.List;

public interface ActivityRepository {

    void saveAll(List<ActivityInstance> activityInstances);

    void saveAttempt(
        String activityId,
        String workflowId,
        String activityName,
        String executedNode,
        String input,
        String output,
        ActivityExecutionStatus status,
        Instant modifiedAt
    );

    List<ActivityInstance> findByWorkflowId(String workflowId);

    ActivityInstance findLatestByWorkflowIdAndActivityName(String workflowId, String activityName);

    ActivityInstance findByActivityId(String activityId);

    long countByWorkflowIdAndActivityNameAndStatus(String workflowId, String activityName, ActivityExecutionStatus status);
}
