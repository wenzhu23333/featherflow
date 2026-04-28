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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.annotation.DirtiesContext.ClassMode;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DirtiesContext(classMode = ClassMode.AFTER_CLASS)
@Sql(scripts = {"/sql/featherflow-schema.sql", "/sql/workflow-list-data.sql"})
class WorkflowListPageTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void shouldRenderWorkflowListWithActions() throws Exception {
        MvcResult result = mockMvc.perform(get("/workflows"))
            .andExpect(status().isOk())
            .andReturn();

        String page = result.getResponse().getContentAsString();
        assertThat(page).contains("class=\"page-shell\"");
        assertThat(page).contains("class=\"panel filter-panel\"");
        assertThat(page).contains("id=\"workflow-filter-form\"");
        assertThat(page).contains("hx-get=\"/workflows/table\"");
        assertThat(page).contains("hx-target=\"#workflow-list-container\"");
        assertThat(page).contains("name=\"workflowId\"");
        assertThat(page).contains("name=\"bizId\"");
        assertThat(page).contains("name=\"status\"");
        assertThat(page).contains("name=\"workflowName\"");
        assertThat(page).contains("name=\"createdFrom\"");
        assertThat(page).contains("name=\"createdTo\"");
        assertThat(page).contains("name=\"modifiedFrom\"");
        assertThat(page).contains("name=\"modifiedTo\"");
        assertThat(page).contains("id=\"workflow-list-container\"");
        assertThat(page).contains("class=\"panel table-panel\"");
        assertThat(page).contains("class=\"table-scroll workflow-table-scroll\"");
        assertThat(page).contains("hx-get=\"/workflows/table\"");
        assertThat(page).contains("hx-trigger=\"every 5s\"");
        String runningRow = extractRowById(page, "workflow-row-wf-running-0001");
        String terminatedRow = extractRowById(page, "workflow-row-wf-terminated-01");

        assertThat(runningRow).contains("status-badge");
        assertThat(runningRow).contains("status-running");
        assertThat(runningRow).contains(">RUNNING<");
        assertThat(runningRow).contains("<td>2026-04-01 09:08:00</td>");
        assertThat(runningRow).contains("href=\"/workflows/wf-running-0001\"");
        assertThat(runningRow).contains(">查看详情<");
        assertThat(runningRow).contains("<span>终止</span>");
        assertThat(runningRow).doesNotContain("<span>重试</span>");
        assertThat(runningRow).doesNotContain("<span>跳过最新活动</span>");

