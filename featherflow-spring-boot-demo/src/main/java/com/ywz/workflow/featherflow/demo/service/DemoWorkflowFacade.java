package com.ywz.workflow.featherflow.demo.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ywz.workflow.featherflow.model.ActivityInstance;
import com.ywz.workflow.featherflow.model.WorkflowInstance;
import com.ywz.workflow.featherflow.repository.ActivityRepository;
import com.ywz.workflow.featherflow.repository.WorkflowRepository;
import com.ywz.workflow.featherflow.service.WorkflowCommandService;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

/**
 * Thin business-facing adapter used by the demo controller and tests.
 */
@Service
public class DemoWorkflowFacade {

    private static final String DEMO_WORKFLOW_NAME = "demoOrderWorkflow";

    private final WorkflowCommandService workflowCommandService;
    private final WorkflowRepository workflowRepository;
    private final ActivityRepository activityRepository;
    private final ObjectMapper objectMapper;

    public DemoWorkflowFacade(
        WorkflowCommandService workflowCommandService,
        WorkflowRepository workflowRepository,
        ActivityRepository activityRepository,
        ObjectMapper objectMapper
    ) {
        this.workflowCommandService = workflowCommandService;
        this.workflowRepository = workflowRepository;
        this.activityRepository = activityRepository;
        this.objectMapper = objectMapper;
    }

    public WorkflowInstance start(String bizId, String input) {
        return workflowCommandService.startWorkflow(DEMO_WORKFLOW_NAME, bizId, input);
    }

    public WorkflowInstance start(String bizId, Integer amount, String customerName, Boolean forceNotifyFailure) {
        return start(bizId, buildInputJson(amount, customerName, forceNotifyFailure));
    }

    public void terminate(String workflowId) {
        workflowCommandService.terminateWorkflow(workflowId, "{\"reason\":\"demo-terminate\"}");
    }

    public void retry(String workflowId) {
        workflowCommandService.retryWorkflow(workflowId);
    }

    public void skip(String workflowId, String activityId) {
        workflowCommandService.skipActivity(workflowId, activityId, "{\"manualSkip\":true}");
    }

    public DemoWorkflowView getWorkflow(String workflowId) {
        WorkflowInstance workflowInstance = workflowRepository.findRequired(workflowId);
        List<ActivityInstance> activities = activityRepository.findByWorkflowId(workflowId);
        ActivityInstance latestActivity = activities.isEmpty() ? null : activities.get(activities.size() - 1);
        return new DemoWorkflowView(
            workflowInstance.getWorkflowId(),
            workflowInstance.getBizId(),
            workflowInstance.getStatus().name(),
            latestActivity == null ? null : latestActivity.getActivityId(),
            latestActivity == null ? null : latestActivity.getActivityName()
        );
    }

    private String buildInputJson(Integer amount, String customerName, Boolean forceNotifyFailure) {
        Map<String, Object> input = new LinkedHashMap<String, Object>();
        if (amount != null) {
            input.put("amount", amount);
        }
        if (customerName != null && !customerName.trim().isEmpty()) {
            input.put("customerName", customerName);
        }
        if (Boolean.TRUE.equals(forceNotifyFailure)) {
            input.put("forceNotifyFailure", Boolean.TRUE);
        }
        try {
            return objectMapper.writeValueAsString(input);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Failed to serialize demo workflow input", e);
        }
    }
}
