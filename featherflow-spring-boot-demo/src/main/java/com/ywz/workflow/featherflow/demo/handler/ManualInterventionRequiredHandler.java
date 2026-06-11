package com.ywz.workflow.featherflow.demo.handler;

import com.ywz.workflow.featherflow.handler.WorkflowActivityHandler;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

@Component("manualInterventionRequiredHandler")
public class ManualInterventionRequiredHandler implements WorkflowActivityHandler {

    private static final Logger log = LoggerFactory.getLogger(ManualInterventionRequiredHandler.class);

    @Override
    public Map<String, Object> handle(Map<String, Object> context) {
        log.info(
            "Manual intervention demo activity failed intentionally, workflowId={}, bizId={}",
            MDC.get("workflowId"),
            MDC.get("bizId")
        );
        throw new IllegalStateException("Simulated manual intervention required");
    }
}
