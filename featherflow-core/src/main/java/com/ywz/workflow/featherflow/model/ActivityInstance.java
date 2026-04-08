package com.ywz.workflow.featherflow.model;

import java.time.Instant;

public class ActivityInstance {

    private final String activityId;
    private final String workflowId;
    private final String activityName;
    private final String executedNode;
    private final Instant gmtCreated;
    private final Instant gmtModified;
    private final String input;
    private final String output;
    private final ActivityExecutionStatus status;

    public ActivityInstance(
        String activityId,
        String workflowId,
        String activityName,
        Instant gmtCreated,
        Instant gmtModified,
        String input,
        String output,
        ActivityExecutionStatus status
    ) {
        this(activityId, workflowId, activityName, null, gmtCreated, gmtModified, input, output, status);
    }

    public ActivityInstance(
        String activityId,
        String workflowId,
        String activityName,
        String executedNode,
        Instant gmtCreated,
        Instant gmtModified,
        String input,
        String output,
        ActivityExecutionStatus status
    ) {
        this.activityId = activityId;
        this.workflowId = workflowId;
        this.activityName = activityName;
        this.executedNode = executedNode;
        this.gmtCreated = gmtCreated;
        this.gmtModified = gmtModified;
        this.input = input;
        this.output = output;
        this.status = status;
    }

    public String getActivityId() {
        return activityId;
    }

    public String getWorkflowId() {
        return workflowId;
    }

    public String getActivityName() {
        return activityName;
    }

    public String getExecutedNode() {
        return executedNode;
    }

    public Instant getGmtCreated() {
        return gmtCreated;
    }

    public Instant getGmtModified() {
        return gmtModified;
    }

    public String getInput() {
        return input;
    }

    public String getOutput() {
        return output;
    }

    public ActivityExecutionStatus getStatus() {
        return status;
    }
}
