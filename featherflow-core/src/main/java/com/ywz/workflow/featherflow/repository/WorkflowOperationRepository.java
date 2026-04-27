package com.ywz.workflow.featherflow.repository;

import com.ywz.workflow.featherflow.model.WorkflowOperation;
import java.time.Instant;
import java.util.List;

public interface WorkflowOperationRepository {

    void savePendingOperation(WorkflowOperation workflowOperation);

    List<WorkflowOperation> findDuePendingOperations(Instant now);

    boolean claimPendingOperation(Long operationId, String claimedInput, Instant modifiedAt);

    List<WorkflowOperation> findPendingByWorkflowId(String workflowId);

    List<WorkflowOperation> findAll();

    void markSuccessful(Long operationId, Instant modifiedAt);

    void markFailed(Long operationId, Instant modifiedAt);
}
