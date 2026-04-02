package com.ywz.workflow.featherflow.support;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.LinkedHashMap;
import java.util.Map;

public class JsonWorkflowContextSerializer implements WorkflowContextSerializer {

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<Map<String, Object>>() {
    };

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public String serialize(Map<String, Object> context) {
        try {
            return objectMapper.writeValueAsString(context == null ? new LinkedHashMap<String, Object>() : context);
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException("Failed to serialize workflow context", ex);
        }
    }

    @Override
    public Map<String, Object> deserialize(String json) {
        if (json == null || json.trim().isEmpty()) {
            return new LinkedHashMap<String, Object>();
        }
        try {
            return objectMapper.readValue(json, MAP_TYPE);
        } catch (IOException ex) {
            throw new IllegalArgumentException("Failed to deserialize workflow context", ex);
        }
    }

    @Override
    public String merge(String baseJson, String overlayJson) {
        Map<String, Object> merged = new LinkedHashMap<String, Object>(deserialize(baseJson));
        merged.putAll(deserialize(overlayJson));
        return serialize(merged);
    }

    @Override
    public String failureOutput(Throwable throwable) {
        Map<String, Object> failure = new LinkedHashMap<String, Object>();
        failure.put("errorType", throwable.getClass().getName());
        failure.put("errorMessage", throwable.getMessage());
        failure.put("stackTrace", stackTraceOf(throwable));
        return serialize(failure);
    }

    private String stackTraceOf(Throwable throwable) {
        StringWriter buffer = new StringWriter();
        throwable.printStackTrace(new PrintWriter(buffer));
        return buffer.toString();
    }
}
