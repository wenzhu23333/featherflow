package com.ywz.workflow.featherflow.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.ywz.workflow.featherflow.definition.ActivityDefinition;
import com.ywz.workflow.featherflow.definition.InMemoryWorkflowDefinitionRegistry;
import com.ywz.workflow.featherflow.definition.WorkflowDefinition;
import com.ywz.workflow.featherflow.engine.WorkflowEngine;
import com.ywz.workflow.featherflow.engine.WorkflowRetryScheduler;
import com.ywz.workflow.featherflow.handler.MapBackedWorkflowActivityHandlerRegistry;
import com.ywz.workflow.featherflow.model.ActivityExecutionStatus;
import com.ywz.workflow.featherflow.model.ActivityInstance;
import com.ywz.workflow.featherflow.model.WorkflowInstance;
import com.ywz.workflow.featherflow.model.WorkflowStatus;
import com.ywz.workflow.featherflow.repository.jdbc.JdbcActivityRepository;
import com.ywz.workflow.featherflow.repository.jdbc.JdbcWorkflowLockService;
import com.ywz.workflow.featherflow.repository.jdbc.JdbcWorkflowRepository;
import com.ywz.workflow.featherflow.support.JsonWorkflowContextSerializer;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

class WorkflowRetrySafetyIntegrationTest {

    private final Clock clock = Clock.fixed(Instant.parse("2026-04-08T10:00:00Z"), ZoneOffset.UTC);
    private final JsonWorkflowContextSerializer serializer = new JsonWorkflowContextSerializer();

