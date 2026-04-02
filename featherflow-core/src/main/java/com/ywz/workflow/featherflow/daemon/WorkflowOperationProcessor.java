package com.ywz.workflow.featherflow.daemon;

import com.ywz.workflow.featherflow.logging.WorkflowLogContext;
import com.ywz.workflow.featherflow.model.WorkflowInstance;
import com.ywz.workflow.featherflow.model.WorkflowOperation;
import com.ywz.workflow.featherflow.repository.WorkflowOperationRepository;
import com.ywz.workflow.featherflow.repository.WorkflowRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class WorkflowOperationProcessor {

    private static final Logger log = LoggerFactory.getLogger(WorkflowOperationProcessor.class);

    private final WorkflowOperationRepository workflowOperationRepository;
    private final WorkflowRepository workflowRepository;
    private final WorkflowOperationHandler workflowOperationHandler;
    private final Clock clock;

    public WorkflowOperationProcessor(
        WorkflowOperationRepository workflowOperationRepository,
        WorkflowRepository workflowRepository,
        WorkflowOperationHandler workflowOperationHandler,
        Clock clock
    ) {
        this.workflowOperationRepository = workflowOperationRepository;
        this.workflowRepository = workflowRepository;
        this.workflowOperationHandler = workflowOperationHandler;
        this.clock = clock;
    }

    public boolean process(WorkflowOperation operation) {
        Map<String, String> logContext = resolveLogContext(operation.getWorkflowId());
        try (WorkflowLogContext.Scope ignored = WorkflowLogContext.open(logContext)) {
            Instant claimTime = clock.instant();
            if (!workflowOperationRepository.claimPendingOperation(operation.getOperationId(), claimTime)) {
                log.info(
                    "Skip workflow operation because it was already claimed, operationId={}, operationType={}",
                    operation.getOperationId(),
                    operation.getOperationType()
                );
                return false;
            }

            log.info(
                "Claim workflow operation, operationId={}, operationType={}, dueAt={}",
                operation.getOperationId(),
                operation.getOperationType(),
                operation.getGmtModified()
            );
            log.info("Process workflow operation, operationId={}, operationType={}", operation.getOperationId(), operation.getOperationType());
            workflowOperationHandler.process(operation);
            workflowOperationRepository.markSuccessful(operation.getOperationId(), clock.instant());
            log.info("Workflow operation processed successfully, operationId={}, operationType={}", operation.getOperationId(), operation.getOperationType());
            return true;
        } catch (Throwable throwable) {
            markOperationFailedBestEffort(operation, throwable);
            log.error(
                "Workflow operation processing failed, operationId={}, operationType={}, message={}",
                operation.getOperationId(),
                operation.getOperationType(),
                throwable.getMessage(),
                throwable
            );
            if (throwable instanceof RuntimeException) {
                throw (RuntimeException) throwable;
            }
            throw new IllegalStateException("Workflow operation processing failed", throwable);
        }
    }

    private void markOperationFailedBestEffort(WorkflowOperation operation, Throwable originalFailure) {
        try {
            workflowOperationRepository.markFailed(operation.getOperationId(), clock.instant());
        } catch (RuntimeException markFailedException) {
            log.error(
                "Failed to mark workflow operation as FAILED after processing error, operationId={}, operationType={}",
                operation.getOperationId(),
                operation.getOperationType(),
                markFailedException
            );
            if (originalFailure instanceof RuntimeException) {
                throw (RuntimeException) originalFailure;
            }
            throw new IllegalStateException("Workflow operation processing failed", originalFailure);
        }
    }

    private Map<String, String> resolveLogContext(String workflowId) {
        WorkflowInstance workflowInstance = workflowRepository.find(workflowId);
        if (workflowInstance == null) {
            return WorkflowLogContext.snapshot(workflowId, null);
        }
        return WorkflowLogContext.snapshot(workflowInstance);
    }
}
