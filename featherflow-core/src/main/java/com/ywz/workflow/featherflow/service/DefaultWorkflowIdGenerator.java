package com.ywz.workflow.featherflow.service;

import java.util.UUID;

public class DefaultWorkflowIdGenerator implements WorkflowIdGenerator {

    @Override
    public String nextId() {
        return UUID.randomUUID().toString();
    }
}
