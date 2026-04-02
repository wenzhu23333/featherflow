package com.ywz.workflow.featherflow.logging;

import com.ywz.workflow.featherflow.model.WorkflowInstance;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.MDC;

public final class WorkflowLogContext {

    public static final String WORKFLOW_ID = "workflowId";
    public static final String BIZ_ID = "bizId";

    private WorkflowLogContext() {
    }

    public static Scope open(WorkflowInstance workflowInstance) {
        return open(snapshot(workflowInstance));
    }

    public static Scope open(String workflowId, String bizId) {
        return open(snapshot(workflowId, bizId));
    }

    public static Scope open(Map<String, String> context) {
        Map<String, String> previous = MDC.getCopyOfContextMap();
        if (context == null || context.isEmpty()) {
            MDC.clear();
        } else {
            MDC.setContextMap(context);
        }
        return new Scope(previous);
    }

    public static Map<String, String> snapshot(WorkflowInstance workflowInstance) {
        return snapshot(workflowInstance.getWorkflowId(), workflowInstance.getBizId());
    }

    public static Map<String, String> snapshot(String workflowId, String bizId) {
        Map<String, String> context = capture();
        putIfNotBlank(context, WORKFLOW_ID, workflowId);
        putIfNotBlank(context, BIZ_ID, bizId);
        return context;
    }

    public static Map<String, String> capture() {
        Map<String, String> current = MDC.getCopyOfContextMap();
        return current == null ? new LinkedHashMap<String, String>() : new LinkedHashMap<String, String>(current);
    }

    private static void putIfNotBlank(Map<String, String> context, String key, String value) {
        if (value == null || value.trim().isEmpty()) {
            context.remove(key);
            return;
        }
        context.put(key, value);
    }

    public static final class Scope implements AutoCloseable {

        private final Map<String, String> previous;

        private Scope(Map<String, String> previous) {
            this.previous = previous;
        }

        @Override
        public void close() {
            if (previous == null || previous.isEmpty()) {
                MDC.clear();
            } else {
                MDC.setContextMap(previous);
            }
        }
    }
}
