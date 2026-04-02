package com.ywz.workflow.featherflow.service;

import com.ywz.workflow.featherflow.engine.WorkflowEngine;
import com.ywz.workflow.featherflow.engine.WorkflowExecutionScheduler;
import com.ywz.workflow.featherflow.logging.WorkflowLogContext;
import com.ywz.workflow.featherflow.model.WorkflowInstance;
import com.ywz.workflow.featherflow.model.WorkflowStatus;
import com.ywz.workflow.featherflow.repository.WorkflowRepository;
import com.ywz.workflow.featherflow.support.WorkflowContextSerializer;
import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Shared runtime implementation used by both direct API calls and daemon-dispatched external operations.
 */
public class DefaultWorkflowRuntimeService implements WorkflowRuntimeService {

    private static final Logger log = LoggerFactory.getLogger(DefaultWorkflowRuntimeService.class);

    private final WorkflowRepository workflowRepository;
    private final WorkflowEngine workflowEngine;
    private final WorkflowExecutionScheduler workflowExecutionScheduler;
    private final WorkflowContextSerializer serializer;
    private final Clock clock;

    public DefaultWorkflowRuntimeService(
        WorkflowRepository workflowRepository,
        WorkflowEngine workflowEngine,
        WorkflowExecutionScheduler workflowExecutionScheduler,
        WorkflowContextSerializer serializer,
        Clock clock
    ) {
        this.workflowRepository = workflowRepository;
        this.workflowEngine = workflowEngine;
        this.workflowExecutionScheduler = workflowExecutionScheduler;
        this.serializer = serializer;
        this.clock = clock;
    }

    @Override
    public void dispatchWorkflow(String workflowId) {
        WorkflowInstance workflowInstance = workflowRepository.findRequired(workflowId);
        try (WorkflowLogContext.Scope ignored = WorkflowLogContext.open(workflowInstance)) {
            log.info("Dispatch workflow into execution scheduler");
            workflowExecutionScheduler.schedule(workflowId);
        }
    }

    @Override
    public void retryWorkflow(String workflowId) {
        WorkflowInstance workflowInstance = workflowRepository.findRequired(workflowId);
        try (WorkflowLogContext.Scope ignored = WorkflowLogContext.open(workflowInstance)) {
            if (workflowInstance.getStatus() != WorkflowStatus.HUMAN_PROCESSING && workflowInstance.getStatus() != WorkflowStatus.TERMINATED) {
                throw new IllegalStateException("Only HUMAN_PROCESSING or TERMINATED workflows support retry");
            }
            WorkflowStatus previousStatus = workflowInstance.getStatus();
            workflowInstance.setExtCol(serializer.serialize(resetRetryCounts(workflowInstance)));
            workflowInstance.setStatus(WorkflowStatus.RUNNING);
            workflowInstance.setGmtModified(clock.instant());
            workflowRepository.update(workflowInstance);
            log.info("Reopen workflow for retry, previousStatus={}, newStatus={}", previousStatus, WorkflowStatus.RUNNING);
            workflowExecutionScheduler.schedule(workflowId);
        }
    }

    @Override
    public void terminateWorkflow(String workflowId) {
        WorkflowInstance workflowInstance = workflowRepository.findRequired(workflowId);
        try (WorkflowLogContext.Scope ignored = WorkflowLogContext.open(workflowInstance)) {
            workflowRepository.updateStatus(workflowId, WorkflowStatus.TERMINATED, clock.instant());
            log.info("Terminate workflow directly, previousStatus={}", workflowInstance.getStatus());
        }
    }

    @Override
    public void skipActivity(String workflowId, String input) {
        WorkflowInstance workflowInstance = workflowRepository.findRequired(workflowId);
        try (WorkflowLogContext.Scope ignored = WorkflowLogContext.open(workflowInstance)) {
            workflowEngine.skipActivity(workflowId, input);
            log.info("Continue workflow after manual skip of latest activity");
            workflowExecutionScheduler.schedule(workflowId);
        }
    }

    private Map<String, Object> resetRetryCounts(WorkflowInstance workflowInstance) {
        Map<String, Object> ext = serializer.deserialize(workflowInstance.getExtCol());
        ext.put("retryCounts", new LinkedHashMap<String, Object>());
        return ext;
    }
}
