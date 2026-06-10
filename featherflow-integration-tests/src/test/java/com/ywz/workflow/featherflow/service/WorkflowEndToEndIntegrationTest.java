package com.ywz.workflow.featherflow.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.ywz.workflow.featherflow.daemon.DefaultWorkflowOperationHandler;
import com.ywz.workflow.featherflow.daemon.WorkflowOperationDaemon;
import com.ywz.workflow.featherflow.daemon.WorkflowOperationProcessor;
import com.ywz.workflow.featherflow.definition.ActivityDefinition;
import com.ywz.workflow.featherflow.definition.InMemoryWorkflowDefinitionRegistry;
import com.ywz.workflow.featherflow.definition.WorkflowDefinition;
import com.ywz.workflow.featherflow.engine.DefaultWorkflowExecutionScheduler;
import com.ywz.workflow.featherflow.engine.WorkflowEngine;
import com.ywz.workflow.featherflow.handler.MapBackedWorkflowActivityHandlerRegistry;
import com.ywz.workflow.featherflow.model.ActivityExecutionStatus;
import com.ywz.workflow.featherflow.model.ActivityInstance;
import com.ywz.workflow.featherflow.model.OperationStatus;
import com.ywz.workflow.featherflow.model.OperationType;
import com.ywz.workflow.featherflow.model.WorkflowInstance;
import com.ywz.workflow.featherflow.model.WorkflowOperation;
import com.ywz.workflow.featherflow.model.WorkflowStatus;
import com.ywz.workflow.featherflow.repository.jdbc.JdbcActivityRepository;
import com.ywz.workflow.featherflow.repository.jdbc.JdbcWorkflowLockService;
import com.ywz.workflow.featherflow.repository.jdbc.JdbcWorkflowOperationRepository;
import com.ywz.workflow.featherflow.repository.jdbc.JdbcWorkflowRepository;
import com.ywz.workflow.featherflow.support.JsonWorkflowContextSerializer;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

class WorkflowEndToEndIntegrationTest {

    private final Clock clock = Clock.fixed(Instant.parse("2026-04-09T10:00:00Z"), ZoneOffset.UTC);
    private final JsonWorkflowContextSerializer serializer = new JsonWorkflowContextSerializer();

    private InMemoryWorkflowDefinitionRegistry definitionRegistry;
    private MapBackedWorkflowActivityHandlerRegistry handlerRegistry;
    private JdbcWorkflowRepository workflowRepository;
    private JdbcActivityRepository activityRepository;
    private JdbcWorkflowOperationRepository operationRepository;
    private JdbcTemplate jdbcTemplate;
    private ExecutorService workflowExecutor;

