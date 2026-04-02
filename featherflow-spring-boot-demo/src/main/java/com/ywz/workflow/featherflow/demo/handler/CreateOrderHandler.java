package com.ywz.workflow.featherflow.demo.handler;

import com.ywz.workflow.featherflow.handler.WorkflowActivityHandler;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

@Component("createOrderHandler")
public class CreateOrderHandler implements WorkflowActivityHandler {

    private static final Logger log = LoggerFactory.getLogger(CreateOrderHandler.class);

    @Override
    public Map<String, Object> handle(Map<String, Object> context) {
        String workflowId = MDC.get("workflowId");
        String bizId = MDC.get("bizId");

        log.info("Create demo order, workflowId={}, bizId={}, amount={}", workflowId, bizId, context.get("amount"));

        context.put("orderCreated", Boolean.TRUE);
        context.put("orderNo", "ORD-" + sanitizeWorkflowId(workflowId));
        context.put("latestBusinessStep", "createOrder");
        return context;
    }

    private String sanitizeWorkflowId(String workflowId) {
        if (workflowId == null) {
            return "UNKNOWN";
        }
        return workflowId.replace("-", "");
    }
}
