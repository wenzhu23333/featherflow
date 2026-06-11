package com.ywz.workflow.featherflow.demo.handler;

import com.ywz.workflow.featherflow.handler.WorkflowActivityHandler;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

@Component("finalizeOrderHandler")
public class FinalizeOrderHandler implements WorkflowActivityHandler {

    private static final Logger log = LoggerFactory.getLogger(FinalizeOrderHandler.class);

    @Override
    public Map<String, Object> handle(Map<String, Object> context) {
        log.info("Finalize demo order, workflowId={}, orderNo={}", MDC.get("workflowId"), context.get("orderNo"));
        context.put("orderFinalized", Boolean.TRUE);
        context.put("latestBusinessStep", "finalizeOrder");
        return context;
    }
}
