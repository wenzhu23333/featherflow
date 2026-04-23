package com.ywz.workflow.featherflow.ops.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
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
@Sql(scripts = {"/sql/featherflow-schema.sql", "/sql/workflow-operation-data.sql"})
class WorkflowOperationControllerTest {

    private static final String OPS_CONSOLE_SOURCE = "\"source\":\"FEATHERFLOW_OPS_CONSOLE\"";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void shouldRenderListAndDetailActionButtons() throws Exception {
        MvcResult listResult = mockMvc.perform(get("/workflows"))
            .andExpect(status().isOk())
            .andReturn();
        String listPage = listResult.getResponse().getContentAsString();
        assertThat(listPage).contains("<dialog");
        assertThat(listPage).contains("/workflows/wf-op-running-001/terminate");
        assertThat(listPage).contains("/workflows/wf-op-human-0001/terminate");
        assertThat(listPage).contains("/workflows/wf-op-terminated/retry");
        assertThat(listPage).contains("/workflows/wf-op-terminated/skip");
        assertThat(listPage).doesNotContain("operation-dialog-wf-op-running-001-terminate");
        assertThat(listPage).doesNotContain("operation-dialog-wf-op-human-0001-retry");
        assertThat(listPage).doesNotContain("operation-dialog-wf-op-terminated-retry");
        assertThat(listPage).contains("operation-dialog-wf-op-terminated-skip");
        assertThat(listPage).doesNotContain("name=\"activityId\"");

        MvcResult detailResult = mockMvc.perform(get("/workflows/wf-op-terminated"))
            .andExpect(status().isOk())
            .andReturn();
        String detailPage = detailResult.getResponse().getContentAsString();
        assertThat(detailPage).contains("<dialog");
        assertThat(detailPage).contains("/workflows/wf-op-terminated/retry");
        assertThat(detailPage).contains("/workflows/wf-op-terminated/skip");
        assertThat(detailPage).doesNotContain("operation-dialog-wf-op-terminated-retry");
        assertThat(detailPage).contains("operation-dialog-wf-op-terminated-skip");
        assertThat(detailPage).doesNotContain("name=\"activityId\"");
    }

