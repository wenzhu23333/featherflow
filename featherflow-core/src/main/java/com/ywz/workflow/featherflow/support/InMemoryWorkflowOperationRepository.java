package com.ywz.workflow.featherflow.support;

import com.ywz.workflow.featherflow.model.OperationStatus;
import com.ywz.workflow.featherflow.model.WorkflowOperation;
import com.ywz.workflow.featherflow.repository.WorkflowOperationRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;

public class InMemoryWorkflowOperationRepository implements WorkflowOperationRepository {

    private final AtomicLong sequence = new AtomicLong(1L);
    private final List<WorkflowOperation> operations = new CopyOnWriteArrayList<WorkflowOperation>();

    @Override
    public void savePendingOperation(WorkflowOperation workflowOperation) {
        if (workflowOperation.getOperationId() == null) {
            workflowOperation.setOperationId(Long.valueOf(sequence.getAndIncrement()));
        }
        operations.add(workflowOperation);
    }

    @Override
    public List<WorkflowOperation> findDuePendingOperations(Instant now) {
        List<WorkflowOperation> result = new ArrayList<WorkflowOperation>();
        for (WorkflowOperation operation : operations) {
            if (operation.getStatus() == OperationStatus.PENDING && !operation.getGmtModified().isAfter(now)) {
                result.add(operation);
            }
        }
        Collections.sort(result, Comparator.comparing(WorkflowOperation::getOperationId));
        return result;
    }

    @Override
    public boolean claimPendingOperation(Long operationId, Instant modifiedAt) {
        WorkflowOperation operation = findRequired(operationId);
        synchronized (operation) {
            if (operation.getStatus() != OperationStatus.PENDING) {
                return false;
            }
            operation.setStatus(OperationStatus.PROCESSING);
            operation.setGmtModified(modifiedAt);
            return true;
        }
    }

    @Override
    public List<WorkflowOperation> findPendingByWorkflowId(String workflowId) {
        List<WorkflowOperation> result = new ArrayList<WorkflowOperation>();
        for (WorkflowOperation operation : operations) {
            if (operation.getStatus() == OperationStatus.PENDING && workflowId.equals(operation.getWorkflowId())) {
                result.add(operation);
            }
        }
        Collections.sort(result, Comparator.comparing(WorkflowOperation::getOperationId));
        return result;
    }

    @Override
    public List<WorkflowOperation> findAll() {
        List<WorkflowOperation> result = new ArrayList<WorkflowOperation>(operations);
        Collections.sort(result, Comparator.comparing(WorkflowOperation::getOperationId));
        return result;
    }

    @Override
    public void markSuccessful(Long operationId, Instant modifiedAt) {
        WorkflowOperation operation = findRequired(operationId);
        synchronized (operation) {
            operation.setStatus(OperationStatus.SUCCESSFUL);
            operation.setGmtModified(modifiedAt);
        }
    }

    @Override
    public void markFailed(Long operationId, Instant modifiedAt) {
        WorkflowOperation operation = findRequired(operationId);
        synchronized (operation) {
            operation.setStatus(OperationStatus.FAILED);
            operation.setGmtModified(modifiedAt);
        }
    }

    private WorkflowOperation findRequired(Long operationId) {
        for (WorkflowOperation operation : operations) {
            if (operationId.equals(operation.getOperationId())) {
                return operation;
            }
        }
        throw new IllegalArgumentException("Operation not found: " + operationId);
    }
}
