package com.ywz.workflow.featherflow.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.ywz.workflow.featherflow.definition.ActivityDefinition;
import com.ywz.workflow.featherflow.definition.InMemoryWorkflowDefinitionRegistry;
import com.ywz.workflow.featherflow.definition.WorkflowDefinition;
import com.ywz.workflow.featherflow.engine.WorkflowEngine;
import com.ywz.workflow.featherflow.engine.WorkflowExecutionScheduler;
import com.ywz.workflow.featherflow.engine.WorkflowRetryScheduler;
import com.ywz.workflow.featherflow.lock.LocalWorkflowLockService;
import com.ywz.workflow.featherflow.model.ActivityExecutionStatus;
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
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

class WorkflowCommandServiceTest {

    private final Clock clock = Clock.fixed(Instant.parse("2026-03-30T10:00:00Z"), ZoneOffset.UTC);
    private final InMemoryWorkflowDefinitionRegistry registry = new InMemoryWorkflowDefinitionRegistry();
    private final WorkflowRepository workflowRepository = new InMemoryWorkflowRepository();
    private final ActivityRepository activityRepository = new InMemoryActivityRepository();
    private final WorkflowOperationRepository operationRepository = new InMemoryWorkflowOperationRepository();
    private Logger commandServiceLogger;
    private ListAppender<ILoggingEvent> commandServiceAppender;
    private RecordingWorkflowExecutionScheduler workflowExecutionScheduler;

    private WorkflowCommandService service;

    @BeforeEach
    void setUp() {
        registry.register(new WorkflowDefinition(
            "orderWorkflow",
            Arrays.asList(
                new ActivityDefinition("createOrder", "createOrderHandler", Duration.ofSeconds(5), 2),
                new ActivityDefinition("notifyCustomer", "notifyCustomerHandler", Duration.ofSeconds(5), 2)
            )
        ));
        commandServiceLogger = (Logger) LoggerFactory.getLogger(DefaultWorkflowCommandService.class);
        commandServiceAppender = new ListAppender<ILoggingEvent>();
        commandServiceAppender.start();
        commandServiceLogger.addAppender(commandServiceAppender);
        workflowExecutionScheduler = new RecordingWorkflowExecutionScheduler();
        WorkflowEngine workflowEngine = new WorkflowEngine(
            registry,
            workflowRepository,
            activityRepository,
            handler -> {
                throw new AssertionError("No activity handler should run in command service unit test");
            },
            new LocalWorkflowLockService(),
            new JsonWorkflowContextSerializer(),
            clock,
            new NoOpWorkflowRetryScheduler()
        );
        WorkflowRuntimeService workflowRuntimeService = new DefaultWorkflowRuntimeService(
            workflowRepository,
            workflowEngine,
            workflowExecutionScheduler,
            new JsonWorkflowContextSerializer(),
            clock
        );
        service = new DefaultWorkflowCommandService(
            registry,
            workflowRepository,
            new DefaultWorkflowIdGenerator(),
            new JsonWorkflowContextSerializer(),
            clock,
            workflowRuntimeService,
            "test-node"
        );
    }

    @AfterEach
    void tearDown() {
        if (commandServiceLogger != null && commandServiceAppender != null) {
            commandServiceLogger.detachAppender(commandServiceAppender);
            commandServiceAppender.stop();
        }
    }

    @Test
    void shouldGenerateWorkflowIdAndDefaultBizIdOnStart() {
        WorkflowInstance workflow = service.startWorkflow("orderWorkflow", null, "{\"amount\":100}");

        assertThat(workflow.getWorkflowId()).matches("[a-f0-9]{4}(-[a-f0-9]{4}){3}");
        assertThat(workflow.getBizId()).isEqualTo(workflow.getWorkflowId());
        assertThat(workflow.getStatus()).isEqualTo(WorkflowStatus.RUNNING);
        assertThat(activityRepository.findByWorkflowId(workflow.getWorkflowId())).isEmpty();
        assertThat(operationRepository.findDuePendingOperations(clock.instant())).isEmpty();
        assertThat(workflowExecutionScheduler.workflowIds).containsExactly(workflow.getWorkflowId());
    }