        assertThat(terminatedRow).contains("status-badge");
        assertThat(terminatedRow).contains("status-terminated");
        assertThat(terminatedRow).contains(">TERMINATED<");
        assertThat(terminatedRow).contains("<td>2026-04-01 09:10:00</td>");
        assertThat(terminatedRow).contains("href=\"/workflows/wf-terminated-01\"");
        assertThat(terminatedRow).contains(">查看详情<");
        assertThat(terminatedRow).contains("<span>重试</span>");
        assertThat(terminatedRow).contains("<span>跳过最新活动</span>");
        assertThat(terminatedRow).doesNotContain("<span>终止</span>");
    }

    @Test
    void shouldFilterWorkflowTableByQueryParameters() throws Exception {
        MvcResult result = mockMvc.perform(
                get("/workflows/table")
                    .param("bizId", "biz-1001")
                    .param("workflowName", "order")
                    .param("status", "RUNNING")
                    .param("modifiedTo", "2026-04-01 09:08:00")
            )
            .andExpect(status().isOk())
            .andReturn();

        String fragment = result.getResponse().getContentAsString();
        assertThat(fragment).contains("workflow-row-wf-running-0001");
        assertThat(fragment).doesNotContain("workflow-row-wf-terminated-01");
    }

    @Test
    void shouldFilterWorkflowTableByMultipleStatuses() throws Exception {
        MvcResult result = mockMvc.perform(
                get("/workflows/table")
                    .param("status", "RUNNING", "TERMINATED")
            )
            .andExpect(status().isOk())
            .andReturn();

        String fragment = result.getResponse().getContentAsString();
        assertThat(fragment).contains("workflow-row-wf-running-0001");
        assertThat(fragment).contains("workflow-row-wf-terminated-01");
    }

    @Test
    void shouldTreatBlankWorkflowStatusFilterAsAllStatuses() throws Exception {
        MvcResult result = mockMvc.perform(
                get("/workflows/table")
                    .param("status", "")
            )
            .andExpect(status().isOk())
            .andReturn();

        String fragment = result.getResponse().getContentAsString();
        assertThat(fragment).contains("workflow-row-wf-running-0001");
        assertThat(fragment).contains("workflow-row-wf-terminated-01");
    }

    @Test
    void shouldRenderWorkflowListTableFragment() throws Exception {
        MvcResult result = mockMvc.perform(get("/workflows/table"))
            .andExpect(status().isOk())
            .andReturn();

        String fragment = result.getResponse().getContentAsString();
        assertThat(fragment).contains("workflow-row-wf-running-0001");
        assertThat(fragment).contains("workflow-row-wf-terminated-01");
        assertThat(fragment).doesNotContain("<html");
    }

    @Test
    void shouldRenderSecondWorkflowPageInTableFragment() throws Exception {
        MvcResult firstPageResult = mockMvc.perform(
                get("/workflows/table")
                    .param("page", "1")
                    .param("size", "1")
            )
            .andExpect(status().isOk())
            .andReturn();
        String firstPage = firstPageResult.getResponse().getContentAsString();

        MvcResult secondPageResult = mockMvc.perform(
                get("/workflows/table")
                    .param("page", "2")
                    .param("size", "1")
            )
            .andExpect(status().isOk())
            .andReturn();

        String fragment = secondPageResult.getResponse().getContentAsString();
        if (firstPage.contains("workflow-row-wf-running-0001")) {
            assertThat(fragment).contains("workflow-row-wf-terminated-01");
            assertThat(fragment).doesNotContain("workflow-row-wf-running-0001");
        } else {
            assertThat(fragment).contains("workflow-row-wf-running-0001");
            assertThat(fragment).doesNotContain("workflow-row-wf-terminated-01");
        }
        assertThat(fragment).contains("class=\"table-toolbar\"");
        assertThat(fragment).contains("class=\"data-table workflow-table\"");
        assertThat(fragment).contains("第 2 / 2 页");
        assertThat(fragment).contains("共 2 条");
        assertThat(fragment).contains("id=\"workflow-page-size\"");
    }

    @Test
    void shouldKeepFilterParametersInTableFragmentPaginationLinks() throws Exception {
        MvcResult result = mockMvc.perform(
                get("/workflows/table")
                    .param("workflowId", "wf-")
                    .param("bizId", "biz-10")
                    .param("status", "")
                    .param("workflowName", "")
                    .param("createdFrom", "2026-04-01 09:00:00")
                    .param("createdTo", "2026-04-01 09:10:00")
                    .param("modifiedFrom", "2026-04-01 09:00:00")
                    .param("modifiedTo", "2026-04-01 09:10:00")
                    .param("order", "asc")
                    .param("page", "1")
                    .param("size", "1")
            )
            .andExpect(status().isOk())
            .andReturn();

        String fragment = result.getResponse().getContentAsString();
        assertThat(fragment).contains("hx-get=\"/workflows/table");
        assertThat(fragment).contains("page=2");
        assertThat(fragment).contains("size=1");
        assertThat(fragment).contains("workflowId=wf-");
        assertThat(fragment).contains("bizId=biz-10");
        assertThat(fragment).contains("createdFrom=2026-04-01%2009:00:00");
        assertThat(fragment).contains("modifiedTo=2026-04-01%2009:10:00");
        assertThat(fragment).contains("order=asc");
        assertThat(fragment).contains("hx-disinherit=\"hx-include\"");
    }

    @Test
    void shouldRenderWorkflowListPagination() throws Exception {
        MvcResult result = mockMvc.perform(
                get("/workflows")
                    .param("page", "1")
                    .param("size", "1")
            )
            .andExpect(status().isOk())
            .andReturn();

        String page = result.getResponse().getContentAsString();
        String pageSizeSelect = extractTagById(page, "workflow-page-size", "select");
        assertThat(page).contains("workflow-page-size");
        assertThat(page).contains("workflow-list-controls.js");
        assertThat(page).contains("第 1 / 2 页");
        assertThat(page).contains("共 2 条");
        assertThat(page).contains("page=2");
        assertThat(page).contains("id=\"workflow-page-size\"");
        assertThat(pageSizeSelect).contains("data-workflow-page-size-control");
        assertThat(pageSizeSelect).doesNotContain("name=\"size\"");
        assertThat(page).contains("class=\"pager-summary\"");
    }

    @Test
    void shouldRenderWorkflowListInAscendingOrderWhenRequested() throws Exception {
        MvcResult result = mockMvc.perform(
                get("/workflows/table")
                    .param("order", "asc")
                    .param("page", "1")
                    .param("size", "1")
            )
            .andExpect(status().isOk())
            .andReturn();

        String fragment = result.getResponse().getContentAsString();
        String orderSelect = extractTagById(fragment, "workflow-sort-order", "select");
        assertThat(fragment).contains("workflow-row-wf-running-0001");
        assertThat(fragment).doesNotContain("workflow-row-wf-terminated-01");
        assertThat(fragment).contains("id=\"workflow-sort-order\"");
        assertThat(orderSelect).contains("data-workflow-order-control");
        assertThat(orderSelect).doesNotContain("name=\"order\"");
        assertThat(fragment).contains("<option value=\"asc\" selected=\"selected\">最早优先</option>");
        assertThat(fragment).contains("order=asc");
        assertThat(fragment).contains("id=\"workflow-order-input\" name=\"order\" value=\"asc\"");
    }

    @Test
    void shouldIgnoreDuplicatedBlankDateParametersFromInheritedHtmxInclude() throws Exception {
        MvcResult result = mockMvc.perform(
                get("/workflows/table")
                    .param("createdFrom", "", "")
                    .param("createdTo", "", "")
                    .param("modifiedFrom", "", "")
                    .param("modifiedTo", "", "")
                    .param("page", "2")
                    .param("size", "1")
            )
            .andExpect(status().isOk())
            .andReturn();

        String fragment = result.getResponse().getContentAsString();
        assertThat(fragment).doesNotContain("workflow-filter-errors");
        assertThat(fragment).doesNotContain("Invalid date-time");
        assertThat(fragment).contains("第 2 / 2 页");
        assertThat(fragment).contains("workflow-row-wf-running-0001");
    }

    @Test
    void shouldIgnoreCommaOnlyDateParametersWhenSwitchingWorkflowPagination() throws Exception {
        MvcResult result = mockMvc.perform(
                get("/workflows/table")
                    .param("createdFrom", ",")
                    .param("createdTo", ", ")
                    .param("modifiedFrom", " ,")
                    .param("modifiedTo", ",")
                    .param("page", "2")
                    .param("size", "1")
            )
            .andExpect(status().isOk())
            .andReturn();

        String fragment = result.getResponse().getContentAsString();
        assertThat(fragment).doesNotContain("workflow-filter-errors");
        assertThat(fragment).doesNotContain("Invalid date-time");
        assertThat(fragment).contains("第 2 / 2 页");
        assertThat(fragment).contains("workflow-row-wf-running-0001");
    }

    @Test
    void shouldRenderWorkflowPageStateWithFiltersInFullPage() throws Exception {
        MvcResult result = mockMvc.perform(
                get("/workflows")
                    .param("workflowId", "wf-")
                    .param("page", "1")
                    .param("size", "1")
            )
            .andExpect(status().isOk())
            .andReturn();

        String page = result.getResponse().getContentAsString();
        assertThat(page).contains("name=\"workflowId\"");
        assertThat(page).contains("value=\"wf-\"");
        assertThat(page).contains("第 1 / 2 页");
        assertThat(page).contains("共 2 条");
        assertThat(page).contains("workflow-page-size");
        assertThat(page).contains("class=\"page-title\">Workflow 列表</h1>");
    }

    @Test
    void shouldRenderWorkflowDateFiltersAsCalendarInputs() throws Exception {
        MvcResult result = mockMvc.perform(
                get("/workflows")
                    .param("createdFrom", "2026-04-01 09:00:00")
                    .param("createdTo", "2026-04-01T09:10:00")
                    .param("modifiedFrom", "2026-04-01 09:00")
                    .param("modifiedTo", "2026-04-01T09:10")
            )
            .andExpect(status().isOk())
            .andReturn();

        String page = result.getResponse().getContentAsString();
        assertThat(page).contains("id=\"created-from-filter\" type=\"datetime-local\" step=\"1\" name=\"createdFrom\"");
        assertThat(page).contains("id=\"created-to-filter\" type=\"datetime-local\" step=\"1\" name=\"createdTo\"");
        assertThat(page).contains("id=\"modified-from-filter\" type=\"datetime-local\" step=\"1\" name=\"modifiedFrom\"");
        assertThat(page).contains("id=\"modified-to-filter\" type=\"datetime-local\" step=\"1\" name=\"modifiedTo\"");
        assertThat(page).contains("value=\"2026-04-01T09:00:00\"");
        assertThat(page).contains("value=\"2026-04-01T09:10:00\"");
        assertThat(page).contains("value=\"2026-04-01T09:00\"");
        assertThat(page).contains("value=\"2026-04-01T09:10\"");
    }

    @Test
    void shouldRenderWorkflowStatusFilterAsMultiSelect() throws Exception {
        MvcResult result = mockMvc.perform(
                get("/workflows")
                    .param("status", "RUNNING", "TERMINATED")
            )
            .andExpect(status().isOk())
            .andReturn();

        String page = result.getResponse().getContentAsString();
        assertThat(page).contains("status-multiselect.js");
        assertThat(page).contains("id=\"workflow-status-filter\" type=\"hidden\" name=\"status\"");
        assertThat(page).contains("data-multi-select");
        assertThat(page).contains("id=\"workflow-status-filter-button\"");
        assertThat(page).contains("class=\"multi-select-summary\"");
        assertThat(page).contains(">RUNNING, TERMINATED<");
        assertThat(page).contains("class=\"multi-select-menu\"");
        assertThat(page).containsPattern("(?s)<input[^>]*type=\"checkbox\"[^>]*value=\"RUNNING\"[^>]*checked=\"checked\"");
        assertThat(page).containsPattern("(?s)<input[^>]*type=\"checkbox\"[^>]*value=\"TERMINATED\"[^>]*checked=\"checked\"");
        assertThat(page).containsPattern("(?s)<input[^>]*type=\"checkbox\"[^>]*value=\"HUMAN_PROCESSING\"");
        assertThat(page).containsPattern("(?s)<input[^>]*type=\"checkbox\"[^>]*value=\"COMPLETED\"");
        assertThat(page).doesNotContain("multiple=\"multiple\"");
    }

    @Test
    void shouldResetToFirstPageWhenSubmittingFilterForm() throws Exception {
        MvcResult result = mockMvc.perform(
                get("/workflows")
                    .param("workflowId", "wf-")
                    .param("page", "2")
                    .param("size", "1")
            )
            .andExpect(status().isOk())
            .andReturn();

        String page = result.getResponse().getContentAsString();
        String filterForm = extractTagById(page, "workflow-filter-form", "form");
        assertThat(filterForm).contains("name=\"size\"");
        assertThat(filterForm).doesNotContain("name=\"page\"");
        assertThat(page).contains("id=\"workflow-page-input\"");
        assertThat(page).contains("hx-include=\"#workflow-filter-form,#workflow-page-input\"");
    }

    @Test
    void shouldKeepCurrentListQueryInOperationRedirectTo() throws Exception {
        MvcResult result = mockMvc.perform(
                get("/workflows")
                    .param("workflowId", "wf-")
                    .param("bizId", "biz-10")
                    .param("order", "asc")
                    .param("page", "2")
                    .param("size", "1")
            )
            .andExpect(status().isOk())
            .andReturn();

        String page = result.getResponse().getContentAsString();
        assertThat(page).contains("name=\"redirectTo\"");
        assertThat(page).contains("/workflows?page=2&amp;size=1");
        assertThat(page).contains("workflowId=wf-");
        assertThat(page).contains("bizId=biz-10");
        assertThat(page).contains("order=asc");
    }

    @Test
    void shouldFallbackToDefaultPaginationWhenPageAndSizeAreInvalid() throws Exception {
        MvcResult result = mockMvc.perform(
                get("/workflows")
                    .param("page", "abc")
                    .param("size", "xyz")
            )
            .andExpect(status().isOk())
            .andReturn();

        String page = result.getResponse().getContentAsString();
        assertThat(page).contains("<option value=\"10\" selected=\"selected\">10</option>");
        assertThat(page).contains("第 1 / 1 页");
        assertThat(page).contains("共 2 条");
        assertThat(page).contains("workflow-row-wf-running-0001");
        assertThat(page).contains("workflow-row-wf-terminated-01");
    }

    @Test
    void shouldShowValidationErrorWhenWorkflowDateFilterIsInvalid() throws Exception {
        MvcResult pageResult = mockMvc.perform(
                get("/workflows")
                    .param("modifiedFrom", "bad-date-time")
            )
            .andExpect(status().isOk())
            .andReturn();

        String page = pageResult.getResponse().getContentAsString();
        assertThat(page).contains("id=\"workflow-filter-errors\"");
        assertThat(page).contains("Invalid date-time for modifiedFrom: bad-date-time");
        assertThat(page).contains("value=\"bad-date-time\"");

        MvcResult fragmentResult = mockMvc.perform(
                get("/workflows/table")
                    .param("modifiedFrom", "bad-date-time")
            )
            .andExpect(status().isOk())
            .andReturn();

        String fragment = fragmentResult.getResponse().getContentAsString();
        assertThat(fragment).contains("id=\"workflow-filter-errors\"");
        assertThat(fragment).contains("Invalid date-time for modifiedFrom: bad-date-time");
    }

    private String extractRowById(String html, String rowId) {
        Pattern pattern = Pattern.compile("(?s)<tr id=\"" + Pattern.quote(rowId) + "\".*?</tr>");
        Matcher matcher = pattern.matcher(html);
        assertThat(matcher.find()).isTrue();
        return matcher.group();
    }

    private String extractTagById(String html, String id, String tagName) {
        Pattern pattern = Pattern.compile("(?s)<" + tagName + "\\s+id=\"" + Pattern.quote(id) + "\".*?</" + tagName + ">");
        Matcher matcher = pattern.matcher(html);
        assertThat(matcher.find()).isTrue();
        return matcher.group();
    }
}
