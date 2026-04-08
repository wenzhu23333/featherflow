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
import com.ywz.workflow.featherflow.model.ActivityExecutionStatus;
import com.ywz.workflow.featherflow.model.ActivityInstance;
import com.ywz.workflow.featherflow.model.WorkflowInstance;
import com.ywz.workflow.featherflow.model.WorkflowStatus;
import com.ywz.workflow.featherflow.repository.ActivityRepository;
import com.ywz.workflow.featherflow.repository.WorkflowOperationRepository;
import com.ywz.workflow.featherflow.repository.WorkflowRepository;
import com.ywz.workflow.featherflow.support.InMemoryActivityRepository;
import com.ywz.workflow.featherflow.support.InMemoryWorkflowOperationRepository;
import com.ywz.workflow.featherflow.support.InMemoryWorkflowRepository;
import com.ywz.workflow.featherflow.support.JsonWorkflowContextSerializer;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

class WorkflowBestPracticeScenariosTest {

    private final Clock clock = Clock.fixed(Instant.parse("2026-03-31T05:00:00Z"), ZoneOffset.UTC);
    private final JsonWorkflowContextSerializer serializer = new JsonWorkflowContextSerializer();

    private InMemoryWorkflowDefinitionRegistry definitionRegistry;
    private WorkflowRepository workflowRepository;
    private ActivityRepository activityRepository;
    private WorkflowOperationRepository operationRepository;
    private MapBackedWorkflowActivityHandlerRegistry handlerRegistry;
    private ExecutorService workflowExecutor;

    @BeforeEach
    void setUp() {
        definitionRegistry = new InMemoryWorkflowDefinitionRegistry();
        workflowRepository = new InMemoryWorkflowRepository();
        activityRepository = new InMemoryActivityRepository();
        operationRepository = new InMemoryWorkflowOperationRepository();
        handlerRegistry = new MapBackedWorkflowActivityHandlerRegistry();
        workflowExecutor = Executors.newSingleThreadExecutor(runnable -> new Thread(runnable, "workflow-best-practice-thread"));
    }

    @AfterEach
    void tearDown() {
        workflowExecutor.shutdownNow();
    }

    @Test
    void shouldPropagateContextAndPersistActivityInputOutputSnapshots() throws Exception {
        definitionRegistry.register(new WorkflowDefinition(
            "orderSnapshotWorkflow",
            Arrays.asList(
                new ActivityDefinition("createOrder", "createOrderHandler", Duration.ofSeconds(5), 1),
                new ActivityDefinition("notifyCustomer", "notifyCustomerHandler", Duration.ofSeconds(5), 1)
            )
        ));
        handlerRegistry.register("createOrderHandler", context -> {
            assertThat(context).containsEntry("amount", Integer.valueOf(100));
            assertThat(context).containsEntry("customer", "alice");
            context.put("orderCreated", Boolean.TRUE);
            context.put("orderNo", "ORD-1001");
            return context;
        });
        handlerRegistry.register("notifyCustomerHandler", context -> {
            assertThat(context).containsEntry("orderCreated", Boolean.TRUE);
            assertThat(context).containsEntry("orderNo", "ORD-1001");
            Map<String, Object> nextContext = new LinkedHashMap<String, Object>(context);
            nextContext.put("customerNotified", Boolean.TRUE);
            return nextContext;
        });

        WorkflowCommandService service = createTriggeredService();
        WorkflowInstance workflow = service.startWorkflow("orderSnapshotWorkflow", "biz-snapshot", "{\"amount\":100,\"customer\":\"alice\"}");

        awaitStatus(workflow.getWorkflowId(), WorkflowStatus.SUCCESSFUL, 1000L);
        assertThat(activityRepository.findByWorkflowId(workflow.getWorkflowId())).hasSize(2);

        ActivityInstance createOrder = activityRepository.findByWorkflowId(workflow.getWorkflowId()).get(0);
        ActivityInstance notifyCustomer = activityRepository.findByWorkflowId(workflow.getWorkflowId()).get(1);

        assertThat(serializer.deserialize(createOrder.getInput()))
            .containsEntry("amount", Integer.valueOf(100))
            .containsEntry("customer", "alice");
        assertThat(serializer.deserialize(createOrder.getOutput()))
            .containsEntry("orderCreated", Boolean.TRUE)
            .containsEntry("orderNo", "ORD-1001");
        assertThat(serializer.deserialize(notifyCustomer.getInput()))
            .containsEntry("orderCreated", Boolean.TRUE)
            .containsEntry("orderNo", "ORD-1001");
        assertThat(serializer.deserialize(notifyCustomer.getOutput()))
            .containsEntry("customerNotified", Boolean.TRUE)
            .containsEntry("orderCreated", Boolean.TRUE);
    }

