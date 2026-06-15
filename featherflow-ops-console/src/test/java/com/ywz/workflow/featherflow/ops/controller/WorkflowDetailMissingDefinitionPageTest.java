package com.ywz.workflow.featherflow.ops.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ywz.workflow.featherflow.service.WorkflowDefinitionQueryService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.annotation.DirtiesContext.ClassMode;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DirtiesContext(classMode = ClassMode.AFTER_CLASS)
@Sql(scripts = {"/sql/featherflow-schema.sql", "/sql/workflow-detail-data.sql"})
class WorkflowDetailMissingDefinitionPageTest {

    @Autowired
    private MockMvc mockMvc;
    @MockBean
    private WorkflowDefinitionQueryService workflowDefinitionQueryService;

    @Test
    void shouldExplainWhenWorkflowDefinitionIsNotLoaded() throws Exception {
        when(workflowDefinitionQueryService.listActivitySteps("graphWorkflow"))
            .thenThrow(new IllegalArgumentException("Workflow definition not found: graphWorkflow"));

        MvcResult result = mockMvc.perform(get("/workflows/wf-graph-0001"))
            .andExpect(status().isOk())
            .andReturn();

        String page = result.getResponse().getContentAsString();
        assertThat(page).contains("执行链路总览");
        assertThat(page).contains("定义未加载");
        assertThat(page).contains("无法展示还未执行的步骤");
    }
}
