package com.ywz.workflow.featherflow.engine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.ywz.workflow.featherflow.definition.ActivityDefinition;
import com.ywz.workflow.featherflow.definition.InMemoryWorkflowDefinitionRegistry;
import com.ywz.workflow.featherflow.definition.WorkflowDefinition;
import com.ywz.workflow.featherflow.handler.MapBackedWorkflowActivityHandlerRegistry;
import com.ywz.workflow.featherflow.lock.LocalWorkflowLockService;
import com.ywz.workflow.featherflow.model.ActivityExecutionStatus;
import com.ywz.workflow.featherflow.model.ActivityInstance;
import com.ywz.workflow.featherflow.model.WorkflowInstance;
import com.ywz.workflow.featherflow.model.WorkflowStatus;
import com.ywz.workflow.featherflow.repository.ActivityRepository;
import com.ywz.workflow.featherflow.repository.WorkflowOperationRepository;
import com.ywz.workflow.featherflow.repository.WorkflowRepository;
import com.ywz.workflow.featherflow.service.DefaultWorkflowCommandService;
import com.ywz.workflow.featherflow.service.DefaultWorkflowIdGenerator;
import com.ywz.workflow.featherflow.service.DefaultWorkflowRuntimeService;
import com.ywz.workflow.featherflow.service.WorkflowCommandService;
import com.ywz.workflow.featherflow.service.WorkflowRuntimeService;
import com.ywz.workflow.featherflow.support.InMemoryActivityRepository;
import com.ywz.workflow.featherflow.support.InMemoryWorkflowOperationRepository;
import com.ywz.workflow.featherflow.support.InMemoryWorkflowRepository;
import com.ywz.workflow.featherflow.support.JsonWorkflowContextSerializer;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.slf4j.LoggerFactory;

class WorkflowEngineTest {

    private final Clock clock = Clock.fixed(Instant.parse("2026-03-30T12:00:00Z"), ZoneOffset.UTC);
    private final InMemoryWorkflowDefinitionRegistry registry = new InMemoryWorkflowDefinitionRegistry();
    private final WorkflowRepository workflowRepository = new InMemoryWorkflowRepository();
    private final ActivityRepository activityRepository = new InMemoryActivityRepository();
    private final WorkflowOperationRepository operationRepository = new InMemoryWorkflowOperationRepository();
    private final JsonWorkflowContextSerializer serializer = new JsonWorkflowContextSerializer();
    private final MapBackedWorkflowActivityHandlerRegistry handlerRegistry = new MapBackedWorkflowActivityHandlerRegistry();
    private final Logger businessLogger = (Logger) LoggerFactory.getLogger("featherflow.test.activity.business");
    private WorkflowCommandService commandService;
    private ListAppender<ILoggingEvent> businessAppender;

    private WorkflowEngine newEngine() {
        return new WorkflowEngine(
            registry,
            workflowRepository,
            activityRepository,
            handlerRegistry,
            new LocalWorkflowLockService(),
            serializer,
            clock,
            new NoOpWorkflowRetryScheduler(),
            "test-node"
        );
    }

    @BeforeEach
    void setUp() {
        registry.register(new WorkflowDefinition(
            "orderWorkflow",
            Arrays.asList(
                new ActivityDefinition("createOrder", "createOrderHandler", Duration.ofSeconds(5), 1),
                new ActivityDefinition("notifyCustomer", "notifyCustomerHandler", Duration.ofSeconds(5), 1)
            )
        ));
        businessAppender = new ListAppender<ILoggingEvent>();
        businessAppender.start();
        businessLogger.addAppender(businessAppender);
        WorkflowRuntimeService workflowRuntimeService = new DefaultWorkflowRuntimeService(
            workflowRepository,
            newEngine(),
            workflowId -> {
            },
            serializer,
            clock
        );
        commandService = new DefaultWorkflowCommandService(
            registry,
            workflowRepository,
            new DefaultWorkflowIdGenerator(),
            serializer,
            clock,
            workflowRuntimeService,
            "test-node"
        );
    }

    @AfterEach
    void tearDown() {
        businessLogger.detachAppender(businessAppender);
        businessAppender.stop();
        MDC.clear();
    }

