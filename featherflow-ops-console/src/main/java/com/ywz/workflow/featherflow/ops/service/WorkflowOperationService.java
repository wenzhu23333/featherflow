package com.ywz.workflow.featherflow.ops.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ywz.workflow.featherflow.ops.view.WorkflowDetailView;
import com.ywz.workflow.featherflow.ops.web.OperationForm;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class WorkflowOperationService {

    private static final String OPS_CONSOLE_SOURCE = "FEATHERFLOW_OPS_CONSOLE";

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final WorkflowQueryService workflowQueryService;

    public WorkflowOperationService(
        JdbcTemplate jdbcTemplate,
        ObjectMapper objectMapper,
        WorkflowQueryService workflowQueryService
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
        this.workflowQueryService = workflowQueryService;
    }

    public void submitTerminate(String workflowId) {
        WorkflowDetailView detail = loadWorkflow(workflowId);
        if (!canTerminate(detail.getWorkflowStatus())) {
            throw badRequest("Terminate only allowed when workflow is RUNNING or HUMAN_PROCESSING");
        }
        insertOperation(workflowId, "TERMINATE", buildInput(null, null, null));
    }

    public void submitRetry(String workflowId) {
        WorkflowDetailView detail = loadWorkflow(workflowId);
        String status = detail.getWorkflowStatus();
        if (!"HUMAN_PROCESSING".equals(status) && !"TERMINATED".equals(status)) {
            throw badRequest("Retry only allowed when workflow is HUMAN_PROCESSING or TERMINATED");
        }
        insertOperation(workflowId, "RETRY", buildInput(null, null, null));
    }

    public void submitSkip(String workflowId, OperationForm form) {
        String operator = required(form.getOperator(), "operator");
        String reason = required(form.getReason(), "reason");
        WorkflowDetailView detail = loadWorkflow(workflowId);
        if (!"TERMINATED".equals(detail.getWorkflowStatus())) {
            throw badRequest("Skip only allowed when workflow is TERMINATED");
        }

        String latestActivityId = detail.getLatestActivityId();
        if (isBlank(latestActivityId)) {
            throw badRequest("Skip requires an existing latest activity");
        }

        insertOperation(workflowId, "SKIP_ACTIVITY", buildInput(operator, reason, null));
    }

    private WorkflowDetailView loadWorkflow(String workflowId) {
        return workflowQueryService.getWorkflowDetail(workflowId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Workflow not found: " + workflowId));
    }

    private boolean canTerminate(String workflowStatus) {
        return "RUNNING".equals(workflowStatus) || "HUMAN_PROCESSING".equals(workflowStatus);
    }

    private String buildInput(String operator, String reason, String activityId) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("source", OPS_CONSOLE_SOURCE);
        if (!isBlank(operator)) {
            payload.put("operator", operator);
        }
        if (!isBlank(reason)) {
            payload.put("reason", reason);
        }
        if (!isBlank(activityId)) {
            payload.put("activityId", activityId);
        }
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to build workflow operation input", ex);
        }
    }

    private void insertOperation(String workflowId, String operationType, String input) {
        LocalDateTime now = LocalDateTime.now();
        jdbcTemplate.update(
            "insert into workflow_operation "
                + "(workflow_id, operation_type, input, status, gmt_created, gmt_modified) "
                + "values (?, ?, ?, 'PENDING', ?, ?)",
            workflowId,
            operationType,
            input,
            now,
            now
        );
    }

    private String required(String value, String fieldName) {
        String normalized = trimToNull(value);
        if (normalized == null) {
            throw badRequest("Missing required field: " + fieldName);
        }
        return normalized;
    }

    private String trimToNull(String value) {
        if (isBlank(value)) {
            return null;
        }
        return value.trim();
    }

    private ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
