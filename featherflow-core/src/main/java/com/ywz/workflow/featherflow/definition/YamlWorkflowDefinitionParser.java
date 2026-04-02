package com.ywz.workflow.featherflow.definition;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

public class YamlWorkflowDefinitionParser implements WorkflowDefinitionParser {

    private final ObjectMapper objectMapper = new ObjectMapper(new YAMLFactory());

    @Override
    public WorkflowDefinition parse(DefinitionFormat format, String content) {
        try {
            WorkflowDocument document = objectMapper.readValue(content, WorkflowDocument.class);
            WorkflowDocument.WorkflowBody body = document.getWorkflow();
            if (body == null) {
                throw new IllegalArgumentException("Invalid yaml workflow definition");
            }
            List<ActivityDefinition> activities = new ArrayList<ActivityDefinition>();
            for (WorkflowDocument.ActivityDocument activityDocument : body.getActivities()) {
                activities.add(new ActivityDefinition(
                    activityDocument.getName(),
                    activityDocument.getHandler(),
                    Duration.parse(activityDocument.getRetryInterval()),
                    activityDocument.getMaxRetryTimes() == null ? 0 : activityDocument.getMaxRetryTimes().intValue()
                ));
            }
            return new WorkflowDefinition(body.getName(), activities);
        } catch (IOException ex) {
            throw new IllegalArgumentException("Failed to parse yaml workflow definition", ex);
        }
    }
}