    @Test
    void shouldExecuteActivitiesSequentiallyAndFinishWorkflow() {
        handlerRegistry.register("createOrderHandler", context -> {
            context.put("orderCreated", true);
            return context;
        });
        handlerRegistry.register("notifyCustomerHandler", context -> {
            context.put("customerNotified", true);
            return context;
        });

        WorkflowInstance workflow = commandService.startWorkflow("orderWorkflow", "biz-1", "{\"amount\":100}");
        WorkflowEngine engine = newEngine();

        assertThat(activityRepository.findByWorkflowId(workflow.getWorkflowId())).isEmpty();
        engine.continueWorkflow(workflow.getWorkflowId());

        assertThat(workflowRepository.findRequired(workflow.getWorkflowId()).getStatus()).isEqualTo(WorkflowStatus.SUCCESSFUL);
        assertThat(activityRepository.findByWorkflowId(workflow.getWorkflowId()))
            .extracting(activity -> activity.getStatus())
            .containsExactly(ActivityExecutionStatus.SUCCESSFUL, ActivityExecutionStatus.SUCCESSFUL);
        assertThat(activityRepository.findByWorkflowId(workflow.getWorkflowId()))
            .allMatch(activity -> "test-node".equals(activity.getExecutedNode()));
        assertThat(activityRepository.findByWorkflowId(workflow.getWorkflowId()).get(1).getOutput()).contains("customerNotified");
    }

    @Test
    void shouldScheduleRetryWhenActivityFails() {
        AtomicInteger attemptCounter = new AtomicInteger();
        RecordingWorkflowRetryScheduler workflowRetryScheduler = new RecordingWorkflowRetryScheduler();
        handlerRegistry.register("createOrderHandler", context -> {
            attemptCounter.incrementAndGet();
            throw new IllegalStateException("boom");
        });
        handlerRegistry.register("notifyCustomerHandler", context -> context);

        WorkflowInstance workflow = commandService.startWorkflow("orderWorkflow", "biz-2", "{\"amount\":100}");
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

        engine.continueWorkflow(workflow.getWorkflowId());

        assertThat(attemptCounter.get()).isEqualTo(1);
        assertThat(workflowRepository.findRequired(workflow.getWorkflowId()).getStatus()).isEqualTo(WorkflowStatus.RUNNING);
        assertThat(activityRepository.findByWorkflowId(workflow.getWorkflowId())).hasSize(1);
        assertThat(activityRepository.findByWorkflowId(workflow.getWorkflowId()).get(0).getStatus()).isEqualTo(ActivityExecutionStatus.FAILED);
        assertThat(operationRepository.findPendingByWorkflowId(workflow.getWorkflowId())).isEmpty();
        assertThat(workflowRetryScheduler.workflowIds).containsExactly(workflow.getWorkflowId());
        assertThat(workflowRetryScheduler.delays).containsExactly(Duration.ofSeconds(5));
    }

    @Test
    void shouldScheduleRetryWhenActivityThrowsError() {
        AtomicInteger attemptCounter = new AtomicInteger();
        RecordingWorkflowRetryScheduler workflowRetryScheduler = new RecordingWorkflowRetryScheduler();
        handlerRegistry.register("createOrderHandler", context -> {
            attemptCounter.incrementAndGet();
            throw new AssertionError("fatal-check");
        });
        handlerRegistry.register("notifyCustomerHandler", context -> context);

        WorkflowInstance workflow = commandService.startWorkflow("orderWorkflow", "biz-error-retry", "{\"amount\":100}");
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

        engine.continueWorkflow(workflow.getWorkflowId());

        assertThat(attemptCounter.get()).isEqualTo(1);
        assertThat(workflowRepository.findRequired(workflow.getWorkflowId()).getStatus()).isEqualTo(WorkflowStatus.RUNNING);
        assertThat(activityRepository.findByWorkflowId(workflow.getWorkflowId())).hasSize(1);
        assertThat(activityRepository.findByWorkflowId(workflow.getWorkflowId()).get(0).getStatus()).isEqualTo(ActivityExecutionStatus.FAILED);
        assertThat(activityRepository.findByWorkflowId(workflow.getWorkflowId()).get(0).getOutput())
            .contains("java.lang.AssertionError")
            .contains("fatal-check");
        assertThat(workflowRetryScheduler.workflowIds).containsExactly(workflow.getWorkflowId());
        assertThat(workflowRetryScheduler.delays).containsExactly(Duration.ofSeconds(5));
    }

