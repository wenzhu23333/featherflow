package com.ywz.workflow.featherflow.starter;

import com.ywz.workflow.featherflow.daemon.DefaultWorkflowOperationHandler;
import com.ywz.workflow.featherflow.daemon.WorkflowOperationDaemon;
import com.ywz.workflow.featherflow.daemon.WorkflowOperationHandler;
import com.ywz.workflow.featherflow.daemon.WorkflowOperationProcessor;
import com.ywz.workflow.featherflow.definition.CompositeWorkflowDefinitionParser;
import com.ywz.workflow.featherflow.definition.InMemoryWorkflowDefinitionRegistry;
import com.ywz.workflow.featherflow.definition.WorkflowDefinitionParser;
import com.ywz.workflow.featherflow.definition.WorkflowDefinitionRegistry;
import com.ywz.workflow.featherflow.engine.DefaultWorkflowRetryScheduler;
import com.ywz.workflow.featherflow.engine.DefaultWorkflowExecutionScheduler;
import com.ywz.workflow.featherflow.definition.XmlWorkflowDefinitionParser;
import com.ywz.workflow.featherflow.definition.YamlWorkflowDefinitionParser;
import com.ywz.workflow.featherflow.engine.WorkflowEngine;
import com.ywz.workflow.featherflow.engine.WorkflowExecutionScheduler;
import com.ywz.workflow.featherflow.engine.WorkflowRetryScheduler;
import com.ywz.workflow.featherflow.handler.MapBackedWorkflowActivityHandlerRegistry;
import com.ywz.workflow.featherflow.handler.WorkflowActivityHandler;
import com.ywz.workflow.featherflow.handler.WorkflowActivityHandlerRegistry;
import com.ywz.workflow.featherflow.lock.LocalWorkflowLockService;
import com.ywz.workflow.featherflow.lock.WorkflowLockService;
import com.ywz.workflow.featherflow.persistence.DefaultPersistenceWriteRetrier;
import com.ywz.workflow.featherflow.persistence.PersistenceWriteRetrier;
import com.ywz.workflow.featherflow.persistence.RetryingActivityRepository;
import com.ywz.workflow.featherflow.persistence.RetryingWorkflowLockService;
import com.ywz.workflow.featherflow.persistence.RetryingWorkflowOperationRepository;
import com.ywz.workflow.featherflow.persistence.RetryingWorkflowRepository;
import com.ywz.workflow.featherflow.repository.ActivityRepository;
import com.ywz.workflow.featherflow.repository.WorkflowOperationRepository;
import com.ywz.workflow.featherflow.repository.WorkflowRepository;
import com.ywz.workflow.featherflow.repository.jdbc.JdbcActivityRepository;
import com.ywz.workflow.featherflow.repository.jdbc.JdbcWorkflowLockService;
import com.ywz.workflow.featherflow.repository.jdbc.JdbcWorkflowOperationRepository;
import com.ywz.workflow.featherflow.repository.jdbc.JdbcWorkflowRepository;
import com.ywz.workflow.featherflow.service.DefaultWorkflowCommandService;
import com.ywz.workflow.featherflow.service.DefaultWorkflowIdGenerator;
import com.ywz.workflow.featherflow.service.DefaultWorkflowRuntimeService;
import com.ywz.workflow.featherflow.service.WorkflowCommandService;
import com.ywz.workflow.featherflow.service.WorkflowRuntimeService;
import com.ywz.workflow.featherflow.support.JsonWorkflowContextSerializer;
import com.ywz.workflow.featherflow.support.WorkflowContextSerializer;
import java.time.Duration;
import java.time.Clock;
import java.util.Map;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import javax.sql.DataSource;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.JdbcTemplateAutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.jdbc.core.JdbcTemplate;

