package com.ywz.workflow.featherflow.support;

import com.ywz.workflow.featherflow.model.ActivityExecutionStatus;
import com.ywz.workflow.featherflow.model.ActivityInstance;
import com.ywz.workflow.featherflow.repository.ActivityRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class InMemoryActivityRepository implements ActivityRepository {

    private final Map<String, ActivityInstance> byActivityId = new ConcurrentHashMap<String, ActivityInstance>();

    @Override
    public void saveAll(List<ActivityInstance> activityInstances) {
        for (ActivityInstance activityInstance : activityInstances) {
            byActivityId.put(activityInstance.getActivityId(), activityInstance);
        }
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
        ActivityInstance activityInstance = new ActivityInstance(
            activityId,
            workflowId,
            activityName,
            executedNode,
            modifiedAt,
            modifiedAt,
            input,
            output,
            status
        );
        byActivityId.put(activityId, activityInstance);
    }

    @Override
    public List<ActivityInstance> findByWorkflowId(String workflowId) {
        List<ActivityInstance> result = new ArrayList<ActivityInstance>();
        for (ActivityInstance activityInstance : byActivityId.values()) {
            if (workflowId.equals(activityInstance.getWorkflowId())) {
                result.add(activityInstance);
            }
        }
        Collections.sort(result, Comparator.comparing(ActivityInstance::getGmtCreated).thenComparing(ActivityInstance::getActivityId));
        return result;
    }

    @Override
    public ActivityInstance findLatestByWorkflowIdAndActivityName(String workflowId, String activityName) {
        ActivityInstance latest = null;
        for (ActivityInstance activityInstance : findByWorkflowId(workflowId)) {
            if (activityName.equals(activityInstance.getActivityName())) {
                latest = activityInstance;
            }
        }
        return latest;
    }

    @Override
    public ActivityInstance findByActivityId(String activityId) {
        return byActivityId.get(activityId);
    }

    @Override
    public long countByWorkflowIdAndActivityNameAndStatus(String workflowId, String activityName, ActivityExecutionStatus status) {
        long count = 0L;
        for (ActivityInstance activityInstance : byActivityId.values()) {
            if (
                workflowId.equals(activityInstance.getWorkflowId())
                    && activityName.equals(activityInstance.getActivityName())
                    && status == activityInstance.getStatus()
            ) {
                count++;
            }
        }
        return count;
    }
}