    @Test
    void shouldMoveWorkflowToHumanProcessingAfterRetryExhausted() {
        registry.register(new WorkflowDefinition(
            "humanWorkflow",
            Collections.singletonList(new ActivityDefinition("alwaysFail", "alwaysFailHandler", Duration.ofSeconds(5), 0))
        ));
        handlerRegistry.register("alwaysFailHandler", context -> {
            throw new IllegalStateException("permanent-failure");
        });

        WorkflowInstance workflow = commandService.startWorkflow("humanWorkflow", "biz-3", "{\"amount\":100}");
        WorkflowEngine engine = newEngine();

        engine.continueWorkflow(workflow.getWorkflowId());

        assertThat(workflowRepository.findRequired(workflow.getWorkflowId()).getStatus()).isEqualTo(WorkflowStatus.HUMAN_PROCESSING);
        assertThat(activityRepository.findByWorkflowId(workflow.getWorkflowId()).get(0).getOutput()).contains("permanent-failure");
    }

    @Test
    void shouldPersistMultipleFailedAttemptsForTheSameActivity() {
        handlerRegistry.register("createOrderHandler", context -> {
            throw new IllegalStateException("boom");
        });
        handlerRegistry.register("notifyCustomerHandler", context -> context);

        WorkflowInstance workflow = commandService.startWorkflow("orderWorkflow", "biz-attempt-history", "{\"amount\":100}");
        WorkflowEngine engine = newEngine();

        engine.continueWorkflow(workflow.getWorkflowId());
        engine.continueWorkflow(workflow.getWorkflowId());

        assertThat(activityRepository.findByWorkflowId(workflow.getWorkflowId()))
            .extracting(ActivityInstance::getActivityId)
            .containsExactly(workflow.getWorkflowId() + "-01-01", workflow.getWorkflowId() + "-01-02");
        assertThat(activityRepository.findByWorkflowId(workflow.getWorkflowId()))
            .extracting(ActivityInstance::getStatus)
            .containsExactly(ActivityExecutionStatus.FAILED, ActivityExecutionStatus.FAILED);
    }

    @Test
    void shouldMoveWorkflowToHumanProcessingWhenActivityThrowsErrorAndRetryIsExhausted() {
        registry.register(new WorkflowDefinition(
            "errorWorkflow",
            Collections.singletonList(new ActivityDefinition("alwaysError", "alwaysErrorHandler", Duration.ofSeconds(5), 0))
        ));
        handlerRegistry.register("alwaysErrorHandler", context -> {
            throw new AssertionError("fatal-check");
        });

        WorkflowInstance workflow = commandService.startWorkflow("errorWorkflow", "biz-error-human", "{\"amount\":100}");
        WorkflowEngine engine = newEngine();

        engine.continueWorkflow(workflow.getWorkflowId());

        assertThat(workflowRepository.findRequired(workflow.getWorkflowId()).getStatus()).isEqualTo(WorkflowStatus.HUMAN_PROCESSING);
        assertThat(activityRepository.findByWorkflowId(workflow.getWorkflowId()).get(0).getStatus()).isEqualTo(ActivityExecutionStatus.FAILED);
        assertThat(activityRepository.findByWorkflowId(workflow.getWorkflowId()).get(0).getOutput())
            .contains("java.lang.AssertionError")
            .contains("fatal-check");
    }

