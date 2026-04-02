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
    public void saveOrUpdateResult(
        String activityId,
        String workflowId,
        String activityName,
        String input,
        String output,
        ActivityExecutionStatus status,
        Instant modifiedAt
    ) {
        ActivityInstance activityInstance = findByActivityId(activityId);
        if (activityInstance == null) {
            activityInstance = new ActivityInstance(
                activityId,
                workflowId,
                activityName,
                modifiedAt,
                modifiedAt,
                input,
                output,
                status
            );
        } else {
            activityInstance.setInput(input);
            activityInstance.setOutput(output);
            activityInstance.setStatus(status);
            activityInstance.setGmtModified(modifiedAt);
        }
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
        Collections.sort(result, Comparator.comparing(ActivityInstance::getActivityId));
        return result;
    }

    @Override
    public ActivityInstance findByWorkflowIdAndActivityName(String workflowId, String activityName) {
        for (ActivityInstance activityInstance : byActivityId.values()) {
            if (workflowId.equals(activityInstance.getWorkflowId()) && activityName.equals(activityInstance.getActivityName())) {
                return activityInstance;
            }
        }
        return null;
    }

    @Override
    public ActivityInstance findByActivityId(String activityId) {
        return byActivityId.get(activityId);
    }

    @Override
    public void update(ActivityInstance activityInstance) {
        byActivityId.put(activityInstance.getActivityId(), activityInstance);
    }

    @Override
    public void markSuccessful(String workflowId, String activityName, String output, Instant modifiedAt) {
        ActivityInstance activityInstance = findByWorkflowIdAndActivityName(workflowId, activityName);
        if (activityInstance == null) {
            throw new IllegalArgumentException("Activity not found for workflow " + workflowId + " and activity " + activityName);
        }
        activityInstance.setOutput(output);
        activityInstance.setStatus(ActivityExecutionStatus.SUCCESSFUL);
        activityInstance.setGmtModified(modifiedAt);
    }

    @Override
    public void updateResult(String activityId, String input, String output, ActivityExecutionStatus status, Instant modifiedAt) {
        ActivityInstance activityInstance = findByActivityId(activityId);
        if (activityInstance == null) {
            throw new IllegalArgumentException("Activity not found: " + activityId);
        }
        activityInstance.setInput(input);
        activityInstance.setOutput(output);
        activityInstance.setStatus(status);
        activityInstance.setGmtModified(modifiedAt);
    }
}
