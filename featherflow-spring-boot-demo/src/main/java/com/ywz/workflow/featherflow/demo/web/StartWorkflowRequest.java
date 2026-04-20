package com.ywz.workflow.featherflow.demo.web;

public class StartWorkflowRequest {

    private String workflowName;
    private String bizId;
    private Integer amount;
    private String customerName;
    private Boolean forceNotifyFailure;

    public String getWorkflowName() {
        return workflowName;
    }

    public void setWorkflowName(String workflowName) {
        this.workflowName = workflowName;
    }

    public String getBizId() {
        return bizId;
    }

    public void setBizId(String bizId) {
        this.bizId = bizId;
    }

    public Integer getAmount() {
        return amount;
    }

    public void setAmount(Integer amount) {
        this.amount = amount;
    }

    public String getCustomerName() {
        return customerName;
    }

    public void setCustomerName(String customerName) {
        this.customerName = customerName;
    }

    public Boolean getForceNotifyFailure() {
        return forceNotifyFailure;
    }

    public void setForceNotifyFailure(Boolean forceNotifyFailure) {
        this.forceNotifyFailure = forceNotifyFailure;
    }
}
