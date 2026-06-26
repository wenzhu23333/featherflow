package com.ywz.workflow.featherflow.demo.handler;

import com.ywz.workflow.featherflow.handler.WorkflowActivityHandler;
import com.ywz.workflow.featherflow.context.WorkflowContextSnapshot;
import com.ywz.workflow.featherflow.runtime.WorkflowRuntimeContext;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component("manualInterventionRequiredHandler")
public class ManualInterventionRequiredHandler implements WorkflowActivityHandler {

    private static final Logger log = LoggerFactory.getLogger(ManualInterventionRequiredHandler.class);

    @Override
    public Map<String, Object> handle(Map<String, Object> context) {
        WorkflowContextSnapshot runtime = WorkflowRuntimeContext.current();
        log.info(
            "Manual intervention demo activity failed intentionally, workflowId={}, bizId={}",
            runtime.getWorkflowId(),
            runtime.getBizId()
        );
        throw new IllegalStateException("Simulated manual intervention required");
    }
}
