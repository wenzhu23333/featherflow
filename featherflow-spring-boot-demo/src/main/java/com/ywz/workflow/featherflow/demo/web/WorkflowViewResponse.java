package com.ywz.workflow.featherflow.demo.web;

import com.ywz.workflow.featherflow.demo.service.DemoWorkflowView;

public class WorkflowViewResponse {

    private String workflowId;
    private String bizId;
    private String bizKey;
    private String workflowName;
    private String status;
    private String latestActivityId;
    private String latestActivityName;

    public static WorkflowViewResponse from(DemoWorkflowView view) {
        WorkflowViewResponse response = new WorkflowViewResponse();
        response.setWorkflowId(view.getWorkflowId());
        response.setBizId(view.getBizId());
        response.setBizKey(view.getBizKey());
        response.setWorkflowName(view.getWorkflowName());
        response.setStatus(view.getStatus());
        response.setLatestActivityId(view.getLatestActivityId());
        response.setLatestActivityName(view.getLatestActivityName());
        return response;
    }

    public String getWorkflowId() {
        return workflowId;
    }

    public void setWorkflowId(String workflowId) {
        this.workflowId = workflowId;
    }

    public String getBizId() {
        return bizId;
    }

    public void setBizId(String bizId) {
        this.bizId = bizId;
    }

    public String getBizKey() {
        return bizKey;
    }

    public void setBizKey(String bizKey) {
        this.bizKey = bizKey;
    }

    public String getWorkflowName() {
        return workflowName;
    }

    public void setWorkflowName(String workflowName) {
        this.workflowName = workflowName;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getLatestActivityId() {
        return latestActivityId;
    }

    public void setLatestActivityId(String latestActivityId) {
        this.latestActivityId = latestActivityId;
    }

    public String getLatestActivityName() {
        return latestActivityName;
    }

    public void setLatestActivityName(String latestActivityName) {
        this.latestActivityName = latestActivityName;
    }
}
