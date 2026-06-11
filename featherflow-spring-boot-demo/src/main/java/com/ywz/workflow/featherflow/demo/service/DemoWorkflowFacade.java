package com.ywz.workflow.featherflow.demo.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ywz.workflow.featherflow.model.ActivityInstance;
import com.ywz.workflow.featherflow.model.WorkflowInstance;
import com.ywz.workflow.featherflow.repository.ActivityRepository;
import com.ywz.workflow.featherflow.repository.WorkflowRepository;
import com.ywz.workflow.featherflow.service.WorkflowCommandService;
import java.util.Arrays;
import java.util.Collections;
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

    public WorkflowInstance start(String workflowName, String bizId, String input) {
        return start(workflowName, bizId, null, input);
    }

    public WorkflowInstance start(String workflowName, String bizId, String bizKey, String input) {
        return workflowCommandService.startWorkflow(resolveWorkflowName(workflowName), bizId, bizKey, input);
    }

    public WorkflowInstance start(String bizId, String input) {
        return start(null, bizId, input);
    }

    public WorkflowInstance start(
        String workflowName,
        String bizId,
        String bizKey,
        Integer amount,
        String customerName,
        Boolean forceNotifyFailure
    ) {
        return start(workflowName, bizId, bizKey, buildInputJson(amount, customerName, forceNotifyFailure));
    }

    public WorkflowInstance start(
        String workflowName,
        String bizId,
        Integer amount,
        String customerName,
        Boolean forceNotifyFailure
    ) {
        return start(workflowName, bizId, null, amount, customerName, forceNotifyFailure);
    }

    public WorkflowInstance start(String bizId, Integer amount, String customerName, Boolean forceNotifyFailure) {
        return start(null, bizId, amount, customerName, forceNotifyFailure);
    }

    public void terminate(String workflowId) {
        workflowCommandService.terminateWorkflow(workflowId, "{\"reason\":\"demo-terminate\"}");
    }

    public void retry(String workflowId) {
        workflowCommandService.retryWorkflow(workflowId);
    }

    public void skip(String workflowId) {
        workflowCommandService.skipActivity(workflowId, "{\"manualSkip\":true}");
    }

    public List<DemoWorkflowScenario> listScenarios() {
        return Collections.unmodifiableList(Arrays.asList(
            new DemoWorkflowScenario(
                "demoSuccessWorkflow",
                "Success path",
                "Create an order, notify the customer, and complete with two successful activities.",
                "demo-biz-001",
                "order-001",
                Integer.valueOf(100),
                "Alice",
                "COMPLETED",
                "workflow_instance.status=COMPLETED and activity_instance contains two SUCCESSFUL rows.",
                Collections.<String>emptyList()
            ),
            new DemoWorkflowScenario(
                "demoRetryThenSuccessWorkflow",
                "Retry then success",
                "The first notification attempt fails, then the engine records FAILED and retries automatically.",
                "demo-biz-retry",
                "order-retry-001",
                Integer.valueOf(100),
                "Retry Alice",
                "COMPLETED",
                "The same activityName appears twice: first FAILED, then SUCCESSFUL.",
                Collections.singletonList("wait for automatic retry")
            ),
            new DemoWorkflowScenario(
                "demoHumanProcessingWorkflow",
                "Human processing",
                "Risk review always fails and the workflow enters HUMAN_PROCESSING after retries are exhausted.",
                "demo-biz-human",
                "order-human-001",
                Integer.valueOf(100),
                "Human Alice",
                "HUMAN_PROCESSING",
                "Failure details are written to activity output for operations troubleshooting.",
                Collections.singletonList("retry")
            ),
            new DemoWorkflowScenario(
                "demoTerminateSkipWorkflow",
                "Terminate and skip",
                "Manual review fails, then the sample demonstrates terminate, skip latest failed activity, and continue.",
                "demo-biz-skip",
                "order-skip-001",
                Integer.valueOf(100),
                "Skip Alice",
                "COMPLETED after terminate and skip",
                "skip appends a SUCCESSFUL manual-skip row, then finalizeOrder runs.",
                Arrays.asList("terminate", "skip")
            ),
            new DemoWorkflowScenario(
                "demoAsyncJobWorkflow",
                "Async job pattern",
                "Split a long-running job into submit and poll activities so handlers do not block for a long time.",
                "demo-biz-async",
                "order-async-001",
                Integer.valueOf(100),
                "Async Alice",
                "COMPLETED",
                "pollAsyncJob fails once and becomes SUCCESSFUL after automatic retry.",
                Collections.singletonList("wait for automatic retry")
            )
        ));
    }

    public DemoWorkflowView getWorkflow(String workflowId) {
        WorkflowInstance workflowInstance = workflowRepository.findRequired(workflowId);
        List<ActivityInstance> activities = activityRepository.findByWorkflowId(workflowId);
        ActivityInstance latestActivity = activities.isEmpty() ? null : activities.get(activities.size() - 1);
        return new DemoWorkflowView(
            workflowInstance.getWorkflowId(),
            workflowInstance.getBizId(),
            workflowInstance.getBizKey(),
            workflowInstance.getWorkflowName(),
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

    private String resolveWorkflowName(String workflowName) {
        if (workflowName == null || workflowName.trim().isEmpty()) {
            return DEMO_WORKFLOW_NAME;
        }
        return workflowName.trim();
    }
}
