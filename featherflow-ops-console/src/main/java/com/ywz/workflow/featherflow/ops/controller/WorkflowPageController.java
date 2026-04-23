package com.ywz.workflow.featherflow.ops.controller;

import com.ywz.workflow.featherflow.ops.service.WorkflowQueryService;
import com.ywz.workflow.featherflow.ops.service.FilterDateTimeParser;
import com.ywz.workflow.featherflow.ops.service.WorkflowListFilter;
import com.ywz.workflow.featherflow.ops.view.PageView;
import com.ywz.workflow.featherflow.ops.view.WorkflowListItemView;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;

import static org.springframework.http.HttpStatus.NOT_FOUND;

@Controller
public class WorkflowPageController {

    private static final String ORDER_ASC = "asc";
    private static final String ORDER_DESC = "desc";

    private final WorkflowQueryService workflowQueryService;

    public WorkflowPageController(WorkflowQueryService workflowQueryService) {
        this.workflowQueryService = workflowQueryService;
    }

    @GetMapping("/workflows")
    public String workflows(
        @RequestParam(required = false) String workflowId,
        @RequestParam(required = false) String bizId,
        @RequestParam(required = false) String status,
        @RequestParam(required = false) String workflowName,
        @RequestParam(required = false) String createdFrom,
        @RequestParam(required = false) String createdTo,
        @RequestParam(required = false) String modifiedFrom,
        @RequestParam(required = false) String modifiedTo,
        @RequestParam(required = false) String page,
        @RequestParam(required = false) String size,
        @RequestParam(required = false) String order,
        Model model
    ) {
        int parsedPage = parsePositiveIntOrDefault(page, 1);
        int parsedSize = parsePositiveIntOrDefault(size, 10);
        String normalizedOrder = normalizeOrder(order, ORDER_DESC);
        Map<String, String> dateFilterErrors = new LinkedHashMap<>();
        WorkflowListFilter filter = new WorkflowListFilter(
            workflowId,
            bizId,
            status,
            workflowName,
            FilterDateTimeParser.parseNullable("createdFrom", createdFrom, dateFilterErrors),
            FilterDateTimeParser.parseNullable("createdTo", createdTo, dateFilterErrors),
            FilterDateTimeParser.parseNullable("modifiedFrom", modifiedFrom, dateFilterErrors),
            FilterDateTimeParser.parseNullable("modifiedTo", modifiedTo, dateFilterErrors)
        );
        addFilterAttributes(model, filter, createdFrom, createdTo, modifiedFrom, modifiedTo, normalizedOrder, dateFilterErrors);
        PageView<WorkflowListItemView> workflowPage =
            workflowQueryService.listWorkflowPage(filter, parsedPage, parsedSize, normalizedOrder);
        model.addAttribute("workflows", workflowPage.getItems());
        model.addAttribute("workflowPage", workflowPage);
        model.addAttribute("page", workflowPage.getPagination().getPage());
        model.addAttribute("size", workflowPage.getPagination().getSize());
        return "workflows/list";
    }

    @GetMapping("/workflows/table")
    public String workflowsTable(
        @RequestParam(required = false) String workflowId,
        @RequestParam(required = false) String bizId,
        @RequestParam(required = false) String status,
        @RequestParam(required = false) String workflowName,
        @RequestParam(required = false) String createdFrom,
        @RequestParam(required = false) String createdTo,
        @RequestParam(required = false) String modifiedFrom,
        @RequestParam(required = false) String modifiedTo,
        @RequestParam(required = false) String page,
        @RequestParam(required = false) String size,
        @RequestParam(required = false) String order,
        Model model
    ) {
        int parsedPage = parsePositiveIntOrDefault(page, 1);
        int parsedSize = parsePositiveIntOrDefault(size, 10);
        String normalizedOrder = normalizeOrder(order, ORDER_DESC);
        Map<String, String> dateFilterErrors = new LinkedHashMap<>();
        WorkflowListFilter filter = new WorkflowListFilter(
            workflowId,
            bizId,
            status,
            workflowName,
            FilterDateTimeParser.parseNullable("createdFrom", createdFrom, dateFilterErrors),
            FilterDateTimeParser.parseNullable("createdTo", createdTo, dateFilterErrors),
            FilterDateTimeParser.parseNullable("modifiedFrom", modifiedFrom, dateFilterErrors),
            FilterDateTimeParser.parseNullable("modifiedTo", modifiedTo, dateFilterErrors)
        );
        addFilterAttributes(model, filter, createdFrom, createdTo, modifiedFrom, modifiedTo, normalizedOrder, dateFilterErrors);
        PageView<WorkflowListItemView> workflowPage =
            workflowQueryService.listWorkflowPage(filter, parsedPage, parsedSize, normalizedOrder);
        model.addAttribute("workflows", workflowPage.getItems());
        model.addAttribute("workflowPage", workflowPage);
        model.addAttribute("page", workflowPage.getPagination().getPage());
        model.addAttribute("size", workflowPage.getPagination().getSize());
        return "workflows/list-table :: table(workflows=${workflows},workflowPage=${workflowPage},dateFilterErrors=${dateFilterErrors})";
    }

