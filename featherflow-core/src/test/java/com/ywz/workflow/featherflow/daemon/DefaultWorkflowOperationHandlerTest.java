package com.ywz.workflow.featherflow.daemon;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import com.ywz.workflow.featherflow.definition.ActivityDefinition;
import com.ywz.workflow.featherflow.definition.InMemoryWorkflowDefinitionRegistry;
import com.ywz.workflow.featherflow.definition.WorkflowDefinition;
import com.ywz.workflow.featherflow.engine.DefaultWorkflowExecutionScheduler;
import com.ywz.workflow.featherflow.engine.WorkflowEngine;
import com.ywz.workflow.featherflow.engine.WorkflowExecutionScheduler;
import com.ywz.workflow.featherflow.engine.WorkflowRetryScheduler;
import com.ywz.workflow.featherflow.handler.MapBackedWorkflowActivityHandlerRegistry;
import com.ywz.workflow.featherflow.lock.LocalWorkflowLockService;
import com.ywz.workflow.featherflow.model.ActivityExecutionStatus;
import com.ywz.workflow.featherflow.model.WorkflowInstance;
import com.ywz.workflow.featherflow.model.WorkflowOperation;
import com.ywz.workflow.featherflow.model.WorkflowStatus;
import com.ywz.workflow.featherflow.repository.ActivityRepository;
import com.ywz.workflow.featherflow.repository.WorkflowOperationRepository;
import com.ywz.workflow.featherflow.repository.WorkflowRepository;
import com.ywz.workflow.featherflow.service.DefaultWorkflowCommandService;
import com.ywz.workflow.featherflow.service.DefaultWorkflowIdGenerator;
import com.ywz.workflow.featherflow.service.DefaultWorkflowRuntimeService;
import com.ywz.workflow.featherflow.service.WorkflowRuntimeService;
import com.ywz.workflow.featherflow.support.InMemoryActivityRepository;
import com.ywz.workflow.featherflow.support.InMemoryWorkflowOperationRepository;
import com.ywz.workflow.featherflow.support.InMemoryWorkflowRepository;
import com.ywz.workflow.featherflow.support.JsonWorkflowContextSerializer;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DefaultWorkflowOperationHandlerTest {

    private final Clock clock = Clock.fixed(Instant.parse("2026-03-30T14:00:00Z"), ZoneOffset.UTC);
    private final InMemoryWorkflowDefinitionRegistry definitionRegistry = new InMemoryWorkflowDefinitionRegistry();
    private final WorkflowRepository workflowRepository = new InMemoryWorkflowRepository();
    private final ActivityRepository activityRepository = new InMemoryActivityRepository();
    private final WorkflowOperationRepository operationRepository = new InMemoryWorkflowOperationRepository();
    private final JsonWorkflowContextSerializer serializer = new JsonWorkflowContextSerializer();
    private final MapBackedWorkflowActivityHandlerRegistry handlerRegistry = new MapBackedWorkflowActivityHandlerRegistry();
    private ExecutorService workflowExecutor;

    @BeforeEach
    void setUp() {
        definitionRegistry.register(new WorkflowDefinition(
            "skipWorkflow",
            Arrays.asList(
                new ActivityDefinition("step1", "step1Handler", Duration.ofSeconds(1), 0),
                new ActivityDefinition("step2", "step2Handler", Duration.ofSeconds(1), 0),
                new ActivityDefinition("step3", "step3Handler", Duration.ofSeconds(1), 0)
            )
        ));
        workflowExecutor = Executors.newSingleThreadExecutor();
    }

    @AfterEach
    void tearDown() {
        workflowExecutor.shutdownNow();
    }

    @Test
    void shouldSkipTerminatedActivityAndResumeWorkflow() throws InterruptedException {
        CountDownLatch step3Completed = new CountDownLatch(1);
        handlerRegistry.register("step1Handler", context -> {
            throw new AssertionError("step1 should reuse prior successful output");
        });
        handlerRegistry.register("step2Handler", context -> {
            throw new AssertionError("step2 should be skipped");
        });
        handlerRegistry.register("step3Handler", context -> {
            context.put("step3", true);
            step3Completed.countDown();
            return context;
        });

        DefaultWorkflowCommandService commandService = new DefaultWorkflowCommandService(
            definitionRegistry,
            workflowRepository,
            new DefaultWorkflowIdGenerator(),
            serializer,
            clock,
            new DefaultWorkflowRuntimeService(
                workflowRepository,
                newEngine(),
                new NoOpWorkflowExecutionScheduler(),
                serializer,
                clock
            )
        );
        WorkflowInstance workflow = commandService.startWorkflow("skipWorkflow", "biz-skip", "{\"base\":1}");
        activityRepository.saveOrUpdateResult(
            workflow.getWorkflowId() + "-01",
            workflow.getWorkflowId(),
            "step1",
            "{\"base\":1}",
            serializer.merge("{\"base\":1}", "{\"step1\":true}"),
            ActivityExecutionStatus.SUCCESSFUL,
            clock.instant()
        );
        activityRepository.saveOrUpdateResult(
            workflow.getWorkflowId() + "-02",
            workflow.getWorkflowId(),
            "step2",
            serializer.merge("{\"base\":1}", "{\"step1\":true}"),
            "{\"error\":\"step2 failed\"}",
            ActivityExecutionStatus.FAILED,
            clock.instant()
        );
        workflowRepository.updateStatus(workflow.getWorkflowId(), WorkflowStatus.TERMINATED, clock.instant());

        WorkflowEngine engine = newEngine();
        DefaultWorkflowExecutionScheduler workflowExecutionScheduler = new DefaultWorkflowExecutionScheduler(
            engine,
            workflowRepository,
            workflowExecutor,
            clock
        );
        WorkflowRuntimeService workflowRuntimeService = new DefaultWorkflowRuntimeService(
            workflowRepository,
            engine,
            workflowExecutionScheduler,
            serializer,
            clock
        );
        DefaultWorkflowOperationHandler handler = new DefaultWorkflowOperationHandler(
            workflowRepository,
            workflowRuntimeService,
            serializer
        );

        WorkflowOperation skipOperation = WorkflowOperation.pending(
            workflow.getWorkflowId(),
            com.ywz.workflow.featherflow.model.OperationType.SKIP_ACTIVITY,
            "{\"payload\":{\"manual\":true}}",
            clock.instant()
        );
        operationRepository.savePendingOperation(skipOperation);
        handler.process(skipOperation);
        assertThat(step3Completed.await(1, TimeUnit.SECONDS)).isTrue();
        waitForWorkflowStatus(workflow.getWorkflowId(), WorkflowStatus.SUCCESSFUL, 1000L);

        assertThat(workflowRepository.findRequired(workflow.getWorkflowId()).getStatus()).isEqualTo(WorkflowStatus.SUCCESSFUL);
        assertThat(activityRepository.findByWorkflowId(workflow.getWorkflowId()).get(1).getOutput()).contains("manual");
        assertThat(activityRepository.findByWorkflowId(workflow.getWorkflowId()).get(1).getOutput()).contains("_featherflowSkip");
        assertThat(activityRepository.findByWorkflowId(workflow.getWorkflowId()).get(2).getOutput()).contains("step3");
        assertThat(activityRepository.findByWorkflowId(workflow.getWorkflowId()).get(2).getOutput()).contains("step1");
    }

    @Test
    void shouldRejectRetryOperationWhenWorkflowIsRunning() {
        WorkflowInstance workflow = new WorkflowInstance(
            "wf-running-retry-1",
            "biz-running-retry-1",
            clock.instant(),
            clock.instant(),
            "{\"base\":1}",
            WorkflowStatus.RUNNING,
            "{\"definitionName\":\"skipWorkflow\",\"retryCounts\":{\"step2\":1}}"
        );
        workflowRepository.save(workflow);

        WorkflowEngine engine = newEngine();
        WorkflowRuntimeService workflowRuntimeService = new DefaultWorkflowRuntimeService(
            workflowRepository,
            engine,
            new NoOpWorkflowExecutionScheduler(),
            serializer,
            clock
        );
        DefaultWorkflowOperationHandler handler = new DefaultWorkflowOperationHandler(
            workflowRepository,
            workflowRuntimeService,
            serializer
        );

        assertThatThrownBy(() -> handler.process(WorkflowOperation.pending(
            workflow.getWorkflowId(),
            com.ywz.workflow.featherflow.model.OperationType.RETRY,
            "{}",
            clock.instant()
        )))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("HUMAN_PROCESSING or TERMINATED");
    }

    @Test
    void shouldIgnoreRetryOperationInputAndResumeFromLatestActivityState() throws InterruptedException {
        CountDownLatch step2Completed = new CountDownLatch(1);
        handlerRegistry.register("step1Handler", context -> {
            throw new AssertionError("step1 should not run again");
        });
        handlerRegistry.register("step2Handler", context -> {
            assertThat(context).containsEntry("step1", Boolean.TRUE);
            assertThat(context).doesNotContainKey("manual");
            context.put("step2", Boolean.TRUE);
            step2Completed.countDown();
            return context;
        });
        handlerRegistry.register("step3Handler", context -> {
            context.put("step3", true);
            return context;
        });

        WorkflowInstance workflow = new WorkflowInstance(
            "wf-retry-ignore-input-1",
            "biz-retry-ignore-input-1",
            clock.instant(),
            clock.instant(),
            "{\"base\":1}",
            WorkflowStatus.TERMINATED,
            "{\"definitionName\":\"skipWorkflow\",\"retryCounts\":{}}"
        );
        workflowRepository.save(workflow);
        activityRepository.saveOrUpdateResult(
            workflow.getWorkflowId() + "-01",
            workflow.getWorkflowId(),
            "step1",
            "{\"base\":1}",
            serializer.merge("{\"base\":1}", "{\"step1\":true}"),
            ActivityExecutionStatus.SUCCESSFUL,
            clock.instant()
        );

        WorkflowEngine engine = newEngine();
        DefaultWorkflowExecutionScheduler workflowExecutionScheduler = new DefaultWorkflowExecutionScheduler(
            engine,
            workflowRepository,
            workflowExecutor,
            clock
        );
        WorkflowRuntimeService workflowRuntimeService = new DefaultWorkflowRuntimeService(
            workflowRepository,
            engine,
            workflowExecutionScheduler,
            serializer,
            clock
        );
        DefaultWorkflowOperationHandler handler = new DefaultWorkflowOperationHandler(
            workflowRepository,
            workflowRuntimeService,
            serializer
        );

        handler.process(WorkflowOperation.pending(
            workflow.getWorkflowId(),
            com.ywz.workflow.featherflow.model.OperationType.RETRY,
            "{\"manual\":true}",
            clock.instant()
        ));

        assertThat(step2Completed.await(1, TimeUnit.SECONDS)).isTrue();
        waitForWorkflowStatus(workflow.getWorkflowId(), WorkflowStatus.SUCCESSFUL, 1000L);
    }

    private WorkflowEngine newEngine() {
        return new WorkflowEngine(
            definitionRegistry,
            workflowRepository,
            activityRepository,
            handlerRegistry,
            new LocalWorkflowLockService(),
            serializer,
            clock,
            new NoOpWorkflowRetryScheduler()
        );
    }

    private void waitForWorkflowStatus(String workflowId, WorkflowStatus expectedStatus, long timeoutMillis) throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMillis;
        while (System.currentTimeMillis() < deadline) {
            if (workflowRepository.findRequired(workflowId).getStatus() == expectedStatus) {
                return;
            }
            Thread.sleep(25L);
        }
        assertThat(workflowRepository.findRequired(workflowId).getStatus()).isEqualTo(expectedStatus);
    }

    private static final class NoOpWorkflowExecutionScheduler implements WorkflowExecutionScheduler {

        @Override
        public void schedule(String workflowId) {
        }
    }

    private static final class NoOpWorkflowRetryScheduler implements WorkflowRetryScheduler {

        @Override
        public void scheduleRetry(String workflowId, Duration delay) {
        }
    }
}
