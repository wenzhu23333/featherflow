package com.ywz.workflow.featherflow.starter;

import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "featherflow")
public class FeatherFlowProperties {

    private boolean enabled = true;
    private boolean autoStartDaemon = true;
    private long pollIntervalMillis = 1000L;
    private int corePoolSize = Math.max(2, Runtime.getRuntime().availableProcessors());
    private int maxPoolSize = Math.max(4, Runtime.getRuntime().availableProcessors() * 2);
    private int queueCapacity = Math.max(100, Runtime.getRuntime().availableProcessors() * 100);
    private int persistenceWriteRetryMaxAttempts = 4;
    private long persistenceWriteRetryInitialDelayMillis = 100L;
    private long persistenceWriteRetryMaxDelayMillis = 1000L;
    private String instanceId;
    private List<String> definitionLocations = new ArrayList<String>();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isAutoStartDaemon() {
        return autoStartDaemon;
    }

    public void setAutoStartDaemon(boolean autoStartDaemon) {
        this.autoStartDaemon = autoStartDaemon;
    }

    public long getPollIntervalMillis() {
        return pollIntervalMillis;
    }

    public void setPollIntervalMillis(long pollIntervalMillis) {
        this.pollIntervalMillis = pollIntervalMillis;
    }

    public int getCorePoolSize() {
        return corePoolSize;
    }

    public void setCorePoolSize(int corePoolSize) {
        this.corePoolSize = corePoolSize;
    }

    public int getMaxPoolSize() {
        return maxPoolSize;
    }

    public void setMaxPoolSize(int maxPoolSize) {
        this.maxPoolSize = maxPoolSize;
    }

    public int getQueueCapacity() {
        return queueCapacity;
    }

    public void setQueueCapacity(int queueCapacity) {
        this.queueCapacity = queueCapacity;
    }

    public int getPersistenceWriteRetryMaxAttempts() {
        return persistenceWriteRetryMaxAttempts;
    }

    public void setPersistenceWriteRetryMaxAttempts(int persistenceWriteRetryMaxAttempts) {
        this.persistenceWriteRetryMaxAttempts = persistenceWriteRetryMaxAttempts;
    }

    public long getPersistenceWriteRetryInitialDelayMillis() {
        return persistenceWriteRetryInitialDelayMillis;
    }

    public void setPersistenceWriteRetryInitialDelayMillis(long persistenceWriteRetryInitialDelayMillis) {
        this.persistenceWriteRetryInitialDelayMillis = persistenceWriteRetryInitialDelayMillis;
    }

    public long getPersistenceWriteRetryMaxDelayMillis() {
        return persistenceWriteRetryMaxDelayMillis;
    }

    public void setPersistenceWriteRetryMaxDelayMillis(long persistenceWriteRetryMaxDelayMillis) {
        this.persistenceWriteRetryMaxDelayMillis = persistenceWriteRetryMaxDelayMillis;
    }

    public String getInstanceId() {
        return instanceId;
    }

    public void setInstanceId(String instanceId) {
        this.instanceId = instanceId;
    }

    public List<String> getDefinitionLocations() {
        return definitionLocations;
    }

    public void setDefinitionLocations(List<String> definitionLocations) {
        this.definitionLocations = definitionLocations;
    }
}
