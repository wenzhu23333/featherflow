package com.ywz.workflow.featherflow.ops.controller;

import com.ywz.workflow.featherflow.ops.service.WorkflowOperationService;
import com.ywz.workflow.featherflow.ops.web.OperationForm;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;

@Controller
public class WorkflowOperationController {

    private final WorkflowOperationService workflowOperationService;

    public WorkflowOperationController(WorkflowOperationService workflowOperationService) {
        this.workflowOperationService = workflowOperationService;
    }

    @PostMapping("/workflows/{workflowId}/terminate")
    public String terminate(@PathVariable String workflowId, @ModelAttribute OperationForm form) {
        workflowOperationService.submitTerminate(workflowId, form);
        return "redirect:" + resolveRedirect(form.getRedirectTo(), workflowId);
    }

    @PostMapping("/workflows/{workflowId}/retry")
    public String retry(@PathVariable String workflowId, @ModelAttribute OperationForm form) {
        workflowOperationService.submitRetry(workflowId, form);
        return "redirect:" + resolveRedirect(form.getRedirectTo(), workflowId);
    }

    @PostMapping("/workflows/{workflowId}/skip")
    public String skip(@PathVariable String workflowId, @ModelAttribute OperationForm form) {
        workflowOperationService.submitSkip(workflowId, form);
        return "redirect:" + resolveRedirect(form.getRedirectTo(), workflowId);
    }

    private String resolveRedirect(String redirectTo, String workflowId) {
        if (isBlank(redirectTo)) {
            return "/workflows/" + workflowId;
        }
        if (redirectTo.startsWith("/") && !redirectTo.startsWith("//")) {
            return redirectTo;
        }
        return "/workflows/" + workflowId;
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
