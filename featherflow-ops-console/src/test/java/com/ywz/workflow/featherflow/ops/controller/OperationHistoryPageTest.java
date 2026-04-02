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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DirtiesContext(classMode = ClassMode.AFTER_CLASS)
@Sql(scripts = {"/sql/featherflow-schema.sql", "/sql/workflow-operations-history-data.sql"})
class OperationHistoryPageTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void shouldRenderOperationHistoryPage() throws Exception {
        MvcResult result = mockMvc.perform(get("/operations"))
            .andExpect(status().isOk())
            .andReturn();

        String page = result.getResponse().getContentAsString();
        assertThat(page).contains("操作历史");
        assertThat(page).contains("id=\"operation-filter-form\"");
        assertThat(page).contains("hx-get=\"/operations/table\"");
        assertThat(page).contains("hx-target=\"#operation-history-container\"");
        assertThat(page).contains("name=\"workflowId\"");
        assertThat(page).contains("name=\"bizId\"");
        assertThat(page).contains("name=\"operationType\"");
        assertThat(page).contains("name=\"status\"");
        assertThat(page).contains("name=\"operator\"");
        assertThat(page).contains("name=\"createdFrom\"");
        assertThat(page).contains("name=\"createdTo\"");
        assertThat(page).contains("operation-history-container");
        assertThat(page).contains("hx-get=\"/operations/table\"");
        assertThat(page).contains("hx-trigger=\"every 5s\"");
        assertThat(page).contains("<th>Biz ID</th>");
        assertThat(page).contains("manual-stop");
        assertThat(page).contains("alice");
        assertThat(page).contains("act-002");
        assertThat(page).contains("{bad-json");
        assertThat(page).contains("wf-detail-0001");
        assertThat(page).contains("wf-detail-0002");
        assertThat(page).contains("biz-2001");
        assertThat(page).contains("biz-2002");

        String row3 = extractRowById(page, "operation-history-row-3");
        String row2 = extractRowById(page, "operation-history-row-2");
        String row1 = extractRowById(page, "operation-history-row-1");

        assertThat(row3).contains("wf-detail-0002");
        assertThat(row3).contains("biz-2002");
        assertThat(row2).contains("wf-detail-0001");
        assertThat(row2).contains("biz-2001");
        assertThat(row1).contains("{bad-json");
        assertThat(page.indexOf("operation-history-row-3")).isLessThan(page.indexOf("operation-history-row-2"));
        assertThat(page.indexOf("operation-history-row-2")).isLessThan(page.indexOf("operation-history-row-1"));
    }

    @Test
    void shouldRenderOperationHistoryTableFragment() throws Exception {
        MvcResult result = mockMvc.perform(get("/operations/table"))
            .andExpect(status().isOk())
            .andReturn();

        String fragment = result.getResponse().getContentAsString();
        assertThat(fragment).contains("operation-history-row-3");
        assertThat(fragment).contains("wf-detail-0002");
        assertThat(fragment).contains("biz-2002");
        assertThat(fragment).doesNotContain("<html");
    }

    @Test
    void shouldFilterOperationHistoryTableByQueryParameters() throws Exception {
        MvcResult result = mockMvc.perform(
                get("/operations/table")
                    .param("operator", "alice")
                    .param("operationType", "TERMINATE")
            )
            .andExpect(status().isOk())
            .andReturn();

        String fragment = result.getResponse().getContentAsString();
        assertThat(fragment).contains("operation-history-row-2");
        assertThat(fragment).doesNotContain("operation-history-row-3");
    }

    @Test
    void shouldFilterOperationHistoryTableByTimeRange() throws Exception {
        MvcResult result = mockMvc.perform(
                get("/operations/table")
                    .param("createdFrom", "2026-04-01 10:08:30")
            )
            .andExpect(status().isOk())
            .andReturn();

        String fragment = result.getResponse().getContentAsString();
        assertThat(fragment).contains("operation-history-row-3");
        assertThat(fragment).doesNotContain("operation-history-row-2");
        assertThat(fragment).doesNotContain("operation-history-row-1");
    }

    @Test
    void shouldShowValidationErrorWhenOperationDateFilterIsInvalid() throws Exception {
        MvcResult pageResult = mockMvc.perform(
                get("/operations")
                    .param("createdFrom", "bad-date-time")
            )
            .andExpect(status().isOk())
            .andReturn();

        String page = pageResult.getResponse().getContentAsString();
        assertThat(page).contains("id=\"operation-filter-errors\"");
        assertThat(page).contains("Invalid date-time for createdFrom: bad-date-time");
        assertThat(page).contains("value=\"bad-date-time\"");

        MvcResult fragmentResult = mockMvc.perform(
                get("/operations/table")
                    .param("createdFrom", "bad-date-time")
            )
            .andExpect(status().isOk())
            .andReturn();

        String fragment = fragmentResult.getResponse().getContentAsString();
        assertThat(fragment).contains("id=\"operation-filter-errors\"");
        assertThat(fragment).contains("Invalid date-time for createdFrom: bad-date-time");
    }

    private String extractRowById(String html, String rowId) {
        Pattern pattern = Pattern.compile("(?s)<tr id=\"" + Pattern.quote(rowId) + "\".*?</tr>");
        Matcher matcher = pattern.matcher(html);
        assertThat(matcher.find()).isTrue();
        return matcher.group();
    }
}
