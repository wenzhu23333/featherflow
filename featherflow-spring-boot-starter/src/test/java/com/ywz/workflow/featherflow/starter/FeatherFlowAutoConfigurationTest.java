package com.ywz.workflow.featherflow.starter;

import static org.assertj.core.api.Assertions.assertThat;

import com.ywz.workflow.featherflow.engine.WorkflowEngine;
import com.ywz.workflow.featherflow.definition.WorkflowDefinitionRegistry;
import com.ywz.workflow.featherflow.model.WorkflowInstance;
import com.ywz.workflow.featherflow.model.WorkflowStatus;
import com.ywz.workflow.featherflow.handler.WorkflowActivityHandler;
import com.ywz.workflow.featherflow.lock.WorkflowLockService;
import com.ywz.workflow.featherflow.persistence.DefaultPersistenceWriteRetrier;
import com.ywz.workflow.featherflow.persistence.PersistenceWriteRetrier;
import com.ywz.workflow.featherflow.persistence.RetryingActivityRepository;
import com.ywz.workflow.featherflow.persistence.RetryingWorkflowLockService;
import com.ywz.workflow.featherflow.persistence.RetryingWorkflowOperationRepository;
import com.ywz.workflow.featherflow.persistence.RetryingWorkflowRepository;
import com.ywz.workflow.featherflow.service.WorkflowCommandService;
import com.ywz.workflow.featherflow.repository.ActivityRepository;
import com.ywz.workflow.featherflow.repository.WorkflowRepository;
import com.ywz.workflow.featherflow.support.WorkflowNodeIdentity;
import java.time.Duration;
import java.time.Clock;
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
            assertThat(context).hasSingleBean(WorkflowNodeIdentity.class);
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
    void shouldShareGeneratedWorkflowNodeIdentityAcrossStartExecutionAndLocks() {
        contextRunner.run(context -> {
            WorkflowNodeIdentity workflowNodeIdentity = context.getBean(WorkflowNodeIdentity.class);
            WorkflowCommandService workflowCommandService = context.getBean(WorkflowCommandService.class);
            WorkflowRepository workflowRepository = context.getBean(WorkflowRepository.class);
            ActivityRepository activityRepository = context.getBean(ActivityRepository.class);
            WorkflowEngine workflowEngine = context.getBean(WorkflowEngine.class);
            WorkflowLockService workflowLockService = context.getBean(WorkflowLockService.class);
            Clock clock = context.getBean(Clock.class);
            JdbcTemplate jdbcTemplate = context.getBean(JdbcTemplate.class);

            WorkflowInstance startedWorkflow = workflowCommandService.startWorkflow("sampleOrderWorkflow", "biz-shared", "{\"amount\":100}");
            assertThat(startedWorkflow.getStartNode()).isEqualTo(workflowNodeIdentity.getInstanceId());
            assertThat(workflowRepository.findRequired(startedWorkflow.getWorkflowId()).getStartNode()).isEqualTo(workflowNodeIdentity.getInstanceId());

            WorkflowInstance workflowInstance = new WorkflowInstance(
                "wf-shared-node",
                "biz-shared-node",
                "sampleOrderWorkflow",
                workflowNodeIdentity.getInstanceId(),
                clock.instant(),
                clock.instant(),
                "{\"amount\":100}",
                WorkflowStatus.RUNNING
            );
            workflowRepository.save(workflowInstance);
            workflowEngine.continueWorkflow(workflowInstance.getWorkflowId());

            assertThat(activityRepository.findByWorkflowId(workflowInstance.getWorkflowId()))
                .singleElement()
                .satisfies(activity -> assertThat(activity.getExecutedNode()).isEqualTo(workflowNodeIdentity.getInstanceId()));

            assertThat(workflowLockService.tryLock("shared-node-lock")).isTrue();
            String owner = jdbcTemplate.queryForObject(
                "select owner from workflow_lock where lock_key = ?",
                String.class,
                "shared-node-lock"
            );
            assertThat(owner).startsWith(workflowNodeIdentity.getInstanceId() + ":");
            workflowLockService.unlock("shared-node-lock");
        });
    }

    @Test
    void shouldLoadMultipleWorkflowDefinitionsAcrossYamlAndXmlFiles() {
        new ApplicationContextRunner()
            .withUserConfiguration(TestConfiguration.class)
            .withPropertyValues(
                "featherflow.enabled=true",
                "featherflow.auto-start-daemon=false",
                "featherflow.definition-locations=classpath:/workflows/multi/*.yml,classpath:/workflows/multi/*.xml"
            )
            .withConfiguration(org.springframework.boot.autoconfigure.AutoConfigurations.of(FeatherFlowAutoConfiguration.class))
            .run(context -> {
                WorkflowDefinitionRegistry registry = context.getBean(WorkflowDefinitionRegistry.class);
                assertThat(registry.find("sampleOrderWorkflow")).isNotNull();
                assertThat(registry.find("samplePaymentWorkflow")).isNotNull();
            });
    }

    @Test
    void shouldFailWhenMultipleDefinitionFilesShareTheSameWorkflowName() {
        new ApplicationContextRunner()
            .withUserConfiguration(TestConfiguration.class)
            .withPropertyValues(
                "featherflow.enabled=true",
                "featherflow.auto-start-daemon=false",
                "featherflow.definition-locations=classpath:/workflows/duplicate/*.yml,classpath:/workflows/duplicate/*.xml"
            )
            .withConfiguration(org.springframework.boot.autoconfigure.AutoConfigurations.of(FeatherFlowAutoConfiguration.class))
            .run(context -> {
                assertThat(context).hasFailed();
                assertThat(context.getStartupFailure()).hasMessageContaining("Duplicate workflow definition name");
            });
    }

    @Test
    void shouldUseConfiguredInstanceIdForJdbcWorkflowLockOwner() {
        FeatherFlowAutoConfiguration autoConfiguration = new FeatherFlowAutoConfiguration();
        FeatherFlowProperties properties = new FeatherFlowProperties();
        properties.setInstanceId("10.9.8.7:engine-a");
        PersistenceWriteRetrier retrier = new DefaultPersistenceWriteRetrier(3, Duration.ofMillis(1), Duration.ofMillis(2), delay -> {
        });

        WorkflowLockService workflowLockService = autoConfiguration.jdbcWorkflowLockService(
            new JdbcTemplate(),
            new WorkflowNodeIdentity(properties.getInstanceId()),
            retrier
        );

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
