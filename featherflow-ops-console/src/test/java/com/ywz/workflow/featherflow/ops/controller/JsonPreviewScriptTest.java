package com.ywz.workflow.featherflow.ops.controller;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.util.StreamUtils;

class JsonPreviewScriptTest {

    @Test
    void shouldFormatModalBodyWhenContentIsValidJson() throws IOException {
        String script = readScript();

        assertThat(script).contains("formatModalBody");
        assertThat(script).contains(".json-modal-body");
        assertThat(script).contains("JSON.parse");
        assertThat(script).contains("JSON.stringify(parsed, null, 2)");
        assertThat(script).contains("body.textContent = formatted");
        assertThat(script).contains("body.setAttribute(\"data-json-formatted\", \"true\")");
        assertThat(script).doesNotContain("data-raw-json");
    }

    @Test
    void shouldPauseAutomaticHtmxRefreshWhileJsonDialogIsOpen() throws IOException {
        String script = readScript();

        assertThat(script).contains("hasOpenJsonDialog");
        assertThat(script).contains(".json-preview-dialog[open]");
        assertThat(script).contains("isAutomaticHtmxRefresh");
        assertThat(script).contains("htmx:beforeRequest");
        assertThat(script).contains("trigger.indexOf(\"every \") !== -1");
        assertThat(script).contains("event.preventDefault()");
    }

    private String readScript() throws IOException {
        ClassPathResource resource = new ClassPathResource("static/js/json-preview.js");
        return StreamUtils.copyToString(resource.getInputStream(), StandardCharsets.UTF_8);
    }
}
