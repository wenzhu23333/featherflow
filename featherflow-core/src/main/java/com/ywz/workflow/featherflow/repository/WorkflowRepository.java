package com.ywz.workflow.featherflow.repository;

import com.ywz.workflow.featherflow.model.WorkflowInstance;
import com.ywz.workflow.featherflow.model.WorkflowStatus;
import java.time.Instant;

public interface WorkflowRepository {

    void save(WorkflowInstance workflowInstance);

    void update(WorkflowInstance workflowInstance);

    WorkflowInstance find(String workflowId);

    WorkflowInstance findRequired(String workflowId);

    void updateStatus(String workflowId, WorkflowStatus status, Instant modifiedAt);
}
