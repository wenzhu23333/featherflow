package com.ywz.workflow.featherflow.ops.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import com.ywz.workflow.featherflow.ops.service.WorkflowQueryService;
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
class WorkflowDetailPageTest {

    @Autowired
    private MockMvc mockMvc;
    @SpyBean
    private WorkflowQueryService workflowQueryService;

    @Test
    void shouldRenderTimelineAndOperationHistory() throws Exception {
        MvcResult result = mockMvc.perform(get("/workflows/wf-detail-0001"))
            .andExpect(status().isOk())
            .andReturn();

        String page = result.getResponse().getContentAsString();
        assertThat(page).contains("class=\"page-shell\"");
        assertThat(page).contains("wf-detail-0001");
        assertThat(page).contains("TERMINATED");
        assertThat(page).contains("orderId");
        assertThat(page).contains("10001");
        assertThat(page).contains("10.9.8.7:host-d:1234:seed");
        assertThat(page).contains("createOrder");
        assertThat(page).contains("notifyCustomer");
        assertThat(page).contains("manual-stop");
        assertThat(page).contains("alice");
        assertThat(page).contains("bad-json");
        assertThat(page).contains("2026-04-01 10:05:00");
        assertThat(page).contains("2026-04-01 10:08:00");
        assertThat(page).contains("重试");
        assertThat(page).contains("跳过最新活动");
        assertThat(page).doesNotContain(">终止<");
        assertThat(page).contains("status-badge");
        assertThat(page).contains("status-terminated");
        assertThat(page).contains("data-latest-activity-id=\"act-500\"");
        assertThat(page).contains("class=\"panel timeline-section\"");
        assertThat(page).contains("class=\"data-table activity-timeline\"");
        assertThat(page).contains("<table id=\"activity-timeline-table\"");
        assertThat(page).doesNotContain(">失败摘要<");
        assertThat(page).contains("<tr id=\"timeline-row-act-900\"");
        assertThat(page).contains("<tr id=\"timeline-row-act-100\"");
        assertThat(page).contains("<tr id=\"timeline-row-act-500\"");
        assertThat(page).contains("id=\"workflow-detail-summary-container\"");
        assertThat(page).contains("hx-get=\"/workflows/wf-detail-0001/summary\"");
        assertThat(page).contains("id=\"workflow-detail-timeline-container\"");
        assertThat(page).contains("hx-get=\"/workflows/wf-detail-0001/timeline?activityPage=1&amp;activitySize=5\"");
        assertThat(page).contains("hx-trigger=\"every 3s\"");
        assertThat(page).contains("hx-sync=\"#workflow-detail-timeline-container:abort\"");
        assertThat(page).contains("class=\"cell-block json-preview-block\"");
        assertThat(page).contains("class=\"json-preview-widget\"");
        assertThat(page).contains("class=\"json-preview-open\"");
        assertThat(page).contains("class=\"json-preview-dialog\"");
        assertThat(page).contains("class=\"json-modal-close\"");
        assertThat(page).contains("class=\"json-modal-body\"");
        assertThat(page).contains("json-preview.js");
        assertThat(page).doesNotContain("json-expand-details");
        assertThat(page).doesNotContain("json-formatted-output");
        assertThat(page).doesNotContain("data-raw-json=");
        assertThat(page).doesNotContain("id=\"json-preview-drawer\"");
        assertThat(page).doesNotContain("id=\"json-preview-drawer-body\"");
        assertThat(page).doesNotContain("data-json-viewer");
        assertThat(page).doesNotContain("data-json-open");
        assertThat(page).doesNotContain("data-json-tab=");
        assertThat(page).doesNotContain(">展开<");
        assertThat(page).doesNotContain(">原始<");
        assertThat(page).doesNotContain(">解析<");
        assertThat(page).doesNotContain("解析预览");
        assertThat(page).doesNotContain("查看原始");
        assertThat(page).doesNotContain("hx-get=\"/workflows/wf-detail-0001\"");

        String activity1 = extractById(page, "timeline-row-act-900", "tr");
        String activity2 = extractById(page, "timeline-row-act-100", "tr");
        String activity3 = extractById(page, "timeline-row-act-500", "tr");

        assertThat(activity1).contains("createOrder");
        assertThat(activity1).contains("10.9.8.7:host-d:1234:seed");
        assertThat(activity1).contains("status-badge");
        assertThat(activity1).contains("status-successful");
        assertThat(activity1).contains("SUCCESSFUL");
        assertThat(activity1).contains("2026-04-01 10:00:00");
        assertThat(activity1).contains("{&quot;ok&quot;:true}");

        assertThat(activity2).contains("reserveInventory");
        assertThat(activity2).contains("10.9.8.8:host-e:1234:seed");
        assertThat(activity2).contains("status-badge");
        assertThat(activity2).contains("status-successful");
        assertThat(activity2).contains("SUCCESSFUL");
        assertThat(activity2).contains("reserved");

        assertThat(activity3).contains("notifyCustomer");
        assertThat(activity3).contains("10.9.8.9:host-f:1234:seed");
        assertThat(activity3).contains("status-badge");
        assertThat(activity3).contains("status-failed");
        assertThat(activity3).contains("FAILED");
        assertThat(activity3).contains("mail gateway timeout");

        assertThat(page.indexOf("timeline-row-act-900")).isLessThan(page.indexOf("timeline-row-act-100"));
        assertThat(page.indexOf("timeline-row-act-100")).isLessThan(page.indexOf("timeline-row-act-500"));

        String operationLatest = extractById(page, "operation-row-2", "tr");
        String operationMalformed = extractById(page, "operation-row-1", "tr");
        assertThat(operationLatest).contains("alice");
        assertThat(operationLatest).contains("manual-stop");
        assertThat(operationLatest).contains("act-500");

        assertThat(countOccurrences(operationMalformed, "<td>-</td>")).isEqualTo(3);
        assertThat(operationMalformed).contains("{bad-json");
        assertThat(page.indexOf("operation-row-2")).isLessThan(page.indexOf("operation-row-1"));
    }

