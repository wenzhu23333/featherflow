package com.ywz.workflow.featherflow.handler;

import com.ywz.workflow.featherflow.model.WorkflowStatus;

/**
 * Throw from an activity handler to stop the workflow as TERMINATED without entering retry.
 */
public class WorkflowTerminateSignalException extends WorkflowControlSignalException {

    public WorkflowTerminateSignalException(String message) {
        super(WorkflowStatus.TERMINATED, message);
    }

    public WorkflowTerminateSignalException(String message, Throwable cause) {
        super(WorkflowStatus.TERMINATED, message, cause);
    }
}
