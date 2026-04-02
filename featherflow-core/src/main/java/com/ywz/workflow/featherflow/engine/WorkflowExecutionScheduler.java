package com.ywz.workflow.featherflow.engine;

/**
 * Submits workflow execution tasks into the worker pool.
 */
public interface WorkflowExecutionScheduler {

    void schedule(String workflowId);
}