    @Test
    void shouldReuseSuccessfulActivityOutputForIdempotency() {
        AtomicInteger notifyCounter = new AtomicInteger();
        handlerRegistry.register("createOrderHandler", context -> {
            throw new AssertionError("createOrderHandler should not be called again");
        });
        handlerRegistry.register("notifyCustomerHandler", context -> {
            notifyCounter.incrementAndGet();
            context.put("customerNotified", true);
            return context;
        });

        WorkflowInstance workflow = commandService.startWorkflow("orderWorkflow", "biz-4", "{\"amount\":100}");
        activityRepository.saveAttempt(
            workflow.getWorkflowId() + "-01-01",
            workflow.getWorkflowId(),
            "createOrder",
            "seed-node",
            "{\"amount\":100}",
            serializer.merge("{\"amount\":100}", "{\"orderCreated\":true}"),
            ActivityExecutionStatus.SUCCESSFUL,
            clock.instant()
        );

        WorkflowEngine engine = newEngine();

        engine.continueWorkflow(workflow.getWorkflowId());

        assertThat(notifyCounter.get()).isEqualTo(1);
        assertThat(activityRepository.findByWorkflowId(workflow.getWorkflowId())).hasSize(2);
        assertThat(activityRepository.findByWorkflowId(workflow.getWorkflowId()).get(1).getOutput()).contains("orderCreated");
    }

    @Test
    void shouldRetryFailedActivityUsingLatestFailedInputSnapshot() {
        AtomicReference<Map<String, Object>> retryContext = new AtomicReference<Map<String, Object>>();
        handlerRegistry.register("createOrderHandler", context -> {
            throw new AssertionError("createOrderHandler should not be called again");
        });
        handlerRegistry.register("notifyCustomerHandler", context -> {
            retryContext.set(new HashMap<String, Object>(context));
            context.put("customerNotified", true);
            return context;
        });

        WorkflowInstance workflow = commandService.startWorkflow("orderWorkflow", "biz-failed-snapshot", "{\"amount\":100}");
        String step1Output = serializer.merge("{\"amount\":100}", "{\"orderCreated\":true}");
        String failedInput = serializer.merge(step1Output, "{\"snapshot\":\"failed-input\"}");
        activityRepository.saveAttempt(
            workflow.getWorkflowId() + "-01-01",
            workflow.getWorkflowId(),
            "createOrder",
            "seed-node",
            "{\"amount\":100}",
            step1Output,
            ActivityExecutionStatus.SUCCESSFUL,
            clock.instant()
        );
        activityRepository.saveAttempt(
            workflow.getWorkflowId() + "-02-01",
            workflow.getWorkflowId(),
            "notifyCustomer",
            "seed-node",
            failedInput,
            "{\"error\":\"notify failed\"}",
            ActivityExecutionStatus.FAILED,
            clock.instant()
        );

        WorkflowEngine engine = newEngine();

        engine.continueWorkflow(workflow.getWorkflowId());

        assertThat(retryContext.get()).containsEntry("snapshot", "failed-input");
        assertThat(activityRepository.findByWorkflowId(workflow.getWorkflowId()).get(2).getInput()).contains("\"snapshot\":\"failed-input\"");
        assertThat(activityRepository.findByWorkflowId(workflow.getWorkflowId()).get(2).getOutput()).contains("\"snapshot\":\"failed-input\"");
    }

    @Test
    void shouldAppendSuccessfulSkipAttemptInsteadOfMutatingFailedRow() {
        WorkflowInstance workflow = commandService.startWorkflow("orderWorkflow", "biz-skip-append", "{\"amount\":100}");
        String step1Output = serializer.merge("{\"amount\":100}", "{\"orderCreated\":true}");
        String failedInput = serializer.merge(step1Output, "{\"manualTag\":\"latest-failed\"}");
        activityRepository.saveAttempt(
            workflow.getWorkflowId() + "-01-01",
            workflow.getWorkflowId(),
            "createOrder",
            "seed-node",
            "{\"amount\":100}",
            step1Output,
            ActivityExecutionStatus.SUCCESSFUL,
            clock.instant()
        );
        activityRepository.saveAttempt(
            workflow.getWorkflowId() + "-02-01",
            workflow.getWorkflowId(),
            "notifyCustomer",
            "seed-node",
            failedInput,
            "{\"error\":\"notify failed\"}",
            ActivityExecutionStatus.FAILED,
            clock.instant()
        );
        workflowRepository.updateStatus(workflow.getWorkflowId(), WorkflowStatus.TERMINATED, clock.instant());

        WorkflowEngine engine = newEngine();
        engine.skipActivity(workflow.getWorkflowId(), "{\"operator\":\"ops\"}");

        List<ActivityInstance> attempts = activityRepository.findByWorkflowId(workflow.getWorkflowId());
        assertThat(attempts).hasSize(3);
        assertThat(attempts)
            .filteredOn(activity -> activity.getActivityName().equals("notifyCustomer"))
            .extracting(ActivityInstance::getActivityId)
            .containsExactly(workflow.getWorkflowId() + "-02-01", workflow.getWorkflowId() + "-02-02");
        assertThat(attempts)
            .filteredOn(activity -> activity.getActivityName().equals("notifyCustomer"))
            .extracting(ActivityInstance::getStatus)
            .containsExactly(ActivityExecutionStatus.FAILED, ActivityExecutionStatus.SUCCESSFUL);
        assertThat(attempts)
            .filteredOn(activity -> activity.getActivityName().equals("notifyCustomer"))
            .filteredOn(activity -> activity.getStatus() == ActivityExecutionStatus.SUCCESSFUL)
            .singleElement()
            .satisfies(activity -> {
                assertThat(activity.getOutput()).contains("_featherflowSkip");
                assertThat(activity.getOutput()).contains("\"manualTag\":\"latest-failed\"");
                assertThat(activity.getExecutedNode()).isEqualTo("test-node");
            });
    }

