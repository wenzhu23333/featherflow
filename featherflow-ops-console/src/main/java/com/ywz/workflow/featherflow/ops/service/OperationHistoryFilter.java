package com.ywz.workflow.featherflow.ops.service;

import java.time.LocalDateTime;

public final class OperationHistoryFilter {

    private final String workflowId;
    private final String bizId;
    private final String operationType;
    private final String status;
    private final String operator;
    private final LocalDateTime createdFrom;
    private final LocalDateTime createdTo;

    public OperationHistoryFilter(
        String workflowId,
        String bizId,
        String operationType,
        String status,
        String operator,
        LocalDateTime createdFrom,
        LocalDateTime createdTo
    ) {
        this.workflowId = workflowId;
        this.bizId = bizId;
        this.operationType = operationType;
        this.status = status;
        this.operator = operator;
        this.createdFrom = createdFrom;
        this.createdTo = createdTo;
    }

    public String workflowId() {
        return workflowId;
    }

    public String bizId() {
        return bizId;
    }

    public String operationType() {
        return operationType;
    }

    public String status() {
        return status;
    }

    public String operator() {
        return operator;
    }

    public LocalDateTime createdFrom() {
        return createdFrom;
    }

    public LocalDateTime createdTo() {
        return createdTo;
    }

    public static OperationHistoryFilter empty() {
        return new OperationHistoryFilter(null, null, null, null, null, null, null);
    }
}
