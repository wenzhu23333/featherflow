package com.ywz.workflow.featherflow.model;

import java.time.Instant;

public class WorkflowOperation {

    private Long operationId;
    private final String workflowId;
    private final OperationType operationType;
    private String input;
    private OperationStatus status;
    private final Instant gmtCreated;
    private Instant gmtModified;

    public WorkflowOperation(Long operationId, String workflowId, OperationType operationType, String input, OperationStatus status, Instant gmtCreated, Instant gmtModified) {
        this.operationId = operationId;
        this.workflowId = workflowId;
        this.operationType = operationType;
        this.input = input;
        this.status = status;
        this.gmtCreated = gmtCreated;
        this.gmtModified = gmtModified;
    }

    public static WorkflowOperation pending(String workflowId, OperationType operationType, String input, Instant dueTime) {
        return new WorkflowOperation(null, workflowId, operationType, input, OperationStatus.PENDING, dueTime, dueTime);
    }

    public Long getOperationId() {
        return operationId;
    }

    public void setOperationId(Long operationId) {
        this.operationId = operationId;
    }

    public String getWorkflowId() {
        return workflowId;
    }

    public OperationType getOperationType() {
        return operationType;
    }

    public String getInput() {
        return input;
    }

    public void setInput(String input) {
        this.input = input;
    }

    public OperationStatus getStatus() {
        return status;
    }

    public void setStatus(OperationStatus status) {
        this.status = status;
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
}
