package com.ywz.workflow.featherflow.service;

import com.ywz.workflow.featherflow.model.WorkflowInstance;
import com.ywz.workflow.featherflow.model.WorkflowStatus;

/**
 * Business-facing command entry point.
 *
 * <p>These methods are used by in-process callers. They operate directly on the runtime service rather than
 * going through {@code workflow_operation}. External operations systems should write command rows for the
 * daemon to consume.
 */
public interface WorkflowCommandService {

    /**
     * Create a workflow instance and submit it for execution.
     */
    WorkflowInstance startWorkflow(String definitionName, String bizId, String input);

    /**
     * Change workflow status directly for administrative use.
     */
    void changeWorkflowStatus(String workflowId, WorkflowStatus status, String note);

    /**
     * Reopen a {@code HUMAN_PROCESSING} or {@code TERMINATED} workflow and continue from the latest
     * persisted activity snapshot.
     */
    void retryWorkflow(String workflowId);

    /**
     * Mark the workflow as {@code TERMINATED}. The engine stops before the next activity.
     */
    void terminateWorkflow(String workflowId, String input);

    /**
     * Mark the latest persisted activity as skipped and continue the workflow from the next step.
     */
    void skipActivity(String workflowId, String activityId, String input);
}
