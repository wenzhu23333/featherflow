package com.ywz.workflow.featherflow.demo.handler;

import com.ywz.workflow.featherflow.handler.WorkflowActivityHandler;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

@Component("submitAsyncJobHandler")
public class SubmitAsyncJobHandler implements WorkflowActivityHandler {

    private static final Logger log = LoggerFactory.getLogger(SubmitAsyncJobHandler.class);

    @Override
    public Map<String, Object> handle(Map<String, Object> context) {
        String workflowId = MDC.get("workflowId");
        String asyncJobId = "JOB-" + workflowId.replace("-", "");
        log.info("Submit async demo job, workflowId={}, asyncJobId={}", workflowId, asyncJobId);

        context.put("asyncJobId", asyncJobId);
        context.put("asyncJobSubmitted", Boolean.TRUE);
        context.put("latestBusinessStep", "submitAsyncJob");
        return context;
    }
}
