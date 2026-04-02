package com.ywz.workflow.featherflow.service;

import java.util.UUID;

public class DefaultWorkflowIdGenerator implements WorkflowIdGenerator {

    @Override
    public String nextId() {
        String compact = UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        return compact.substring(0, 4)
            + "-"
            + compact.substring(4, 8)
            + "-"
            + compact.substring(8, 12)
            + "-"
            + compact.substring(12, 16);
    }
}
