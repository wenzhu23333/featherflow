package com.ywz.workflow.featherflow.handler;

import java.util.Map;

/**
 * Business activity entry point.
 *
 * <p>The handler receives the current workflow context and returns the updated context snapshot that should
 * become the current activity output. The engine catches all {@link Throwable}, so both {@link Exception}
 * and {@link Error} are treated as workflow failures and will flow into retry or {@code HUMAN_PROCESSING}.
 */
public interface WorkflowActivityHandler {

    Map<String, Object> handle(Map<String, Object> context) throws Exception;
}
