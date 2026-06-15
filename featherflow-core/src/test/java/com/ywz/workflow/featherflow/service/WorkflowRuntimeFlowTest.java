package com.ywz.workflow.featherflow.service;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.ywz.workflow.featherflow.definition.ActivityDefinition;
import com.ywz.workflow.featherflow.definition.InMemoryWorkflowDefinitionRegistry;
import com.ywz.workflow.featherflow.definition.WorkflowDefinition;
import com.ywz.workflow.featherflow.engine.DefaultWorkflowExecutionScheduler;
import com.ywz.workflow.featherflow.engine.WorkflowEngine;
import com.ywz.workflow.featherflow.engine.WorkflowRetryScheduler;
import com.ywz.workflow.featherflow.handler.MapBackedWorkflowActivityHandlerRegistry;
import com.ywz.workflow.featherflow.lock.LocalWorkflowLockService;
import com.ywz.workflow.featherflow.lock.WorkflowLockService;
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
import org.slf4j.LoggerFactory;

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
            "manualRetryFailedSnapshotWorkflow",
            Arrays.asList(
                new ActivityDefinition("step1", "retrySeedStepHandler", Duration.ofSeconds(5), 0),
                new ActivityDefinition("step2", "retryFromFailedInputHandler", Duration.ofSeconds(5), 0)
            )
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
        registry.register(new WorkflowDefinition(
            "startupRecoveryWorkflow",
            Arrays.asList(
                new ActivityDefinition("step1", "recoveryStep1Handler", Duration.ofSeconds(5), 0),
                new ActivityDefinition("step2", "recoveryStep2Handler", Duration.ofSeconds(5), 0)
            )
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
        awaitStatus(workflow.getWorkflowId(), WorkflowStatus.COMPLETED, 1000L);
    }

    @Test
    void shouldTouchWorkflowModifiedTimeAfterSuccessfulActivity() throws Exception {
        CountDownLatch step2Started = new CountDownLatch(1);
        CountDownLatch releaseStep2 = new CountDownLatch(1);
        Instant oldModified = clock.instant().minus(Duration.ofMinutes(20));
        handlerRegistry.register("step1Handler", context -> {
            context.put("step1", true);
            return context;
        });
        handlerRegistry.register("step2Handler", context -> {
            step2Started.countDown();
            assertThat(releaseStep2.await(1, TimeUnit.SECONDS)).isTrue();
            context.put("step2", true);
            return context;
        });
        WorkflowInstance workflow = new WorkflowInstance(
            "wf-touch-modified-1",
            "biz-touch-modified-1",
            "asyncWorkflow",
            "old-node",
            oldModified,
            oldModified,
            "{\"base\":1}",
            WorkflowStatus.RUNNING
        );
        workflowRepository.save(workflow);

        createRuntimeService().dispatchWorkflow(workflow.getWorkflowId());

        assertThat(step2Started.await(1, TimeUnit.SECONDS)).isTrue();
        assertThat(workflowRepository.findRequired(workflow.getWorkflowId()).getStatus()).isEqualTo(WorkflowStatus.RUNNING);
        assertThat(workflowRepository.findRequired(workflow.getWorkflowId()).getGmtModified()).isEqualTo(clock.instant());
        releaseStep2.countDown();
        awaitStatus(workflow.getWorkflowId(), WorkflowStatus.COMPLETED, 1000L);
    }

    @Test
    void shouldRetryFailedLatestActivityUsingPersistedInput() throws Exception {
        CountDownLatch retryCompleted = new CountDownLatch(1);
        handlerRegistry.register("retrySeedStepHandler", context -> {
            throw new AssertionError("step1 should not run again");
        });
        handlerRegistry.register("retryFromFailedInputHandler", context -> {
            assertThat(context).containsEntry("base", Integer.valueOf(1));
            assertThat(context).containsEntry("step1", Boolean.TRUE);
            assertThat(context).containsEntry("failedSeed", "snapshot");
            context.put("retried", true);
            retryCompleted.countDown();
            return context;
        });

        WorkflowInstance workflow = new WorkflowInstance(
            "wf-manual-retry-snapshot-1",
            "biz-retry",
            "manualRetryFailedSnapshotWorkflow",
            "test-node",
            clock.instant(),
            clock.instant(),
            "{\"base\":1}",
            WorkflowStatus.HUMAN_PROCESSING
        );
        workflowRepository.save(workflow);
        String step1Output = serializer.merge("{\"base\":1}", "{\"step1\":true}");
        String failedInput = serializer.merge(step1Output, "{\"failedSeed\":\"snapshot\"}");
        activityRepository.saveAttempt(
            workflow.getWorkflowId() + "-01-01",
            workflow.getWorkflowId(),
            "step1",
            "seed-node",
            "{\"base\":1}",
            step1Output,
            ActivityExecutionStatus.SUCCESSFUL,
            clock.instant()
        );
        activityRepository.saveAttempt(
            workflow.getWorkflowId() + "-02-01",
            workflow.getWorkflowId(),
            "step2",
            "seed-node",
            failedInput,
            "{\"error\":\"boom\"}",
            ActivityExecutionStatus.FAILED,
            clock.instant()
        );

        WorkflowCommandService service = createTriggeredService();
        service.retryWorkflow(workflow.getWorkflowId());

        assertThat(retryCompleted.await(1, TimeUnit.SECONDS)).isTrue();
        awaitStatus(workflow.getWorkflowId(), WorkflowStatus.COMPLETED, 1000L);
        assertThat(activityRepository.findByWorkflowId(workflow.getWorkflowId()))
            .extracting(ActivityInstance::getStatus)
            .containsExactly(ActivityExecutionStatus.SUCCESSFUL, ActivityExecutionStatus.FAILED, ActivityExecutionStatus.SUCCESSFUL);
        assertThat(activityRepository.findByWorkflowId(workflow.getWorkflowId()).get(2).getInput()).contains("\"failedSeed\":\"snapshot\"");
        assertThat(activityRepository.findByWorkflowId(workflow.getWorkflowId()).get(2).getOutput()).contains("\"failedSeed\":\"snapshot\"");
        assertThat(activityRepository.countByWorkflowIdAndActivityNameAndStatus(
            workflow.getWorkflowId(),
            "step2",
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
        awaitStatus(workflow.getWorkflowId(), WorkflowStatus.COMPLETED, 1000L);
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
        awaitStatus(workflow.getWorkflowId(), WorkflowStatus.COMPLETED, 1000L);
        assertThat(activityThreadName.get()).isEqualTo("workflow-test-thread");
    }

    @Test
    void shouldRecoverStaleRunningWorkflowFromLatestSuccessfulActivity() throws Exception {
        CountDownLatch recovered = new CountDownLatch(1);
        handlerRegistry.register("recoveryStep1Handler", context -> {
            throw new AssertionError("completed step1 should be skipped during recovery");
        });
        handlerRegistry.register("recoveryStep2Handler", context -> {
            assertThat(context).containsEntry("step1", Boolean.TRUE);
            context.put("step2", true);
            recovered.countDown();
            return context;
        });

        WorkflowInstance workflow = new WorkflowInstance(
            "wf-stale-running-1",
            "biz-stale-running-1",
            "startupRecoveryWorkflow",
            "old-node",
            clock.instant().minus(Duration.ofMinutes(20)),
            clock.instant().minus(Duration.ofMinutes(20)),
            "{\"base\":1}",
            WorkflowStatus.RUNNING
        );
        workflowRepository.save(workflow);
        activityRepository.saveAttempt(
            workflow.getWorkflowId() + "-01-01",
            workflow.getWorkflowId(),
            "step1",
            "old-node",
            "{\"base\":1}",
            serializer.merge("{\"base\":1}", "{\"step1\":true}"),
            ActivityExecutionStatus.SUCCESSFUL,
            clock.instant().minus(Duration.ofMinutes(20))
        );

        StaleRunningWorkflowRecoveryService recoveryService = new StaleRunningWorkflowRecoveryService(
            workflowRepository,
            createRuntimeService(),
            clock
        );

        assertThat(recoveryService.recover(Duration.ofMinutes(10), 10)).isEqualTo(1);
        assertThat(recovered.await(1, TimeUnit.SECONDS)).isTrue();
        awaitStatus(workflow.getWorkflowId(), WorkflowStatus.COMPLETED, 1000L);
        assertThat(activityRepository.findByWorkflowId(workflow.getWorkflowId()))
            .extracting(ActivityInstance::getActivityName)
            .containsExactly("step1", "step2");
    }

    @Test
    void shouldLogLatestActivityMetadataWhenRecoveringStaleRunningWorkflow() {
        Logger logger = (Logger) LoggerFactory.getLogger(StaleRunningWorkflowRecoveryService.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<ILoggingEvent>();
        appender.start();
        logger.addAppender(appender);
        try {
            WorkflowInstance workflow = new WorkflowInstance(
                "wf-recovery-log-1",
                "biz-recovery-log-1",
                "startupRecoveryWorkflow",
                "old-node",
                clock.instant().minus(Duration.ofMinutes(20)),
                clock.instant().minus(Duration.ofMinutes(20)),
                "{\"base\":1}",
                WorkflowStatus.RUNNING
            );
            workflowRepository.save(workflow);
            activityRepository.saveAttempt(
                workflow.getWorkflowId() + "-01-01",
                workflow.getWorkflowId(),
                "step1",
                "old-node",
                "{\"base\":1}",
                "{\"error\":\"first\"}",
                ActivityExecutionStatus.FAILED,
                clock.instant().minus(Duration.ofMinutes(20))
            );
            activityRepository.saveAttempt(
                workflow.getWorkflowId() + "-01-02",
                workflow.getWorkflowId(),
                "step1",
                "old-node",
                "{\"base\":1}",
                "{\"error\":\"second\"}",
                ActivityExecutionStatus.FAILED,
                clock.instant().minus(Duration.ofMinutes(19))
            );
            StaleRunningWorkflowRecoveryService recoveryService = new StaleRunningWorkflowRecoveryService(
                workflowRepository,
                new RecordingWorkflowRuntimeService(),
                clock,
                activityRepository,
                new RecordingWorkflowLockService()
            );

            assertThat(recoveryService.recover(Duration.ofMinutes(10), 10)).isEqualTo(1);

            assertThat(appender.list)
                .extracting(ILoggingEvent::getFormattedMessage)
                .anySatisfy(message -> assertThat(message)
                    .contains("workflowId=wf-recovery-log-1")
                    .contains("latestActivityId=wf-recovery-log-1-01-02")
                    .contains("latestActivityName=step1")
                    .contains("latestActivityStatus=FAILED")
                    .contains("failedAttemptCount=2"));
        } finally {
            logger.detachAppender(appender);
        }
    }

    @Test
    void shouldNotRecoverFreshRunningWorkflow() {
        WorkflowInstance workflow = new WorkflowInstance(
            "wf-fresh-running-1",
            "biz-fresh-running-1",
            "startupRecoveryWorkflow",
            "active-node",
            clock.instant().minus(Duration.ofMinutes(1)),
            clock.instant().minus(Duration.ofMinutes(1)),
            "{\"base\":1}",
            WorkflowStatus.RUNNING
        );
        workflowRepository.save(workflow);

        StaleRunningWorkflowRecoveryService recoveryService = new StaleRunningWorkflowRecoveryService(
            workflowRepository,
            createRuntimeService(),
            clock
        );

        assertThat(recoveryService.recover(Duration.ofMinutes(10), 10)).isZero();
        assertThat(activityRepository.findByWorkflowId(workflow.getWorkflowId())).isEmpty();
    }

    @Test
    void shouldCleanExpiredWorkflowLocksBeforeStartupRecoveryScan() {
        RecordingWorkflowLockService lockService = new RecordingWorkflowLockService();
        StaleRunningWorkflowRecoveryService recoveryService = new StaleRunningWorkflowRecoveryService(
            workflowRepository,
            createRuntimeService(),
            clock,
            lockService
        );

        assertThat(recoveryService.recover(Duration.ofMinutes(5), 10)).isZero();

        assertThat(lockService.cleanCallCount.get()).isEqualTo(1);
        assertThat(lockService.lastModifiedBefore.get()).isEqualTo(clock.instant().minus(Duration.ofMinutes(5)));
    }

    private WorkflowCommandService createTriggeredService() {
        DefaultWorkflowRuntimeService workflowRuntimeService = createRuntimeService();
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

    private DefaultWorkflowRuntimeService createRuntimeService() {
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
        return new DefaultWorkflowRuntimeService(
            workflowRepository,
            engine,
            workflowExecutionScheduler,
            serializer,
            clock
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

    private static final class RecordingWorkflowLockService implements WorkflowLockService {

        private final AtomicInteger cleanCallCount = new AtomicInteger();
        private final AtomicReference<Instant> lastModifiedBefore = new AtomicReference<Instant>();

        @Override
        public boolean tryLock(String key) {
            return true;
        }

        @Override
        public void unlock(String key) {
        }

        @Override
        public int cleanExpiredLocks(Instant modifiedBefore) {
            cleanCallCount.incrementAndGet();
            lastModifiedBefore.set(modifiedBefore);
            return 2;
        }
    }

    private static final class RecordingWorkflowRuntimeService implements WorkflowRuntimeService {

        @Override
        public void dispatchWorkflow(String workflowId) {
        }

        @Override
        public void retryWorkflow(String workflowId) {
        }

        @Override
        public void terminateWorkflow(String workflowId) {
        }

        @Override
        public void skipActivity(String workflowId, String input) {
        }
    }
}
