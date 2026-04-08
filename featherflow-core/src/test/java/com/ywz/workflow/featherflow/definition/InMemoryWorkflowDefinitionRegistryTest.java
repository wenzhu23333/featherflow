package com.ywz.workflow.featherflow.definition;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.Collections;
import org.junit.jupiter.api.Test;

class InMemoryWorkflowDefinitionRegistryTest {

    @Test
    void shouldRejectDuplicateWorkflowDefinitionNames() {
        InMemoryWorkflowDefinitionRegistry registry = new InMemoryWorkflowDefinitionRegistry();
        WorkflowDefinition definition = new WorkflowDefinition(
            "orderWorkflow",
            Collections.singletonList(new ActivityDefinition("createOrder", "createOrderHandler", Duration.ofSeconds(1), 1))
        );

        registry.register(definition);

        assertThatThrownBy(() -> registry.register(definition))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Duplicate workflow definition name");
    }
}