    @Test
    void shouldReturnNotFoundWhenWorkflowIsMissing() throws Exception {
        mockMvc.perform(get("/workflows/not-exists-0001"))
            .andExpect(status().isNotFound());
    }

    @Test
    void shouldRenderDetailSummaryFragment() throws Exception {
        Mockito.clearInvocations(workflowQueryService);
        MvcResult result = mockMvc.perform(get("/workflows/wf-detail-0001/summary"))
            .andExpect(status().isOk())
            .andReturn();

        String fragment = result.getResponse().getContentAsString();
        assertThat(fragment).contains("data-latest-activity-id=\"act-500\"");
        assertThat(fragment).contains("wf-detail-0001");
        assertThat(fragment).doesNotContain("<html");

        Mockito.verify(workflowQueryService, Mockito.atLeastOnce()).getWorkflowSummary("wf-detail-0001");
        Mockito.verify(workflowQueryService, Mockito.never()).getWorkflowDetail("wf-detail-0001");
    }

    @Test
    void shouldRenderDetailTimelineFragment() throws Exception {
        Mockito.clearInvocations(workflowQueryService);
        MvcResult result = mockMvc.perform(get("/workflows/wf-detail-0001/timeline"))
            .andExpect(status().isOk())
            .andReturn();

        String fragment = result.getResponse().getContentAsString();
        assertThat(fragment).contains("id=\"workflow-detail-timeline-container\"");
        assertThat(fragment).contains("hx-get=\"/workflows/wf-detail-0001/timeline?activityPage=1&amp;activitySize=5\"");
        assertThat(fragment).contains("hx-sync=\"#workflow-detail-timeline-container:abort\"");
        assertThat(fragment).contains("class=\"panel timeline-section\"");
        assertThat(fragment).contains("activity-timeline-table");
        assertThat(fragment).contains("class=\"pager-summary\"");
        assertThat(fragment).doesNotContain(">失败摘要<");
        assertThat(fragment).contains("timeline-row-act-900");
        assertThat(fragment).contains("timeline-row-act-500");
        assertThat(fragment).contains("共 3 步");
        assertThat(fragment).contains("id=\"activity-page-size\"");
        assertThat(fragment).doesNotContain("workflow-detail-summary-container");
        assertThat(fragment).doesNotContain("operation-row-");
        assertThat(fragment).doesNotContain("<html");

        Mockito.verify(workflowQueryService, Mockito.atLeastOnce()).getWorkflowTimeline("wf-detail-0001", 1, 5);
        Mockito.verify(workflowQueryService, Mockito.never()).getWorkflowDetail("wf-detail-0001");
    }

