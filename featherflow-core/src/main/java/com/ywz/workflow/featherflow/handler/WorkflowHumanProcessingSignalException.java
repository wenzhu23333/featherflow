package com.ywz.workflow.featherflow.handler;

import com.ywz.workflow.featherflow.model.WorkflowStatus;

/**
 * Throw from an activity handler to move the workflow to HUMAN_PROCESSING without entering retry.
 */
public class WorkflowHumanProcessingSignalException extends WorkflowControlSignalException {

    public WorkflowHumanProcessingSignalException(String message) {
        super(WorkflowStatus.HUMAN_PROCESSING, message);
    }

    public WorkflowHumanProcessingSignalException(String message, Throwable cause) {
        super(WorkflowStatus.HUMAN_PROCESSING, message, cause);
    }
}
