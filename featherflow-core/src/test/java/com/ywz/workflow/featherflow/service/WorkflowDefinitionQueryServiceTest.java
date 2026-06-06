package com.ywz.workflow.featherflow.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.ywz.workflow.featherflow.definition.ActivityDefinition;
import com.ywz.workflow.featherflow.definition.InMemoryWorkflowDefinitionRegistry;
import com.ywz.workflow.featherflow.definition.WorkflowDefinition;
import java.time.Duration;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

class WorkflowDefinitionQueryServiceTest {

    @Test
    void shouldReturnWorkflowDefinitionStepsInDeclaredOrder() {
        InMemoryWorkflowDefinitionRegistry registry = new InMemoryWorkflowDefinitionRegistry();
        registry.register(new WorkflowDefinition(
            "publishWorkflow",
            Arrays.asList(
                new ActivityDefinition("validateWorker", "validateWorkerHandler", "校验 worker / Validate worker", Duration.ofSeconds(3), 2),
                new ActivityDefinition("publishWorker", "publishWorkerHandler", "发布 worker / Publish worker", Duration.ofSeconds(5), 1)
            )
        ));
        WorkflowDefinitionQueryService service = new WorkflowDefinitionQueryService(registry);

        assertThat(service.listActivitySteps("publishWorkflow"))
            .extracting(
                WorkflowDefinitionStepView::getSequence,
                WorkflowDefinitionStepView::getWorkflowName,
                WorkflowDefinitionStepView::getActivityName,
                WorkflowDefinitionStepView::getDesc,
                WorkflowDefinitionStepView::getHandler,
                WorkflowDefinitionStepView::getRetryInterval,
                WorkflowDefinitionStepView::getMaxRetryTimes
            )
            .containsExactly(
                org.assertj.core.groups.Tuple.tuple(1, "publishWorkflow", "validateWorker", "校验 worker / Validate worker", "validateWorkerHandler", Duration.ofSeconds(3), 2),
                org.assertj.core.groups.Tuple.tuple(2, "publishWorkflow", "publishWorker", "发布 worker / Publish worker", "publishWorkerHandler", Duration.ofSeconds(5), 1)
            );
    }
}