    @Test
    void shouldRenderActivityTimelinePaginationOnDetailPage() throws Exception {
        MvcResult result = mockMvc.perform(
                get("/workflows/wf-detail-0001")
                    .param("activityPage", "1")
                    .param("activitySize", "2")
            )
            .andExpect(status().isOk())
            .andReturn();

        String page = result.getResponse().getContentAsString();
        assertThat(page).contains("activity-page-size");
        assertThat(page).contains("<option value=\"2\" selected=\"selected\">2</option>");
        assertThat(page).doesNotContain("activity-page-size=2");
        assertThat(page).contains("活动第 1 / 2 页");
        assertThat(page).contains("class=\"page-size-form\"");
        assertThat(page).contains("timeline-row-act-900");
        assertThat(page).contains("timeline-row-act-100");
        assertThat(page).doesNotContain("timeline-row-act-500");
    }

    @Test
    void shouldKeepActivityPaginationInTimelineHtmxGetOnDetailPage() throws Exception {
        MvcResult result = mockMvc.perform(
                get("/workflows/wf-detail-0001")
                    .param("activityPage", "2")
                    .param("activitySize", "2")
            )
            .andExpect(status().isOk())
            .andReturn();

        String page = result.getResponse().getContentAsString();
        assertThat(page).contains("id=\"workflow-detail-timeline-container\"");
        assertThat(page).contains("hx-get=\"/workflows/wf-detail-0001/timeline?activityPage=2&amp;activitySize=2\"");
    }

    @Test
    void shouldRenderTimelineFragmentWithRequestedActivityPagination() throws Exception {
        MvcResult result = mockMvc.perform(
                get("/workflows/wf-detail-0001/timeline")
                    .param("activityPage", "2")
                    .param("activitySize", "2")
            )
            .andExpect(status().isOk())
            .andReturn();

        String fragment = result.getResponse().getContentAsString();
        assertThat(fragment).contains("<option value=\"2\" selected=\"selected\">2</option>");
        assertThat(fragment).doesNotContain("activity-page-size=2");
        assertThat(fragment).contains("活动第 2 / 2 页");
        assertThat(fragment).contains("共 3 步");
        assertThat(fragment).contains("hx-get=\"/workflows/wf-detail-0001/timeline?activityPage=1&amp;activitySize=2\"");
        assertThat(fragment).doesNotContain("activityPage=1&amp;activitySize=5");
        assertThat(fragment).contains("timeline-row-act-500");
        assertThat(fragment).doesNotContain("timeline-row-act-900");
        assertThat(fragment).doesNotContain("timeline-row-act-100");
    }

    @Test
    void shouldKeepActivitySizeWhenNavigatingTimelinePages() throws Exception {
        MvcResult result = mockMvc.perform(
                get("/workflows/wf-detail-0001/timeline")
                    .param("activityPage", "1")
                    .param("activitySize", "2")
            )
            .andExpect(status().isOk())
            .andReturn();

        String fragment = result.getResponse().getContentAsString();
        assertThat(fragment).contains("id=\"activity-page-size\"");
        assertThat(fragment).contains("name=\"activitySize\"");
        assertThat(fragment).contains("hx-get=\"/workflows/wf-detail-0001/timeline?activityPage=2&amp;activitySize=2\"");
        assertThat(fragment).contains("hx-sync=\"#workflow-detail-timeline-container:replace\"");
        assertThat(fragment).contains("id=\"activity-page-size-form\"");
    }

    @Test
    void shouldFallbackToDefaultActivityPaginationWhenActivityPageAndSizeAreInvalid() throws Exception {
        MvcResult result = mockMvc.perform(
                get("/workflows/wf-detail-0001")
                    .param("activityPage", "abc")
                    .param("activitySize", "xyz")
            )
            .andExpect(status().isOk())
            .andReturn();

        String page = result.getResponse().getContentAsString();
        assertThat(page).contains("<option value=\"5\" selected=\"selected\">5</option>");
        assertThat(page).doesNotContain("activity-page-size=5");
        assertThat(page).contains("活动第 1 / 1 页");
        assertThat(page).contains("timeline-row-act-900");
        assertThat(page).contains("timeline-row-act-100");
        assertThat(page).contains("timeline-row-act-500");
    }

    private String extractById(String html, String id, String tag) {
        Pattern pattern = Pattern.compile("(?s)<" + tag + "\\s+id=\"" + Pattern.quote(id) + "\".*?</" + tag + ">");
        Matcher matcher = pattern.matcher(html);
        assertThat(matcher.find()).isTrue();
        return matcher.group();
    }

    private int countOccurrences(String text, String fragment) {
        int count = 0;
        int from = 0;
        while (true) {
            int index = text.indexOf(fragment, from);
            if (index < 0) {
                return count;
            }
            count++;
            from = index + fragment.length();
        }
    }
}
