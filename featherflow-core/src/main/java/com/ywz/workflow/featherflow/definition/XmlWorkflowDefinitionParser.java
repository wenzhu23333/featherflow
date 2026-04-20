package com.ywz.workflow.featherflow.definition;

import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import java.io.IOException;
import java.io.StringReader;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Document;
import org.xml.sax.InputSource;

public class XmlWorkflowDefinitionParser implements WorkflowDefinitionParser {

    private final XmlMapper xmlMapper = new XmlMapper();

    @Override
    public List<WorkflowDefinition> parseAll(DefinitionFormat format, String content) {
        try {
            String rootName = resolveRootName(content);
            if ("workflow".equals(rootName)) {
                return singleDefinitionList(xmlMapper.readValue(content, WorkflowDocument.class));
            }
            if ("workflows".equals(rootName)) {
                WorkflowDocument.WorkflowListDocument document = xmlMapper.readValue(content, WorkflowDocument.WorkflowListDocument.class);
                List<WorkflowDefinition> definitions = new ArrayList<WorkflowDefinition>();
                for (WorkflowDocument workflowDocument : document.getWorkflows()) {
                    definitions.add(toDefinition(workflowDocument));
                }
                return definitions;
            }
            throw new IllegalArgumentException("Invalid xml workflow definition");
        } catch (IOException ex) {
            throw new IllegalArgumentException("Failed to parse xml workflow definition", ex);
        } catch (Exception ex) {
            throw new IllegalArgumentException("Failed to parse xml workflow definition", ex);
        }
    }

    private List<WorkflowDefinition> singleDefinitionList(WorkflowDocument document) {
        List<WorkflowDefinition> definitions = new ArrayList<WorkflowDefinition>();
        definitions.add(toDefinition(document));
        return definitions;
    }

    private WorkflowDefinition toDefinition(WorkflowDocument document) {
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
    }

    private String resolveRootName(String content) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(false);
        factory.setExpandEntityReferences(false);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        Document document = factory.newDocumentBuilder().parse(new InputSource(new StringReader(content)));
        return document.getDocumentElement().getNodeName();
    }
}
