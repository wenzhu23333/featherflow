package com.ywz.workflow.featherflow.ops.controller;

import com.ywz.workflow.featherflow.ops.service.WorkflowOperationService;
import com.ywz.workflow.featherflow.ops.web.OperationForm;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class WorkflowOperationController {

    private final WorkflowOperationService workflowOperationService;

    public WorkflowOperationController(WorkflowOperationService workflowOperationService) {
        this.workflowOperationService = workflowOperationService;
    }

    @PostMapping("/workflows/{workflowId}/terminate")
    public String terminate(
        @PathVariable String workflowId,
        @ModelAttribute OperationForm form,
        RedirectAttributes redirectAttributes
    ) {
        workflowOperationService.submitTerminate(workflowId);
        addOperationFeedback(redirectAttributes, "终止命令已提交成功");
        return "redirect:" + resolveRedirect(form.getRedirectTo(), workflowId);
    }

    @PostMapping("/workflows/{workflowId}/retry")
    public String retry(
        @PathVariable String workflowId,
        @ModelAttribute OperationForm form,
        RedirectAttributes redirectAttributes
    ) {
        workflowOperationService.submitRetry(workflowId);
        addOperationFeedback(redirectAttributes, "重试命令已提交成功");
        return "redirect:" + resolveRedirect(form.getRedirectTo(), workflowId);
    }

    @PostMapping("/workflows/{workflowId}/skip")
    public String skip(
        @PathVariable String workflowId,
        @ModelAttribute OperationForm form,
        RedirectAttributes redirectAttributes
    ) {
        workflowOperationService.submitSkip(workflowId, form);
        addOperationFeedback(redirectAttributes, "跳过命令已提交成功");
        return "redirect:" + resolveRedirect(form.getRedirectTo(), workflowId);
    }

    private void addOperationFeedback(RedirectAttributes redirectAttributes, String message) {
        redirectAttributes.addFlashAttribute("operationFeedback", message);
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
