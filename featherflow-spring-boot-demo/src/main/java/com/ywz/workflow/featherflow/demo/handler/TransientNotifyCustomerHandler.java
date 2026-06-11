package com.ywz.workflow.featherflow.demo.handler;

import com.ywz.workflow.featherflow.handler.WorkflowActivityHandler;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

@Component("transientNotifyCustomerHandler")
public class TransientNotifyCustomerHandler implements WorkflowActivityHandler {

    private static final Logger log = LoggerFactory.getLogger(TransientNotifyCustomerHandler.class);

    private final ConcurrentHashMap<String, AtomicInteger> attempts = new ConcurrentHashMap<String, AtomicInteger>();

    @Override
    public Map<String, Object> handle(Map<String, Object> context) {
        String workflowId = MDC.get("workflowId");
        int attempt = attempts.computeIfAbsent(workflowId, key -> new AtomicInteger()).incrementAndGet();
        log.info("Notify customer with transient failure demo, workflowId={}, attempt={}", workflowId, Integer.valueOf(attempt));

        if (attempt == 1) {
            throw new IllegalStateException("Simulated transient notify failure");
        }

        context.put("customerNotified", Boolean.TRUE);
        context.put("transientNotifyAttempts", Integer.valueOf(attempt));
        context.put("latestBusinessStep", "transientNotifyCustomer");
        return context;
    }
}