    @Test
    void shouldRequireLatestRecordedActivityWhenSkipping() {
        WorkflowInstance workflow = commandService.startWorkflow("orderWorkflow", "biz-skip-check", "{\"amount\":100}");
        workflowRepository.updateStatus(workflow.getWorkflowId(), WorkflowStatus.TERMINATED, clock.instant());

        WorkflowEngine engine = newEngine();

        assertThatThrownBy(() -> engine.skipActivity(workflow.getWorkflowId(), "{\"manual\":true}"))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("latest recorded activity");
    }

    @Test
    void shouldExposeWorkflowIdsInActivityExecutionMdc() {
        AtomicReference<String> workflowIdRef = new AtomicReference<String>();
        AtomicReference<String> bizIdRef = new AtomicReference<String>();
        handlerRegistry.register("createOrderHandler", context -> {
            workflowIdRef.set(MDC.get("workflowId"));
            bizIdRef.set(MDC.get("bizId"));
            context.put("orderCreated", true);
            return context;
        });
        handlerRegistry.register("notifyCustomerHandler", context -> context);

        WorkflowInstance workflow = commandService.startWorkflow("orderWorkflow", "biz-log-1", "{\"amount\":100}");
        WorkflowEngine engine = newEngine();

        engine.continueWorkflow(workflow.getWorkflowId());

        assertThat(workflowIdRef.get()).isEqualTo(workflow.getWorkflowId());
        assertThat(bizIdRef.get()).isEqualTo(workflow.getBizId());
    }

    @Test
    void shouldIncludeWorkflowIdsInActivityBusinessLogs() {
        handlerRegistry.register("createOrderHandler", context -> {
            businessLogger.info("create order business log");
            context.put("orderCreated", true);
            return context;
        });
        handlerRegistry.register("notifyCustomerHandler", context -> context);

        WorkflowInstance workflow = commandService.startWorkflow("orderWorkflow", "biz-log-2", "{\"amount\":100}");
        WorkflowEngine engine = newEngine();

        engine.continueWorkflow(workflow.getWorkflowId());

        assertThat(businessAppender.list).anySatisfy(event -> {
            assertThat(event.getFormattedMessage()).contains("create order business log");
            assertThat(event.getMDCPropertyMap()).containsEntry("workflowId", workflow.getWorkflowId());
            assertThat(event.getMDCPropertyMap()).containsEntry("bizId", workflow.getBizId());
        });
    }

    private static final class RecordingWorkflowRetryScheduler implements WorkflowRetryScheduler {

        private final List<String> workflowIds = new ArrayList<String>();
        private final List<Duration> delays = new ArrayList<Duration>();

        @Override
        public void scheduleRetry(String workflowId, Duration delay) {
            workflowIds.add(workflowId);
            delays.add(delay);
        }
    }

    private static final class NoOpWorkflowRetryScheduler implements WorkflowRetryScheduler {

        @Override
        public void scheduleRetry(String workflowId, Duration delay) {
        }
    }
}
