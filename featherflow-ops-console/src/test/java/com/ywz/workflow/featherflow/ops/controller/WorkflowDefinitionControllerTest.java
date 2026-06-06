package com.ywz.workflow.featherflow.ops.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.annotation.DirtiesContext.ClassMode;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DirtiesContext(classMode = ClassMode.AFTER_CLASS)
class WorkflowDefinitionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void shouldReturnConfiguredWorkflowDefinitionSteps() throws Exception {
        MvcResult result = mockMvc.perform(get("/workflow-definitions/opsDefinitionWorkflow/steps"))
            .andExpect(status().isOk())
            .andReturn();

        String json = result.getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(json).contains("\"workflowName\":\"opsDefinitionWorkflow\"");
        assertThat(json).contains("\"sequence\":1");
        assertThat(json).contains("\"activityName\":\"validateWorker\"");
        assertThat(json).contains("\"desc\":\"校验 worker / Validate worker\"");
        assertThat(json).contains("\"retryInterval\":\"PT3S\"");
        assertThat(json).contains("\"maxRetryTimes\":2");
        assertThat(json).contains("\"sequence\":2");
        assertThat(json).contains("\"activityName\":\"publishWorker\"");
    }
}
