package com.ywz.workflow.featherflow.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.ywz.workflow.featherflow.model.WorkflowInstance;
import com.ywz.workflow.featherflow.model.WorkflowStatus;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.Test;

class WorkflowRepositoryTest {

    @Test
    void defaultConditionalStatusUpdateShouldNotPerformNonAtomicTransition() {
        MinimalWorkflowRepository repository = new MinimalWorkflowRepository();
        WorkflowInstance workflow = new WorkflowInstance(
            "wf-default-status-update-1",
            "biz-default-status-update-1",
            "testWorkflow",
            "test-node",
            Instant.parse("2026-03-31T02:00:00Z"),
            Instant.parse("2026-03-31T02:00:00Z"),
            "{}",
            WorkflowStatus.RUNNING
        );
        repository.save(workflow);

        boolean updated = repository.updateStatusIfStatus(
            workflow.getWorkflowId(),
            WorkflowStatus.RUNNING,
            WorkflowStatus.TERMINATED,
            Instant.parse("2026-03-31T02:00:01Z")
        );

        assertThat(updated).isFalse();
        assertThat(repository.findRequired(workflow.getWorkflowId()).getStatus()).isEqualTo(WorkflowStatus.RUNNING);
    }

    private static final class MinimalWorkflowRepository implements WorkflowRepository {

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
            return Collections.emptyList();
        }
    }
}