    @Test
    void shouldOnlyAllowRetryFromHumanProcessingOrTerminated() {
        WorkflowInstance workflow = service.startWorkflow("orderWorkflow", "biz-1", "{\"amount\":100}");

        assertThatThrownBy(() -> service.retryWorkflow(workflow.getWorkflowId()))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("HUMAN_PROCESSING or TERMINATED");

        workflowRepository.updateStatus(workflow.getWorkflowId(), WorkflowStatus.HUMAN_PROCESSING, clock.instant());
        service.retryWorkflow(workflow.getWorkflowId());

        assertThat(workflowRepository.findRequired(workflow.getWorkflowId()).getStatus()).isEqualTo(WorkflowStatus.RUNNING);
        workflowRepository.updateStatus(workflow.getWorkflowId(), WorkflowStatus.TERMINATED, clock.instant());
        service.retryWorkflow(workflow.getWorkflowId());

        assertThat(operationRepository.findDuePendingOperations(clock.instant())).isEmpty();
        assertThat(workflowExecutionScheduler.workflowIds).containsExactly(
            workflow.getWorkflowId(),
            workflow.getWorkflowId(),
            workflow.getWorkflowId()
        );
    }

    @Test
    void shouldOnlyAllowSkipFromTerminatedWorkflow() {
        WorkflowInstance workflow = service.startWorkflow("orderWorkflow", "biz-2", "{\"amount\":100}");
        activityRepository.saveAttempt(
            workflow.getWorkflowId() + "-01-01",
            workflow.getWorkflowId(),
            "createOrder",
            "seed-node",
            "{\"amount\":100}",
            "{\"error\":\"failed\"}",
            ActivityExecutionStatus.FAILED,
            clock.instant()
        );

        assertThatThrownBy(() -> service.skipActivity(workflow.getWorkflowId(), "{\"manual\":true}"))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("TERMINATED");

        workflowRepository.updateStatus(workflow.getWorkflowId(), WorkflowStatus.TERMINATED, clock.instant());
        service.skipActivity(workflow.getWorkflowId(), "{\"manual\":true}");

        assertThat(operationRepository.findDuePendingOperations(clock.instant())).isEmpty();
        assertThat(activityRepository.findByWorkflowId(workflow.getWorkflowId()))
            .extracting(activity -> activity.getStatus())
            .containsExactly(ActivityExecutionStatus.FAILED, ActivityExecutionStatus.SUCCESSFUL);
        assertThat(workflowRepository.findRequired(workflow.getWorkflowId()).getStatus()).isEqualTo(WorkflowStatus.RUNNING);
        assertThat(workflowExecutionScheduler.workflowIds).containsExactly(workflow.getWorkflowId(), workflow.getWorkflowId());
    }

    @Test
    void shouldIncludeWorkflowIdsInStartWorkflowLogs() {
        WorkflowInstance workflow = service.startWorkflow("orderWorkflow", "biz-log-start", "{\"amount\":100}");

        assertThat(commandServiceAppender.list).isNotEmpty();
        assertThat(commandServiceAppender.list).anySatisfy(event -> {
            assertThat(event.getMDCPropertyMap()).containsEntry("workflowId", workflow.getWorkflowId());
            assertThat(event.getMDCPropertyMap()).containsEntry("bizId", workflow.getBizId());
        });
    }

    private static final class RecordingWorkflowExecutionScheduler implements WorkflowExecutionScheduler {

        private final List<String> workflowIds = new ArrayList<String>();

        @Override
        public void schedule(String workflowId) {
            workflowIds.add(workflowId);
        }
    }

    private static final class NoOpWorkflowRetryScheduler implements WorkflowRetryScheduler {

        @Override
        public void scheduleRetry(String workflowId, Duration delay) {
        }
    }
}
