package com.ywz.workflow.featherflow.ops.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.annotation.DirtiesContext.ClassMode;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = ClassMode.AFTER_CLASS)
class DefaultDemoDataPageTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void shouldRenderBuiltInDemoWorkflowsOnDefaultStartup() throws Exception {
        MvcResult result = mockMvc.perform(get("/workflows"))
            .andExpect(status().isOk())
            .andReturn();

        String page = result.getResponse().getContentAsString();
        assertThat(page).contains("class=\"page-shell\"");
        String runningRow = extractRowById(page, "workflow-row-demo-run-0001");
        String successfulRow = extractRowById(page, "workflow-row-demo-success-01");
        String humanProcessingRow = extractRowById(page, "workflow-row-demo-human-0001");

        assertThat(runningRow).contains("<td>demo-run-0001</td>");
        assertThat(runningRow).contains("status-badge");
        assertThat(runningRow).contains("status-running");
        assertThat(runningRow).contains(">RUNNING<");
        assertThat(runningRow).contains("demoOrderWorkflow");
        assertThat(runningRow).contains("<span>终止</span>");

        assertThat(successfulRow).contains("<td>demo-success-01</td>");
        assertThat(successfulRow).contains("status-badge");
        assertThat(successfulRow).contains("status-successful");
        assertThat(successfulRow).contains(">SUCCESSFUL<");
        assertThat(successfulRow).contains("demoOrderWorkflow");

        assertThat(humanProcessingRow).contains("<td>demo-human-0001</td>");
        assertThat(humanProcessingRow).contains("status-badge");
        assertThat(humanProcessingRow).contains("status-human_processing");
        assertThat(humanProcessingRow).contains(">HUMAN_PROCESSING<");
        assertThat(humanProcessingRow).contains("<span>重试</span>");
    }

    @Test
    void shouldRenderBuiltInDemoWorkflowDetailTimeline() throws Exception {
        MvcResult result = mockMvc.perform(get("/workflows/demo-human-0001"))
            .andExpect(status().isOk())
            .andReturn();

        String page = result.getResponse().getContentAsString();
        assertThat(page).contains("demo-human-0001");
        assertThat(page).contains("demoRiskWorkflow");
        assertThat(page).contains("HUMAN_PROCESSING");
        assertThat(page).contains("10.9.8.9:ops-node-c:8421:demo");
        assertThat(page).contains("id=\"workflow-detail-timeline-container\"");
        assertThat(page).contains("class=\"page-shell\"");
        assertThat(page).contains("hx-sync=\"#workflow-detail-timeline-container:replace\"");
        assertThat(page).contains("id=\"activity-page-size\"");
        assertThat(page).doesNotContain("activity-page-size=");
        assertThat(page).contains("activity-timeline-table");
        assertThat(page).contains("status-badge");
        assertThat(page).contains("status-human_processing");
        assertThat(page).contains("timeline-row-demo-human-act-01-01");
        assertThat(page).contains("timeline-row-demo-human-act-02-01");
        assertThat(page).contains("timeline-row-demo-human-act-02-02");
        assertThat(page).contains("风控审核");
        assertThat(page).contains("发送审核结果");
        assertThat(page).contains("risk service timeout");
        assertThat(page).contains("operation-row-3002");
    }

    private String extractRowById(String html, String rowId) {
        Pattern pattern = Pattern.compile("(?s)<tr id=\"" + Pattern.quote(rowId) + "\".*?</tr>");
        Matcher matcher = pattern.matcher(html);
        assertThat(matcher.find()).isTrue();
        return matcher.group();
    }
}
