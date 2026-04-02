package com.ywz.workflow.featherflow.daemon;

import com.ywz.workflow.featherflow.logging.WorkflowLogContext;
import com.ywz.workflow.featherflow.model.OperationType;
import com.ywz.workflow.featherflow.model.WorkflowOperation;
import com.ywz.workflow.featherflow.model.WorkflowInstance;
import com.ywz.workflow.featherflow.repository.WorkflowRepository;
import com.ywz.workflow.featherflow.service.WorkflowRuntimeService;
import com.ywz.workflow.featherflow.support.WorkflowContextSerializer;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Bridges daemon-consumed external operations into the same runtime service used by in-process callers.
 */
public class DefaultWorkflowOperationHandler implements WorkflowOperationHandler {

    private static final Logger log = LoggerFactory.getLogger(DefaultWorkflowOperationHandler.class);

    private final WorkflowRepository workflowRepository;
    private final WorkflowRuntimeService workflowRuntimeService;
    private final WorkflowContextSerializer serializer;

    public DefaultWorkflowOperationHandler(
        WorkflowRepository workflowRepository,
        WorkflowRuntimeService workflowRuntimeService,
        WorkflowContextSerializer serializer
    ) {
        this.workflowRepository = workflowRepository;
        this.workflowRuntimeService = workflowRuntimeService;
        this.serializer = serializer;
    }

    @Override
    public void process(WorkflowOperation operation) {
        WorkflowInstance workflowInstance = workflowRepository.findRequired(operation.getWorkflowId());
        try (WorkflowLogContext.Scope ignored = WorkflowLogContext.open(workflowInstance)) {
            switch (operation.getOperationType()) {
                case TERMINATE:
                    applyTerminate(operation);
                    return;
                case START:
                    applyStart(operation);
                    return;
                case RETRY:
                    applyRetry(operation);
                    return;
                case SKIP_ACTIVITY:
                    applySkip(operation);
                    return;
                default:
                    throw new IllegalArgumentException("Unsupported operation type: " + operation.getOperationType());
            }
        }
    }

    private void applyStart(WorkflowOperation operation) {
        log.info("Dispatch workflow start operation");
        workflowRuntimeService.dispatchWorkflow(operation.getWorkflowId());
    }

    private void applyRetry(WorkflowOperation operation) {
        log.info("Apply workflow retry operation");
        workflowRuntimeService.retryWorkflow(operation.getWorkflowId());
    }

    private void applyTerminate(WorkflowOperation operation) {
        workflowRuntimeService.terminateWorkflow(operation.getWorkflowId());
        log.info("Apply workflow terminate operation");
    }

    private void applySkip(WorkflowOperation operation) {
        Map<String, Object> instruction = serializer.deserialize(operation.getInput());
        String activityId = readRequiredActivityId(instruction);
        String payloadJson = readPayloadJson(instruction);
        log.info("Apply workflow skip activity operation, activityId={}", activityId);
        workflowRuntimeService.skipActivity(operation.getWorkflowId(), activityId, payloadJson);
    }

    private String readRequiredActivityId(Map<String, Object> instruction) {
        Object activityId = instruction.get("activityId");
        if (activityId == null) {
            throw new IllegalArgumentException("Skip activity operation requires activityId");
        }
        return activityId.toString();
    }

    @SuppressWarnings("unchecked")
    private String readPayloadJson(Map<String, Object> instruction) {
        Object payload = instruction.get("payload");
        if (payload instanceof Map) {
            return serializer.serialize((Map<String, Object>) payload);
        }
        return serializer.serialize(new LinkedHashMap<String, Object>());
    }
}
