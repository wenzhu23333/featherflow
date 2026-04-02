package com.ywz.workflow.featherflow.daemon;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.ywz.workflow.featherflow.model.OperationStatus;
import com.ywz.workflow.featherflow.model.OperationType;
import com.ywz.workflow.featherflow.model.WorkflowInstance;
import com.ywz.workflow.featherflow.model.WorkflowStatus;
import com.ywz.workflow.featherflow.repository.WorkflowRepository;
import com.ywz.workflow.featherflow.model.WorkflowOperation;
import com.ywz.workflow.featherflow.repository.WorkflowOperationRepository;
import com.ywz.workflow.featherflow.support.InMemoryWorkflowOperationRepository;
import com.ywz.workflow.featherflow.support.InMemoryWorkflowRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

class WorkflowOperationDaemonTest {

    private final Clock clock = Clock.fixed(Instant.parse("2026-03-30T12:30:00Z"), ZoneOffset.UTC);
    private Logger processorLogger;
    private ListAppender<ILoggingEvent> processorAppender;

    @BeforeEach
    void setUp() {
        processorLogger = (Logger) LoggerFactory.getLogger(WorkflowOperationProcessor.class);
        processorAppender = new ListAppender<ILoggingEvent>();
        processorAppender.start();
        processorLogger.addAppender(processorAppender);
    }

    @AfterEach
    void tearDown() {
        processorLogger.detachAppender(processorAppender);
        processorAppender.stop();
    }

    @Test
    void shouldProcessPendingStartAndRetryOperations() {
        WorkflowOperationRepository repository = new InMemoryWorkflowOperationRepository();
        repository.savePendingOperation(WorkflowOperation.pending("wf-1", OperationType.START, "{\"a\":1}", clock.instant()));
        repository.savePendingOperation(WorkflowOperation.pending("wf-2", OperationType.RETRY, "{\"a\":2}", clock.instant()));

        List<String> processed = new ArrayList<String>();
        WorkflowOperationDaemon daemon = new WorkflowOperationDaemon(
            repository,
            clock,
            new WorkflowOperationProcessor(
                repository,
                new InMemoryWorkflowRepository(),
                operation -> processed.add(operation.getWorkflowId() + ":" + operation.getOperationType().name()),
                clock
            )
        );

        daemon.pollOnce();

        assertThat(processed).containsExactly("wf-1:START", "wf-2:RETRY");
        assertThat(repository.findDuePendingOperations(clock.instant())).isEmpty();
    }

    @Test
    void shouldCatchThrowableFromOperationProcessingAndMarkFailure() {
        WorkflowOperationRepository repository = new InMemoryWorkflowOperationRepository();
        repository.savePendingOperation(WorkflowOperation.pending("wf-3", OperationType.START, "{\"a\":1}", clock.instant()));

        WorkflowOperationDaemon daemon = new WorkflowOperationDaemon(
            repository,
            clock,
            new WorkflowOperationProcessor(
                repository,
                new InMemoryWorkflowRepository(),
                operation -> {
                    throw new IllegalStateException("daemon boom");
                },
                clock
            )
        );

        daemon.pollOnce();

        assertThat(repository.findAll()).singleElement().satisfies(operation -> {
            assertThat(operation.getStatus()).isEqualTo(OperationStatus.FAILED);
            assertThat(operation.getInput()).contains("\"a\":1");
        });
    }

    @Test
    void shouldIncludeWorkflowIdsInOperationProcessorLogs() {
        WorkflowOperationRepository repository = new InMemoryWorkflowOperationRepository();
        WorkflowRepository workflowRepository = new InMemoryWorkflowRepository();
        WorkflowInstance workflowInstance = new WorkflowInstance(
            "wf-log-1",
            "biz-log-1",
            clock.instant(),
            clock.instant(),
            "{\"a\":1}",
            WorkflowStatus.RUNNING,
            "{}"
        );
        workflowRepository.save(workflowInstance);
        repository.savePendingOperation(WorkflowOperation.pending("wf-log-1", OperationType.START, "{\"a\":1}", clock.instant()));

        WorkflowOperationDaemon daemon = new WorkflowOperationDaemon(
            repository,
            clock,
            new WorkflowOperationProcessor(
                repository,
                workflowRepository,
                ignored -> {
                },
                clock
            )
        );

        daemon.pollOnce();

        assertThat(processorAppender.list).anySatisfy(event -> {
            assertThat(event.getFormattedMessage()).contains("Process workflow operation");
            assertThat(event.getMDCPropertyMap()).containsEntry("workflowId", "wf-log-1");
            assertThat(event.getMDCPropertyMap()).containsEntry("bizId", "biz-log-1");
        });
    }
}
