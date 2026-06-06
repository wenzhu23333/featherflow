package com.ywz.workflow.featherflow.ops.view;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ActivityFlowNodeView {

    private final int sequence;
    private final String activityName;
    private final String finalStatus;
    private final String latestExecutedNode;
    private final String latestModifiedDisplay;
    private final int totalAttempts;
    private final int failedTimes;
    private final int retryTimes;
    private final int successfulTimes;
    private final boolean latestNode;
    private final List<ActivityTimelineItemView> attempts;

    public ActivityFlowNodeView(
        int sequence,
        String activityName,
        String finalStatus,
        String latestExecutedNode,
        String latestModifiedDisplay,
        int totalAttempts,
        int failedTimes,
        int retryTimes,
        int successfulTimes,
        boolean latestNode,
        List<ActivityTimelineItemView> attempts
    ) {
        this.sequence = sequence;
        this.activityName = activityName;
        this.finalStatus = finalStatus;
        this.latestExecutedNode = latestExecutedNode;
        this.latestModifiedDisplay = latestModifiedDisplay;
        this.totalAttempts = totalAttempts;
        this.failedTimes = failedTimes;
        this.retryTimes = retryTimes;
        this.successfulTimes = successfulTimes;
        this.latestNode = latestNode;
        this.attempts = Collections.unmodifiableList(new ArrayList<ActivityTimelineItemView>(attempts));
    }

    public int getSequence() {
        return sequence;
    }

    public String getActivityName() {
        return activityName;
    }

    public String getFinalStatus() {
        return finalStatus;
    }

    public String getLatestExecutedNode() {
        return latestExecutedNode;
    }

    public String getLatestModifiedDisplay() {
        return latestModifiedDisplay;
    }

    public int getTotalAttempts() {
        return totalAttempts;
    }

    public int getFailedTimes() {
        return failedTimes;
    }

    public int getRetryTimes() {
        return retryTimes;
    }

    public int getSuccessfulTimes() {
        return successfulTimes;
    }

    public boolean isLatestNode() {
        return latestNode;
    }

    public List<ActivityTimelineItemView> getAttempts() {
        return attempts;
    }
}