    @BeforeEach
    void setUp() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
            "jdbc:h2:mem:featherflow-e2e-" + System.nanoTime() + ";MODE=MYSQL;DB_CLOSE_DELAY=-1",
            "sa",
            ""
        );
        jdbcTemplate = new JdbcTemplate(dataSource);
        new ResourceDatabasePopulator(new ClassPathResource("db/featherflow-h2.sql")).execute(dataSource);
        definitionRegistry = new InMemoryWorkflowDefinitionRegistry();
        handlerRegistry = new MapBackedWorkflowActivityHandlerRegistry();
        workflowRepository = new JdbcWorkflowRepository(jdbcTemplate);
        activityRepository = new JdbcActivityRepository(jdbcTemplate);
        operationRepository = new JdbcWorkflowOperationRepository(jdbcTemplate);
        workflowExecutor = Executors.newFixedThreadPool(2);
    }

    @AfterEach
    void tearDown() {
        workflowExecutor.shutdownNow();
    }

    @Test
    void shouldStartFailRetryAndCompleteThroughCommandServiceSchedulerAndJdbc() throws Exception {
        definitionRegistry.register(new WorkflowDefinition(
            "paymentE2eWorkflow",
            Arrays.asList(new ActivityDefinition("settlePayment", "settlePaymentHandler", Duration.ofMillis(10), 0))
        ));
        String workflowId = "wf-command-e2e-0001";
        AtomicInteger handlerCalls = new AtomicInteger();
        handlerRegistry.register("settlePaymentHandler", context -> {
            if (handlerCalls.incrementAndGet() == 1) {
                throw new IllegalStateException("bank timeout");
            }
            context.put("settled", Boolean.TRUE);
            return context;
        });
        WorkflowCommandService commandService = createCommandService(workflowId, "10.0.1.1:command-node");

        WorkflowInstance workflow = commandService.startWorkflow(
            "paymentE2eWorkflow",
            "biz-command-e2e",
            "payment:P-900",
            "{\"paymentId\":\"P-900\"}"
        );

        awaitStatus(workflow.getWorkflowId(), WorkflowStatus.HUMAN_PROCESSING);
        commandService.retryWorkflow(workflow.getWorkflowId());
        awaitStatus(workflow.getWorkflowId(), WorkflowStatus.COMPLETED);

        WorkflowInstance completedWorkflow = workflowRepository.findRequired(workflowId);
        List<ActivityInstance> attempts = activityRepository.findByWorkflowId(workflowId);
        assertThat(completedWorkflow.getBizId()).isEqualTo("biz-command-e2e");
        assertThat(completedWorkflow.getBizKey()).isEqualTo("payment:P-900");
        assertThat(completedWorkflow.getStartNode()).isEqualTo("10.0.1.1:command-node");
        assertThat(handlerCalls.get()).isEqualTo(2);
        assertThat(attempts)
            .extracting(ActivityInstance::getStatus)
            .containsExactly(ActivityExecutionStatus.FAILED, ActivityExecutionStatus.SUCCESSFUL);
        assertThat(attempts.get(0).getInput()).contains("\"paymentId\":\"P-900\"");
        assertThat(attempts.get(1).getInput()).contains("\"paymentId\":\"P-900\"");
        assertThat(attempts.get(1).getOutput()).contains("\"settled\":true");
        assertThat(jdbcTemplate.queryForObject("select count(*) from workflow_lock", Long.class)).isEqualTo(0L);
    }

    @Test
    void shouldConsumeRetryOperationThroughDaemonAndCompleteWorkflowEndToEnd() throws Exception {
        definitionRegistry.register(new WorkflowDefinition(
            "daemonRetryE2eWorkflow",
            Arrays.asList(new ActivityDefinition("approveOrder", "approveOrderHandler", Duration.ofMillis(10), 0))
        ));
        String workflowId = "wf-daemon-e2e-0001";
        AtomicInteger handlerCalls = new AtomicInteger();
        handlerRegistry.register("approveOrderHandler", context -> {
            if (handlerCalls.incrementAndGet() == 1) {
                throw new IllegalStateException("risk service timeout");
            }
            context.put("approved", Boolean.TRUE);
            return context;
        });
        WorkflowRuntimeService runtimeService = createRuntimeService("10.0.1.2:daemon-engine-node");
        WorkflowCommandService commandService = createCommandService(workflowId, "10.0.1.2:starter-node", runtimeService);

        WorkflowInstance workflow = commandService.startWorkflow(
            "daemonRetryE2eWorkflow",
            "biz-daemon-e2e",
            "order:O-900",
            "{\"orderId\":\"O-900\"}"
        );
        awaitStatus(workflow.getWorkflowId(), WorkflowStatus.HUMAN_PROCESSING);

        WorkflowOperation retryOperation = WorkflowOperation.pending(
            workflow.getWorkflowId(),
            OperationType.RETRY,
            "{\"operator\":\"ops-e2e\"}",
            clock.instant()
        );
        operationRepository.savePendingOperation(retryOperation);
        WorkflowOperationDaemon daemon = createDaemon(runtimeService, "10.0.1.3:daemon-node");

        daemon.pollOnce();

        awaitStatus(workflow.getWorkflowId(), WorkflowStatus.COMPLETED);
        List<ActivityInstance> attempts = activityRepository.findByWorkflowId(workflowId);
        WorkflowOperation consumedOperation = operationRepository.findAll().get(0);
        assertThat(handlerCalls.get()).isEqualTo(2);
        assertThat(attempts)
            .extracting(ActivityInstance::getStatus)
            .containsExactly(ActivityExecutionStatus.FAILED, ActivityExecutionStatus.SUCCESSFUL);
        assertThat(attempts.get(1).getOutput()).contains("\"approved\":true");
        assertThat(consumedOperation.getStatus()).isEqualTo(OperationStatus.SUCCESSFUL);
        assertThat(consumedOperation.getInput())
            .contains("\"operator\":\"ops-e2e\"")
            .contains("\"processedNode\":\"10.0.1.3:daemon-node\"");
        assertThat(operationRepository.findPendingByWorkflowId(workflowId)).isEmpty();
        assertThat(jdbcTemplate.queryForObject("select count(*) from workflow_lock", Long.class)).isEqualTo(0L);
    }

    private WorkflowCommandService createCommandService(String workflowId, String nodeIdentity) {
        return createCommandService(workflowId, nodeIdentity, createRuntimeService(nodeIdentity));
    }

    private WorkflowCommandService createCommandService(String workflowId, String nodeIdentity, WorkflowRuntimeService runtimeService) {
        return new DefaultWorkflowCommandService(
            definitionRegistry,
            workflowRepository,
            () -> workflowId,
            serializer,
            clock,
            runtimeService,
            nodeIdentity
        );
    }

    private WorkflowRuntimeService createRuntimeService(String nodeIdentity) {
        WorkflowEngine engine = new WorkflowEngine(
            definitionRegistry,
            workflowRepository,
            activityRepository,
            handlerRegistry,
            new JdbcWorkflowLockService(jdbcTemplate, nodeIdentity),
            serializer,
            clock,
            (workflowId, delay) -> {
            },
            nodeIdentity
        );
        DefaultWorkflowExecutionScheduler scheduler = new DefaultWorkflowExecutionScheduler(
            engine,
            workflowRepository,
            workflowExecutor,
            clock
        );
        return new DefaultWorkflowRuntimeService(workflowRepository, engine, scheduler, serializer, clock);
    }

    private WorkflowOperationDaemon createDaemon(WorkflowRuntimeService runtimeService, String daemonNodeIdentity) {
        DefaultWorkflowOperationHandler operationHandler = new DefaultWorkflowOperationHandler(
            workflowRepository,
            runtimeService,
            serializer
        );
        WorkflowOperationProcessor operationProcessor = new WorkflowOperationProcessor(
            operationRepository,
            workflowRepository,
            operationHandler,
            clock,
            daemonNodeIdentity
        );
        return new WorkflowOperationDaemon(operationRepository, clock, operationProcessor);
    }

    private void awaitStatus(String workflowId, WorkflowStatus expectedStatus) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 3000L;
        while (System.currentTimeMillis() < deadline) {
            if (workflowRepository.findRequired(workflowId).getStatus() == expectedStatus) {
                return;
            }
            TimeUnit.MILLISECONDS.sleep(20L);
        }
        assertThat(workflowRepository.findRequired(workflowId).getStatus()).isEqualTo(expectedStatus);
    }
}
