package com.ywz.workflow.featherflow.ops.view;

public class AllowedActionsView {

    private final boolean canTerminate;
    private final boolean canRetry;
    private final boolean canSkipLatest;

    public AllowedActionsView(boolean canTerminate, boolean canRetry, boolean canSkipLatest) {
        this.canTerminate = canTerminate;
        this.canRetry = canRetry;
        this.canSkipLatest = canSkipLatest;
    }

    public boolean isCanTerminate() {
        return canTerminate;
    }

    public boolean isCanRetry() {
        return canRetry;
    }

    public boolean isCanSkipLatest() {
        return canSkipLatest;
    }
}
