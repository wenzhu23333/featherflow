package com.ywz.workflow.featherflow.demo.service;

public class DemoWorkflowView {

    private final String workflowId;
    private final String bizId;
    private final String bizKey;
    private final String workflowName;
    private final String status;
    private final String latestActivityId;
    private final String latestActivityName;

    public DemoWorkflowView(
        String workflowId,
        String bizId,
        String bizKey,
        String workflowName,
        String status,
        String latestActivityId,
        String latestActivityName
    ) {
        this.workflowId = workflowId;
        this.bizId = bizId;
        this.bizKey = bizKey;
        this.workflowName = workflowName;
        this.status = status;
        this.latestActivityId = latestActivityId;
        this.latestActivityName = latestActivityName;
    }

    public String getWorkflowId() {
        return workflowId;
    }

    public String getBizId() {
        return bizId;
    }

    public String getBizKey() {
        return bizKey;
    }

    public String getWorkflowName() {
        return workflowName;
    }

    public String getStatus() {
        return status;
    }

    public String getLatestActivityId() {
        return latestActivityId;
    }

    public String getLatestActivityName() {
        return latestActivityName;
    }
}
