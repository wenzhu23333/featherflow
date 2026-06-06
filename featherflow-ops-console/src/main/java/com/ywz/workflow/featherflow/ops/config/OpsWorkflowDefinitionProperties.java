package com.ywz.workflow.featherflow.ops.config;

import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "featherflow")
public class OpsWorkflowDefinitionProperties {

    private List<String> definitionLocations = new ArrayList<String>();

    public List<String> getDefinitionLocations() {
        return definitionLocations;
    }

    public void setDefinitionLocations(List<String> definitionLocations) {
        this.definitionLocations = definitionLocations;
    }
}
