package com.ywz.workflow.featherflow.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
import java.sql.Timestamp;
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
        assertThat(operationRepository.claimPendingOperation(operation.getOperationId(), "{\"amount\":100,\"processedNode\":\"daemon-node-a\"}", now)).isTrue();
        assertThat(operationRepository.claimPendingOperation(operation.getOperationId(), "{\"amount\":100,\"processedNode\":\"daemon-node-b\"}", now)).isFalse();
        operationRepository.markSuccessful(operation.getOperationId(), now);

        WorkflowInstance loadedWorkflow = workflowRepository.findRequired(workflow.getWorkflowId());
        assertThat(loadedWorkflow.getBizId()).isEqualTo("biz-1");
        assertThat(loadedWorkflow.getBizKey()).isNull();
        assertThat(loadedWorkflow.getWorkflowName()).isEqualTo("orderWorkflow");
        assertThat(loadedWorkflow.getStartNode()).isEqualTo(startNode);
        assertThat(activityRepository.findByWorkflowId(workflow.getWorkflowId())).hasSize(2);
        assertThat(activityRepository.findByWorkflowId(workflow.getWorkflowId()))
            .extracting(ActivityInstance::getExecutedNode)
            .containsExactly("test-node-a", "test-node-b");
        assertThat(operationRepository.findAll()).hasSize(1);
        assertThat(operationRepository.findAll()).singleElement().satisfies(savedOperation -> {
            assertThat(savedOperation.getStatus()).isEqualTo(OperationStatus.SUCCESSFUL);
            assertThat(savedOperation.getInput()).contains("\"processedNode\":\"daemon-node-a\"");
        });
    }

    @Test
    void shouldPersistAndLoadWorkflowBizKey() {
        Instant now = Instant.parse("2026-03-30T13:00:00Z");
        WorkflowInstance workflow = new WorkflowInstance(
            "biz-key-workflow-0001",
            "biz-2",
            "worker:publish:10001",
            "publishWorkflow",
            "10.9.8.7:host-a:1234:seed",
            now,
            now,
            "{\"workerName\":\"publish\"}",
            WorkflowStatus.RUNNING
        );

        workflowRepository.save(workflow);

        WorkflowInstance loadedWorkflow = workflowRepository.findRequired(workflow.getWorkflowId());
        assertThat(loadedWorkflow.getBizKey()).isEqualTo("worker:publish:10001");
    }

    @Test
    void shouldFindStaleRunningWorkflowsForStartupRecovery() {
        Instant now = Instant.parse("2026-03-30T13:00:00Z");
        workflowRepository.save(new WorkflowInstance(
            "wf-stale-running-a",
            "biz-stale-a",
            "orderWorkflow",
            "old-node-a",
            now.minusSeconds(1200),
            now.minusSeconds(1200),
            "{}",
            WorkflowStatus.RUNNING
        ));
        workflowRepository.save(new WorkflowInstance(
            "wf-stale-running-b",
            "biz-stale-b",
            "orderWorkflow",
            "old-node-b",
            now.minusSeconds(900),
            now.minusSeconds(900),
            "{}",
            WorkflowStatus.RUNNING
        ));
        workflowRepository.save(new WorkflowInstance(
            "wf-fresh-running",
            "biz-fresh",
            "orderWorkflow",
            "active-node",
            now.minusSeconds(60),
            now.minusSeconds(60),
            "{}",
            WorkflowStatus.RUNNING
        ));
        workflowRepository.save(new WorkflowInstance(
            "wf-stale-completed",
            "biz-completed",
            "orderWorkflow",
            "old-node-c",
            now.minusSeconds(1200),
            now.minusSeconds(1200),
            "{}",
            WorkflowStatus.COMPLETED
        ));

        assertThat(workflowRepository.findRunningModifiedBefore(now.minusSeconds(300), 10))
            .extracting(WorkflowInstance::getWorkflowId)
            .containsExactly("wf-stale-running-a", "wf-stale-running-b");
        assertThat(workflowRepository.findRunningModifiedBefore(now.minusSeconds(300), 1))
            .extracting(WorkflowInstance::getWorkflowId)
            .containsExactly("wf-stale-running-a");
    }

    @Test
    void shouldRejectUnsupportedWorkflowStatusFromDatabase() {
        Instant now = Instant.parse("2026-03-30T13:00:00Z");
        jdbcTemplate.update(
            "insert into workflow_instance (workflow_id, biz_id, workflow_name, start_node, gmt_created, gmt_modified, input, status) values (?, ?, ?, ?, ?, ?, ?, ?)",
            "legacy-success-01",
            "biz-legacy-01",
            "legacyWorkflow",
            "10.9.8.7:host-a:1234:seed",
            java.sql.Timestamp.from(now),
            java.sql.Timestamp.from(now),
            "{\"legacy\":true}",
            "SUCCESSFUL"
        );

        assertThatThrownBy(() -> workflowRepository.findRequired("legacy-success-01"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("SUCCESSFUL");
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
    void shouldCleanOnlyExpiredWorkflowLocks() {
        Instant now = Instant.parse("2026-03-30T13:00:00Z");
        insertWorkflowLock("wf-old:step1", "dead-node", now.minusSeconds(600));
        insertWorkflowLock("wf-fresh:step1", "active-node", now.minusSeconds(60));

        JdbcWorkflowLockService lockService = new JdbcWorkflowLockService(jdbcTemplate);

        assertThat(lockService.cleanExpiredLocks(now.minusSeconds(300))).isEqualTo(1);

        assertThat(jdbcTemplate.queryForObject("select count(*) from workflow_lock", Long.class)).isEqualTo(1L);
        assertThat(jdbcTemplate.queryForObject("select owner from workflow_lock where lock_key = ?", String.class, "wf-fresh:step1"))
            .isEqualTo("active-node");
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

    private void insertWorkflowLock(String lockKey, String owner, Instant modifiedTime) {
        jdbcTemplate.update(
            "insert into workflow_lock (lock_key, owner, gmt_created, gmt_modified) values (?, ?, ?, ?)",
            lockKey,
            owner,
            Timestamp.from(modifiedTime),
            Timestamp.from(modifiedTime)
        );
    }
}