    @Test
    void shouldPersistFailureOutputWhenActivityThrows() throws Exception {
        definitionRegistry.register(new WorkflowDefinition(
            "failureWorkflow",
            Arrays.asList(new ActivityDefinition("reserveInventory", "reserveInventoryHandler", Duration.ofSeconds(5), 0))
        ));
        handlerRegistry.register("reserveInventoryHandler", context -> {
            throw new IllegalStateException("inventory not enough");
        });

        WorkflowCommandService service = createTriggeredService();
        WorkflowInstance workflow = service.startWorkflow("failureWorkflow", "biz-failure", "{\"sku\":\"SKU-1\"}");

        awaitStatus(workflow.getWorkflowId(), WorkflowStatus.HUMAN_PROCESSING, 1000L);
        ActivityInstance activityInstance = activityRepository.findByWorkflowId(workflow.getWorkflowId()).get(0);

        assertThat(activityInstance.getStatus()).isEqualTo(ActivityExecutionStatus.FAILED);
        assertThat(serializer.deserialize(activityInstance.getInput())).containsEntry("sku", "SKU-1");
        assertThat(serializer.deserialize(activityInstance.getOutput()))
            .containsEntry("errorType", "java.lang.IllegalStateException")
            .containsEntry("errorMessage", "inventory not enough");
    }

    @Test
    void shouldResumeWorkflowFromManualRetryUsingLatestActivityInput() throws Exception {
        definitionRegistry.register(new WorkflowDefinition(
            "manualRetryWorkflow",
            Arrays.asList(new ActivityDefinition("settlePayment", "settlePaymentHandler", Duration.ofSeconds(5), 0))
        ));
        AtomicInteger attempts = new AtomicInteger();
        handlerRegistry.register("settlePaymentHandler", context -> {
            if (attempts.incrementAndGet() == 1) {
                throw new IllegalStateException("bank timeout");
            }
            assertThat(context).containsEntry("paymentId", "P-1");
            assertThat(context).doesNotContainKey("operator");
            context.put("settled", Boolean.TRUE);
            return context;
        });

        WorkflowCommandService service = createTriggeredService();
        WorkflowInstance workflow = service.startWorkflow("manualRetryWorkflow", "biz-retry", "{\"paymentId\":\"P-1\"}");

        awaitStatus(workflow.getWorkflowId(), WorkflowStatus.HUMAN_PROCESSING, 1000L);
        service.retryWorkflow(workflow.getWorkflowId());

        awaitStatus(workflow.getWorkflowId(), WorkflowStatus.SUCCESSFUL, 1000L);
        assertThat(activityRepository.findByWorkflowId(workflow.getWorkflowId()))
            .extracting(ActivityInstance::getStatus)
            .containsExactly(ActivityExecutionStatus.FAILED, ActivityExecutionStatus.SUCCESSFUL);
        assertThat(activityRepository.countByWorkflowIdAndActivityNameAndStatus(
            workflow.getWorkflowId(),
            "settlePayment",
            ActivityExecutionStatus.FAILED
        )).isEqualTo(1L);
        ActivityInstance activityInstance = activityRepository.findByWorkflowId(workflow.getWorkflowId()).get(1);
        assertThat(serializer.deserialize(activityInstance.getOutput()))
            .containsEntry("paymentId", "P-1")
            .containsEntry("settled", Boolean.TRUE);
    }

    @Test
    void shouldReuseSuccessfulActivityOutputForIdempotentContinuation() {
        definitionRegistry.register(new WorkflowDefinition(
            "idempotentWorkflow",
            Arrays.asList(
                new ActivityDefinition("createOrder", "createOrderHandler", Duration.ofSeconds(5), 1),
                new ActivityDefinition("sendReceipt", "sendReceiptHandler", Duration.ofSeconds(5), 1)
            )
        ));
        AtomicInteger createOrderCalls = new AtomicInteger();
        AtomicReference<Map<String, Object>> secondStepContext = new AtomicReference<Map<String, Object>>();
        handlerRegistry.register("createOrderHandler", context -> {
            createOrderCalls.incrementAndGet();
            context.put("orderNo", "ORD-2001");
            return context;
        });
        handlerRegistry.register("sendReceiptHandler", context -> {
            secondStepContext.set(new LinkedHashMap<String, Object>(context));
            context.put("receiptSent", Boolean.TRUE);
            return context;
        });

        WorkflowInstance workflow = new WorkflowInstance(
            "wf-idempotent-1",
            "biz-idempotent-1",
            "idempotentWorkflow",
            "test-node",
            clock.instant(),
            clock.instant(),
            "{\"amount\":100}",
            WorkflowStatus.RUNNING
        );
        workflowRepository.save(workflow);
        activityRepository.saveAttempt(
            "wf-idempotent-1-01-01",
            workflow.getWorkflowId(),
            "createOrder",
            "seed-node",
            "{\"amount\":100}",
            "{\"amount\":100,\"orderNo\":\"ORD-2001\"}",
            ActivityExecutionStatus.SUCCESSFUL,
            clock.instant()
        );

        WorkflowEngine engine = new WorkflowEngine(
            definitionRegistry,
            workflowRepository,
            activityRepository,
            handlerRegistry,
            new LocalWorkflowLockService(),
            serializer,
            clock,
            new NoOpWorkflowRetryScheduler()
        );

        engine.continueWorkflow(workflow.getWorkflowId());

        assertThat(createOrderCalls.get()).isZero();
        assertThat(secondStepContext.get()).containsEntry("orderNo", "ORD-2001");
        assertThat(workflowRepository.findRequired(workflow.getWorkflowId()).getStatus()).isEqualTo(WorkflowStatus.SUCCESSFUL);
    }

