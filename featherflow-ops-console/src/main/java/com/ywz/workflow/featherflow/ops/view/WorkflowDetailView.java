package com.ywz.workflow.featherflow.ops.view;

import java.util.List;

public class WorkflowDetailView {

    private final String workflowId;
    private final String bizId;
    private final String workflowName;
    private final String startNode;
    private final String workflowStatus;
    private final String workflowInput;
    private final String gmtCreatedDisplay;
    private final String gmtModifiedDisplay;
    private final PageView<ActivityTimelineItemView> activityPage;
    private final List<ActivityFlowNodeView> activityFlowNodes;
    private final List<OperationRecordView> operations;
    private final String latestActivityId;
    private final AllowedActionsView allowedActions;

    public WorkflowDetailView(
        String workflowId,
        String bizId,
        String workflowName,
        String startNode,
        String workflowStatus,
        String workflowInput,
        String gmtCreatedDisplay,
        String gmtModifiedDisplay,
        PageView<ActivityTimelineItemView> activityPage,
        List<ActivityFlowNodeView> activityFlowNodes,
        List<OperationRecordView> operations,
        String latestActivityId,
        AllowedActionsView allowedActions
    ) {
        this.workflowId = workflowId;
        this.bizId = bizId;
        this.workflowName = workflowName;
        this.startNode = startNode;
        this.workflowStatus = workflowStatus;
        this.workflowInput = workflowInput;
        this.gmtCreatedDisplay = gmtCreatedDisplay;
        this.gmtModifiedDisplay = gmtModifiedDisplay;
        this.activityPage = activityPage;
        this.activityFlowNodes = activityFlowNodes;
        this.operations = operations;
        this.latestActivityId = latestActivityId;
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

    public String getStartNode() {
        return startNode;
    }

    public String getWorkflowStatus() {
        return workflowStatus;
    }

    public String getWorkflowInput() {
        return workflowInput;
    }

    public String getGmtCreatedDisplay() {
        return gmtCreatedDisplay;
    }

    public String getGmtModifiedDisplay() {
        return gmtModifiedDisplay;
    }

    public List<ActivityTimelineItemView> getActivities() {
        return activityPage.getItems();
    }

    public PaginationView getActivityPagination() {
        return activityPage.getPagination();
    }

    public List<ActivityFlowNodeView> getActivityFlowNodes() {
        return activityFlowNodes;
    }

    public List<OperationRecordView> getOperations() {
        return operations;
    }

    public String getLatestActivityId() {
        return latestActivityId;
    }

    public AllowedActionsView getAllowedActions() {
        return allowedActions;
    }
}
