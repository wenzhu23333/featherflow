package com.ywz.workflow.featherflow.engine;

import java.time.Duration;

/**
 * Schedules automatic retries after an activity failure without relying on {@code workflow_operation}.
 */
public interface WorkflowRetryScheduler {

    void scheduleRetry(String workflowId, Duration delay);
}
