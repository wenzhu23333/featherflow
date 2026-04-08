package com.ywz.workflow.featherflow.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.ywz.workflow.featherflow.model.ActivityExecutionStatus;
import com.ywz.workflow.featherflow.model.ActivityInstance;
import com.ywz.workflow.featherflow.model.OperationStatus;
import com.ywz.workflow.featherflow.model.OperationType;
import com.ywz.workflow.featherflow.model.WorkflowInstance;
import com.ywz.workflow.featherflow.model.WorkflowOperation;
import com.ywz.workflow.featherflow.model.WorkflowStatus;
import com.ywz.workflow.featherflow.repository.jdbc.JdbcWorkflowLockService;
import com.ywz.workflow.featherflow.repository.jdbc.JdbcActivityRepository;
import com.ywz.workflow.featherflow.repository.jdbc.JdbcWorkflowOperationRepository;
import com.ywz.workflow.featherflow.repository.jdbc.JdbcWorkflowRepository;
import java.lang.reflect.Field;
import java.net.InetAddress;
import java.time.Instant;
import java.util.Arrays;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

class JdbcRepositoryIntegrationTest {

    private JdbcTemplate jdbcTemplate;
    private JdbcWorkflowRepository workflowRepository;
    private JdbcActivityRepository activityRepository;
    private JdbcWorkflowOperationRepository operationRepository;

    @BeforeEach
    void setUp() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
            "jdbc:h2:mem:featherflow-" + System.nanoTime() + ";MODE=MYSQL;DB_CLOSE_DELAY=-1",
            "sa",
            ""
        );
        jdbcTemplate = new JdbcTemplate(dataSource);
        ResourceDatabasePopulator populator = new ResourceDatabasePopulator(new ClassPathResource("schema.sql"));
        populator.execute(dataSource);
        workflowRepository = new JdbcWorkflowRepository(jdbcTemplate);
        activityRepository = new JdbcActivityRepository(jdbcTemplate);
        operationRepository = new JdbcWorkflowOperationRepository(jdbcTemplate);
    }

    @Test
    void shouldPersistAndLoadWorkflowRecords() {
        Instant now = Instant.parse("2026-03-30T13:00:00Z");
        String startNode = "10.9.8.7:host-a:1234:seed";
        WorkflowInstance workflow = new WorkflowInstance(
            "abcd-1234-abcd-1234",
            "biz-1",
            "orderWorkflow",
            startNode,
            now,
            now,
            "{\"amount\":100}",
            WorkflowStatus.RUNNING
        );
        workflowRepository.save(workflow);

        activityRepository.saveAll(Arrays.asList(
            new ActivityInstance("abcd-1234-abcd-1234-01-01", workflow.getWorkflowId(), "createOrder", "test-node-a", now, now, "{\"amount\":100}", "{\"ok\":true}", ActivityExecutionStatus.SUCCESSFUL),
            new ActivityInstance("abcd-1234-abcd-1234-02-01", workflow.getWorkflowId(), "notifyCustomer", "test-node-b", now, now, "{\"ok\":true}", "{\"done\":true}", ActivityExecutionStatus.SUCCESSFUL)
        ));

        WorkflowOperation operation = WorkflowOperation.pending(workflow.getWorkflowId(), OperationType.START, "{\"amount\":100}", now);
        operationRepository.savePendingOperation(operation);
        assertThat(operationRepository.claimPendingOperation(operation.getOperationId(), now)).isTrue();
        assertThat(operationRepository.claimPendingOperation(operation.getOperationId(), now)).isFalse();
        operationRepository.markSuccessful(operation.getOperationId(), now);

        WorkflowInstance loadedWorkflow = workflowRepository.findRequired(workflow.getWorkflowId());
        assertThat(loadedWorkflow.getBizId()).isEqualTo("biz-1");
        assertThat(loadedWorkflow.getWorkflowName()).isEqualTo("orderWorkflow");
        assertThat(loadedWorkflow.getStartNode()).isEqualTo(startNode);
        assertThat(activityRepository.findByWorkflowId(workflow.getWorkflowId())).hasSize(2);
        assertThat(activityRepository.findByWorkflowId(workflow.getWorkflowId()))
            .extracting(ActivityInstance::getExecutedNode)
            .containsExactly("test-node-a", "test-node-b");
        assertThat(operationRepository.findAll()).hasSize(1);
        assertThat(operationRepository.findAll()).singleElement().satisfies(savedOperation -> {
            assertThat(savedOperation.getStatus()).isEqualTo(OperationStatus.SUCCESSFUL);
        });
    }

    @Test
    void shouldUseJdbcWorkflowLockToPreventConcurrentExecutionAcrossInstances() {
        JdbcWorkflowLockService firstLockService = new JdbcWorkflowLockService(jdbcTemplate);
        JdbcWorkflowLockService secondLockService = new JdbcWorkflowLockService(jdbcTemplate);

        assertThat(firstLockService.tryLock("wf-1:step1")).isTrue();
        assertThat(secondLockService.tryLock("wf-1:step1")).isFalse();

        firstLockService.unlock("wf-1:step1");

        assertThat(secondLockService.tryLock("wf-1:step1")).isTrue();
    }

    @Test
    void shouldWriteReadableInstanceIdIntoLockOwner() {
        JdbcWorkflowLockService lockService = new JdbcWorkflowLockService(jdbcTemplate, "10.1.2.3:8080");

        assertThat(lockService.tryLock("wf-2:step1")).isTrue();

        String owner = jdbcTemplate.queryForObject(
            "select owner from workflow_lock where lock_key = ?",
            String.class,
            "wf-2:step1"
        );
        assertThat(owner).startsWith("10.1.2.3:8080:");
    }

    @Test
    void shouldIncludeHostnameInGeneratedInstanceId() throws Exception {
        JdbcWorkflowLockService lockService = new JdbcWorkflowLockService(jdbcTemplate);

        Field instanceIdField = JdbcWorkflowLockService.class.getDeclaredField("instanceId");
        instanceIdField.setAccessible(true);
        String instanceId = (String) instanceIdField.get(lockService);
        String hostName = InetAddress.getLocalHost().getHostName();

        assertThat(instanceId).contains(hostName);
    }
}
