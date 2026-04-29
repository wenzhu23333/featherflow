package com.ywz.workflow.featherflow.ops.controller;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.util.StreamUtils;

class WorkflowListStyleTest {

    @Test
    void shouldFitWorkflowTableInsideCurrentPageWidth() throws IOException {
        String css = readCss();

        assertThat(css).contains(".workflow-table {\n    width: 100%;\n    table-layout: fixed;");
        assertThat(css).doesNotContain("min-width: 1320px;");
        assertThat(css).contains("overflow-x: hidden;");
        assertThat(css).contains("--surface-glass");
        assertThat(css).contains("backdrop-filter: blur(18px);");
        assertThat(css).contains(".json-preview-open");
        assertThat(css).contains(".json-preview-dialog");
        assertThat(css).contains(".json-modal-close");
        assertThat(css).contains(".json-modal-body");
        assertThat(css).contains(".json-modal-card {\n    position: relative;\n    width: 100%;\n    max-width: 100%;\n    min-width: 0;\n    box-sizing: border-box;\n    overflow: hidden;");
        assertThat(css).contains(".json-modal-close {\n    position: absolute;\n    top: 12px;\n    right: 12px;");
        assertThat(css).contains(".json-modal-body {\n    display: block;\n    width: 100%;\n    max-width: 100%;");
        assertThat(css).contains("overflow-x: hidden;\n    overflow-y: auto;");
        assertThat(css).contains("overflow-wrap: anywhere;");
        assertThat(css).contains("white-space: pre-wrap;");
        assertThat(css).contains("word-break: break-all;");
        assertThat(css).contains("word-wrap: break-word;");
        assertThat(css).contains("box-sizing: border-box;");
        assertThat(css).contains("min-width: 0;");
        assertThat(css).contains(".copy-value-button {\n    display: inline-flex;");
        assertThat(css).contains("width: 24px;");
        assertThat(css).contains("height: 24px;");
        assertThat(css).contains("border: 0;");
        assertThat(css).contains("border-radius: 6px;");
        assertThat(css).contains("width: 16px;\n    height: 16px;");
        assertThat(css).contains("stroke-width: 2;");
        assertThat(css).contains(".copy-value-button:hover");
        assertThat(css).doesNotContain(".multi-select-trigger");
        assertThat(css).doesNotContain(".multi-select-menu");
        assertThat(css).doesNotContain(".json-formatted-output");
    }

    private String readCss() throws IOException {
        ClassPathResource resource = new ClassPathResource("static/css/app.css");
        return StreamUtils.copyToString(resource.getInputStream(), StandardCharsets.UTF_8);
    }
}
