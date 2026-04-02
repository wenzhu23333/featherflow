package com.ywz.workflow.featherflow.ops.view;

public class OperationRecordView {

    private final Long operationId;
    private final String operationType;
    private final String status;
    private final String operator;
    private final String reason;
    private final String activityId;
    private final String rawInput;
    private final String gmtCreatedDisplay;
    private final String gmtModifiedDisplay;

    public OperationRecordView(
        Long operationId,
        String operationType,
        String status,
        String operator,
        String reason,
        String activityId,
        String rawInput,
        String gmtCreatedDisplay,
        String gmtModifiedDisplay
    ) {
        this.operationId = operationId;
        this.operationType = operationType;
        this.status = status;
        this.operator = operator;
        this.reason = reason;
        this.activityId = activityId;
        this.rawInput = rawInput;
        this.gmtCreatedDisplay = gmtCreatedDisplay;
        this.gmtModifiedDisplay = gmtModifiedDisplay;
    }

    public Long getOperationId() {
        return operationId;
    }

    public String getOperationType() {
        return operationType;
    }

    public String getStatus() {
        return status;
    }

    public String getOperator() {
        return operator;
    }

    public String getReason() {
        return reason;
    }

    public String getActivityId() {
        return activityId;
    }

    public String getRawInput() {
        return rawInput;
    }

    public String getGmtCreatedDisplay() {
        return gmtCreatedDisplay;
    }

    public String getGmtModifiedDisplay() {
        return gmtModifiedDisplay;
    }
}
