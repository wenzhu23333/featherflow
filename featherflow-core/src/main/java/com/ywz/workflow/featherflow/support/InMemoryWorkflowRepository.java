package com.ywz.workflow.featherflow.support;

import com.ywz.workflow.featherflow.model.WorkflowInstance;
import com.ywz.workflow.featherflow.model.WorkflowStatus;
import com.ywz.workflow.featherflow.repository.WorkflowRepository;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class InMemoryWorkflowRepository implements WorkflowRepository {

    private final Map<String, WorkflowInstance> storage = new ConcurrentHashMap<String, WorkflowInstance>();

    @Override
    public void save(WorkflowInstance workflowInstance) {
        storage.put(workflowInstance.getWorkflowId(), workflowInstance);
    }

    @Override
    public void update(WorkflowInstance workflowInstance) {
        storage.put(workflowInstance.getWorkflowId(), workflowInstance);
    }

    @Override
    public WorkflowInstance find(String workflowId) {
        return storage.get(workflowId);
    }

    @Override
    public WorkflowInstance findRequired(String workflowId) {
        WorkflowInstance workflowInstance = find(workflowId);
        if (workflowInstance == null) {
            throw new IllegalArgumentException("Workflow not found: " + workflowId);
        }
        return workflowInstance;
    }

    @Override
    public void updateStatus(String workflowId, WorkflowStatus status, Instant modifiedAt) {
        WorkflowInstance workflowInstance = findRequired(workflowId);
        workflowInstance.setStatus(status);
        workflowInstance.setGmtModified(modifiedAt);
    }

    @Override
    public List<WorkflowInstance> findRunningModifiedBefore(Instant modifiedBefore, int limit) {
        return storage.values().stream()
            .filter(workflow -> workflow.getStatus() == WorkflowStatus.RUNNING)
            .filter(workflow -> workflow.getGmtModified().isBefore(modifiedBefore))
            .sorted(Comparator.comparing(WorkflowInstance::getGmtModified).thenComparing(WorkflowInstance::getWorkflowId))
            .limit(limit)
            .collect(Collectors.toList());
    }
}
