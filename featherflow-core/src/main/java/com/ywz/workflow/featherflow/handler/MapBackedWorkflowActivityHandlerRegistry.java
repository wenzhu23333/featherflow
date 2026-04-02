package com.ywz.workflow.featherflow.handler;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class MapBackedWorkflowActivityHandlerRegistry implements WorkflowActivityHandlerRegistry {

    private final Map<String, WorkflowActivityHandler> handlers = new ConcurrentHashMap<String, WorkflowActivityHandler>();

    public void register(String name, WorkflowActivityHandler handler) {
        handlers.put(name, handler);
    }

    @Override
    public WorkflowActivityHandler getRequired(String name) {
        WorkflowActivityHandler handler = handlers.get(name);
        if (handler == null) {
            throw new IllegalArgumentException("Workflow activity handler not found: " + name);
        }
        return handler;
    }
}
