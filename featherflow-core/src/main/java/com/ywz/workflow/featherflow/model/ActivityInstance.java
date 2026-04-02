package com.ywz.workflow.featherflow.model;

import java.time.Instant;

public class ActivityInstance {

    private final String activityId;
    private final String workflowId;
    private final String activityName;
    private final Instant gmtCreated;
    private Instant gmtModified;
    private String input;
    private String output;
    private ActivityExecutionStatus status;

    public ActivityInstance(String activityId, String workflowId, String activityName, Instant gmtCreated, Instant gmtModified, String input, String output, ActivityExecutionStatus status) {
        this.activityId = activityId;
        this.workflowId = workflowId;
        this.activityName = activityName;
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

    public Instant getGmtCreated() {
        return gmtCreated;
    }

    public Instant getGmtModified() {
        return gmtModified;
    }

    public void setGmtModified(Instant gmtModified) {
        this.gmtModified = gmtModified;
    }

    public String getInput() {
        return input;
    }

    public void setInput(String input) {
        this.input = input;
    }

    public String getOutput() {
        return output;
    }

    public void setOutput(String output) {
        this.output = output;
    }

    public ActivityExecutionStatus getStatus() {
        return status;
    }

    public void setStatus(ActivityExecutionStatus status) {
        this.status = status;
    }
}
