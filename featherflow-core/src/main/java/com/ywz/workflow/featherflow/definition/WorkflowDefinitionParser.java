package com.ywz.workflow.featherflow.definition;

public interface WorkflowDefinitionParser {

    WorkflowDefinition parse(DefinitionFormat format, String content);
}
