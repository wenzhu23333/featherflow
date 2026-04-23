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
        assertThat(css).contains(".json-expand-trigger");
        assertThat(css).contains(".json-formatted-output");
    }

    private String readCss() throws IOException {
        ClassPathResource resource = new ClassPathResource("static/css/app.css");
        return StreamUtils.copyToString(resource.getInputStream(), StandardCharsets.UTF_8);
    }
}
