package com.ywz.workflow.featherflow.runtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ywz.workflow.featherflow.context.WorkflowContextSnapshot;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class WorkflowRuntimeContextTest {

    @AfterEach
    void tearDown() {
        WorkflowRuntimeContext.clear();
    }

    @Test
    void shouldExposeCurrentWorkflowContextWithinScope() {
        WorkflowContextSnapshot snapshot = new WorkflowContextSnapshot("workflow-1", "biz-1", "biz-key-1");

        try (WorkflowRuntimeContext.Scope ignored = WorkflowRuntimeContext.open(snapshot)) {
            assertThat(WorkflowRuntimeContext.current()).isSameAs(snapshot);
            assertThat(WorkflowRuntimeContext.currentOrNull()).isSameAs(snapshot);
        }

        assertThat(WorkflowRuntimeContext.currentOrNull()).isNull();
    }

    @Test
    void shouldRestorePreviousWorkflowContextAfterNestedScope() {
        WorkflowContextSnapshot outer = new WorkflowContextSnapshot("workflow-outer", "biz-outer", "biz-key-outer");
        WorkflowContextSnapshot inner = new WorkflowContextSnapshot("workflow-inner", "biz-inner", "biz-key-inner");

        try (WorkflowRuntimeContext.Scope ignored = WorkflowRuntimeContext.open(outer)) {
            try (WorkflowRuntimeContext.Scope nested = WorkflowRuntimeContext.open(inner)) {
                assertThat(WorkflowRuntimeContext.current()).isSameAs(inner);
            }

            assertThat(WorkflowRuntimeContext.current()).isSameAs(outer);
        }

        assertThat(WorkflowRuntimeContext.currentOrNull()).isNull();
    }

    @Test
    void shouldFailWhenCurrentWorkflowContextIsMissing() {
        assertThatThrownBy(WorkflowRuntimeContext::current)
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("No workflow runtime context bound");
    }

    @Test
    void shouldKeepClearOutOfPublicApi() throws Exception {
        Method clearMethod = WorkflowRuntimeContext.class.getDeclaredMethod("clear");

        assertThat(Modifier.isPublic(clearMethod.getModifiers())).isFalse();
    }
}
