package com.ywz.workflow.featherflow.context;

import com.ywz.workflow.featherflow.model.WorkflowInstance;

public final class WorkflowContextSnapshot {

    private final String workflowId;
    private final String bizId;
    private final String bizKey;

    public WorkflowContextSnapshot(String workflowId, String bizId, String bizKey) {
        this.workflowId = workflowId;
        this.bizId = bizId;
        this.bizKey = bizKey;
    }

    public static WorkflowContextSnapshot from(WorkflowInstance workflowInstance) {
        return new WorkflowContextSnapshot(
            workflowInstance.getWorkflowId(),
            workflowInstance.getBizId(),
            workflowInstance.getBizKey()
        );
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
}
