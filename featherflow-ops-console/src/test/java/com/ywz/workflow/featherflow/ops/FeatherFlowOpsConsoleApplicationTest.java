package com.ywz.workflow.featherflow.ops;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(classes = FeatherFlowOpsConsoleApplication.class)
@ActiveProfiles("test")
class FeatherFlowOpsConsoleApplicationTest {

    @Autowired
    private Environment environment;

    @Test
    void shouldLoadApplicationContext() {
        assertThat(environment.getActiveProfiles()).contains("test");
        assertThat(environment.getProperty("featherflow.ops.test-profile-active", Boolean.class)).isTrue();
        assertThat(environment.getProperty("spring.datasource.driver-class-name")).isEqualTo("org.h2.Driver");
        assertThat(environment.getProperty("spring.sql.init.mode")).isEqualTo("never");
    }
}
