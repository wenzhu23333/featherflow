package com.ywz.workflow.featherflow.model;

public enum WorkflowStatus {
    RUNNING,
    HUMAN_PROCESSING,
    TERMINATED,
    COMPLETED;

    public static WorkflowStatus fromDatabaseValue(String value) {
        if ("SUCCESSFUL".equals(value)) {
            return COMPLETED;
        }
        return WorkflowStatus.valueOf(value);
    }
}
