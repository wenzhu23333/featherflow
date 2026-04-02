package com.ywz.workflow.featherflow.ops.controller;

import com.ywz.workflow.featherflow.ops.service.WorkflowQueryService;
import com.ywz.workflow.featherflow.ops.service.FilterDateTimeParser;
import com.ywz.workflow.featherflow.ops.service.OperationHistoryFilter;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class OperationHistoryController {

    private final WorkflowQueryService workflowQueryService;

    public OperationHistoryController(WorkflowQueryService workflowQueryService) {
        this.workflowQueryService = workflowQueryService;
    }

    @GetMapping("/operations")
    public String operations(
        @RequestParam(required = false) String workflowId,
        @RequestParam(required = false) String bizId,
        @RequestParam(required = false) String operationType,
        @RequestParam(required = false) String status,
        @RequestParam(required = false) String operator,
        @RequestParam(required = false) String createdFrom,
        @RequestParam(required = false) String createdTo,
        Model model
    ) {
        Map<String, String> dateFilterErrors = new LinkedHashMap<>();
        OperationHistoryFilter filter = new OperationHistoryFilter(
            workflowId,
            bizId,
            operationType,
            status,
            operator,
            FilterDateTimeParser.parseNullable("createdFrom", createdFrom, dateFilterErrors),
            FilterDateTimeParser.parseNullable("createdTo", createdTo, dateFilterErrors)
        );
        addFilterAttributes(model, filter, createdFrom, createdTo, dateFilterErrors);
        model.addAttribute("operations", workflowQueryService.listOperations(filter));
        return "operations/list";
    }

    @GetMapping("/operations/table")
    public String operationTable(
        @RequestParam(required = false) String workflowId,
        @RequestParam(required = false) String bizId,
        @RequestParam(required = false) String operationType,
        @RequestParam(required = false) String status,
        @RequestParam(required = false) String operator,
        @RequestParam(required = false) String createdFrom,
        @RequestParam(required = false) String createdTo,
        Model model
    ) {
        Map<String, String> dateFilterErrors = new LinkedHashMap<>();
        OperationHistoryFilter filter = new OperationHistoryFilter(
            workflowId,
            bizId,
            operationType,
            status,
            operator,
            FilterDateTimeParser.parseNullable("createdFrom", createdFrom, dateFilterErrors),
            FilterDateTimeParser.parseNullable("createdTo", createdTo, dateFilterErrors)
        );
        addFilterAttributes(model, filter, createdFrom, createdTo, dateFilterErrors);
        model.addAttribute("operations", workflowQueryService.listOperations(filter));
        return "operations/list :: table(operations=${operations},dateFilterErrors=${dateFilterErrors})";
    }

    private void addFilterAttributes(
        Model model,
        OperationHistoryFilter filter,
        String createdFrom,
        String createdTo,
        Map<String, String> dateFilterErrors
    ) {
        model.addAttribute("workflowId", filter.workflowId());
        model.addAttribute("bizId", filter.bizId());
        model.addAttribute("operationType", filter.operationType());
        model.addAttribute("status", filter.status());
        model.addAttribute("operator", filter.operator());
        model.addAttribute("createdFrom", createdFrom);
        model.addAttribute("createdTo", createdTo);
        model.addAttribute("dateFilterErrors", dateFilterErrors);
    }
}
