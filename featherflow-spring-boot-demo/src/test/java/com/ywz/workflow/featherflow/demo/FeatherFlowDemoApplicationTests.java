package com.ywz.workflow.featherflow.demo;

import static org.assertj.core.api.Assertions.assertThat;

import com.ywz.workflow.featherflow.demo.service.DemoWorkflowFacade;
import com.ywz.workflow.featherflow.model.WorkflowInstance;
import com.ywz.workflow.featherflow.model.WorkflowStatus;
import com.ywz.workflow.featherflow.repository.ActivityRepository;
import com.ywz.workflow.featherflow.repository.WorkflowRepository;
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
    void shouldStartWorkflowThroughWorkflowCommandService() throws Exception {
        WorkflowInstance workflow = demoWorkflowFacade.start(
            "demo-biz-service",
            "{\"amount\":88,\"customerName\":\"Alice\"}"
        );

        long deadline = System.currentTimeMillis() + 3000L;
        while (System.currentTimeMillis() < deadline) {
            if (workflowRepository.findRequired(workflow.getWorkflowId()).getStatus() == WorkflowStatus.COMPLETED) {
                break;
            }
            Thread.sleep(20L);
        }

        WorkflowInstance persistedWorkflow = workflowRepository.findRequired(workflow.getWorkflowId());
        assertThat(persistedWorkflow.getBizId()).isEqualTo("demo-biz-service");
        assertThat(persistedWorkflow.getStatus()).isEqualTo(WorkflowStatus.COMPLETED);
        assertThat(activityRepository.findByWorkflowId(workflow.getWorkflowId())).hasSize(2);
        assertThat(activityRepository.findByWorkflowId(workflow.getWorkflowId()).get(0).getOutput()).contains("\"orderCreated\":true");
    }
}
