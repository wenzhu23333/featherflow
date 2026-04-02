package com.ywz.workflow.featherflow.definition;

import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

public class XmlWorkflowDefinitionParser implements WorkflowDefinitionParser {

    private final XmlMapper xmlMapper = new XmlMapper();

    @Override
    public WorkflowDefinition parse(DefinitionFormat format, String content) {
        try {
            WorkflowDocument document = xmlMapper.readValue(content, WorkflowDocument.class);
            List<ActivityDefinition> activities = new ArrayList<ActivityDefinition>();
            for (WorkflowDocument.ActivityDocument activityDocument : document.getActivity()) {
                activities.add(new ActivityDefinition(
                    activityDocument.getName(),
                    activityDocument.getHandler(),
                    Duration.parse(activityDocument.getRetryInterval()),
                    activityDocument.getMaxRetryTimes() == null ? 0 : activityDocument.getMaxRetryTimes().intValue()
                ));
            }
            return new WorkflowDefinition(document.getName(), activities);
        } catch (IOException ex) {
            throw new IllegalArgumentException("Failed to parse xml workflow definition", ex);
        }
    }
}
