package com.ywz.workflow.featherflow.definition;

import java.io.StringReader;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

/**
 * 使用 JDK 内置 DOM 解析 XML 工作流定义，无需 jackson-dataformat-xml 依赖。
 */
public class XmlWorkflowDefinitionParser implements WorkflowDefinitionParser {

    @Override
    public List<WorkflowDefinition> parseAll(DefinitionFormat format, String content) {
        try {
            Document document = parseDocument(content);
            Element root = document.getDocumentElement();
            String rootName = root.getNodeName();

            if ("workflow".equals(rootName)) {
                List<WorkflowDefinition> result = new ArrayList<>();
                result.add(parseWorkflowElement(root));
                return result;
            }
            if ("workflows".equals(rootName)) {
                List<WorkflowDefinition> result = new ArrayList<>();
                NodeList workflowNodes = root.getElementsByTagName("workflow");
                for (int i = 0; i < workflowNodes.getLength(); i++) {
                    result.add(parseWorkflowElement((Element) workflowNodes.item(i)));
                }
                return result;
            }
            throw new IllegalArgumentException("Invalid xml workflow definition, unknown root: " + rootName);
        } catch (IllegalArgumentException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalArgumentException("Failed to parse xml workflow definition", ex);
        }
    }

    private WorkflowDefinition parseWorkflowElement(Element workflowElement) {
        String name = workflowElement.getAttribute("name");
        List<ActivityDefinition> activities = new ArrayList<>();
        NodeList activityNodes = workflowElement.getElementsByTagName("activity");
        for (int i = 0; i < activityNodes.getLength(); i++) {
            Element activityElement = (Element) activityNodes.item(i);
            String activityName = activityElement.getAttribute("name");
            String handler = activityElement.getAttribute("handler");
            String retryInterval = activityElement.getAttribute("retryInterval");
            String maxRetryTimesStr = activityElement.getAttribute("maxRetryTimes");
            int maxRetryTimes = maxRetryTimesStr.isEmpty() ? 0 : Integer.parseInt(maxRetryTimesStr);
            activities.add(new ActivityDefinition(activityName, handler, Duration.parse(retryInterval), maxRetryTimes));
        }
        return new WorkflowDefinition(name, activities);
    }

    private Document parseDocument(String content) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(false);
        factory.setExpandEntityReferences(false);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        return factory.newDocumentBuilder().parse(new InputSource(new StringReader(content)));
    }
}
