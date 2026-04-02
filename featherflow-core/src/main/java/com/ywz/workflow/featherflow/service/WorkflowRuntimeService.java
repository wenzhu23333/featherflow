package com.ywz.workflow.featherflow.service;

/**
 * Internal runtime boundary shared by direct in-process commands and daemon-consumed external operations.
 */
public interface WorkflowRuntimeService {

    /**
     * Submit the workflow into the execution scheduler.
     */
    void dispatchWorkflow(String workflowId);

    /**
     * Reopen a paused workflow and reschedule it.
     */
    void retryWorkflow(String workflowId);

    /**
     * Mark the workflow as terminated.
     */
    void terminateWorkflow(String workflowId);

    /**
     * Mark the latest activity as skipped and continue the workflow.
     */
    void skipActivity(String workflowId, String activityId, String input);
}
