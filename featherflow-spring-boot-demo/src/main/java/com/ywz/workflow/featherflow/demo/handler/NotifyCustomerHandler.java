package com.ywz.workflow.featherflow.demo.handler;

import com.ywz.workflow.featherflow.handler.WorkflowActivityHandler;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

@Component("notifyCustomerHandler")
public class NotifyCustomerHandler implements WorkflowActivityHandler {

    private static final Logger log = LoggerFactory.getLogger(NotifyCustomerHandler.class);

    @Override
    public Map<String, Object> handle(Map<String, Object> context) {
        String workflowId = MDC.get("workflowId");
        String bizId = MDC.get("bizId");
        log.info("Notify demo customer, workflowId={}, bizId={}, orderNo={}", workflowId, bizId, context.get("orderNo"));

        if (Boolean.TRUE.equals(context.get("forceNotifyFailure"))) {
            throw new IllegalStateException("Simulated notify failure for demo");
        }

        context.put("customerNotified", Boolean.TRUE);
        context.put("latestBusinessStep", "notifyCustomer");
        return context;
    }
}
