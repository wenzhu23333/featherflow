package com.ywz.workflow.featherflow.starter;

import static org.assertj.core.api.Assertions.assertThat;

import com.ywz.workflow.featherflow.definition.WorkflowDefinitionRegistry;
import com.ywz.workflow.featherflow.handler.WorkflowActivityHandler;
import com.ywz.workflow.featherflow.lock.WorkflowLockService;
import com.ywz.workflow.featherflow.persistence.DefaultPersistenceWriteRetrier;
import com.ywz.workflow.featherflow.persistence.PersistenceWriteRetrier;
import com.ywz.workflow.featherflow.persistence.RetryingActivityRepository;
import com.ywz.workflow.featherflow.persistence.RetryingWorkflowLockService;
import com.ywz.workflow.featherflow.persistence.RetryingWorkflowOperationRepository;
import com.ywz.workflow.featherflow.persistence.RetryingWorkflowRepository;
import com.ywz.workflow.featherflow.service.WorkflowCommandService;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.test.util.ReflectionTestUtils;

class FeatherFlowAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
        .withUserConfiguration(TestConfiguration.class)
        .withPropertyValues(
            "featherflow.enabled=true",
            "featherflow.auto-start-daemon=false",
            "featherflow.definition-locations=classpath:/workflows/sample-workflow.yml"
        )
        .withConfiguration(org.springframework.boot.autoconfigure.AutoConfigurations.of(FeatherFlowAutoConfiguration.class));

    @Test
    void shouldAutoConfigureCoreBeansWhenJdbcTemplateExists() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(WorkflowCommandService.class);
            assertThat(context).hasSingleBean(WorkflowDefinitionRegistry.class);
            assertThat(context).hasSingleBean(FeatherFlowProperties.class);
            assertThat(context).hasSingleBean(PersistenceWriteRetrier.class);
            assertThat(context.getBean(PersistenceWriteRetrier.class)).isInstanceOf(DefaultPersistenceWriteRetrier.class);
            assertThat(context.getBean("workflowRepository")).isInstanceOf(RetryingWorkflowRepository.class);
            assertThat(context.getBean("activityRepository")).isInstanceOf(RetryingActivityRepository.class);
            assertThat(context.getBean("workflowOperationRepository")).isInstanceOf(RetryingWorkflowOperationRepository.class);
            assertThat(context.getBean(WorkflowLockService.class)).isInstanceOf(RetryingWorkflowLockService.class);
            assertThat(context.getBean(WorkflowDefinitionRegistry.class).find("sampleOrderWorkflow")).isNotNull();
        });
    }

    @Test
    void shouldUseConfiguredInstanceIdForJdbcWorkflowLockOwner() {
        FeatherFlowAutoConfiguration autoConfiguration = new FeatherFlowAutoConfiguration();
        FeatherFlowProperties properties = new FeatherFlowProperties();
        properties.setInstanceId("10.9.8.7:engine-a");
        PersistenceWriteRetrier retrier = new DefaultPersistenceWriteRetrier(3, Duration.ofMillis(1), Duration.ofMillis(2), delay -> {
        });

        WorkflowLockService workflowLockService = autoConfiguration.jdbcWorkflowLockService(new JdbcTemplate(), properties, retrier);

        Object delegate = ReflectionTestUtils.getField(workflowLockService, "delegate");
        assertThat(delegate).isNotNull();
        assertThat(ReflectionTestUtils.getField(delegate, "instanceId")).isEqualTo("10.9.8.7:engine-a");
    }

    @Configuration
    static class TestConfiguration {

        @Bean
        DataSource dataSource() {
            DriverManagerDataSource dataSource = new DriverManagerDataSource();
            dataSource.setDriverClassName("org.h2.Driver");
            dataSource.setUrl("jdbc:h2:mem:starter-" + UUID.randomUUID() + ";MODE=MYSQL;DB_CLOSE_DELAY=-1");
            dataSource.setUsername("sa");
            dataSource.setPassword("");
            new ResourceDatabasePopulator(new ClassPathResource("db/featherflow-h2.sql")).execute(dataSource);
            return dataSource;
        }

        @Bean
        WorkflowActivityHandler createOrderHandler() {
            return new WorkflowActivityHandler() {
                @Override
                public Map<String, Object> handle(Map<String, Object> context) {
                    context.put("created", true);
                    return context;
                }
            };
        }
    }
}
