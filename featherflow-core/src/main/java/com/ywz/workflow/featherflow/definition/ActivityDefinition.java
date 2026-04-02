package com.ywz.workflow.featherflow.definition;

import java.time.Duration;

public class ActivityDefinition {

    private final String name;
    private final String handler;
    private final Duration retryInterval;
    private final int maxRetryTimes;

    public ActivityDefinition(String name, String handler, Duration retryInterval, int maxRetryTimes) {
        this.name = name;
        this.handler = handler;
        this.retryInterval = retryInterval;
        this.maxRetryTimes = maxRetryTimes;
    }

    public String getName() {
        return name;
    }

    public String getHandler() {
        return handler;
    }

    public Duration getRetryInterval() {
        return retryInterval;
    }

    public int getMaxRetryTimes() {
        return maxRetryTimes;
    }
}
