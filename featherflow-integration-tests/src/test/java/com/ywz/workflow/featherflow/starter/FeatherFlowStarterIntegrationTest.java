package com.ywz.workflow.featherflow.starter;

import static org.assertj.core.api.Assertions.assertThat;

import com.ywz.workflow.featherflow.handler.WorkflowActivityHandler;
import com.ywz.workflow.featherflow.model.WorkflowInstance;
import com.ywz.workflow.featherflow.model.WorkflowStatus;
import com.ywz.workflow.featherflow.repository.ActivityRepository;
import com.ywz.workflow.featherflow.repository.WorkflowOperationRepository;
import com.ywz.workflow.featherflow.repository.WorkflowRepository;
import com.ywz.workflow.featherflow.service.WorkflowCommandService;
import java.util.Map;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

class FeatherFlowStarterIntegrationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
        .withUserConfiguration(TestConfiguration.class)
        .withPropertyValues(
            "featherflow.enabled=true",
            "featherflow.auto-start-daemon=false",
            "featherflow.definition-locations=classpath:/workflows/sample-workflow.yml"
        )
        .withConfiguration(org.springframework.boot.autoconfigure.AutoConfigurations.of(FeatherFlowAutoConfiguration.class));

    @Test
    void shouldRunWorkflowSuccessfullyWithStarterDefaults() {
        contextRunner.run(context -> {
            WorkflowCommandService workflowCommandService = context.getBean(WorkflowCommandService.class);
            WorkflowRepository workflowRepository = context.getBean(WorkflowRepository.class);
            ActivityRepository activityRepository = context.getBean(ActivityRepository.class);
            WorkflowOperationRepository workflowOperationRepository = context.getBean(WorkflowOperationRepository.class);

            WorkflowInstance workflow = workflowCommandService.startWorkflow(
                "sampleOrderWorkflow",
                "biz-starter-best-practice",
                "{\"amount\":100}"
            );

            long deadline = System.currentTimeMillis() + 1000L;
            while (System.currentTimeMillis() < deadline) {
                if (workflowRepository.findRequired(workflow.getWorkflowId()).getStatus() == WorkflowStatus.COMPLETED) {
                    break;
                }
                Thread.sleep(20L);
            }

            assertThat(workflowRepository.findRequired(workflow.getWorkflowId()).getStatus()).isEqualTo(WorkflowStatus.COMPLETED);
            assertThat(workflowOperationRepository.findAll()).isEmpty();
            assertThat(activityRepository.findByWorkflowId(workflow.getWorkflowId())).singleElement().satisfies(activity -> {
                assertThat(activity.getActivityName()).isEqualTo("createOrder");
                assertThat(activity.getOutput()).contains("\"created\":true");
            });
        });
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
