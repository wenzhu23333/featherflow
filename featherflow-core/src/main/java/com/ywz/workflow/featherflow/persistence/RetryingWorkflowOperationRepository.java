package com.ywz.workflow.featherflow.persistence;

import com.ywz.workflow.featherflow.model.WorkflowOperation;
import com.ywz.workflow.featherflow.repository.WorkflowOperationRepository;
import java.time.Instant;
import java.util.List;

/**
 * Retries external operation table writes such as claim and status updates.
 */
public class RetryingWorkflowOperationRepository implements WorkflowOperationRepository {

    private final WorkflowOperationRepository delegate;
    private final PersistenceWriteRetrier retrier;

    public RetryingWorkflowOperationRepository(WorkflowOperationRepository delegate, PersistenceWriteRetrier retrier) {
        this.delegate = delegate;
        this.retrier = retrier;
    }

    @Override
    public void savePendingOperation(WorkflowOperation workflowOperation) {
        retrier.run("workflowOperationRepository.savePendingOperation", () -> delegate.savePendingOperation(workflowOperation));
    }

    @Override
    public List<WorkflowOperation> findDuePendingOperations(Instant now) {
        return delegate.findDuePendingOperations(now);
    }

    @Override
    public boolean claimPendingOperation(Long operationId, Instant modifiedAt) {
        return retrier.call("workflowOperationRepository.claimPendingOperation", () -> Boolean.valueOf(delegate.claimPendingOperation(operationId, modifiedAt)))
            .booleanValue();
    }

    @Override
    public List<WorkflowOperation> findPendingByWorkflowId(String workflowId) {
        return delegate.findPendingByWorkflowId(workflowId);
    }

    @Override
    public List<WorkflowOperation> findAll() {
        return delegate.findAll();
    }

    @Override
    public void markSuccessful(Long operationId, Instant modifiedAt) {
        retrier.run("workflowOperationRepository.markSuccessful", () -> delegate.markSuccessful(operationId, modifiedAt));
    }

    @Override
    public void markFailed(Long operationId, Instant modifiedAt) {
        retrier.run("workflowOperationRepository.markFailed", () -> delegate.markFailed(operationId, modifiedAt));
    }
}
