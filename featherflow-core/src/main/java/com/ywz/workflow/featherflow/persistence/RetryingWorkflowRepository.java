package com.ywz.workflow.featherflow.persistence;

import com.ywz.workflow.featherflow.model.WorkflowInstance;
import com.ywz.workflow.featherflow.model.WorkflowStatus;
import com.ywz.workflow.featherflow.repository.WorkflowRepository;
import java.time.Instant;

/**
 * Retries framework-owned workflow state writes before delegating to the real repository.
 */
public class RetryingWorkflowRepository implements WorkflowRepository {

    private final WorkflowRepository delegate;
    private final PersistenceWriteRetrier retrier;

    public RetryingWorkflowRepository(WorkflowRepository delegate, PersistenceWriteRetrier retrier) {
        this.delegate = delegate;
        this.retrier = retrier;
    }

    @Override
    public void save(WorkflowInstance workflowInstance) {
        retrier.run("workflowRepository.save", () -> delegate.save(workflowInstance));
    }

    @Override
    public void update(WorkflowInstance workflowInstance) {
        retrier.run("workflowRepository.update", () -> delegate.update(workflowInstance));
    }

    @Override
    public WorkflowInstance find(String workflowId) {
        return delegate.find(workflowId);
    }

    @Override
    public WorkflowInstance findRequired(String workflowId) {
        return delegate.findRequired(workflowId);
    }

    @Override
    public void updateStatus(String workflowId, WorkflowStatus status, Instant modifiedAt) {
        retrier.run("workflowRepository.updateStatus", () -> delegate.updateStatus(workflowId, status, modifiedAt));
    }
}
