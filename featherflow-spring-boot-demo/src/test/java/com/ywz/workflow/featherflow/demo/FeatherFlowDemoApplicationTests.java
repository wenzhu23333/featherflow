package com.ywz.workflow.featherflow.demo;

import static org.assertj.core.api.Assertions.assertThat;

import com.ywz.workflow.featherflow.demo.service.DemoWorkflowFacade;
import com.ywz.workflow.featherflow.model.ActivityExecutionStatus;
import com.ywz.workflow.featherflow.model.ActivityInstance;
import com.ywz.workflow.featherflow.model.WorkflowInstance;
import com.ywz.workflow.featherflow.model.WorkflowStatus;
import com.ywz.workflow.featherflow.repository.ActivityRepository;
import com.ywz.workflow.featherflow.repository.WorkflowRepository;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(classes = FeatherFlowDemoApplication.class)
class FeatherFlowDemoApplicationTests {

    @Autowired
    private DemoWorkflowFacade demoWorkflowFacade;

    @Autowired
    private WorkflowRepository workflowRepository;

    @Autowired
    private ActivityRepository activityRepository;

    @Test
    void shouldCompleteSuccessWorkflowThroughWorkflowCommandService() throws Exception {
        WorkflowInstance workflow = demoWorkflowFacade.start(
            "demoSuccessWorkflow",
            "demo-biz-service",
            "order-demo-001",
            "{\"amount\":88,\"customerName\":\"Alice\"}"
        );

        WorkflowInstance persistedWorkflow = awaitStatus(workflow.getWorkflowId(), WorkflowStatus.COMPLETED, 6000L);
        List<ActivityInstance> activities = activityRepository.findByWorkflowId(workflow.getWorkflowId());

        assertThat(persistedWorkflow.getBizId()).isEqualTo("demo-biz-service");
        assertThat(persistedWorkflow.getBizKey()).isEqualTo("order-demo-001");
        assertThat(persistedWorkflow.getStatus()).isEqualTo(WorkflowStatus.COMPLETED);
        assertThat(activities).hasSize(2);
        assertThat(activities.get(0).getOutput()).contains("\"orderCreated\":true");
        assertThat(activities.get(1).getOutput()).contains("\"customerNotified\":true");
    }

    @Test
    void shouldRetryTransientFailureThenComplete() throws Exception {
        WorkflowInstance workflow = demoWorkflowFacade.start(
            "demoRetryThenSuccessWorkflow",
            "demo-biz-retry",
            "order-demo-retry",
            "{\"amount\":99,\"customerName\":\"Retry Alice\"}"
        );

        awaitStatus(workflow.getWorkflowId(), WorkflowStatus.COMPLETED, 6000L);
        List<ActivityInstance> activities = activityRepository.findByWorkflowId(workflow.getWorkflowId());

        assertThat(activities).extracting(ActivityInstance::getActivityName)
            .containsExactly("createOrder", "transientNotifyCustomer", "transientNotifyCustomer");
        assertThat(activities).extracting(ActivityInstance::getStatus)
            .containsExactly(
                ActivityExecutionStatus.SUCCESSFUL,
                ActivityExecutionStatus.FAILED,
                ActivityExecutionStatus.SUCCESSFUL
            );
    }

    @Test
    void shouldMoveHumanProcessingWorkflowWhenRetriesAreExhausted() throws Exception {
        WorkflowInstance workflow = demoWorkflowFacade.start(
            "demoHumanProcessingWorkflow",
            "demo-biz-human",
            "order-demo-human",
            "{\"amount\":66,\"customerName\":\"Human Alice\"}"
        );

        WorkflowInstance persistedWorkflow = awaitStatus(
            workflow.getWorkflowId(),
            WorkflowStatus.HUMAN_PROCESSING,
            6000L
        );
        List<ActivityInstance> activities = activityRepository.findByWorkflowId(workflow.getWorkflowId());

        assertThat(persistedWorkflow.getStatus()).isEqualTo(WorkflowStatus.HUMAN_PROCESSING);
        assertThat(activities).extracting(ActivityInstance::getActivityName)
            .containsExactly("createOrder", "riskReview");
        assertThat(activities.get(1).getStatus()).isEqualTo(ActivityExecutionStatus.FAILED);
        assertThat(activities.get(1).getOutput()).contains("Simulated manual intervention required");
    }

    @Test
    void shouldTerminateAndSkipLatestActivityToCompletion() throws Exception {
        WorkflowInstance workflow = demoWorkflowFacade.start(
            "demoTerminateSkipWorkflow",
            "demo-biz-skip",
            "order-demo-skip",
            "{\"amount\":77,\"customerName\":\"Skip Alice\"}"
        );
        awaitStatus(workflow.getWorkflowId(), WorkflowStatus.HUMAN_PROCESSING, 6000L);

        demoWorkflowFacade.terminate(workflow.getWorkflowId());
        assertThat(workflowRepository.findRequired(workflow.getWorkflowId()).getStatus())
            .isEqualTo(WorkflowStatus.TERMINATED);

        demoWorkflowFacade.skip(workflow.getWorkflowId());

        awaitStatus(workflow.getWorkflowId(), WorkflowStatus.COMPLETED, 6000L);
        List<ActivityInstance> activities = activityRepository.findByWorkflowId(workflow.getWorkflowId());

        assertThat(activities).extracting(ActivityInstance::getActivityName)
            .containsExactly("createOrder", "manualReview", "manualReview", "finalizeOrder");
        assertThat(activities.get(2).getStatus()).isEqualTo(ActivityExecutionStatus.SUCCESSFUL);
        assertThat(activities.get(2).getOutput()).contains("\"manualSkip\":true");
        assertThat(activities.get(3).getOutput()).contains("\"orderFinalized\":true");
    }

    @Test
    void shouldDemonstrateAsyncSubmitAndPollPattern() throws Exception {
        WorkflowInstance workflow = demoWorkflowFacade.start(
            "demoAsyncJobWorkflow",
            "demo-biz-async",
            "order-demo-async",
            "{\"amount\":55,\"customerName\":\"Async Alice\"}"
        );

        awaitStatus(workflow.getWorkflowId(), WorkflowStatus.COMPLETED, 6000L);
        List<ActivityInstance> activities = activityRepository.findByWorkflowId(workflow.getWorkflowId());

        assertThat(activities).extracting(ActivityInstance::getActivityName)
            .containsExactly("submitAsyncJob", "pollAsyncJob", "pollAsyncJob");
        assertThat(activities).extracting(ActivityInstance::getStatus)
            .containsExactly(
                ActivityExecutionStatus.SUCCESSFUL,
                ActivityExecutionStatus.FAILED,
                ActivityExecutionStatus.SUCCESSFUL
            );
        assertThat(activities.get(0).getOutput()).contains("\"asyncJobSubmitted\":true");
        assertThat(activities.get(2).getOutput()).contains("\"asyncJobCompleted\":true");
    }

    private WorkflowInstance awaitStatus(String workflowId, WorkflowStatus expectedStatus, long timeoutMillis)
        throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMillis;
        WorkflowInstance latest = workflowRepository.findRequired(workflowId);
        while (System.currentTimeMillis() < deadline) {
            latest = workflowRepository.findRequired(workflowId);
            if (latest.getStatus() == expectedStatus) {
                return latest;
            }
            Thread.sleep(20L);
        }
        return latest;
    }
}
