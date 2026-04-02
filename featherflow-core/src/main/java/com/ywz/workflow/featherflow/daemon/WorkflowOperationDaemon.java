package com.ywz.workflow.featherflow.daemon;

import com.ywz.workflow.featherflow.model.WorkflowOperation;
import com.ywz.workflow.featherflow.repository.WorkflowOperationRepository;
import java.time.Clock;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class WorkflowOperationDaemon {

    private static final Logger log = LoggerFactory.getLogger(WorkflowOperationDaemon.class);

    private final WorkflowOperationRepository workflowOperationRepository;
    private final Clock clock;
    private final WorkflowOperationProcessor workflowOperationProcessor;

    public WorkflowOperationDaemon(
        WorkflowOperationRepository workflowOperationRepository,
        Clock clock,
        WorkflowOperationProcessor workflowOperationProcessor
    ) {
        this.workflowOperationRepository = workflowOperationRepository;
        this.clock = clock;
        this.workflowOperationProcessor = workflowOperationProcessor;
    }

    public void pollOnce() {
        List<WorkflowOperation> dueOperations = workflowOperationRepository.findDuePendingOperations(clock.instant());
        if (!dueOperations.isEmpty()) {
            log.info("Scan due workflow operations, dueCount={}", Integer.valueOf(dueOperations.size()));
        }
        for (WorkflowOperation operation : dueOperations) {
            try {
                workflowOperationProcessor.process(operation);
            } catch (RuntimeException ignored) {
                // The processor already marks the operation and writes logs.
            }
        }
    }
}