    @GetMapping("/workflows/{workflowId}")
    public String workflowDetail(
        @PathVariable String workflowId,
        @RequestParam(required = false) String activityPage,
        @RequestParam(required = false) String activitySize,
        @RequestParam(required = false) String activityOrder,
        Model model
    ) {
        int parsedActivityPage = parsePositiveIntOrDefault(activityPage, 1);
        int parsedActivitySize = parsePositiveIntOrDefault(activitySize, 5);
        String normalizedActivityOrder = normalizeOrder(activityOrder, ORDER_ASC);
        return workflowQueryService.getWorkflowDetail(workflowId, parsedActivityPage, parsedActivitySize, normalizedActivityOrder)
            .map(detail -> {
                model.addAttribute("detail", detail);
                model.addAttribute("activityPage", detail.getActivityPagination().getPage());
                model.addAttribute("activitySize", detail.getActivityPagination().getSize());
                model.addAttribute("activityOrder", normalizedActivityOrder);
                return "workflows/detail";
            })
            .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Workflow not found: " + workflowId));
    }

    @GetMapping("/workflows/{workflowId}/summary")
    public String workflowDetailSummary(@PathVariable String workflowId, Model model) {
        return workflowQueryService.getWorkflowSummary(workflowId)
            .map(detail -> {
                model.addAttribute("detail", detail);
                return "workflows/detail-summary :: summary(detail=${detail})";
            })
            .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Workflow not found: " + workflowId));
    }

    @GetMapping("/workflows/{workflowId}/timeline")
    public String workflowDetailTimeline(
        @PathVariable String workflowId,
        @RequestParam(required = false) String activityPage,
        @RequestParam(required = false) String activitySize,
        @RequestParam(required = false) String activityOrder,
        Model model
    ) {
        int parsedActivityPage = parsePositiveIntOrDefault(activityPage, 1);
        int parsedActivitySize = parsePositiveIntOrDefault(activitySize, 5);
        String normalizedActivityOrder = normalizeOrder(activityOrder, ORDER_ASC);
        return workflowQueryService.getWorkflowTimeline(workflowId, parsedActivityPage, parsedActivitySize, normalizedActivityOrder)
            .map(pageView -> {
                model.addAttribute("activities", pageView.getItems());
                model.addAttribute("activityPagination", pageView.getPagination());
                model.addAttribute("workflowId", workflowId);
                model.addAttribute("activityOrder", normalizedActivityOrder);
                return "workflows/detail-timeline :: timelineContainer(workflowId=${workflowId},activities=${activities},activityPagination=${activityPagination})";
            })
            .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Workflow not found: " + workflowId));
    }

    private void addFilterAttributes(
        Model model,
        WorkflowListFilter filter,
        String createdFrom,
        String createdTo,
        String modifiedFrom,
        String modifiedTo,
        String order,
        Map<String, String> dateFilterErrors
    ) {
        model.addAttribute("workflowId", filter.workflowId());
        model.addAttribute("bizId", filter.bizId());
        model.addAttribute("status", filter.status());
        model.addAttribute("workflowName", filter.workflowName());
        model.addAttribute("createdFrom", createdFrom);
        model.addAttribute("createdTo", createdTo);
        model.addAttribute("modifiedFrom", modifiedFrom);
        model.addAttribute("modifiedTo", modifiedTo);
        model.addAttribute("order", order);
        model.addAttribute("dateFilterErrors", dateFilterErrors);
    }

    private int parsePositiveIntOrDefault(String rawValue, int defaultValue) {
        if (rawValue == null || rawValue.trim().isEmpty()) {
            return defaultValue;
        }
        try {
            int parsed = Integer.parseInt(rawValue.trim());
            return parsed > 0 ? parsed : defaultValue;
        } catch (NumberFormatException ex) {
            return defaultValue;
        }
    }

    private String normalizeOrder(String rawOrder, String defaultOrder) {
        if (rawOrder == null || rawOrder.trim().isEmpty()) {
            return defaultOrder;
        }
        String normalized = rawOrder.trim().toLowerCase();
        return ORDER_ASC.equals(normalized) || ORDER_DESC.equals(normalized) ? normalized : defaultOrder;
    }
}