    @Test
    void shouldCorrelateFrameworkAndBusinessLogsWithWorkflowAndBizIds() throws Exception {
        definitionRegistry.register(new WorkflowDefinition(
            "loggingWorkflow",
            Arrays.asList(new ActivityDefinition("auditOrder", "auditOrderHandler", Duration.ofSeconds(5), 1))
        ));
        Logger commandServiceLogger = (Logger) LoggerFactory.getLogger(DefaultWorkflowCommandService.class);
        Logger schedulerLogger = (Logger) LoggerFactory.getLogger(DefaultWorkflowExecutionScheduler.class);
        Logger businessLogger = (Logger) LoggerFactory.getLogger("featherflow.best.practice.business");
        ListAppender<ILoggingEvent> commandServiceAppender = attachAppender(commandServiceLogger);
        ListAppender<ILoggingEvent> schedulerAppender = attachAppender(schedulerLogger);
        ListAppender<ILoggingEvent> businessAppender = attachAppender(businessLogger);
        handlerRegistry.register("auditOrderHandler", context -> {
            businessLogger.info("business audit log");
            context.put("audited", Boolean.TRUE);
            return context;
        });

        try {
            WorkflowCommandService service = createTriggeredService();
            WorkflowInstance workflow = service.startWorkflow("loggingWorkflow", "biz-log-bp", "{\"orderNo\":\"ORD-9\"}");

            awaitStatus(workflow.getWorkflowId(), WorkflowStatus.SUCCESSFUL, 1000L);

            assertThat(commandServiceAppender.list).anySatisfy(event -> {
                assertThat(event.getMDCPropertyMap()).containsEntry("workflowId", workflow.getWorkflowId());
                assertThat(event.getMDCPropertyMap()).containsEntry("bizId", workflow.getBizId());
            });
            assertThat(schedulerAppender.list).anySatisfy(event -> {
                assertThat(event.getFormattedMessage()).contains("Start workflow execution task");
                assertThat(event.getMDCPropertyMap()).containsEntry("workflowId", workflow.getWorkflowId());
                assertThat(event.getMDCPropertyMap()).containsEntry("bizId", workflow.getBizId());
            });
            assertThat(businessAppender.list).anySatisfy(event -> {
                assertThat(event.getFormattedMessage()).contains("business audit log");
                assertThat(event.getMDCPropertyMap()).containsEntry("workflowId", workflow.getWorkflowId());
                assertThat(event.getMDCPropertyMap()).containsEntry("bizId", workflow.getBizId());
            });
        } finally {
            detachAppender(commandServiceLogger, commandServiceAppender);
            detachAppender(schedulerLogger, schedulerAppender);
            detachAppender(businessLogger, businessAppender);
        }
    }

    private WorkflowCommandService createTriggeredService() {
        WorkflowRetryScheduler workflowRetryScheduler = (workflowId, delay) -> {
        };
        WorkflowEngine engine = new WorkflowEngine(
            definitionRegistry,
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
            definitionRegistry,
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
            TimeUnit.MILLISECONDS.sleep(20L);
        }
        assertThat(workflowRepository.findRequired(workflowId).getStatus()).isEqualTo(expectedStatus);
    }

    private ListAppender<ILoggingEvent> attachAppender(Logger logger) {
        ListAppender<ILoggingEvent> appender = new ListAppender<ILoggingEvent>();
        appender.start();
        logger.addAppender(appender);
        return appender;
    }

    private void detachAppender(Logger logger, ListAppender<ILoggingEvent> appender) {
        logger.detachAppender(appender);
        appender.stop();
    }

    private static final class NoOpWorkflowRetryScheduler implements WorkflowRetryScheduler {

        @Override
        public void scheduleRetry(String workflowId, Duration delay) {
        }
    }
}
