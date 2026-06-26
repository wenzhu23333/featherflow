package com.ywz.workflow.featherflow.runtime;

import com.ywz.workflow.featherflow.context.WorkflowContextSnapshot;

public final class WorkflowRuntimeContext {

    private static final ThreadLocal<WorkflowContextSnapshot> CURRENT = new ThreadLocal<WorkflowContextSnapshot>();

    private WorkflowRuntimeContext() {
    }

    public static WorkflowContextSnapshot current() {
        WorkflowContextSnapshot snapshot = CURRENT.get();
        if (snapshot == null) {
            throw new IllegalStateException("No workflow runtime context bound to current thread");
        }
        return snapshot;
    }

    public static WorkflowContextSnapshot currentOrNull() {
        return CURRENT.get();
    }

    public static WorkflowContextSnapshot capture() {
        return CURRENT.get();
    }

    public static Scope open(WorkflowContextSnapshot snapshot) {
        WorkflowContextSnapshot previous = CURRENT.get();
        if (snapshot == null) {
            CURRENT.remove();
        } else {
            CURRENT.set(snapshot);
        }
        return new Scope(previous);
    }

    static void clear() {
        CURRENT.remove();
    }

    public static final class Scope implements AutoCloseable {

        private final WorkflowContextSnapshot previous;

        private Scope(WorkflowContextSnapshot previous) {
            this.previous = previous;
        }

        @Override
        public void close() {
            if (previous == null) {
                CURRENT.remove();
            } else {
                CURRENT.set(previous);
            }
        }
    }
}
