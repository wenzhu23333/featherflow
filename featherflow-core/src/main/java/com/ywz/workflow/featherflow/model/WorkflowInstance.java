package com.ywz.workflow.featherflow.model;

import java.time.Instant;

public class WorkflowInstance {

    private final String workflowId;
    private final String bizId;
    private String bizKey;
    private final String workflowName;
    private final String startNode;
    private final Instant gmtCreated;
    private Instant gmtModified;
    private String input;
    private WorkflowStatus status;

    public WorkflowInstance(
        String workflowId,
        String bizId,
        String workflowName,
        String startNode,
        Instant gmtCreated,
        Instant gmtModified,
        String input,
        WorkflowStatus status
    ) {
        this(workflowId, bizId, null, workflowName, startNode, gmtCreated, gmtModified, input, status);
    }

    public WorkflowInstance(
        String workflowId,
        String bizId,
        String bizKey,
        String workflowName,
        String startNode,
        Instant gmtCreated,
        Instant gmtModified,
        String input,
        WorkflowStatus status
    ) {
        this.workflowId = workflowId;
        this.bizId = bizId;
        this.bizKey = bizKey;
        this.workflowName = workflowName;
        this.startNode = startNode;
        this.gmtCreated = gmtCreated;
        this.gmtModified = gmtModified;
        this.input = input;
        this.status = status;
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

    public void setBizKey(String bizKey) {
        this.bizKey = bizKey;
    }

    public String getWorkflowName() {
        return workflowName;
    }

    public String getStartNode() {
        return startNode;
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

    public WorkflowStatus getStatus() {
        return status;
    }

    public void setStatus(WorkflowStatus status) {
        this.status = status;
    }
}
