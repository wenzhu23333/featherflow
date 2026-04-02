package com.ywz.workflow.featherflow.definition;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
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
}
