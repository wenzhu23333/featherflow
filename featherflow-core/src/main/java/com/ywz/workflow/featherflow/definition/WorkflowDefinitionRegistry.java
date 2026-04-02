package com.ywz.workflow.featherflow.definition;

public interface WorkflowDefinitionRegistry {

    void register(WorkflowDefinition definition);

    WorkflowDefinition find(String name);

    WorkflowDefinition getRequired(String name);
}
