package com.ywz.workflow.featherflow.service;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.time.Duration;

public class WorkflowDefinitionStepView {

    private final int sequence;
    private final String workflowName;
    private final String activityName;
    private final String desc;
    private final String handler;
    private final Duration retryInterval;
    private final int maxRetryTimes;

    public WorkflowDefinitionStepView(
        int sequence,
        String workflowName,
        String activityName,
        String desc,
        String handler,
        Duration retryInterval,
        int maxRetryTimes
    ) {
        this.sequence = sequence;
        this.workflowName = workflowName;
        this.activityName = activityName;
        this.desc = desc;
        this.handler = handler;
        this.retryInterval = retryInterval;
        this.maxRetryTimes = maxRetryTimes;
    }

    public int getSequence() {
        return sequence;
    }

    public String getWorkflowName() {
        return workflowName;
    }

    public String getActivityName() {
        return activityName;
    }

    public String getDesc() {
        return desc;
    }

    public String getHandler() {
        return handler;
    }

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    public Duration getRetryInterval() {
        return retryInterval;
    }

    public int getMaxRetryTimes() {
        return maxRetryTimes;
    }
}
