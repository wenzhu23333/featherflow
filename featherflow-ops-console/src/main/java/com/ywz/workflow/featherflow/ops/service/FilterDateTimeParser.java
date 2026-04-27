package com.ywz.workflow.featherflow.ops.service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

public final class FilterDateTimeParser {

    private static final List<DateTimeFormatter> FORMATTERS = Arrays.asList(
        DateTimeFormatter.ISO_LOCAL_DATE_TIME,
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
    );

    private FilterDateTimeParser() {
    }

    public static LocalDateTime parseNullable(String value) {
        if (isBlank(value)) {
            return null;
        }
        for (DateTimeFormatter formatter : FORMATTERS) {
            try {
                return LocalDateTime.parse(value.trim(), formatter);
            } catch (DateTimeParseException ignored) {
                // try next format
            }
        }
        return null;
    }

    public static LocalDateTime parseNullable(String fieldName, String value, Map<String, String> errors) {
        if (isBlank(value)) {
            return null;
        }
        LocalDateTime parsed = parseNullable(value);
        if (parsed == null) {
            errors.put(fieldName, "Invalid date-time for " + fieldName + ": " + value);
        }
        return parsed;
    }

    private static boolean isBlank(String value) {
        if (value == null) {
            return true;
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            return true;
        }
        for (int index = 0; index < trimmed.length(); index++) {
            char current = trimmed.charAt(index);
            if (current != ',' && !Character.isWhitespace(current)) {
                return false;
            }
        }
        return true;
    }
}
