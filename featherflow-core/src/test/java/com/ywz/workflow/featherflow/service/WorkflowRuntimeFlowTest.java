package com.ywz.workflow.featherflow.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.ywz.workflow.featherflow.definition.ActivityDefinition;
import com.ywz.workflow.featherflow.definition.InMemoryWorkflowDefinitionRegistry;
import com.ywz.workflow.featherflow.definition.WorkflowDefinition;
import com.ywz.workflow.featherflow.engine.DefaultWorkflowExecutionScheduler;
import com.ywz.workflow.featherflow.engine.WorkflowEngine;
import com.ywz.workflow.featherflow.engine.WorkflowRetryScheduler;
import com.ywz.workflow.featherflow.handler.MapBackedWorkflowActivityHandlerRegistry;
import com.ywz.workflow.featherflow.lock.LocalWorkflowLockService;
import com.ywz.workflow.featherflow.model.ActivityExecutionStatus;
import com.ywz.workflow.featherflow.model.ActivityInstance;
import com.ywz.workflow.featherflow.model.WorkflowInstance;
import com.ywz.workflow.featherflow.model.WorkflowStatus;
import com.ywz.workflow.featherflow.repository.ActivityRepository;
import com.ywz.workflow.featherflow.repository.WorkflowOperationRepository;
import com.ywz.workflow.featherflow.repository.WorkflowRepository;
import com.ywz.workflow.featherflow.service.DefaultWorkflowRuntimeService;
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
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class WorkflowRuntimeFlowTest {

    private final Clock clock = Clock.fixed(Instant.parse("2026-03-31T02:00:00Z"), ZoneOffset.UTC);
    private final InMemoryWorkflowDefinitionRegistry registry = new InMemoryWorkflowDefinitionRegistry();
    private final WorkflowRepository workflowRepository = new InMemoryWorkflowRepository();
    private final ActivityRepository activityRepository = new InMemoryActivityRepository();
    private final WorkflowOperationRepository operationRepository = new InMemoryWorkflowOperationRepository();
    private final JsonWorkflowContextSerializer serializer = new JsonWorkflowContextSerializer();
    private final MapBackedWorkflowActivityHandlerRegistry handlerRegistry = new MapBackedWorkflowActivityHandlerRegistry();
    private ExecutorService workflowExecutor;

    @BeforeEach
    void setUp() {
        registry.register(new WorkflowDefinition(
            "asyncWorkflow",
            Arrays.asList(
                new ActivityDefinition("step1", "step1Handler", Duration.ofSeconds(5), 1),
                new ActivityDefinition("step2", "step2Handler", Duration.ofSeconds(5), 1)
            )
        ));
        registry.register(new WorkflowDefinition(
            "manualRetryWorkflow",
            Arrays.asList(new ActivityDefinition("step1", "retryStepHandler", Duration.ofSeconds(5), 0))
        ));
        registry.register(new WorkflowDefinition(
            "retryAfterTerminateWorkflow",
            Arrays.asList(
                new ActivityDefinition("step1", "terminatedStep1Handler", Duration.ofSeconds(5), 0),
                new ActivityDefinition("step2", "terminatedStep2Handler", Duration.ofSeconds(5), 0)
            )
        ));
        registry.register(new WorkflowDefinition(
            "manualRetryHistoryWorkflow",
            Arrays.asList(new ActivityDefinition("alwaysFail", "alwaysFailHandler", Duration.ofSeconds(5), 0))
        ));
        workflowExecutor = Executors.newSingleThreadExecutor(runnable -> new Thread(runnable, "workflow-test-thread"));
    }

    @AfterEach
    void tearDown() {
        workflowExecutor.shutdownNow();
    }

    @Test
    void shouldStartWorkflowAsynchronouslyAndFinishActivities() throws Exception {
        CountDownLatch step1Started = new CountDownLatch(1);
        CountDownLatch step2Completed = new CountDownLatch(1);
        handlerRegistry.register("step1Handler", context -> {
            step1Started.countDown();
            context.put("step1", true);
            return context;
        });
        handlerRegistry.register("step2Handler", context -> {
            context.put("step2", true);
            step2Completed.countDown();
            return context;
        });

        WorkflowCommandService service = createTriggeredService();
        WorkflowInstance workflow = service.startWorkflow("asyncWorkflow", "biz-async", "{\"amount\":100}");

        assertThat(workflow.getWorkflowId()).isNotBlank();
        assertThat(workflow.getBizId()).isEqualTo("biz-async");
        assertThat(step1Started.await(1, TimeUnit.SECONDS)).isTrue();
        assertThat(step2Completed.await(1, TimeUnit.SECONDS)).isTrue();
        awaitStatus(workflow.getWorkflowId(), WorkflowStatus.SUCCESSFUL, 1000L);
    }

    @Test
    void shouldRetryFailedLatestActivityUsingPersistedInput() throws Exception {
        AtomicInteger attempts = new AtomicInteger();
        CountDownLatch retryCompleted = new CountDownLatch(1);
        handlerRegistry.register("retryStepHandler", context -> {
            if (attempts.incrementAndGet() == 1) {
                throw new IllegalStateException("boom");
            }
            assertThat(context).containsEntry("base", Integer.valueOf(1));
            assertThat(context).doesNotContainKey("operator");
            context.put("retried", true);
            retryCompleted.countDown();
            return context;
        });

        WorkflowCommandService service = createTriggeredService();
        WorkflowInstance workflow = service.startWorkflow("manualRetryWorkflow", "biz-retry", "{\"base\":1}");

        awaitStatus(workflow.getWorkflowId(), WorkflowStatus.HUMAN_PROCESSING, 1000L);
        service.retryWorkflow(workflow.getWorkflowId());

        assertThat(retryCompleted.await(1, TimeUnit.SECONDS)).isTrue();
        awaitStatus(workflow.getWorkflowId(), WorkflowStatus.SUCCESSFUL, 1000L);
        assertThat(activityRepository.findByWorkflowId(workflow.getWorkflowId()))
            .extracting(ActivityInstance::getStatus)
            .containsExactly(ActivityExecutionStatus.FAILED, ActivityExecutionStatus.SUCCESSFUL);
        assertThat(activityRepository.findByWorkflowId(workflow.getWorkflowId()).get(1).getOutput()).contains("\"base\":1");
        assertThat(activityRepository.countByWorkflowIdAndActivityNameAndStatus(
            workflow.getWorkflowId(),
            "step1",
            ActivityExecutionStatus.FAILED
        )).isEqualTo(1L);
    }

    @Test
    void manualRetryShouldNotResetFailedAttemptHistory() throws Exception {
        handlerRegistry.register("alwaysFailHandler", context -> {
            throw new IllegalStateException("still failing");
        });

        WorkflowCommandService service = createTriggeredService();
        WorkflowInstance workflow = service.startWorkflow("manualRetryHistoryWorkflow", "biz-no-reset", "{\"base\":1}");

        awaitStatus(workflow.getWorkflowId(), WorkflowStatus.HUMAN_PROCESSING, 1000L);
        service.retryWorkflow(workflow.getWorkflowId());

        awaitStatus(workflow.getWorkflowId(), WorkflowStatus.HUMAN_PROCESSING, 1000L);
        assertThat(activityRepository.countByWorkflowIdAndActivityNameAndStatus(
            workflow.getWorkflowId(),
            "alwaysFail",
            ActivityExecutionStatus.FAILED
        )).isEqualTo(2L);
        assertThat(activityRepository.findByWorkflowId(workflow.getWorkflowId()))
            .extracting(ActivityInstance::getActivityId)
            .containsExactly(workflow.getWorkflowId() + "-01-01", workflow.getWorkflowId() + "-01-02");
    }

    @Test
    void shouldContinueFromLatestSuccessfulActivityOutputWhenRetryingTerminatedWorkflow() throws Exception {
        CountDownLatch step2Completed = new CountDownLatch(1);
        handlerRegistry.register("terminatedStep1Handler", context -> {
            throw new AssertionError("step1 should not run again");
        });
        handlerRegistry.register("terminatedStep2Handler", context -> {
            assertThat(context).containsEntry("step1", Boolean.TRUE);
            context.put("step2", Boolean.TRUE);
            step2Completed.countDown();
            return context;
        });

        WorkflowInstance workflow = new WorkflowInstance(
            "wf-terminated-retry-1",
            "biz-terminated-retry-1",
            "retryAfterTerminateWorkflow",
            "test-node",
            clock.instant(),
            clock.instant(),
            "{\"base\":1}",
            WorkflowStatus.TERMINATED
        );
        workflowRepository.save(workflow);
        activityRepository.saveAttempt(
            workflow.getWorkflowId() + "-01-01",
            workflow.getWorkflowId(),
            "step1",
            "seed-node",
            "{\"base\":1}",
            serializer.merge("{\"base\":1}", "{\"step1\":true}"),
            com.ywz.workflow.featherflow.model.ActivityExecutionStatus.SUCCESSFUL,
            clock.instant()
        );

        WorkflowCommandService service = createTriggeredService();
        service.retryWorkflow(workflow.getWorkflowId());

        assertThat(step2Completed.await(1, TimeUnit.SECONDS)).isTrue();
        awaitStatus(workflow.getWorkflowId(), WorkflowStatus.SUCCESSFUL, 1000L);
        assertThat(activityRepository.findByWorkflowId(workflow.getWorkflowId()).get(1).getOutput()).contains("\"step1\":true");
    }

    @Test
    void shouldExecuteActivityInsideWorkflowExecutionThread() throws Exception {
        AtomicReference<String> activityThreadName = new AtomicReference<String>();
        CountDownLatch workflowCompleted = new CountDownLatch(1);
        handlerRegistry.register("step1Handler", context -> {
            activityThreadName.set(Thread.currentThread().getName());
            context.put("step1", true);
            return context;
        });
        handlerRegistry.register("step2Handler", context -> {
            context.put("step2", true);
            workflowCompleted.countDown();
            return context;
        });

        WorkflowCommandService service = createTriggeredService();
        WorkflowInstance workflow = service.startWorkflow("asyncWorkflow", "biz-thread", "{\"amount\":100}");

        assertThat(workflowCompleted.await(1, TimeUnit.SECONDS)).isTrue();
        awaitStatus(workflow.getWorkflowId(), WorkflowStatus.SUCCESSFUL, 1000L);
        assertThat(activityThreadName.get()).isEqualTo("workflow-test-thread");
    }

    private WorkflowCommandService createTriggeredService() {
        WorkflowRetryScheduler workflowRetryScheduler = (workflowId, delay) -> {
        };
        WorkflowEngine engine = new WorkflowEngine(
            registry,
            workflowRepository,
            activityRepository,
            handlerRegistry,
            new LocalWorkflowLockService(),
            serializer,
            clock,
            workflowRetryScheduler
        );
        DefaultWorkflowExecutionScheduler workflowExecutionScheduler = new DefaultWorkflowExecutionScheduler(
            engine,
            workflowRepository,
            workflowExecutor,
            clock
        );
        DefaultWorkflowRuntimeService workflowRuntimeService = new DefaultWorkflowRuntimeService(
            workflowRepository,
            engine,
            workflowExecutionScheduler,
            serializer,
            clock
        );
        return new DefaultWorkflowCommandService(
            registry,
            workflowRepository,
            new DefaultWorkflowIdGenerator(),
            serializer,
            clock,
            workflowRuntimeService,
            "test-node"
        );
    }

    private void awaitStatus(String workflowId, WorkflowStatus expectedStatus, long timeoutMillis) throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMillis;
        while (System.currentTimeMillis() < deadline) {
            if (workflowRepository.findRequired(workflowId).getStatus() == expectedStatus) {
                return;
            }
            Thread.sleep(20L);
        }
        assertThat(workflowRepository.findRequired(workflowId).getStatus()).isEqualTo(expectedStatus);
    }
}
