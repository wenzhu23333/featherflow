package com.ywz.workflow.featherflow.demo.service;

import java.util.List;

public class DemoWorkflowScenario {

    private final String workflowName;
    private final String title;
    private final String description;
    private final String sampleBizId;
    private final String sampleBizKey;
    private final Integer sampleAmount;
    private final String sampleCustomerName;
    private final String expectedStatus;
    private final String observe;
    private final List<String> suggestedOperations;

    public DemoWorkflowScenario(
        String workflowName,
        String title,
        String description,
        String sampleBizId,
        String sampleBizKey,
        Integer sampleAmount,
        String sampleCustomerName,
        String expectedStatus,
        String observe,
        List<String> suggestedOperations
    ) {
        this.workflowName = workflowName;
        this.title = title;
        this.description = description;
        this.sampleBizId = sampleBizId;
        this.sampleBizKey = sampleBizKey;
        this.sampleAmount = sampleAmount;
        this.sampleCustomerName = sampleCustomerName;
        this.expectedStatus = expectedStatus;
        this.observe = observe;
        this.suggestedOperations = suggestedOperations;
    }

    public String getWorkflowName() {
        return workflowName;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public String getSampleBizId() {
        return sampleBizId;
    }

    public String getSampleBizKey() {
        return sampleBizKey;
    }

    public Integer getSampleAmount() {
        return sampleAmount;
    }

    public String getSampleCustomerName() {
        return sampleCustomerName;
    }

    public String getExpectedStatus() {
        return expectedStatus;
    }

    public String getObserve() {
        return observe;
    }

    public List<String> getSuggestedOperations() {
        return suggestedOperations;
    }
}
