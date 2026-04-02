package com.ywz.workflow.featherflow.handler;

public interface WorkflowActivityHandlerRegistry {

    WorkflowActivityHandler getRequired(String name);
}
