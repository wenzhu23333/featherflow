package com.ywz.workflow.featherflow.daemon;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Enriches workflow operation input with daemon-side processing metadata while preserving original payloads.
 */
final class WorkflowOperationInputEnricher {

    private static final String PROCESSED_NODE_FIELD = "processedNode";
    private static final String ORIGINAL_INPUT_FIELD = "originalInput";

    private final ObjectMapper objectMapper = new ObjectMapper();

    String appendProcessedNode(String input, String processedNode) {
        ObjectNode enriched = objectMapper.createObjectNode();
        JsonNode rootNode = readJsonNode(input);
        if (rootNode != null && rootNode.isObject()) {
            enriched.setAll((ObjectNode) rootNode.deepCopy());
        } else if (rootNode != null) {
            enriched.set(ORIGINAL_INPUT_FIELD, rootNode);
        } else if (!isBlank(input)) {
            enriched.put(ORIGINAL_INPUT_FIELD, input);
        }
        enriched.put(PROCESSED_NODE_FIELD, processedNode);
        return writeJson(enriched, input);
    }

    private JsonNode readJsonNode(String input) {
        if (isBlank(input)) {
            return null;
        }
        try {
            return objectMapper.readTree(input);
        } catch (Exception ignored) {
            return null;
        }
    }

    private String writeJson(ObjectNode enriched, String fallbackInput) {
        try {
            return objectMapper.writeValueAsString(enriched);
        } catch (JsonProcessingException ex) {
            return fallbackInput;
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
