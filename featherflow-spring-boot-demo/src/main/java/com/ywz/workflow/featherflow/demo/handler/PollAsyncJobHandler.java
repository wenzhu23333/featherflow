package com.ywz.workflow.featherflow.demo.handler;

import com.ywz.workflow.featherflow.handler.WorkflowActivityHandler;
import com.ywz.workflow.featherflow.runtime.WorkflowRuntimeContext;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component("pollAsyncJobHandler")
public class PollAsyncJobHandler implements WorkflowActivityHandler {

    private static final Logger log = LoggerFactory.getLogger(PollAsyncJobHandler.class);

    private final ConcurrentHashMap<String, AtomicInteger> attempts = new ConcurrentHashMap<String, AtomicInteger>();

    @Override
    public Map<String, Object> handle(Map<String, Object> context) {
        String workflowId = WorkflowRuntimeContext.current().getWorkflowId();
        int attempt = attempts.computeIfAbsent(workflowId, key -> new AtomicInteger()).incrementAndGet();
        log.info(
            "Poll async demo job, workflowId={}, asyncJobId={}, attempt={}",
            workflowId,
            context.get("asyncJobId"),
            Integer.valueOf(attempt)
        );

        if (attempt == 1) {
            throw new IllegalStateException("Async demo job is not ready yet");
        }

        context.put("asyncJobCompleted", Boolean.TRUE);
        context.put("asyncPollAttempts", Integer.valueOf(attempt));
        context.put("latestBusinessStep", "pollAsyncJob");
        return context;
    }
}
