package com.ywz.workflow.featherflow.ops.config;

import com.ywz.workflow.featherflow.definition.CompositeWorkflowDefinitionParser;
import com.ywz.workflow.featherflow.definition.DefinitionFormat;
import com.ywz.workflow.featherflow.definition.InMemoryWorkflowDefinitionRegistry;
import com.ywz.workflow.featherflow.definition.WorkflowDefinition;
import com.ywz.workflow.featherflow.definition.WorkflowDefinitionParser;
import com.ywz.workflow.featherflow.definition.WorkflowDefinitionRegistry;
import com.ywz.workflow.featherflow.definition.XmlWorkflowDefinitionParser;
import com.ywz.workflow.featherflow.definition.YamlWorkflowDefinitionParser;
import com.ywz.workflow.featherflow.service.WorkflowDefinitionQueryService;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.util.StreamUtils;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(OpsWorkflowDefinitionProperties.class)
public class OpsWorkflowDefinitionConfiguration {

    @Bean
    public WorkflowDefinitionParser opsWorkflowDefinitionParser() {
        return new CompositeWorkflowDefinitionParser(new YamlWorkflowDefinitionParser(), new XmlWorkflowDefinitionParser());
    }

    @Bean
    public WorkflowDefinitionRegistry opsWorkflowDefinitionRegistry(
        OpsWorkflowDefinitionProperties properties,
        WorkflowDefinitionParser opsWorkflowDefinitionParser
    ) {
        InMemoryWorkflowDefinitionRegistry registry = new InMemoryWorkflowDefinitionRegistry();
        PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
        for (String location : properties.getDefinitionLocations()) {
            loadDefinitions(location, resolver, opsWorkflowDefinitionParser, registry);
        }
        return registry;
    }

    @Bean
    public WorkflowDefinitionQueryService opsWorkflowDefinitionQueryService(WorkflowDefinitionRegistry opsWorkflowDefinitionRegistry) {
        return new WorkflowDefinitionQueryService(opsWorkflowDefinitionRegistry);
    }

    private void loadDefinitions(
        String location,
        PathMatchingResourcePatternResolver resolver,
        WorkflowDefinitionParser parser,
        WorkflowDefinitionRegistry registry
    ) {
        try {
            Resource[] resources = resolver.getResources(location);
            for (Resource resource : resources) {
                String content = StreamUtils.copyToString(resource.getInputStream(), StandardCharsets.UTF_8);
                List<WorkflowDefinition> definitions = parser.parseAll(resolveFormat(resource.getFilename()), content);
                for (WorkflowDefinition definition : definitions) {
                    registry.register(definition);
                }
            }
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to load workflow definitions from " + location, ex);
        }
    }

    private DefinitionFormat resolveFormat(String filename) {
        String lower = filename == null ? "" : filename.toLowerCase();
        if (lower.endsWith(".yml") || lower.endsWith(".yaml")) {
            return DefinitionFormat.YAML;
        }
        if (lower.endsWith(".xml")) {
            return DefinitionFormat.XML;
        }
        throw new IllegalArgumentException("Unsupported workflow definition file: " + filename);
    }
}
