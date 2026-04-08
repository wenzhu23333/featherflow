package com.ywz.workflow.featherflow.ops.view;

public class ActivityTimelineItemView {

    private final String activityId;
    private final String activityName;
    private final String executedNode;
    private final String status;
    private final String gmtCreatedDisplay;
    private final String gmtModifiedDisplay;
    private final String input;
    private final String output;

    public ActivityTimelineItemView(
        String activityId,
        String activityName,
        String executedNode,
        String status,
        String gmtCreatedDisplay,
        String gmtModifiedDisplay,
        String input,
        String output
    ) {
        this.activityId = activityId;
        this.activityName = activityName;
        this.executedNode = executedNode;
        this.status = status;
        this.gmtCreatedDisplay = gmtCreatedDisplay;
        this.gmtModifiedDisplay = gmtModifiedDisplay;
        this.input = input;
        this.output = output;
    }

    public String getActivityId() {
        return activityId;
    }

    public String getActivityName() {
        return activityName;
    }

    public String getExecutedNode() {
        return executedNode;
    }

    public String getStatus() {
        return status;
    }

    public String getGmtCreatedDisplay() {
        return gmtCreatedDisplay;
    }

    public String getGmtModifiedDisplay() {
        return gmtModifiedDisplay;
    }

    public String getInput() {
        return input;
    }

    public String getOutput() {
        return output;
    }
}
