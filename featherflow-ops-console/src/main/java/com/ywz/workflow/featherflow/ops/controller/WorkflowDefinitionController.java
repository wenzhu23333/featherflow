package com.ywz.workflow.featherflow.ops.controller;

import com.ywz.workflow.featherflow.service.WorkflowDefinitionQueryService;
import com.ywz.workflow.featherflow.service.WorkflowDefinitionStepView;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
public class WorkflowDefinitionController {

    private final WorkflowDefinitionQueryService workflowDefinitionQueryService;

    public WorkflowDefinitionController(WorkflowDefinitionQueryService workflowDefinitionQueryService) {
        this.workflowDefinitionQueryService = workflowDefinitionQueryService;
    }

    @GetMapping("/workflow-definitions/{workflowName}/steps")
    public List<WorkflowDefinitionStepView> steps(@PathVariable String workflowName) {
        try {
            return workflowDefinitionQueryService.listActivitySteps(workflowName);
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, ex.getMessage(), ex);
        }
    }
}
