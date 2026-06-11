package com.ywz.workflow.featherflow.demo;

import static org.assertj.core.api.Assertions.assertThat;

import com.ywz.workflow.featherflow.demo.web.StartWorkflowRequest;
import com.ywz.workflow.featherflow.demo.web.WorkflowViewResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

@SpringBootTest(
    classes = FeatherFlowDemoApplication.class,
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT
)
class FeatherFlowDemoControllerTests {

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    void shouldStartWorkflowThroughHttpEndpoint() throws Exception {
        StartWorkflowRequest request = new StartWorkflowRequest();
        request.setWorkflowName("demoSuccessWorkflow");
        request.setBizId("demo-biz-http");
        request.setBizKey("order-demo-http");
        request.setAmount(120);
        request.setCustomerName("Bob");

        ResponseEntity<WorkflowViewResponse> startResponse = restTemplate.postForEntity(
            "/demo/workflows/start",
            request,
            WorkflowViewResponse.class
        );

        assertThat(startResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(startResponse.getBody()).isNotNull();
        assertThat(startResponse.getBody().getWorkflowId()).isNotBlank();
        assertThat(startResponse.getBody().getBizId()).isEqualTo("demo-biz-http");
        assertThat(startResponse.getBody().getBizKey()).isEqualTo("order-demo-http");

        String workflowId = startResponse.getBody().getWorkflowId();
        long deadline = System.currentTimeMillis() + 3000L;
        WorkflowViewResponse latest = null;
        while (System.currentTimeMillis() < deadline) {
            ResponseEntity<WorkflowViewResponse> getResponse = restTemplate.getForEntity(
                "/demo/workflows/" + workflowId,
                WorkflowViewResponse.class
            );
            latest = getResponse.getBody();
            if (latest != null && "COMPLETED".equals(latest.getStatus())) {
                break;
            }
            Thread.sleep(20L);
        }

        assertThat(latest).isNotNull();
        assertThat(latest.getStatus()).isEqualTo("COMPLETED");
    }
}
