package com.ywz.workflow.featherflow.ops.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ywz.workflow.featherflow.ops.repository.WorkflowViewRepository;
import com.ywz.workflow.featherflow.ops.repository.WorkflowViewRepository.ActivitySummaryRow;
import com.ywz.workflow.featherflow.ops.repository.WorkflowViewRepository.ActivityTimelineRow;
import com.ywz.workflow.featherflow.ops.repository.WorkflowViewRepository.OperationHistoryRow;
import com.ywz.workflow.featherflow.ops.repository.WorkflowViewRepository.OperationRecordRow;
import com.ywz.workflow.featherflow.ops.repository.WorkflowViewRepository.WorkflowDetailRow;
import com.ywz.workflow.featherflow.ops.repository.WorkflowViewRepository.WorkflowListRow;
import com.ywz.workflow.featherflow.ops.view.ActivityFlowNodeView;
import com.ywz.workflow.featherflow.ops.view.ActivityFlowOverviewView;
import com.ywz.workflow.featherflow.ops.view.ActivityTimelineItemView;
import com.ywz.workflow.featherflow.ops.view.AllowedActionsView;
import com.ywz.workflow.featherflow.ops.view.OperationRecordView;
import com.ywz.workflow.featherflow.ops.view.PageView;
import com.ywz.workflow.featherflow.ops.view.PaginationView;
import com.ywz.workflow.featherflow.ops.view.WorkflowDetailView;
import com.ywz.workflow.featherflow.ops.view.WorkflowListItemView;
import com.ywz.workflow.featherflow.service.WorkflowDefinitionQueryService;
import com.ywz.workflow.featherflow.service.WorkflowDefinitionStepView;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class WorkflowQueryService {

    private static final Logger LOGGER = LoggerFactory.getLogger(WorkflowQueryService.class);
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<Map<String, Object>>() {
    };
    private static final DateTimeFormatter MODIFIED_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final String ORDER_ASC = "asc";
    private static final String ORDER_DESC = "desc";
    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 100;
    private static final String DEFINITION_MISSING_WARNING =
        "定义未加载，无法展示还未执行的步骤。请配置 featherflow.definition-locations 指向业务服务使用的 workflow XML/YML。";

    private final WorkflowViewRepository workflowViewRepository;
    private final ObjectMapper objectMapper;
    private final WorkflowDefinitionQueryService workflowDefinitionQueryService;

    public WorkflowQueryService(
        WorkflowViewRepository workflowViewRepository,
        ObjectMapper objectMapper,
        WorkflowDefinitionQueryService workflowDefinitionQueryService
    ) {
        this.workflowViewRepository = workflowViewRepository;
        this.objectMapper = objectMapper;
        this.workflowDefinitionQueryService = workflowDefinitionQueryService;
    }

    public List<WorkflowListItemView> listWorkflows() {
        return listWorkflowPage(WorkflowListFilter.empty(), 1, 10).getItems();
    }

    public List<WorkflowListItemView> listWorkflows(WorkflowListFilter filter) {
        return listWorkflowPage(filter, 1, MAX_PAGE_SIZE).getItems();
    }

    public PageView<WorkflowListItemView> listWorkflowPage(WorkflowListFilter filter, int page, int size) {
        return listWorkflowPage(filter, page, size, ORDER_DESC);
    }

    public PageView<WorkflowListItemView> listWorkflowPage(WorkflowListFilter filter, int page, int size, String order) {
        int safeSize = normalizePageSize(size);
        long totalElements = workflowViewRepository.countWorkflowListRows(
            filter.workflowId(),
            filter.bizId(),
            filter.bizKey(),
            filter.status(),
            filter.workflowName(),
            filter.createdFrom(),
            filter.createdTo(),
            filter.modifiedFrom(),
            filter.modifiedTo()
        );
        int totalPages = calculateTotalPages(totalElements, safeSize);
        int safePage = normalizePage(page, totalPages);
        int offset = (safePage - 1) * safeSize;
        List<WorkflowListRow> rows = workflowViewRepository.findWorkflowPageRows(
                filter.workflowId(),
                filter.bizId(),
                filter.bizKey(),
                filter.status(),
                filter.workflowName(),
                filter.createdFrom(),
                filter.createdTo(),
                filter.modifiedFrom(),
                filter.modifiedTo(),
                safeSize,
                offset,
                order
            );
        List<String> workflowIds = rows.stream()
            .map(WorkflowListRow::workflowId)
            .collect(Collectors.toList());
        Map<String, ActivitySummaryRow> latestActivities =
            indexActivitySummaryRows(workflowViewRepository.findLatestActivityRows(workflowIds));
        Map<String, ActivitySummaryRow> latestFailures =
            indexActivitySummaryRows(workflowViewRepository.findLatestFailedActivityRows(workflowIds));
        List<WorkflowListItemView> items = rows.stream()
            .map(row -> toListItemView(row, latestActivities.get(row.workflowId()), latestFailures.get(row.workflowId())))
            .collect(Collectors.toList());
        return page(items, safePage, safeSize, totalPages, totalElements);
    }

    public Optional<WorkflowDetailView> getWorkflowDetail(String workflowId) {
        return getWorkflowDetail(workflowId, 1, 5);
    }

    public Optional<WorkflowDetailView> getWorkflowDetail(String workflowId, int activityPage, int activitySize) {
        return getWorkflowDetail(workflowId, activityPage, activitySize, ORDER_ASC);
    }

    public Optional<WorkflowDetailView> getWorkflowDetail(String workflowId, int activityPage, int activitySize, String activityOrder) {
        Optional<WorkflowDetailRow> row = workflowViewRepository.findWorkflowDetailRow(workflowId);
        if (!row.isPresent()) {
            return Optional.empty();
        }
        WorkflowDetailRow detailRow = row.get();
        PageView<ActivityTimelineItemView> activityPageView =
            buildWorkflowActivityTimeline(workflowId, activityPage, activitySize, activityOrder);
        ActivityFlowOverviewView activityFlow =
            buildCompressedWorkflowActivityFlow(workflowId, detailRow.workflowName());
        List<OperationRecordView> operations = workflowViewRepository.findOperationRecordRows(workflowId).stream()
            .map(this::toOperationRecordView)
            .collect(Collectors.toList());

        ActivitySummaryRow latestActivity = findLatestActivitySummary(workflowId);
        String latestActivityId = latestActivity == null ? detailRow.latestActivityId() : latestActivity.activityId();
        String latestExecutedNode = latestActivity == null ? detailRow.latestExecutedNode() : latestActivity.executedNode();
        return Optional.of(
            new WorkflowDetailView(
                detailRow.workflowId(),
                detailRow.bizId(),
                blankToDash(detailRow.bizKey()),
                blankToDash(detailRow.workflowName()),
                blankToDash(detailRow.startNode()),
                blankToDash(latestExecutedNode),
                detailRow.workflowStatus(),
                blankToDash(detailRow.workflowInput()),
                "",
                formatTime(detailRow.gmtCreated()),
                formatTime(detailRow.gmtModified()),
                activityPageView,
                activityFlow.getNodes(),
                activityFlow.isDefinitionMissing(),
                activityFlow.getDefinitionWarning(),
                operations,
                latestActivityId,
                buildAllowedActions(detailRow.workflowStatus(), latestActivityId)
            )
        );
    }

    public Optional<WorkflowDetailView> getWorkflowSummary(String workflowId) {
        Optional<WorkflowDetailRow> row = workflowViewRepository.findWorkflowDetailRow(workflowId);
        if (!row.isPresent()) {
            return Optional.empty();
        }
        WorkflowDetailRow detailRow = row.get();
        ActivitySummaryRow latestActivity = findLatestActivitySummary(workflowId);
        String latestActivityId = latestActivity == null ? detailRow.latestActivityId() : latestActivity.activityId();
        String latestExecutedNode = latestActivity == null ? detailRow.latestExecutedNode() : latestActivity.executedNode();
        return Optional.of(
            new WorkflowDetailView(
                detailRow.workflowId(),
                detailRow.bizId(),
                blankToDash(detailRow.bizKey()),
                blankToDash(detailRow.workflowName()),
                blankToDash(detailRow.startNode()),
                blankToDash(latestExecutedNode),
                detailRow.workflowStatus(),
                blankToDash(detailRow.workflowInput()),
                "",
                formatTime(detailRow.gmtCreated()),
                formatTime(detailRow.gmtModified()),
                paginate(Collections.<ActivityTimelineItemView>emptyList(), 1, 5),
                Collections.<ActivityFlowNodeView>emptyList(),
                false,
                "",
                Collections.<OperationRecordView>emptyList(),
                latestActivityId,
                buildAllowedActions(detailRow.workflowStatus(), latestActivityId)
            )
        );
    }

    public Optional<List<ActivityTimelineItemView>> getWorkflowTimeline(String workflowId) {
        return getWorkflowActivityTimeline(workflowId, 1, 5).map(PageView::getItems);
    }

    public Optional<PageView<ActivityTimelineItemView>> getWorkflowTimeline(String workflowId, int activityPage, int activitySize) {
        return getWorkflowActivityTimeline(workflowId, activityPage, activitySize, ORDER_ASC);
    }

    public Optional<PageView<ActivityTimelineItemView>> getWorkflowTimeline(
        String workflowId,
        int activityPage,
        int activitySize,
        String activityOrder
    ) {
        return getWorkflowActivityTimeline(workflowId, activityPage, activitySize, activityOrder);
    }

    public Optional<List<ActivityTimelineItemView>> getWorkflowActivityTimeline(String workflowId) {
        return getWorkflowActivityTimeline(workflowId, 1, 5).map(PageView::getItems);
    }

    public Optional<PageView<ActivityTimelineItemView>> getWorkflowActivityTimeline(String workflowId, int activityPage, int activitySize) {
        return getWorkflowActivityTimeline(workflowId, activityPage, activitySize, ORDER_ASC);
    }

    public Optional<PageView<ActivityTimelineItemView>> getWorkflowActivityTimeline(
        String workflowId,
        int activityPage,
        int activitySize,
        String activityOrder
    ) {
        if (!workflowViewRepository.workflowExists(workflowId)) {
            return Optional.empty();
        }
        return Optional.of(buildWorkflowActivityTimeline(workflowId, activityPage, activitySize, activityOrder));
    }

    public Optional<List<ActivityFlowNodeView>> getWorkflowActivityFlow(String workflowId) {
        return getCompressedWorkflowActivityFlow(workflowId);
    }

    public Optional<List<ActivityFlowNodeView>> getCompressedWorkflowActivityFlow(String workflowId) {
        return getCompressedWorkflowActivityFlowOverview(workflowId).map(ActivityFlowOverviewView::getNodes);
    }

    public Optional<ActivityFlowOverviewView> getCompressedWorkflowActivityFlowOverview(String workflowId) {
        Optional<WorkflowDetailRow> row = workflowViewRepository.findWorkflowDetailRow(workflowId);
        if (!row.isPresent()) {
            return Optional.empty();
        }
        return Optional.of(buildCompressedWorkflowActivityFlow(workflowId, row.get().workflowName()));
    }

    public List<OperationHistoryItemView> listOperations() {
        return listOperations(OperationHistoryFilter.empty());
    }

    public List<OperationHistoryItemView> listOperations(OperationHistoryFilter filter) {
        return workflowViewRepository.findOperationHistoryRows().stream()
            .filter(row -> containsIgnoreCase(row.workflowId(), filter.workflowId()))
            .filter(row -> containsIgnoreCase(row.bizId(), filter.bizId()))
            .filter(row -> containsIgnoreCase(row.operationType(), filter.operationType()))
            .filter(row -> containsIgnoreCase(row.status(), filter.status()))
            .filter(row -> isWithinRange(row.gmtCreated(), filter.createdFrom(), filter.createdTo()))
            .map(this::toOperationHistoryItemView)
            .filter(view -> containsIgnoreCase(view.getOperator(), filter.operator()))
            .collect(Collectors.toList());
    }

    private WorkflowListItemView toListItemView(WorkflowListRow row) {
        return toListItemView(row, null, null);
    }

    private WorkflowListItemView toListItemView(
        WorkflowListRow row,
        ActivitySummaryRow latestActivity,
        ActivitySummaryRow latestFailure
    ) {
        String latestActivityId = latestActivity == null ? row.latestActivityId() : latestActivity.activityId();
        String latestActivityName = latestActivity == null ? row.latestActivityName() : latestActivity.activityName();
        String latestActivityStatus = latestActivity == null ? row.latestActivityStatus() : latestActivity.status();
        String latestExecutedNode = latestActivity == null ? row.latestExecutedNode() : latestActivity.executedNode();
        String latestFailureOutput = latestFailure == null ? row.latestFailureOutput() : latestFailure.output();
        return new WorkflowListItemView(
            row.workflowId(),
            row.bizId(),
            blankToDash(row.bizKey()),
            blankToDash(row.workflowName()),
            row.workflowStatus(),
            latestActivityId,
            buildLatestActivitySummary(latestActivityName, latestActivityStatus),
            blankToDash(latestExecutedNode),
            summarizeFailure(latestFailureOutput),
            formatTime(row.gmtCreated()),
            formatTime(row.gmtModified()),
            buildAllowedActions(row.workflowStatus(), latestActivityId)
        );
    }

    private ActivityTimelineItemView toActivityTimelineItemView(ActivityTimelineRow row) {
        return new ActivityTimelineItemView(
            row.activityId(),
            row.activityName(),
            blankToDash(row.executedNode()),
            blankToDash(row.status()),
            formatTime(row.gmtCreated()),
            formatTime(row.gmtModified()),
            blankToDash(row.input()),
            blankToDash(row.output())
        );
    }

    private PageView<ActivityTimelineItemView> buildWorkflowActivityTimeline(
        String workflowId,
        int activityPage,
        int activitySize,
        String activityOrder
    ) {
        List<ActivityTimelineItemView> activities = workflowViewRepository.findActivityTimelineRows(workflowId).stream()
            .sorted(activityTimelineComparator(activityOrder))
            .map(this::toActivityTimelineItemView)
            .collect(Collectors.toList());
        return paginate(activities, activityPage, activitySize);
    }

    private ActivityFlowOverviewView buildCompressedWorkflowActivityFlow(String workflowId, String workflowName) {
        DefinitionStepLookup lookup = findDefinitionSteps(workflowName);
        List<ActivityFlowNodeView> nodes = buildActivityFlowNodes(
            workflowViewRepository.findActivityTimelineRows(workflowId),
            lookup.steps()
        );
        return new ActivityFlowOverviewView(
            nodes,
            lookup.definitionMissing(),
            lookup.definitionMissing() ? DEFINITION_MISSING_WARNING : ""
        );
    }

    private List<ActivityFlowNodeView> buildActivityFlowNodes(
        List<ActivityTimelineRow> activityRows,
        List<WorkflowDefinitionStepView> definitionSteps
    ) {
        List<ActivityTimelineItemView> attempts = activityRows.stream()
            .sorted(activityTimelineComparator(ORDER_ASC))
            .map(this::toActivityTimelineItemView)
            .collect(Collectors.toList());
        Map<String, List<ActivityTimelineItemView>> groupedAttempts =
            new LinkedHashMap<String, List<ActivityTimelineItemView>>();
        for (ActivityTimelineItemView attempt : attempts) {
            if (!groupedAttempts.containsKey(attempt.getActivityName())) {
                groupedAttempts.put(attempt.getActivityName(), new ArrayList<ActivityTimelineItemView>());
            }
            groupedAttempts.get(attempt.getActivityName()).add(attempt);
        }

        String latestActivityId = attempts.isEmpty() ? null : attempts.get(attempts.size() - 1).getActivityId();
        List<ActivityFlowNodeView> nodes = new ArrayList<ActivityFlowNodeView>();
        int sequence = 1;
        for (WorkflowDefinitionStepView step : definitionSteps) {
            String activityName = step.getActivityName();
            List<ActivityTimelineItemView> nodeAttempts = groupedAttempts.remove(activityName);
            if (nodeAttempts == null || nodeAttempts.isEmpty()) {
                nodes.add(new ActivityFlowNodeView(
                    sequence,
                    activityName,
                    "NOT_STARTED",
                    "-",
                    "-",
                    0,
                    0,
                    0,
                    0,
                    false,
                    Collections.<ActivityTimelineItemView>emptyList()
                ));
            } else {
                nodes.add(toActivityFlowNode(sequence, activityName, nodeAttempts, latestActivityId));
            }
            sequence++;
        }
        for (Map.Entry<String, List<ActivityTimelineItemView>> entry : groupedAttempts.entrySet()) {
            nodes.add(toActivityFlowNode(sequence, entry.getKey(), entry.getValue(), latestActivityId));
            sequence++;
        }
        return nodes;
    }

    private ActivityFlowNodeView toActivityFlowNode(
        int sequence,
        String activityName,
        List<ActivityTimelineItemView> nodeAttempts,
        String latestActivityId
    ) {
        ActivityTimelineItemView latestAttempt = nodeAttempts.get(nodeAttempts.size() - 1);
        int failedAttempts = countAttemptsByStatus(nodeAttempts, "FAILED");
        int successfulAttempts = countAttemptsByStatus(nodeAttempts, "SUCCESSFUL");
        int retryTimes = Math.max(nodeAttempts.size() - 1, 0);
        return new ActivityFlowNodeView(
            sequence,
            activityName,
            latestAttempt.getStatus(),
            latestAttempt.getExecutedNode(),
            latestAttempt.getGmtModifiedDisplay(),
            nodeAttempts.size(),
            failedAttempts,
            retryTimes,
            successfulAttempts,
            latestAttempt.getActivityId().equals(latestActivityId),
            Collections.singletonList(latestAttempt)
        );
    }

    private DefinitionStepLookup findDefinitionSteps(String workflowName) {
        if (isBlank(workflowName)) {
            return new DefinitionStepLookup(Collections.<WorkflowDefinitionStepView>emptyList(), false);
        }
        try {
            return new DefinitionStepLookup(workflowDefinitionQueryService.listActivitySteps(workflowName), false);
        } catch (IllegalArgumentException ex) {
            LOGGER.debug("Workflow definition not found for ops flow overview, workflowName={}", workflowName);
            return new DefinitionStepLookup(Collections.<WorkflowDefinitionStepView>emptyList(), true);
        }
    }

    private static class DefinitionStepLookup {

        private final List<WorkflowDefinitionStepView> steps;
        private final boolean definitionMissing;

        DefinitionStepLookup(List<WorkflowDefinitionStepView> steps, boolean definitionMissing) {
            this.steps = steps;
            this.definitionMissing = definitionMissing;
        }

        List<WorkflowDefinitionStepView> steps() {
            return steps;
        }

        boolean definitionMissing() {
            return definitionMissing;
        }
    }

    private int countAttemptsByStatus(List<ActivityTimelineItemView> attempts, String status) {
        int count = 0;
        for (ActivityTimelineItemView attempt : attempts) {
            if (status.equals(attempt.getStatus())) {
                count++;
            }
        }
        return count;
    }

    private OperationRecordView toOperationRecordView(OperationRecordRow row) {
        OperationMetadata metadata = extractOperationMetadata(row.operationId(), row.input());
        return new OperationRecordView(
            row.operationId(),
            row.operationType(),
            blankToDash(row.status()),
            metadata.operator(),
            metadata.reason(),
            metadata.activityId(),
            blankToDash(row.input()),
            formatTime(row.gmtCreated()),
            formatTime(row.gmtModified())
        );
    }

    private OperationHistoryItemView toOperationHistoryItemView(OperationHistoryRow row) {
        OperationMetadata metadata = extractOperationMetadata(row.operationId(), row.input());
        return new OperationHistoryItemView(
            row.workflowId(),
            row.bizId(),
            row.operationId(),
            row.operationType(),
            blankToDash(row.status()),
            metadata.operator(),
            metadata.reason(),
            metadata.activityId(),
            blankToDash(row.input()),
            formatTime(row.gmtCreated()),
            formatTime(row.gmtModified())
        );
    }

    private String formatTime(LocalDateTime time) {
        if (time == null) {
            return "-";
        }
        return MODIFIED_TIME_FORMATTER.format(time);
    }

    private String buildLatestActivitySummary(String activityName, String activityStatus) {
        if (isBlank(activityName)) {
            return "-";
        }
        if (isBlank(activityStatus)) {
            return activityName;
        }
        return activityName + " [" + activityStatus + "]";
    }

    private String summarizeFailure(String latestFailureOutput) {
        if (isBlank(latestFailureOutput)) {
            return "-";
        }
        String compact = latestFailureOutput.replaceAll("\\s+", " ").trim();
        if (compact.length() <= 120) {
            return compact;
        }
        return compact.substring(0, 117) + "...";
    }

    private AllowedActionsView buildAllowedActions(String workflowStatus, String latestActivityId) {
        boolean canTerminate = "RUNNING".equals(workflowStatus) || "HUMAN_PROCESSING".equals(workflowStatus);
        boolean canRetry = "HUMAN_PROCESSING".equals(workflowStatus) || "TERMINATED".equals(workflowStatus);
        boolean canSkipLatest = "TERMINATED".equals(workflowStatus) && !isBlank(latestActivityId);
        return new AllowedActionsView(canTerminate, canRetry, canSkipLatest);
    }

    private String abbreviate(String value) {
        if (value.length() <= 160) {
            return value;
        }
        return value.substring(0, 157) + "...";
    }

    private OperationMetadata extractOperationMetadata(long operationId, String rawInput) {
        if (isBlank(rawInput)) {
            return OperationMetadata.empty();
        }
        try {
            Map<String, Object> payload = objectMapper.readValue(rawInput, MAP_TYPE);
            return new OperationMetadata(
                toDisplayValue(payload.get("operator")),
                toDisplayValue(payload.get("reason")),
                toDisplayValue(payload.get("activityId"))
            );
        } catch (Exception ex) {
            LOGGER.warn(
                "Failed to parse workflow_operation.input, operationId={}, inputPreview={}",
                operationId,
                abbreviate(rawInput),
                ex
            );
            return OperationMetadata.empty();
        }
    }

    private String toDisplayValue(Object value) {
        if (value == null) {
            return "-";
        }
        String text = String.valueOf(value).trim();
        if (text.isEmpty()) {
            return "-";
        }
        return text;
    }

    private String blankToDash(String value) {
        if (isBlank(value)) {
            return "-";
        }
        return value;
    }

    private Map<String, ActivitySummaryRow> indexActivitySummaryRows(List<ActivitySummaryRow> rows) {
        Map<String, ActivitySummaryRow> indexedRows = new LinkedHashMap<String, ActivitySummaryRow>();
        for (ActivitySummaryRow row : rows) {
            indexedRows.put(row.workflowId(), row);
        }
        return indexedRows;
    }

    private ActivitySummaryRow findLatestActivitySummary(String workflowId) {
        List<ActivitySummaryRow> rows =
            workflowViewRepository.findLatestActivityRows(Collections.singletonList(workflowId));
        if (rows.isEmpty()) {
            return null;
        }
        return rows.get(0);
    }

    private boolean containsIgnoreCase(String value, String keyword) {
        if (isBlank(keyword)) {
            return true;
        }
        if (value == null) {
            return false;
        }
        return value.toLowerCase().contains(keyword.trim().toLowerCase());
    }

    private boolean matchesAnyStatus(String value, String keyword) {
        if (isBlank(keyword)) {
            return true;
        }
        if (value == null) {
            return false;
        }
        for (String expectedStatus : keyword.split(",")) {
            String normalizedStatus = expectedStatus.trim();
            if (!normalizedStatus.isEmpty() && value.equalsIgnoreCase(normalizedStatus)) {
                return true;
            }
        }
        return false;
    }

    private boolean isWithinRange(LocalDateTime value, LocalDateTime from, LocalDateTime to) {
        if (value == null) {
            return false;
        }
        if (from != null && value.isBefore(from)) {
            return false;
        }
        return to == null || !value.isAfter(to);
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private Comparator<WorkflowListRow> workflowListComparator(String order) {
        boolean ascending = isAscendingOrder(order, false);
        return Comparator
            .comparing(WorkflowListRow::gmtModified, timeComparator(ascending))
            .thenComparing(WorkflowListRow::workflowId, textComparator(ascending));
    }

    private Comparator<ActivityTimelineRow> activityTimelineComparator(String order) {
        boolean ascending = isAscendingOrder(order, true);
        return Comparator
            .comparing(ActivityTimelineRow::gmtCreated, timeComparator(ascending))
            .thenComparing(ActivityTimelineRow::gmtModified, timeComparator(ascending))
            .thenComparing(ActivityTimelineRow::activityId, textComparator(ascending));
    }

    private boolean isAscendingOrder(String order, boolean defaultAscending) {
        if (ORDER_ASC.equalsIgnoreCase(trimToEmpty(order))) {
            return true;
        }
        if (ORDER_DESC.equalsIgnoreCase(trimToEmpty(order))) {
            return false;
        }
        return defaultAscending;
    }

    private Comparator<LocalDateTime> timeComparator(boolean ascending) {
        Comparator<LocalDateTime> comparator = ascending ? Comparator.naturalOrder() : Comparator.reverseOrder();
        return Comparator.nullsLast(comparator);
    }

    private Comparator<String> textComparator(boolean ascending) {
        Comparator<String> comparator = ascending ? Comparator.naturalOrder() : Comparator.reverseOrder();
        return Comparator.nullsLast(comparator);
    }

    private String trimToEmpty(String value) {
        return value == null ? "" : value.trim();
    }

    private <T> PageView<T> paginate(List<T> rows, int page, int size) {
        int safeSize = normalizePageSize(size);
        int totalElements = rows.size();
        int totalPages = calculateTotalPages(totalElements, safeSize);
        int safePage = normalizePage(page, totalPages);
        int fromIndex = Math.min((safePage - 1) * safeSize, totalElements);
        int toIndex = Math.min(fromIndex + safeSize, totalElements);
        List<T> pageItems = rows.subList(fromIndex, toIndex);
        return page(pageItems, safePage, safeSize, totalPages, totalElements);
    }

    private <T> PageView<T> page(List<T> pageItems, int safePage, int safeSize, int totalPages, long totalElements) {
        PaginationView pagination = new PaginationView(
            safePage,
            safeSize,
            totalPages,
            totalElements,
            safePage > 1,
            safePage < totalPages,
            safePage > 1 ? safePage - 1 : 1,
            safePage < totalPages ? safePage + 1 : totalPages
        );
        return new PageView<T>(pageItems, pagination);
    }

    private int normalizePageSize(int size) {
        if (size <= 0) {
            return DEFAULT_PAGE_SIZE;
        }
        return Math.min(size, MAX_PAGE_SIZE);
    }

    private int calculateTotalPages(long totalElements, int safeSize) {
        if (totalElements <= 0) {
            return 1;
        }
        long totalPages = (totalElements + safeSize - 1) / safeSize;
        return totalPages > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) totalPages;
    }

    private int normalizePage(int page, int totalPages) {
        if (page <= 0) {
            return 1;
        }
        return Math.min(page, totalPages);
    }

    private static final class OperationMetadata {

        private final String operator;
        private final String reason;
        private final String activityId;

        private OperationMetadata(String operator, String reason, String activityId) {
            this.operator = operator;
            this.reason = reason;
            this.activityId = activityId;
        }

        private static OperationMetadata empty() {
            return new OperationMetadata("-", "-", "-");
        }

        public String operator() {
            return operator;
        }

        public String reason() {
            return reason;
        }

        public String activityId() {
            return activityId;
        }
    }

    public static class OperationHistoryItemView {

        private final String workflowId;
        private final String bizId;
        private final long operationId;
        private final String operationType;
        private final String status;
        private final String operator;
        private final String reason;
        private final String activityId;
        private final String rawInput;
        private final String gmtCreatedDisplay;
        private final String gmtModifiedDisplay;

        public OperationHistoryItemView(
            String workflowId,
            String bizId,
            long operationId,
            String operationType,
            String status,
            String operator,
            String reason,
            String activityId,
            String rawInput,
            String gmtCreatedDisplay,
            String gmtModifiedDisplay
        ) {
            this.workflowId = workflowId;
            this.bizId = bizId;
            this.operationId = operationId;
            this.operationType = operationType;
            this.status = status;
            this.operator = operator;
            this.reason = reason;
            this.activityId = activityId;
            this.rawInput = rawInput;
            this.gmtCreatedDisplay = gmtCreatedDisplay;
            this.gmtModifiedDisplay = gmtModifiedDisplay;
        }

        public String getWorkflowId() {
            return workflowId;
        }

        public String getBizId() {
            return bizId;
        }

        public long getOperationId() {
            return operationId;
        }

        public String getOperationType() {
            return operationType;
        }

        public String getStatus() {
            return status;
        }

        public String getOperator() {
            return operator;
        }

        public String getReason() {
            return reason;
        }

        public String getActivityId() {
            return activityId;
        }

        public String getRawInput() {
            return rawInput;
        }

        public String getGmtCreatedDisplay() {
            return gmtCreatedDisplay;
        }

        public String getGmtModifiedDisplay() {
            return gmtModifiedDisplay;
        }
    }
}
