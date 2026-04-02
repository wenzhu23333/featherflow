package com.ywz.workflow.featherflow.support;

import java.util.Map;

/**
 * Serializes workflow context snapshots and failure payloads.
 */
public interface WorkflowContextSerializer {

    String serialize(Map<String, Object> context);

    Map<String, Object> deserialize(String json);

    String merge(String baseJson, String overlayJson);

    /**
     * Build the failure payload persisted into {@code activity_instance.output}.
     */
    String failureOutput(Throwable throwable);
}
