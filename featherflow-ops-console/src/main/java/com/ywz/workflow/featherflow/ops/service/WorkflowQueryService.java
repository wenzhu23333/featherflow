package com.ywz.workflow.featherflow.ops.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ywz.workflow.featherflow.ops.repository.WorkflowViewRepository;
import com.ywz.workflow.featherflow.ops.repository.WorkflowViewRepository.ActivityTimelineRow;
import com.ywz.workflow.featherflow.ops.repository.WorkflowViewRepository.OperationHistoryRow;
import com.ywz.workflow.featherflow.ops.repository.WorkflowViewRepository.OperationRecordRow;
import com.ywz.workflow.featherflow.ops.repository.WorkflowViewRepository.WorkflowDetailRow;
import com.ywz.workflow.featherflow.ops.repository.WorkflowViewRepository.WorkflowListRow;
import com.ywz.workflow.featherflow.ops.view.ActivityTimelineItemView;
import com.ywz.workflow.featherflow.ops.view.AllowedActionsView;
import com.ywz.workflow.featherflow.ops.view.OperationRecordView;
import com.ywz.workflow.featherflow.ops.view.PageView;
import com.ywz.workflow.featherflow.ops.view.PaginationView;
import com.ywz.workflow.featherflow.ops.view.WorkflowDetailView;
import com.ywz.workflow.featherflow.ops.view.WorkflowListItemView;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.Comparator;
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

    private final WorkflowViewRepository workflowViewRepository;
    private final ObjectMapper objectMapper;

    public WorkflowQueryService(WorkflowViewRepository workflowViewRepository, ObjectMapper objectMapper) {
        this.workflowViewRepository = workflowViewRepository;
        this.objectMapper = objectMapper;
    }

    public List<WorkflowListItemView> listWorkflows() {
        return listWorkflowPage(WorkflowListFilter.empty(), 1, 10).getItems();
    }

    public List<WorkflowListItemView> listWorkflows(WorkflowListFilter filter) {
        return listWorkflowPage(filter, 1, Integer.MAX_VALUE).getItems();
    }

    public PageView<WorkflowListItemView> listWorkflowPage(WorkflowListFilter filter, int page, int size) {
        return listWorkflowPage(filter, page, size, ORDER_DESC);
    }

    public PageView<WorkflowListItemView> listWorkflowPage(WorkflowListFilter filter, int page, int size, String order) {
        return workflowViewRepository.findWorkflowListRows().stream()
            .filter(row -> containsIgnoreCase(row.workflowId(), filter.workflowId()))
            .filter(row -> containsIgnoreCase(row.bizId(), filter.bizId()))
            .filter(row -> matchesAnyStatus(row.workflowStatus(), filter.status()))
            .filter(row -> isWithinRange(row.gmtCreated(), filter.createdFrom(), filter.createdTo()))
            .filter(row -> isWithinRange(row.gmtModified(), filter.modifiedFrom(), filter.modifiedTo()))
            .sorted(workflowListComparator(order))
            .map(this::toListItemView)
            .filter(view -> containsIgnoreCase(view.getWorkflowName(), filter.workflowName()))
            .collect(Collectors.collectingAndThen(Collectors.toList(), rows -> paginate(rows, page, size)));
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
        List<ActivityTimelineItemView> activities = workflowViewRepository.findActivityTimelineRows(workflowId).stream()
            .sorted(activityTimelineComparator(activityOrder))
            .map(this::toActivityTimelineItemView)
            .collect(Collectors.toList());
        PageView<ActivityTimelineItemView> activityPageView = paginate(activities, activityPage, activitySize);
        List<OperationRecordView> operations = workflowViewRepository.findOperationRecordRows(workflowId).stream()
            .map(this::toOperationRecordView)
            .collect(Collectors.toList());

        WorkflowDetailRow detailRow = row.get();
        String latestActivityId = workflowViewRepository.findLatestActivityId(workflowId).orElse(null);
        return Optional.of(
            new WorkflowDetailView(
                detailRow.workflowId(),
                detailRow.bizId(),
                blankToDash(detailRow.workflowName()),
                blankToDash(detailRow.startNode()),
                detailRow.workflowStatus(),
                blankToDash(detailRow.workflowInput()),
                formatTime(detailRow.gmtCreated()),
                formatTime(detailRow.gmtModified()),
                activityPageView,
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
        String latestActivityId = workflowViewRepository.findLatestActivityId(workflowId).orElse(null);
        return Optional.of(
            new WorkflowDetailView(
                detailRow.workflowId(),
                detailRow.bizId(),
                blankToDash(detailRow.workflowName()),
                blankToDash(detailRow.startNode()),
                detailRow.workflowStatus(),
                blankToDash(detailRow.workflowInput()),
                formatTime(detailRow.gmtCreated()),
                formatTime(detailRow.gmtModified()),
                paginate(Collections.<ActivityTimelineItemView>emptyList(), 1, 5),
                Collections.<OperationRecordView>emptyList(),
                latestActivityId,
                buildAllowedActions(detailRow.workflowStatus(), latestActivityId)
            )
        );
    }

    public Optional<List<ActivityTimelineItemView>> getWorkflowTimeline(String workflowId) {
        return getWorkflowTimeline(workflowId, 1, 5).map(PageView::getItems);
    }

    public Optional<PageView<ActivityTimelineItemView>> getWorkflowTimeline(String workflowId, int activityPage, int activitySize) {
        return getWorkflowTimeline(workflowId, activityPage, activitySize, ORDER_ASC);
    }

    public Optional<PageView<ActivityTimelineItemView>> getWorkflowTimeline(
        String workflowId,
        int activityPage,
        int activitySize,
        String activityOrder
    ) {
        if (!workflowViewRepository.findWorkflowDetailRow(workflowId).isPresent()) {
            return Optional.empty();
        }
        List<ActivityTimelineItemView> activities = workflowViewRepository.findActivityTimelineRows(workflowId).stream()
            .sorted(activityTimelineComparator(activityOrder))
            .map(this::toActivityTimelineItemView)
            .collect(Collectors.toList());
        return Optional.of(paginate(activities, activityPage, activitySize));
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
        return new WorkflowListItemView(
            row.workflowId(),
            row.bizId(),
            blankToDash(row.workflowName()),
            row.workflowStatus(),
            row.latestActivityId(),
            buildLatestActivitySummary(row.latestActivityName(), row.latestActivityStatus()),
            summarizeFailure(row.latestFailureOutput()),
            formatTime(row.gmtModified()),
            buildAllowedActions(row.workflowStatus(), row.latestActivityId())
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
        int safeSize = size <= 0 ? 20 : size;
        int totalElements = rows.size();
        int totalPages = Math.max(1, (int) Math.ceil(totalElements / (double) safeSize));
        int safePage = page <= 0 ? 1 : Math.min(page, totalPages);
        int fromIndex = Math.min((safePage - 1) * safeSize, totalElements);
        int toIndex = Math.min(fromIndex + safeSize, totalElements);
        List<T> pageItems = rows.subList(fromIndex, toIndex);
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
