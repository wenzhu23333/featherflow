package com.ywz.workflow.featherflow.repository;

import com.ywz.workflow.featherflow.model.WorkflowInstance;
import com.ywz.workflow.featherflow.model.WorkflowStatus;
import java.time.Instant;
import java.util.List;

public interface WorkflowRepository {

    void save(WorkflowInstance workflowInstance);

    void update(WorkflowInstance workflowInstance);

    WorkflowInstance find(String workflowId);

    WorkflowInstance findRequired(String workflowId);

    void updateStatus(String workflowId, WorkflowStatus status, Instant modifiedAt);

    /**
     * Atomically updates workflow status only when the current status matches the expected status.
     * Repositories must override this with a single conditional write. The default is a safe no-op
     * because a read followed by updateStatus can overwrite concurrent status changes.
     */
    default boolean updateStatusIfStatus(String workflowId, WorkflowStatus expectedStatus, WorkflowStatus status, Instant modifiedAt) {
        return false;
    }

    default boolean updateModifiedAtIfStatus(String workflowId, WorkflowStatus status, Instant modifiedAt) {
        return false;
    }

    /**
     * Finds workflows that are still RUNNING but have not been updated before the given time.
     * This is used for lightweight restart recovery after a pod or process exits unexpectedly.
     */
    List<WorkflowInstance> findRunningModifiedBefore(Instant modifiedBefore, int limit);
}
