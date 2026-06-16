package com.ywz.workflow.featherflow.handler;

import com.ywz.workflow.featherflow.model.WorkflowStatus;

/**
 * Base unchecked signal that lets an activity handler ask the engine to stop normal execution.
 */
public abstract class WorkflowControlSignalException extends RuntimeException {

    private final WorkflowStatus targetStatus;

    protected WorkflowControlSignalException(WorkflowStatus targetStatus, String message) {
        super(message);
        this.targetStatus = validateTargetStatus(targetStatus);
    }

    protected WorkflowControlSignalException(WorkflowStatus targetStatus, String message, Throwable cause) {
        super(message, cause);
        this.targetStatus = validateTargetStatus(targetStatus);
    }

    public WorkflowStatus getTargetStatus() {
        return targetStatus;
    }

    private static WorkflowStatus validateTargetStatus(WorkflowStatus targetStatus) {
        if (targetStatus != WorkflowStatus.TERMINATED && targetStatus != WorkflowStatus.HUMAN_PROCESSING) {
            throw new IllegalArgumentException("Control signal target status must be TERMINATED or HUMAN_PROCESSING");
        }
        return targetStatus;
    }
}
