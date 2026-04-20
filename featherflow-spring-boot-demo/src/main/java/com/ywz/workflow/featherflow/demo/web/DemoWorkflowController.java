package com.ywz.workflow.featherflow.demo.web;

import com.ywz.workflow.featherflow.demo.service.DemoWorkflowFacade;
import com.ywz.workflow.featherflow.model.WorkflowInstance;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/demo/workflows")
public class DemoWorkflowController {

    private final DemoWorkflowFacade demoWorkflowFacade;

    public DemoWorkflowController(DemoWorkflowFacade demoWorkflowFacade) {
        this.demoWorkflowFacade = demoWorkflowFacade;
    }

    @PostMapping("/start")
    public WorkflowViewResponse start(@RequestBody StartWorkflowRequest request) {
        WorkflowInstance workflowInstance = demoWorkflowFacade.start(
            request.getWorkflowName(),
            request.getBizId(),
            request.getAmount(),
            request.getCustomerName(),
            request.getForceNotifyFailure()
        );
        return WorkflowViewResponse.from(demoWorkflowFacade.getWorkflow(workflowInstance.getWorkflowId()));
    }

    @PostMapping("/{workflowId}/terminate")
    public void terminate(@PathVariable("workflowId") String workflowId) {
        demoWorkflowFacade.terminate(workflowId);
    }

    @PostMapping("/{workflowId}/retry")
    public void retry(@PathVariable("workflowId") String workflowId) {
        demoWorkflowFacade.retry(workflowId);
    }

    @PostMapping("/{workflowId}/skip")
    public void skip(@PathVariable("workflowId") String workflowId) {
        demoWorkflowFacade.skip(workflowId);
    }

    @GetMapping("/{workflowId}")
    public WorkflowViewResponse getWorkflow(@PathVariable("workflowId") String workflowId) {
        return WorkflowViewResponse.from(demoWorkflowFacade.getWorkflow(workflowId));
    }
}