    private InMemoryWorkflowDefinitionRegistry definitionRegistry;
    private MapBackedWorkflowActivityHandlerRegistry handlerRegistry;
    private JdbcWorkflowRepository workflowRepository;
    private JdbcActivityRepository activityRepository;
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
            "jdbc:h2:mem:featherflow-retry-safety-" + System.nanoTime() + ";MODE=MYSQL;DB_CLOSE_DELAY=-1",
            "sa",
            ""
        );
        jdbcTemplate = new JdbcTemplate(dataSource);
        new ResourceDatabasePopulator(new ClassPathResource("db/featherflow-h2.sql")).execute(dataSource);
        definitionRegistry = new InMemoryWorkflowDefinitionRegistry();
        handlerRegistry = new MapBackedWorkflowActivityHandlerRegistry();
        workflowRepository = new JdbcWorkflowRepository(jdbcTemplate);
        activityRepository = new JdbcActivityRepository(jdbcTemplate);
    }

    @Test
    void shouldPreventSplitBrainWhenSameRetryActivityIsDispatchedConcurrently() throws Exception {
        definitionRegistry.register(new WorkflowDefinition(
            "concurrentRetryWorkflow",
            Arrays.asList(new ActivityDefinition("chargePayment", "chargePaymentHandler", Duration.ofMillis(10), 0))
        ));
        String workflowId = "wf-concurrent-retry-lock-0001";
        saveRunningWorkflow(workflowId, "concurrentRetryWorkflow", "{\"paymentId\":\"P-100\"}");
        activityRepository.saveAttempt(
            workflowId + "-01-01",
            workflowId,
            "chargePayment",
            "seed-node",
            "{\"paymentId\":\"P-100\"}",
            "{\"errorMessage\":\"bank timeout\"}",
            ActivityExecutionStatus.FAILED,
            clock.instant()
        );

        CountDownLatch handlerEntered = new CountDownLatch(1);
        CountDownLatch releaseHandler = new CountDownLatch(1);
        AtomicInteger handlerCalls = new AtomicInteger();
        handlerRegistry.register("chargePaymentHandler", context -> {
            handlerCalls.incrementAndGet();
            handlerEntered.countDown();
            assertThat(releaseHandler.await(2, TimeUnit.SECONDS)).isTrue();
            Map<String, Object> output = new LinkedHashMap<String, Object>(context);
            output.put("charged", Boolean.TRUE);
            return output;
        });

        WorkflowEngine firstNodeEngine = createEngine("10.0.0.1:retry-node-a");
        WorkflowEngine secondNodeEngine = createEngine("10.0.0.2:retry-node-b");
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<?> firstRun = executor.submit(() -> firstNodeEngine.continueWorkflow(workflowId));
            assertThat(handlerEntered.await(2, TimeUnit.SECONDS)).isTrue();

            Future<?> duplicatedRetryRun = executor.submit(() -> secondNodeEngine.continueWorkflow(workflowId));
            duplicatedRetryRun.get(2, TimeUnit.SECONDS);

            releaseHandler.countDown();
            firstRun.get(2, TimeUnit.SECONDS);
        } finally {
            releaseHandler.countDown();
            executor.shutdownNow();
        }

        List<ActivityInstance> attempts = activityRepository.findByWorkflowId(workflowId);
        assertThat(handlerCalls.get()).isEqualTo(1);
        assertThat(attempts).hasSize(2);
        assertThat(attempts)
            .extracting(ActivityInstance::getActivityId)
            .containsExactly(workflowId + "-01-01", workflowId + "-01-02");
        assertThat(attempts)
            .extracting(ActivityInstance::getStatus)
            .containsExactly(ActivityExecutionStatus.FAILED, ActivityExecutionStatus.SUCCESSFUL);
        assertThat(activityRepository.countByWorkflowIdAndActivityNameAndStatus(
            workflowId,
            "chargePayment",
            ActivityExecutionStatus.SUCCESSFUL
        )).isEqualTo(1L);
        assertThat(jdbcTemplate.queryForObject("select count(*) from workflow_lock", Long.class)).isEqualTo(0L);
        assertThat(workflowRepository.findRequired(workflowId).getStatus()).isEqualTo(WorkflowStatus.COMPLETED);
    }

    @Test
    void shouldReuseSuccessfulActivityOutputAndAvoidDuplicateRecordsWhenRetryContinues() {
        definitionRegistry.register(new WorkflowDefinition(
            "idempotentRetryWorkflow",
            Arrays.asList(
                new ActivityDefinition("prepareOrder", "prepareOrderHandler", Duration.ofMillis(10), 0),
                new ActivityDefinition("notifyCustomer", "notifyCustomerHandler", Duration.ofMillis(10), 0)
            )
        ));
        String workflowId = "wf-idempotent-retry-0001";
        saveRunningWorkflow(workflowId, "idempotentRetryWorkflow", "{\"orderId\":\"O-100\"}");
        activityRepository.saveAttempt(
            workflowId + "-01-01",
            workflowId,
            "prepareOrder",
            "seed-node",
            "{\"orderId\":\"O-100\"}",
            "{\"orderId\":\"O-100\",\"prepared\":true}",
            ActivityExecutionStatus.SUCCESSFUL,
            clock.instant()
        );
        activityRepository.saveAttempt(
            workflowId + "-02-01",
            workflowId,
            "notifyCustomer",
            "seed-node",
            "{\"orderId\":\"O-100\",\"prepared\":true,\"retrySeed\":\"failed-input\"}",
            "{\"errorMessage\":\"sms timeout\"}",
            ActivityExecutionStatus.FAILED,
            clock.instant()
        );

        AtomicInteger prepareCalls = new AtomicInteger();
        AtomicInteger notifyCalls = new AtomicInteger();
        AtomicReference<Map<String, Object>> notifyInput = new AtomicReference<Map<String, Object>>();
        handlerRegistry.register("prepareOrderHandler", context -> {
            prepareCalls.incrementAndGet();
            throw new AssertionError("prepareOrder is already SUCCESSFUL and must be reused by idempotency");
        });
        handlerRegistry.register("notifyCustomerHandler", context -> {
            notifyCalls.incrementAndGet();
            notifyInput.set(new LinkedHashMap<String, Object>(context));
            Map<String, Object> output = new LinkedHashMap<String, Object>(context);
            output.put("notified", Boolean.TRUE);
            return output;
        });

        createEngine("10.0.0.3:retry-node-c").continueWorkflow(workflowId);

        List<ActivityInstance> attempts = activityRepository.findByWorkflowId(workflowId);
        assertThat(prepareCalls.get()).isZero();
        assertThat(notifyCalls.get()).isEqualTo(1);
        assertThat(notifyInput.get())
            .containsEntry("prepared", Boolean.TRUE)
            .containsEntry("retrySeed", "failed-input");
        assertThat(attempts).hasSize(3);
        assertThat(attempts)
            .extracting(ActivityInstance::getActivityName)
            .containsExactly("prepareOrder", "notifyCustomer", "notifyCustomer");
        assertThat(activityRepository.countByWorkflowIdAndActivityNameAndStatus(
            workflowId,
            "prepareOrder",
            ActivityExecutionStatus.SUCCESSFUL
        )).isEqualTo(1L);
        assertThat(activityRepository.countByWorkflowIdAndActivityNameAndStatus(
            workflowId,
            "notifyCustomer",
            ActivityExecutionStatus.SUCCESSFUL
        )).isEqualTo(1L);
        assertThat(workflowRepository.findRequired(workflowId).getStatus()).isEqualTo(WorkflowStatus.COMPLETED);
    }

    @Test
    void shouldInsertOnlyOneRecordForNextActivityWhenDuplicateRetryDispatchesRace() throws Exception {
        definitionRegistry.register(new WorkflowDefinition(
            "nextActivityRaceWorkflow",
            Arrays.asList(
                new ActivityDefinition("prepareOrder", "prepareOrderHandler", Duration.ofMillis(10), 0),
                new ActivityDefinition("shipOrder", "shipOrderHandler", Duration.ofMillis(10), 0)
            )
        ));
        String workflowId = "wf-next-activity-race-0001";
        saveRunningWorkflow(workflowId, "nextActivityRaceWorkflow", "{\"orderId\":\"O-200\"}");
        activityRepository.saveAttempt(
            workflowId + "-01-01",
            workflowId,
            "prepareOrder",
            "seed-node",
            "{\"orderId\":\"O-200\"}",
            "{\"orderId\":\"O-200\",\"prepared\":true}",
            ActivityExecutionStatus.SUCCESSFUL,
            clock.instant()
        );

        CountDownLatch shipEntered = new CountDownLatch(1);
        CountDownLatch releaseShip = new CountDownLatch(1);
        AtomicInteger prepareCalls = new AtomicInteger();
        AtomicInteger shipCalls = new AtomicInteger();
        handlerRegistry.register("prepareOrderHandler", context -> {
            prepareCalls.incrementAndGet();
            throw new AssertionError("prepareOrder must be reused and must not run again");
        });
        handlerRegistry.register("shipOrderHandler", context -> {
            shipCalls.incrementAndGet();
            shipEntered.countDown();
            assertThat(releaseShip.await(2, TimeUnit.SECONDS)).isTrue();
            Map<String, Object> output = new LinkedHashMap<String, Object>(context);
            output.put("shipped", Boolean.TRUE);
            return output;
        });

        WorkflowEngine firstNodeEngine = createEngine("10.0.0.4:retry-node-d");
        WorkflowEngine secondNodeEngine = createEngine("10.0.0.5:retry-node-e");
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<?> firstRun = executor.submit(() -> firstNodeEngine.continueWorkflow(workflowId));
            assertThat(shipEntered.await(2, TimeUnit.SECONDS)).isTrue();

            Future<?> duplicatedRetryRun = executor.submit(() -> secondNodeEngine.continueWorkflow(workflowId));
            duplicatedRetryRun.get(2, TimeUnit.SECONDS);

            releaseShip.countDown();
            firstRun.get(2, TimeUnit.SECONDS);
        } finally {
            releaseShip.countDown();
            executor.shutdownNow();
        }

        List<ActivityInstance> attempts = activityRepository.findByWorkflowId(workflowId);
        assertThat(prepareCalls.get()).isZero();
        assertThat(shipCalls.get()).isEqualTo(1);
        assertThat(attempts).hasSize(2);
        assertThat(attempts)
            .extracting(ActivityInstance::getActivityName)
            .containsExactly("prepareOrder", "shipOrder");
        assertThat(attempts)
            .filteredOn(activity -> "shipOrder".equals(activity.getActivityName()))
            .singleElement()
            .satisfies(activity -> {
                assertThat(activity.getActivityId()).isEqualTo(workflowId + "-02-01");
                assertThat(activity.getStatus()).isEqualTo(ActivityExecutionStatus.SUCCESSFUL);
                assertThat(activity.getOutput()).contains("\"shipped\":true");
            });
        assertThat(jdbcTemplate.queryForObject("select count(*) from workflow_lock", Long.class)).isEqualTo(0L);
        assertThat(workflowRepository.findRequired(workflowId).getStatus()).isEqualTo(WorkflowStatus.COMPLETED);
    }

    private WorkflowEngine createEngine(String nodeIdentity) {
        return new WorkflowEngine(
            definitionRegistry,
            workflowRepository,
            activityRepository,
            handlerRegistry,
            new JdbcWorkflowLockService(jdbcTemplate, nodeIdentity),
            serializer,
            clock,
            new NoOpWorkflowRetryScheduler(),
            nodeIdentity
        );
    }

    private void saveRunningWorkflow(String workflowId, String workflowName, String input) {
        workflowRepository.save(new WorkflowInstance(
            workflowId,
            "biz-" + workflowId,
            workflowName,
            "starter-node",
            clock.instant(),
            clock.instant(),
            input,
            WorkflowStatus.RUNNING
        ));
    }

    private static final class NoOpWorkflowRetryScheduler implements WorkflowRetryScheduler {
        @Override
        public void scheduleRetry(String workflowId, Duration delay) {
        }
    }
}
