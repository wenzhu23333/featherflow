package com.ywz.workflow.featherflow.ops.view;

public class WorkflowListItemView {

    private final String workflowId;
    private final String bizId;
    private final String workflowName;
    private final String workflowStatus;
    private final String latestActivityId;
    private final String latestActivitySummary;
    private final String latestFailureSummary;
    private final String gmtModifiedDisplay;
    private final AllowedActionsView allowedActions;

    public WorkflowListItemView(
        String workflowId,
        String bizId,
        String workflowName,
        String workflowStatus,
        String latestActivityId,
        String latestActivitySummary,
        String latestFailureSummary,
        String gmtModifiedDisplay,
        AllowedActionsView allowedActions
    ) {
        this.workflowId = workflowId;
        this.bizId = bizId;
        this.workflowName = workflowName;
        this.workflowStatus = workflowStatus;
        this.latestActivityId = latestActivityId;
        this.latestActivitySummary = latestActivitySummary;
        this.latestFailureSummary = latestFailureSummary;
        this.gmtModifiedDisplay = gmtModifiedDisplay;
        this.allowedActions = allowedActions;
    }

    public String getWorkflowId() {
        return workflowId;
    }

    public String getBizId() {
        return bizId;
    }

    public String getWorkflowName() {
        return workflowName;
    }

    public String getWorkflowStatus() {
        return workflowStatus;
    }

    public String getLatestActivityId() {
        return latestActivityId;
    }

    public String getLatestActivitySummary() {
        return latestActivitySummary;
    }

    public String getLatestFailureSummary() {
        return latestFailureSummary;
    }

    public String getGmtModifiedDisplay() {
        return gmtModifiedDisplay;
    }

    public AllowedActionsView getAllowedActions() {
        return allowedActions;
    }
}
