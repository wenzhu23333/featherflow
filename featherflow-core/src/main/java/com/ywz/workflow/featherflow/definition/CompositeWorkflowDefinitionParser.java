package com.ywz.workflow.featherflow.definition;

import java.util.List;

public class CompositeWorkflowDefinitionParser implements WorkflowDefinitionParser {

    private final WorkflowDefinitionParser yamlParser;
    private final WorkflowDefinitionParser xmlParser;

    public CompositeWorkflowDefinitionParser(WorkflowDefinitionParser yamlParser, WorkflowDefinitionParser xmlParser) {
        this.yamlParser = yamlParser;
        this.xmlParser = xmlParser;
    }

    @Override
    public List<WorkflowDefinition> parseAll(DefinitionFormat format, String content) {
        if (format == DefinitionFormat.YAML) {
            return yamlParser.parseAll(format, content);
        }
        if (format == DefinitionFormat.XML) {
            return xmlParser.parseAll(format, content);
        }
        throw new IllegalArgumentException("Unsupported definition format: " + format);
    }
}
