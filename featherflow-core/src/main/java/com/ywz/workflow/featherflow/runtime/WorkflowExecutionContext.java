package com.ywz.workflow.featherflow.runtime;

import com.ywz.workflow.featherflow.context.WorkflowContextSnapshot;
import com.ywz.workflow.featherflow.logging.WorkflowLogContext;

public final class WorkflowExecutionContext {

    private WorkflowExecutionContext() {
    }

    public static Scope open(WorkflowContextSnapshot snapshot) {
        WorkflowRuntimeContext.Scope runtimeScope = WorkflowRuntimeContext.open(snapshot);
        WorkflowLogContext.Scope logScope = WorkflowLogContext.open(snapshot);
        return new Scope(runtimeScope, logScope);
    }

    public static final class Scope implements AutoCloseable {

        private final WorkflowRuntimeContext.Scope runtimeScope;
        private final WorkflowLogContext.Scope logScope;

        private Scope(WorkflowRuntimeContext.Scope runtimeScope, WorkflowLogContext.Scope logScope) {
            this.runtimeScope = runtimeScope;
            this.logScope = logScope;
        }

        @Override
        public void close() {
            logScope.close();
            runtimeScope.close();
        }
    }
}