@Configuration(proxyBeanMethods = false)
@AutoConfigureAfter({DataSourceAutoConfiguration.class, JdbcTemplateAutoConfiguration.class})
@EnableConfigurationProperties(FeatherFlowProperties.class)
@ConditionalOnProperty(prefix = "featherflow", name = "enabled", havingValue = "true", matchIfMissing = true)
public class FeatherFlowAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public Clock featherflowClock() {
        return Clock.systemUTC();
    }

    @Bean
    @ConditionalOnMissingBean
    public WorkflowContextSerializer workflowContextSerializer() {
        return new JsonWorkflowContextSerializer();
    }

    @Bean
    @ConditionalOnMissingBean
    public WorkflowDefinitionParser workflowDefinitionParser() {
        return new CompositeWorkflowDefinitionParser(new YamlWorkflowDefinitionParser(), new XmlWorkflowDefinitionParser());
    }

    @Bean
    @ConditionalOnMissingBean
    public ResourcePatternResolver resourcePatternResolver() {
        return new PathMatchingResourcePatternResolver();
    }

    @Bean
    @ConditionalOnMissingBean
    public WorkflowDefinitionResourceLoader workflowDefinitionResourceLoader(
        ResourcePatternResolver resourcePatternResolver,
        WorkflowDefinitionParser workflowDefinitionParser
    ) {
        return new WorkflowDefinitionResourceLoader(resourcePatternResolver, workflowDefinitionParser);
    }

    @Bean
    @ConditionalOnMissingBean
    public WorkflowDefinitionRegistry workflowDefinitionRegistry(
        WorkflowDefinitionResourceLoader workflowDefinitionResourceLoader,
        FeatherFlowProperties properties
    ) {
        InMemoryWorkflowDefinitionRegistry registry = new InMemoryWorkflowDefinitionRegistry();
        workflowDefinitionResourceLoader.loadDefinitions(properties.getDefinitionLocations(), registry);
        return registry;
    }

    @Bean
    @ConditionalOnMissingBean
    public WorkflowActivityHandlerRegistry workflowActivityHandlerRegistry(Map<String, WorkflowActivityHandler> handlers) {
        MapBackedWorkflowActivityHandlerRegistry registry = new MapBackedWorkflowActivityHandlerRegistry();
        for (Map.Entry<String, WorkflowActivityHandler> entry : handlers.entrySet()) {
            registry.register(entry.getKey(), entry.getValue());
        }
        return registry;
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean(DataSource.class)
    public JdbcTemplate jdbcTemplate(DataSource dataSource) {
        return new JdbcTemplate(dataSource);
    }

    @Bean
    @ConditionalOnMissingBean
    public PersistenceWriteRetrier persistenceWriteRetrier(FeatherFlowProperties properties) {
        return new DefaultPersistenceWriteRetrier(
            properties.getPersistenceWriteRetryMaxAttempts(),
            Duration.ofMillis(properties.getPersistenceWriteRetryInitialDelayMillis()),
            Duration.ofMillis(properties.getPersistenceWriteRetryMaxDelayMillis())
        );
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean(JdbcTemplate.class)
    public WorkflowRepository workflowRepository(JdbcTemplate jdbcTemplate, PersistenceWriteRetrier persistenceWriteRetrier) {
        return new RetryingWorkflowRepository(new JdbcWorkflowRepository(jdbcTemplate), persistenceWriteRetrier);
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean(JdbcTemplate.class)
    public ActivityRepository activityRepository(JdbcTemplate jdbcTemplate, PersistenceWriteRetrier persistenceWriteRetrier) {
        return new RetryingActivityRepository(new JdbcActivityRepository(jdbcTemplate), persistenceWriteRetrier);
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean(JdbcTemplate.class)
    public WorkflowOperationRepository workflowOperationRepository(JdbcTemplate jdbcTemplate, PersistenceWriteRetrier persistenceWriteRetrier) {
        return new RetryingWorkflowOperationRepository(new JdbcWorkflowOperationRepository(jdbcTemplate), persistenceWriteRetrier);
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean(JdbcTemplate.class)
    public WorkflowLockService jdbcWorkflowLockService(
        JdbcTemplate jdbcTemplate,
        FeatherFlowProperties properties,
        PersistenceWriteRetrier persistenceWriteRetrier
    ) {
        return new RetryingWorkflowLockService(new JdbcWorkflowLockService(jdbcTemplate, properties.getInstanceId()), persistenceWriteRetrier);
    }

    @Bean
    @ConditionalOnMissingBean
    public WorkflowLockService localWorkflowLockService() {
        return new LocalWorkflowLockService();
    }

    @Bean(destroyMethod = "shutdown")
    @ConditionalOnMissingBean
    public WorkflowExecutionScheduler workflowExecutionScheduler(
        WorkflowEngine workflowEngine,
        WorkflowRepository workflowRepository,
        FeatherFlowProperties properties,
        Clock featherflowClock
    ) {
        ThreadPoolExecutor threadPoolExecutor = new ThreadPoolExecutor(
            properties.getCorePoolSize(),
            properties.getMaxPoolSize(),
            60L,
            TimeUnit.SECONDS,
            new LinkedBlockingQueue<Runnable>(properties.getQueueCapacity()),
            runnable -> {
                Thread thread = new Thread(runnable, "featherflow-execution");
                thread.setDaemon(true);
                return thread;
            },
            new ThreadPoolExecutor.CallerRunsPolicy()
        );
        return new DefaultWorkflowExecutionScheduler(workflowEngine, workflowRepository, threadPoolExecutor, featherflowClock);
    }

    @Bean(destroyMethod = "shutdown")
    @ConditionalOnMissingBean
    public WorkflowRetryScheduler workflowRetryScheduler(
        ObjectProvider<WorkflowExecutionScheduler> workflowExecutionSchedulerProvider,
        WorkflowRepository workflowRepository
    ) {
        ScheduledExecutorService scheduledExecutorService = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "featherflow-retry");
            thread.setDaemon(true);
            return thread;
        });
        return new DefaultWorkflowRetryScheduler(workflowExecutionSchedulerProvider::getObject, workflowRepository, scheduledExecutorService);
    }

    @Bean
    @ConditionalOnMissingBean
    public WorkflowEngine workflowEngine(
        WorkflowDefinitionRegistry workflowDefinitionRegistry,
        WorkflowRepository workflowRepository,
        ActivityRepository activityRepository,
        WorkflowActivityHandlerRegistry workflowActivityHandlerRegistry,
        WorkflowLockService workflowLockService,
        WorkflowContextSerializer workflowContextSerializer,
        Clock featherflowClock,
        WorkflowRetryScheduler workflowRetryScheduler
    ) {
        return new WorkflowEngine(
            workflowDefinitionRegistry,
            workflowRepository,
            activityRepository,
            workflowActivityHandlerRegistry,
            workflowLockService,
            workflowContextSerializer,
            featherflowClock,
            workflowRetryScheduler
        );
    }

    @Bean
    @ConditionalOnMissingBean
    public WorkflowRuntimeService workflowRuntimeService(
        WorkflowRepository workflowRepository,
        WorkflowEngine workflowEngine,
        WorkflowExecutionScheduler workflowExecutionScheduler,
        WorkflowContextSerializer workflowContextSerializer,
        Clock featherflowClock
    ) {
        return new DefaultWorkflowRuntimeService(
            workflowRepository,
            workflowEngine,
            workflowExecutionScheduler,
            workflowContextSerializer,
            featherflowClock
        );
    }

    @Bean
    @ConditionalOnMissingBean
    public WorkflowCommandService workflowCommandService(
        WorkflowDefinitionRegistry workflowDefinitionRegistry,
        WorkflowRepository workflowRepository,
        WorkflowContextSerializer workflowContextSerializer,
        Clock featherflowClock,
        WorkflowRuntimeService workflowRuntimeService
    ) {
        return new DefaultWorkflowCommandService(
            workflowDefinitionRegistry,
            workflowRepository,
            new DefaultWorkflowIdGenerator(),
            workflowContextSerializer,
            featherflowClock,
            workflowRuntimeService
        );
    }

    @Bean
    @ConditionalOnMissingBean
    public WorkflowOperationHandler workflowOperationHandler(
        WorkflowRepository workflowRepository,
        WorkflowRuntimeService workflowRuntimeService,
        WorkflowContextSerializer workflowContextSerializer
    ) {
        return new DefaultWorkflowOperationHandler(
            workflowRepository,
            workflowRuntimeService,
            workflowContextSerializer
        );
    }

    @Bean
    @ConditionalOnMissingBean
    public WorkflowOperationProcessor workflowOperationProcessor(
        WorkflowOperationRepository workflowOperationRepository,
        WorkflowRepository workflowRepository,
        WorkflowOperationHandler workflowOperationHandler,
        Clock featherflowClock
    ) {
        return new WorkflowOperationProcessor(workflowOperationRepository, workflowRepository, workflowOperationHandler, featherflowClock);
    }

    @Bean
    @ConditionalOnMissingBean
    public WorkflowOperationDaemon workflowOperationDaemon(
        WorkflowOperationRepository workflowOperationRepository,
        Clock featherflowClock,
        WorkflowOperationProcessor workflowOperationProcessor
    ) {
        return new WorkflowOperationDaemon(workflowOperationRepository, featherflowClock, workflowOperationProcessor);
    }

    @Bean
    @ConditionalOnMissingBean
    public WorkflowOperationDaemonLifecycle workflowOperationDaemonLifecycle(
        WorkflowOperationDaemon workflowOperationDaemon,
        FeatherFlowProperties properties
    ) {
        return new WorkflowOperationDaemonLifecycle(workflowOperationDaemon, properties);
    }
}
