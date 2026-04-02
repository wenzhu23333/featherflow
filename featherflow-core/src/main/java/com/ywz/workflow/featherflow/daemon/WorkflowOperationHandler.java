package com.ywz.workflow.featherflow.daemon;

import com.ywz.workflow.featherflow.model.WorkflowOperation;

/**
 * Consumes externally submitted workflow operations after the daemon claims them.
 */
public interface WorkflowOperationHandler {

    void process(WorkflowOperation operation) throws Exception;
}
