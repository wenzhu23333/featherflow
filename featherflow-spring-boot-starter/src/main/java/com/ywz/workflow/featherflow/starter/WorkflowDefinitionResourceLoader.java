package com.ywz.workflow.featherflow.starter;

import com.ywz.workflow.featherflow.definition.DefinitionFormat;
import com.ywz.workflow.featherflow.definition.WorkflowDefinition;
import com.ywz.workflow.featherflow.definition.WorkflowDefinitionParser;
import com.ywz.workflow.featherflow.definition.WorkflowDefinitionRegistry;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.util.StreamUtils;

public class WorkflowDefinitionResourceLoader {

    private final ResourcePatternResolver resourcePatternResolver;
    private final WorkflowDefinitionParser workflowDefinitionParser;

    public WorkflowDefinitionResourceLoader(ResourcePatternResolver resourcePatternResolver, WorkflowDefinitionParser workflowDefinitionParser) {
        this.resourcePatternResolver = resourcePatternResolver;
        this.workflowDefinitionParser = workflowDefinitionParser;
    }

    public void loadDefinitions(List<String> locations, WorkflowDefinitionRegistry registry) {
        if (locations == null) {
            return;
        }
        for (String location : locations) {
            try {
                Resource[] resources = resourcePatternResolver.getResources(location);
                for (Resource resource : resources) {
                    String content = StreamUtils.copyToString(resource.getInputStream(), StandardCharsets.UTF_8);
                    WorkflowDefinition definition = workflowDefinitionParser.parse(resolveFormat(resource.getFilename()), content);
                    registry.register(definition);
                }
            } catch (IOException ex) {
                throw new IllegalStateException("Failed to load workflow definitions from " + location, ex);
            }
        }
    }

    private DefinitionFormat resolveFormat(String filename) {
        if (filename == null) {
            throw new IllegalArgumentException("Workflow definition resource filename is missing");
        }
        if (filename.endsWith(".yml") || filename.endsWith(".yaml")) {
            return DefinitionFormat.YAML;
        }
        if (filename.endsWith(".xml")) {
            return DefinitionFormat.XML;
        }
        throw new IllegalArgumentException("Unsupported workflow definition file: " + filename);
    }
}
