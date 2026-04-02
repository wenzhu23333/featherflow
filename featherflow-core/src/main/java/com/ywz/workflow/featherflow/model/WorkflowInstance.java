package com.ywz.workflow.featherflow.model;

import java.time.Instant;

public class WorkflowInstance {

    private final String workflowId;
    private final String bizId;
    private final Instant gmtCreated;
    private Instant gmtModified;
    private String input;
    private WorkflowStatus status;
    private String extCol;

    public WorkflowInstance(String workflowId, String bizId, Instant gmtCreated, Instant gmtModified, String input, WorkflowStatus status, String extCol) {
        this.workflowId = workflowId;
        this.bizId = bizId;
        this.gmtCreated = gmtCreated;
        this.gmtModified = gmtModified;
        this.input = input;
        this.status = status;
        this.extCol = extCol;
    }

    public String getWorkflowId() {
        return workflowId;
    }

    public String getBizId() {
        return bizId;
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

    public String getExtCol() {
        return extCol;
    }

    public void setExtCol(String extCol) {
        this.extCol = extCol;
    }
}