    @Test
    void shouldSubmitTerminateForRunningAndHumanProcessingWorkflows() throws Exception {
        int beforeCount = countOperations("wf-op-running-001", "TERMINATE");
        int beforeHumanProcessing = countOperations("wf-op-human-0001", "TERMINATE");

        mockMvc.perform(post("/workflows/wf-op-running-001/terminate")
                .param("redirectTo", "/workflows"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/workflows"))
            .andExpect(flash().attribute("operationFeedback", "终止命令已提交成功"));

        assertThat(countOperations("wf-op-running-001", "TERMINATE")).isEqualTo(beforeCount + 1);
        assertThat(operationStatus("wf-op-running-001", "TERMINATE")).isEqualTo("PENDING");
        assertThat(operationInput("wf-op-running-001", "TERMINATE")).contains(OPS_CONSOLE_SOURCE);

        mockMvc.perform(post("/workflows/wf-op-human-0001/terminate"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/workflows/wf-op-human-0001"))
            .andExpect(flash().attribute("operationFeedback", "终止命令已提交成功"));

        assertThat(countOperations("wf-op-human-0001", "TERMINATE")).isEqualTo(beforeHumanProcessing + 1);
        assertThat(operationStatus("wf-op-human-0001", "TERMINATE")).isEqualTo("PENDING");
        assertThat(operationInput("wf-op-human-0001", "TERMINATE")).contains(OPS_CONSOLE_SOURCE);
    }

    @Test
    void shouldRejectTerminateWhenWorkflowIsNotRunningOrHumanProcessing() throws Exception {
        int beforeCount = countOperations("wf-op-terminated", "TERMINATE");

        mockMvc.perform(post("/workflows/wf-op-terminated/terminate"))
            .andExpect(status().isBadRequest());

        assertThat(countOperations("wf-op-terminated", "TERMINATE")).isEqualTo(beforeCount);
    }

    @Test
    void shouldSubmitRetryForAllowedWorkflowStates() throws Exception {
        int beforeTerminated = countOperations("wf-op-terminated", "RETRY");
        int beforeHumanProcessing = countOperations("wf-op-human-0001", "RETRY");

        mockMvc.perform(post("/workflows/wf-op-terminated/retry"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/workflows/wf-op-terminated"))
            .andExpect(flash().attribute("operationFeedback", "重试命令已提交成功"));

        mockMvc.perform(post("/workflows/wf-op-human-0001/retry"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/workflows/wf-op-human-0001"))
            .andExpect(flash().attribute("operationFeedback", "重试命令已提交成功"));

        assertThat(countOperations("wf-op-terminated", "RETRY")).isEqualTo(beforeTerminated + 1);
        assertThat(countOperations("wf-op-human-0001", "RETRY")).isEqualTo(beforeHumanProcessing + 1);
        assertThat(operationStatus("wf-op-terminated", "RETRY")).isEqualTo("PENDING");
        assertThat(operationInput("wf-op-terminated", "RETRY")).contains(OPS_CONSOLE_SOURCE);
        assertThat(operationInput("wf-op-human-0001", "RETRY")).contains(OPS_CONSOLE_SOURCE);
    }

    @Test
    void shouldSubmitSkipForLatestActivity() throws Exception {
        int beforeCount = countOperations("wf-op-terminated", "SKIP_ACTIVITY");

        mockMvc.perform(post("/workflows/wf-op-terminated/skip")
                .param("operator", "alice")
                .param("reason", "manual-skip"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/workflows/wf-op-terminated"))
            .andExpect(flash().attribute("operationFeedback", "跳过命令已提交成功"));

        assertThat(countOperations("wf-op-terminated", "SKIP_ACTIVITY")).isEqualTo(beforeCount + 1);
        assertThat(operationInput("wf-op-terminated", "SKIP_ACTIVITY"))
            .contains(OPS_CONSOLE_SOURCE)
            .contains("\"operator\":\"alice\"")
            .contains("\"reason\":\"manual-skip\"");
    }

    @Test
    void shouldRenderOperationFeedbackToastOnWorkflowPages() throws Exception {
        MvcResult listResult = mockMvc.perform(get("/workflows")
                .flashAttr("operationFeedback", "终止命令已提交成功"))
            .andExpect(status().isOk())
            .andReturn();
        assertThat(listResult.getResponse().getContentAsString())
            .contains("operation-toast")
            .contains("终止命令已提交成功");

        MvcResult detailResult = mockMvc.perform(get("/workflows/wf-op-terminated")
                .flashAttr("operationFeedback", "重试命令已提交成功"))
            .andExpect(status().isOk())
            .andReturn();
        assertThat(detailResult.getResponse().getContentAsString())
            .contains("operation-toast")
            .contains("重试命令已提交成功");
    }

    @Test
    void shouldRejectSkipWhenLatestActivityIsMissing() throws Exception {
        jdbcTemplate.update("delete from activity_instance where workflow_id = ?", "wf-op-terminated");
        int beforeCount = countOperations("wf-op-terminated", "SKIP_ACTIVITY");

        mockMvc.perform(post("/workflows/wf-op-terminated/skip")
                .param("operator", "alice")
                .param("reason", "missing-target"))
            .andExpect(status().isBadRequest());

        assertThat(countOperations("wf-op-terminated", "SKIP_ACTIVITY")).isEqualTo(beforeCount);
    }

    @Test
    void shouldRejectSkipWhenOperatorOrReasonMissing() throws Exception {
        int beforeCount = countOperations("wf-op-terminated", "SKIP_ACTIVITY");

        mockMvc.perform(post("/workflows/wf-op-terminated/skip")
                .param("operator", "")
                .param("reason", "manual-skip"))
            .andExpect(status().isBadRequest());

        mockMvc.perform(post("/workflows/wf-op-terminated/skip")
                .param("operator", "alice")
                .param("reason", ""))
            .andExpect(status().isBadRequest());

        assertThat(countOperations("wf-op-terminated", "SKIP_ACTIVITY")).isEqualTo(beforeCount);
    }

    private int countOperations(String workflowId, String operationType) {
        Integer count = jdbcTemplate.queryForObject(
            "select count(*) from workflow_operation where workflow_id = ? and operation_type = ?",
            Integer.class,
            workflowId,
            operationType
        );
        return count == null ? 0 : count;
    }

    private String operationStatus(String workflowId, String operationType) {
        return jdbcTemplate.queryForObject(
            "select status from workflow_operation where workflow_id = ? and operation_type = ? order by operation_id desc limit 1",
            String.class,
            workflowId,
            operationType
        );
    }

    private String operationInput(String workflowId, String operationType) {
        return jdbcTemplate.queryForObject(
            "select input from workflow_operation where workflow_id = ? and operation_type = ? order by operation_id desc limit 1",
            String.class,
            workflowId,
            operationType
        );
    }
}
