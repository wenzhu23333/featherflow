package com.ywz.workflow.featherflow.service;

import com.ywz.workflow.featherflow.definition.WorkflowDefinition;
import com.ywz.workflow.featherflow.definition.WorkflowDefinitionRegistry;
import com.ywz.workflow.featherflow.logging.WorkflowLogContext;
import com.ywz.workflow.featherflow.model.WorkflowInstance;
import com.ywz.workflow.featherflow.model.WorkflowStatus;
import com.ywz.workflow.featherflow.repository.WorkflowRepository;
import com.ywz.workflow.featherflow.support.WorkflowContextSerializer;
import java.time.Clock;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DefaultWorkflowCommandService implements WorkflowCommandService {

    private static final Logger log = LoggerFactory.getLogger(DefaultWorkflowCommandService.class);

    private final WorkflowDefinitionRegistry definitionRegistry;
    private final WorkflowRepository workflowRepository;
    private final WorkflowIdGenerator workflowIdGenerator;
    private final WorkflowContextSerializer serializer;
    private final Clock clock;
    private final WorkflowRuntimeService workflowRuntimeService;
    private final String nodeIdentity;

    public DefaultWorkflowCommandService(
        WorkflowDefinitionRegistry definitionRegistry,
        WorkflowRepository workflowRepository,
        WorkflowIdGenerator workflowIdGenerator,
        WorkflowContextSerializer serializer,
        Clock clock,
        WorkflowRuntimeService workflowRuntimeService,
        String nodeIdentity
    ) {
        this.definitionRegistry = definitionRegistry;
        this.workflowRepository = workflowRepository;
        this.workflowIdGenerator = workflowIdGenerator;
        this.serializer = serializer;
        this.clock = clock;
        this.workflowRuntimeService = workflowRuntimeService;
        this.nodeIdentity = nodeIdentity;
    }

    @Override
    public WorkflowInstance startWorkflow(String definitionName, String bizId, String input) {
        WorkflowDefinition definition = definitionRegistry.getRequired(definitionName);
        Instant now = clock.instant();
        String workflowId = workflowIdGenerator.nextId();
        String effectiveBizId = bizId == null || bizId.trim().isEmpty() ? workflowId : bizId;
        String effectiveInput = normalizeJson(input);

        WorkflowInstance workflowInstance = new WorkflowInstance(
            workflowId,
            effectiveBizId,
            definition.getName(),
            nodeIdentity,
            now,
            now,
            effectiveInput,
            WorkflowStatus.RUNNING
        );
        try (WorkflowLogContext.Scope ignored = WorkflowLogContext.open(workflowInstance)) {
            workflowRepository.save(workflowInstance);
            log.info("Create workflow instance, definitionName={}, workflowStatus={}", definition.getName(), workflowInstance.getStatus());
            workflowRuntimeService.dispatchWorkflow(workflowId);
            log.info("Start workflow accepted, definitionName={}, activityCount={}", definition.getName(), Integer.valueOf(definition.getActivities().size()));
        }
        return workflowInstance;
    }

    @Override
    public void changeWorkflowStatus(String workflowId, WorkflowStatus status, String note) {
        WorkflowInstance workflowInstance = workflowRepository.findRequired(workflowId);
        try (WorkflowLogContext.Scope ignored = WorkflowLogContext.open(workflowInstance)) {
            WorkflowStatus previousStatus = workflowInstance.getStatus();
            workflowInstance.setStatus(status);
            workflowInstance.setGmtModified(clock.instant());
            workflowRepository.update(workflowInstance);
            log.info("Workflow status changed, fromStatus={}, toStatus={}", previousStatus, status);
        }
    }

    @Override
    public void retryWorkflow(String workflowId) {
        workflowRuntimeService.retryWorkflow(workflowId);
        WorkflowInstance workflowInstance = workflowRepository.findRequired(workflowId);
        try (WorkflowLogContext.Scope ignored = WorkflowLogContext.open(workflowInstance)) {
            log.info("Workflow retry requested from persisted latest activity context");
        }
    }

    @Override
    public void terminateWorkflow(String workflowId, String input) {
        workflowRuntimeService.terminateWorkflow(workflowId);
        WorkflowInstance workflowInstance = workflowRepository.findRequired(workflowId);
        try (WorkflowLogContext.Scope ignored = WorkflowLogContext.open(workflowInstance)) {
            log.info("Workflow terminate requested");
        }
    }

    @Override
    public void skipActivity(String workflowId, String input) {
        workflowRuntimeService.skipActivity(workflowId, normalizeJson(input));
        WorkflowInstance workflowInstance = workflowRepository.findRequired(workflowId);
        try (WorkflowLogContext.Scope ignored = WorkflowLogContext.open(workflowInstance)) {
            log.info("Workflow skip latest activity requested");
        }
    }

    private String normalizeJson(String input) {
        return input == null || input.trim().isEmpty() ? "{}" : input;
    }
}
