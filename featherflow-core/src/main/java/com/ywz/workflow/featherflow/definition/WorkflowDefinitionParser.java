package com.ywz.workflow.featherflow.definition;

import java.util.List;

public interface WorkflowDefinitionParser {

    default WorkflowDefinition parse(DefinitionFormat format, String content) {
        List<WorkflowDefinition> definitions = parseAll(format, content);
        if (definitions.isEmpty()) {
            throw new IllegalArgumentException("Workflow definition is empty");
        }
        if (definitions.size() > 1) {
            throw new IllegalArgumentException("Workflow definition contains multiple workflows, use parseAll instead");
        }
        return definitions.get(0);
    }

    List<WorkflowDefinition> parseAll(DefinitionFormat format, String content);
}
