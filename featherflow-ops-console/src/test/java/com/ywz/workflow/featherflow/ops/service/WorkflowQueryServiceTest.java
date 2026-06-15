package com.ywz.workflow.featherflow.ops.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.ywz.workflow.featherflow.ops.repository.WorkflowViewRepository;
import com.ywz.workflow.featherflow.ops.view.ActivityFlowNodeView;
import com.ywz.workflow.featherflow.ops.view.ActivityTimelineItemView;
import com.ywz.workflow.featherflow.ops.view.PageView;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.annotation.DirtiesContext.ClassMode;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;

@SpringBootTest
@ActiveProfiles("test")
@DirtiesContext(classMode = ClassMode.AFTER_CLASS)
@Sql(scripts = {"/sql/featherflow-schema.sql", "/sql/workflow-detail-data.sql"})
class WorkflowQueryServiceTest {

    @Autowired
    private WorkflowQueryService workflowQueryService;
    @SpyBean
    private WorkflowViewRepository workflowViewRepository;

    @Test
    void shouldReturnFullActivityTimelineWithEveryAttempt() {
        Optional<PageView<ActivityTimelineItemView>> timeline =
            workflowQueryService.getWorkflowActivityTimeline("wf-graph-0001", 1, 10, "asc");

        assertThat(timeline).isPresent();
        assertThat(activityIds(timeline.get().getItems()))
            .containsExactly("act-g-100", "act-g-101", "act-g-102", "act-g-200");
        assertThat(timeline.get().getPagination().getTotalElements()).isEqualTo(4);
        assertThat(timeline.get().getItems().get(0).getOutput()).contains("risk timeout");
        assertThat(timeline.get().getItems().get(2).getOutput()).contains("validated");
    }

    @Test
    void shouldExposeLatestExecutedNodeInWorkflowDetail() {
        assertThat(workflowQueryService.getWorkflowDetail("wf-graph-0001", 1, 10, "asc"))
            .isPresent()
            .get()
            .extracting("latestExecutedNode")
            .isEqualTo("10.9.8.13:host-j:1234:seed");
    }

    @Test
    void shouldReturnCompressedActivityFlowWithLatestAttemptAndCounters() {
        Optional<List<ActivityFlowNodeView>> compressedFlow =
            workflowQueryService.getCompressedWorkflowActivityFlow("wf-graph-0001");

        assertThat(compressedFlow).isPresent();
        assertThat(compressedFlow.get()).hasSize(3);

        ActivityFlowNodeView validateOrder = compressedFlow.get().get(0);
        assertThat(validateOrder.getActivityName()).isEqualTo("validateOrder");
        assertThat(validateOrder.getFinalStatus()).isEqualTo("SUCCESSFUL");
        assertThat(validateOrder.getTotalAttempts()).isEqualTo(3);
        assertThat(validateOrder.getFailedTimes()).isEqualTo(2);
        assertThat(validateOrder.getRetryTimes()).isEqualTo(2);
        assertThat(validateOrder.getSuccessfulTimes()).isEqualTo(1);
        assertThat(validateOrder.isLatestNode()).isFalse();
        assertThat(activityIds(validateOrder.getAttempts())).containsExactly("act-g-102");
        assertThat(validateOrder.getAttempts().get(0).getOutput()).contains("validated");

        ActivityFlowNodeView chargePayment = compressedFlow.get().get(1);
        assertThat(chargePayment.getActivityName()).isEqualTo("chargePayment");
        assertThat(chargePayment.getFinalStatus()).isEqualTo("FAILED");
        assertThat(chargePayment.getTotalAttempts()).isEqualTo(1);
        assertThat(chargePayment.getFailedTimes()).isEqualTo(1);
        assertThat(chargePayment.getRetryTimes()).isEqualTo(0);
        assertThat(chargePayment.getSuccessfulTimes()).isEqualTo(0);
        assertThat(chargePayment.isLatestNode()).isTrue();
        assertThat(activityIds(chargePayment.getAttempts())).containsExactly("act-g-200");
        assertThat(chargePayment.getAttempts().get(0).getOutput()).contains("payment gateway timeout");

        ActivityFlowNodeView captureFunds = compressedFlow.get().get(2);
        assertThat(captureFunds.getActivityName()).isEqualTo("captureFunds");
        assertThat(captureFunds.getFinalStatus()).isEqualTo("NOT_STARTED");
        assertThat(captureFunds.getTotalAttempts()).isEqualTo(0);
        assertThat(captureFunds.getFailedTimes()).isEqualTo(0);
        assertThat(captureFunds.getRetryTimes()).isEqualTo(0);
        assertThat(captureFunds.getSuccessfulTimes()).isEqualTo(0);
        assertThat(captureFunds.isLatestNode()).isFalse();
        assertThat(captureFunds.getAttempts()).isEmpty();
    }

    @Test
    void shouldUseBoundedRepositoryQueryForWorkflowPages() {
        Mockito.clearInvocations(workflowViewRepository);

        PageView<?> page = workflowQueryService.listWorkflowPage(WorkflowListFilter.empty(), 1, 1, "desc");

        assertThat(page.getItems()).hasSize(1);
        Mockito.verify(workflowViewRepository, Mockito.never()).findWorkflowListRows();
    }

    @Test
    void shouldReturnEmptyWhenWorkflowIsMissing() {
        assertThat(workflowQueryService.getWorkflowActivityTimeline("missing-workflow", 1, 10, "asc")).isEmpty();
        assertThat(workflowQueryService.getCompressedWorkflowActivityFlow("missing-workflow")).isEmpty();
    }

    private List<String> activityIds(List<ActivityTimelineItemView> activities) {
        return activities.stream()
            .map(ActivityTimelineItemView::getActivityId)
            .collect(Collectors.toList());
    }
}
