package com.ywz.workflow.featherflow.logging;

import com.ywz.workflow.featherflow.model.WorkflowInstance;
import com.ywz.workflow.featherflow.context.WorkflowContextSnapshot;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.MDC;

public final class WorkflowLogContext {

    public static final String WORKFLOW_ID = "workflowId";
    public static final String BIZ_ID = "bizId";
    public static final String BIZ_KEY = "bizKey";

    private WorkflowLogContext() {
    }

    public static Scope open(WorkflowInstance workflowInstance) {
        return open(WorkflowContextSnapshot.from(workflowInstance));
    }

    public static Scope open(String workflowId, String bizId) {
        return open(new WorkflowContextSnapshot(workflowId, bizId, null));
    }

    public static Scope open(WorkflowContextSnapshot contextSnapshot) {
        return open(snapshot(contextSnapshot));
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
        return snapshot(WorkflowContextSnapshot.from(workflowInstance));
    }

    public static Map<String, String> snapshot(String workflowId, String bizId) {
        return snapshot(new WorkflowContextSnapshot(workflowId, bizId, null));
    }

    public static Map<String, String> snapshot(WorkflowContextSnapshot contextSnapshot) {
        Map<String, String> context = capture();
        if (contextSnapshot == null) {
            return context;
        }
        putIfNotBlank(context, WORKFLOW_ID, contextSnapshot.getWorkflowId());
        putIfNotBlank(context, BIZ_ID, contextSnapshot.getBizId());
        putIfNotBlank(context, BIZ_KEY, contextSnapshot.getBizKey());
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
