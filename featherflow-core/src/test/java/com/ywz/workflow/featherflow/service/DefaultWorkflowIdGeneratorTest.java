package com.ywz.workflow.featherflow.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class DefaultWorkflowIdGeneratorTest {

    @Test
    void shouldGenerateFullUuidWorkflowId() {
        String workflowId = new DefaultWorkflowIdGenerator().nextId();

        assertThat(workflowId).hasSize(36);
        assertThat(workflowId)
            .matches("[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}");
        assertThat(UUID.fromString(workflowId).toString()).isEqualTo(workflowId);
    }
}
