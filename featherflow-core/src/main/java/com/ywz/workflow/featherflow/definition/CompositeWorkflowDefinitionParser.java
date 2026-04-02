package com.ywz.workflow.featherflow.definition;

public class CompositeWorkflowDefinitionParser implements WorkflowDefinitionParser {

    private final WorkflowDefinitionParser yamlParser;
    private final WorkflowDefinitionParser xmlParser;

    public CompositeWorkflowDefinitionParser(WorkflowDefinitionParser yamlParser, WorkflowDefinitionParser xmlParser) {
        this.yamlParser = yamlParser;
        this.xmlParser = xmlParser;
    }

    @Override
    public WorkflowDefinition parse(DefinitionFormat format, String content) {
        if (format == DefinitionFormat.YAML) {
            return yamlParser.parse(format, content);
        }
        if (format == DefinitionFormat.XML) {
            return xmlParser.parse(format, content);
        }
        throw new IllegalArgumentException("Unsupported definition format: " + format);
    }
}
