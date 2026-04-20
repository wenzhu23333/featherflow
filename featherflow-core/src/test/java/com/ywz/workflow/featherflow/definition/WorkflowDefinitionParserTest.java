package com.ywz.workflow.featherflow.definition;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;

class WorkflowDefinitionParserTest {

    private final WorkflowDefinitionParser parser = new CompositeWorkflowDefinitionParser(
        new YamlWorkflowDefinitionParser(),
        new XmlWorkflowDefinitionParser()
    );

    @Test
    void shouldParseYamlDefinition() {
        String yaml = ""
            + "workflow:\n"
            + "  name: orderWorkflow\n"
            + "  activities:\n"
            + "    - name: createOrder\n"
            + "      handler: createOrderHandler\n"
            + "      retryInterval: PT5S\n"
            + "      maxRetryTimes: 3\n"
            + "    - name: notifyCustomer\n"
            + "      handler: notifyCustomerHandler\n"
            + "      retryInterval: PT10S\n"
            + "      maxRetryTimes: 1\n";

        WorkflowDefinition definition = parser.parse(DefinitionFormat.YAML, yaml);

        assertThat(definition.getName()).isEqualTo("orderWorkflow");
        assertThat(definition.getActivities()).hasSize(2);
        assertThat(definition.getActivities().get(0).getName()).isEqualTo("createOrder");
        assertThat(definition.getActivities().get(0).getHandler()).isEqualTo("createOrderHandler");
        assertThat(definition.getActivities().get(0).getRetryInterval()).isEqualTo(Duration.ofSeconds(5));
        assertThat(definition.getActivities().get(0).getMaxRetryTimes()).isEqualTo(3);
    }

    @Test
    void shouldParseXmlDefinition() {
        String xml = ""
            + "<workflow name=\"paymentWorkflow\">"
            + "  <activity name=\"freezeBalance\" handler=\"freezeBalanceHandler\" retryInterval=\"PT2S\" maxRetryTimes=\"2\"/>"
            + "  <activity name=\"confirmPayment\" handler=\"confirmPaymentHandler\" retryInterval=\"PT4S\" maxRetryTimes=\"1\"/>"
            + "</workflow>";

        WorkflowDefinition definition = parser.parse(DefinitionFormat.XML, xml);

        assertThat(definition.getName()).isEqualTo("paymentWorkflow");
        assertThat(definition.getActivities()).hasSize(2);
        assertThat(definition.getActivities().get(1).getName()).isEqualTo("confirmPayment");
        assertThat(definition.getActivities().get(1).getRetryInterval()).isEqualTo(Duration.ofSeconds(4));
    }

    @Test
    void shouldParseMultipleYamlDefinitionsFromSingleFile() {
        String yaml = ""
            + "workflows:\n"
            + "  - name: orderWorkflow\n"
            + "    activities:\n"
            + "      - name: createOrder\n"
            + "        handler: createOrderHandler\n"
            + "        retryInterval: PT5S\n"
            + "        maxRetryTimes: 3\n"
            + "  - name: paymentWorkflow\n"
            + "    activities:\n"
            + "      - name: freezeBalance\n"
            + "        handler: freezeBalanceHandler\n"
            + "        retryInterval: PT2S\n"
            + "        maxRetryTimes: 1\n";

        List<WorkflowDefinition> definitions = parser.parseAll(DefinitionFormat.YAML, yaml);

        assertThat(definitions).hasSize(2);
        assertThat(definitions).extracting(WorkflowDefinition::getName)
            .containsExactly("orderWorkflow", "paymentWorkflow");
    }

    @Test
    void shouldParseMultipleXmlDefinitionsFromSingleFile() {
        String xml = ""
            + "<workflows>"
            + "  <workflow name=\"orderWorkflow\">"
            + "    <activity name=\"createOrder\" handler=\"createOrderHandler\" retryInterval=\"PT5S\" maxRetryTimes=\"3\"/>"
            + "  </workflow>"
            + "  <workflow name=\"paymentWorkflow\">"
            + "    <activity name=\"freezeBalance\" handler=\"freezeBalanceHandler\" retryInterval=\"PT2S\" maxRetryTimes=\"1\"/>"
            + "  </workflow>"
            + "</workflows>";

        List<WorkflowDefinition> definitions = parser.parseAll(DefinitionFormat.XML, xml);

        assertThat(definitions).hasSize(2);
        assertThat(definitions).extracting(WorkflowDefinition::getName)
            .containsExactly("orderWorkflow", "paymentWorkflow");
    }
}
