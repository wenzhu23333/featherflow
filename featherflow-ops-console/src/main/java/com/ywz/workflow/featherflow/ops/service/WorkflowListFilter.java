package com.ywz.workflow.featherflow.ops.service;

import java.time.LocalDateTime;

public final class WorkflowListFilter {

    private final String workflowId;
    private final String bizId;
    private final String status;
    private final String workflowName;
    private final LocalDateTime createdFrom;
    private final LocalDateTime createdTo;
    private final LocalDateTime modifiedFrom;
    private final LocalDateTime modifiedTo;

    public WorkflowListFilter(
        String workflowId,
        String bizId,
        String status,
        String workflowName,
        LocalDateTime createdFrom,
        LocalDateTime createdTo,
        LocalDateTime modifiedFrom,
        LocalDateTime modifiedTo
    ) {
        this.workflowId = workflowId;
        this.bizId = bizId;
        this.status = status;
        this.workflowName = workflowName;
        this.createdFrom = createdFrom;
        this.createdTo = createdTo;
        this.modifiedFrom = modifiedFrom;
        this.modifiedTo = modifiedTo;
    }

    public String workflowId() {
        return workflowId;
    }

    public String bizId() {
        return bizId;
    }

    public String status() {
        return status;
    }

    public String workflowName() {
        return workflowName;
    }

    public LocalDateTime createdFrom() {
        return createdFrom;
    }

    public LocalDateTime createdTo() {
        return createdTo;
    }

    public LocalDateTime modifiedFrom() {
        return modifiedFrom;
    }

    public LocalDateTime modifiedTo() {
        return modifiedTo;
    }

    public static WorkflowListFilter empty() {
        return new WorkflowListFilter(null, null, null, null, null, null, null, null);
    }
}
