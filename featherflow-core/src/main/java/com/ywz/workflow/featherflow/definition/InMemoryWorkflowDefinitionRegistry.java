package com.ywz.workflow.featherflow.definition;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class InMemoryWorkflowDefinitionRegistry implements WorkflowDefinitionRegistry {

    private final Map<String, WorkflowDefinition> definitions = new ConcurrentHashMap<String, WorkflowDefinition>();

    @Override
    public void register(WorkflowDefinition definition) {
        WorkflowDefinition previous = definitions.putIfAbsent(definition.getName(), definition);
        if (previous != null) {
            throw new IllegalArgumentException("Duplicate workflow definition name: " + definition.getName());
        }
    }

    @Override
    public WorkflowDefinition find(String name) {
        return definitions.get(name);
    }

    @Override
    public WorkflowDefinition getRequired(String name) {
        WorkflowDefinition definition = find(name);
        if (definition == null) {
            throw new IllegalArgumentException("Workflow definition not found: " + name);
        }
        return definition;
    }
}
