package com.ywz.workflow.featherflow.ops.repository;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class WorkflowViewRepository {

    private static final String WORKFLOW_PAGE_COLUMNS =
        " w.workflow_id,"
            + " w.biz_id,"
            + " w.biz_key,"
            + " w.workflow_name,"
            + " w.status,"
            + " w.gmt_created,"
            + " w.gmt_modified";

    private static final String LIST_PAGE_SELECT =
        "select"
            + "     w.workflow_id,"
            + "     w.biz_id,"
            + "     w.biz_key,"
            + "     w.workflow_name,"
            + "     w.status as workflow_status,"
            + "     w.gmt_created,"
            + "     w.gmt_modified,"
            + "     null as latest_activity_id,"
            + "     null as latest_activity_name,"
            + "     null as latest_executed_node,"
            + "     null as latest_activity_status,"
            + "     null as latest_failure_output";

    private static final String LIST_DEFAULT_ORDER_CLAUSE =
        " order by w.gmt_modified desc, w.workflow_id desc";

    private static final String LIST_SQL =
        LIST_PAGE_SELECT
            + " from workflow_instance w"
            + LIST_DEFAULT_ORDER_CLAUSE;

    private static final String DETAIL_SQL =
        "select"
            + " w.workflow_id,"
            + " w.biz_id,"
            + " w.biz_key,"
            + " w.workflow_name,"
            + " w.start_node,"
            + " null as latest_activity_id,"
            + " null as latest_executed_node,"
            + " w.status as workflow_status,"
            + " w.input as workflow_input,"
            + " w.gmt_created,"
            + " w.gmt_modified"
            + " from workflow_instance w"
            + " where w.workflow_id = ?";

    private static final String WORKFLOW_EXISTS_SQL =
        "select count(*) from workflow_instance w where w.workflow_id = ?";

    private static final String ACTIVITY_SQL =
        "select"
            + " a.activity_id,"
            + " a.activity_name,"
            + " a.executed_node,"
            + " a.status,"
            + " a.gmt_created,"
            + " a.gmt_modified,"
            + " a.input,"
            + " a.output"
            + " from activity_instance a"
            + " where a.workflow_id = ?"
            + " order by a.gmt_created asc, a.gmt_modified asc, a.activity_id asc";

    private static final String LATEST_ACTIVITY_ID_SQL =
        "select a.activity_id"
            + " from activity_instance a"
            + " where a.workflow_id = ?"
            + " order by a.gmt_created desc, a.activity_id desc"
            + " limit 1";

    private static final String LATEST_ACTIVITY_SUMMARY_SQL =
        "select"
            + " a.workflow_id,"
            + " a.activity_id,"
            + " a.activity_name,"
            + " a.executed_node,"
            + " a.status,"
            + " null as output"
            + " from activity_instance a"
            + " where a.workflow_id = ?"
            + " order by a.gmt_created desc, a.activity_id desc"
            + " limit 1";

    private static final String LATEST_FAILED_ACTIVITY_SUMMARY_SQL =
        "select"
            + " a.workflow_id,"
            + " a.activity_id,"
            + " a.activity_name,"
            + " a.executed_node,"
            + " a.status,"
            + " substring(a.output, 1, 512) as output"
            + " from activity_instance a"
            + " where a.workflow_id = ?"
            + "   and a.status = 'FAILED'"
            + " order by a.gmt_created desc, a.activity_id desc"
            + " limit 1";

    private static final String OPERATION_SQL =
        "select"
            + " o.operation_id,"
            + " o.operation_type,"
            + " o.status,"
            + " o.input,"
            + " o.gmt_created,"
            + " o.gmt_modified"
            + " from workflow_operation o"
            + " where o.workflow_id = ?"
            + " order by o.operation_id desc";

    private static final String OPERATION_HISTORY_SQL =
        "select"
            + " o.operation_id,"
            + " o.workflow_id,"
            + " w.biz_id,"
            + " o.operation_type,"
            + " o.status,"
            + " o.input,"
            + " o.gmt_created,"
            + " o.gmt_modified"
            + " from workflow_operation o"
            + " join workflow_instance w on w.workflow_id = o.workflow_id"
            + " order by o.operation_id desc";

    private final JdbcTemplate jdbcTemplate;

    public WorkflowViewRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public long countWorkflowListRows(
        String workflowId,
        String bizId,
        String bizKey,
        String status,
        String workflowName,
        LocalDateTime createdFrom,
        LocalDateTime createdTo,
        LocalDateTime modifiedFrom,
        LocalDateTime modifiedTo
    ) {
        StringBuilder sql = new StringBuilder("select count(*) from workflow_instance w");
        List<Object> params = new ArrayList<Object>();
        appendWorkflowListFilters(
            sql,
            params,
            workflowId,
            bizId,
            bizKey,
            status,
            workflowName,
            createdFrom,
            createdTo,
            modifiedFrom,
            modifiedTo
        );
        Long total = jdbcTemplate.queryForObject(sql.toString(), Long.class, params.toArray());
        return total == null ? 0L : total;
    }

    public List<WorkflowListRow> findWorkflowPageRows(
        String workflowId,
        String bizId,
        String bizKey,
        String status,
        String workflowName,
        LocalDateTime createdFrom,
        LocalDateTime createdTo,
        LocalDateTime modifiedFrom,
        LocalDateTime modifiedTo,
        int limit,
        int offset,
        String order
    ) {
        String orderClause = workflowListOrderClause(order);
        StringBuilder sql = new StringBuilder(LIST_PAGE_SELECT)
            .append(" from workflow_instance w");
        List<Object> params = new ArrayList<Object>();
        appendWorkflowListFilters(
            sql,
            params,
            workflowId,
            bizId,
            bizKey,
            status,
            workflowName,
            createdFrom,
            createdTo,
            modifiedFrom,
            modifiedTo
        );
        sql.append(orderClause)
            .append(" limit ? offset ?");
        params.add(limit);
        params.add(offset);

        return jdbcTemplate.query(sql.toString(), this::mapWorkflowListRow, params.toArray());
    }

    public List<WorkflowListRow> findWorkflowListRows() {
        return jdbcTemplate.query(
            LIST_SQL,
            this::mapWorkflowListRow
        );
    }

    public Optional<WorkflowDetailRow> findWorkflowDetailRow(String workflowId) {
        List<WorkflowDetailRow> rows = jdbcTemplate.query(
            DETAIL_SQL,
            (rs, rowNum) -> new WorkflowDetailRow(
                rs.getString("workflow_id"),
                rs.getString("biz_id"),
                rs.getString("biz_key"),
                rs.getString("workflow_name"),
                rs.getString("start_node"),
                rs.getString("latest_activity_id"),
                rs.getString("latest_executed_node"),
                rs.getString("workflow_status"),
                rs.getString("workflow_input"),
                rs.getObject("gmt_created", LocalDateTime.class),
                rs.getObject("gmt_modified", LocalDateTime.class)
            ),
            workflowId
        );
        if (rows.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(rows.get(0));
    }

    public boolean workflowExists(String workflowId) {
        Long count = jdbcTemplate.queryForObject(WORKFLOW_EXISTS_SQL, Long.class, workflowId);
        return count != null && count > 0;
    }

    public List<ActivityTimelineRow> findActivityTimelineRows(String workflowId) {
        return jdbcTemplate.query(
            ACTIVITY_SQL,
            (rs, rowNum) -> new ActivityTimelineRow(
                rs.getString("activity_id"),
                rs.getString("activity_name"),
                rs.getString("executed_node"),
                rs.getString("status"),
                rs.getString("input"),
                rs.getString("output"),
                rs.getObject("gmt_created", LocalDateTime.class),
                rs.getObject("gmt_modified", LocalDateTime.class)
            ),
            workflowId
        );
    }

    public Optional<String> findLatestActivityId(String workflowId) {
        List<String> rows = jdbcTemplate.query(
            LATEST_ACTIVITY_ID_SQL,
            (rs, rowNum) -> rs.getString("activity_id"),
            workflowId
        );
        if (rows.isEmpty()) {
            return Optional.empty();
        }
        return Optional.ofNullable(rows.get(0));
    }

    public List<ActivitySummaryRow> findLatestActivityRows(List<String> workflowIds) {
        return findActivitySummaryRows(workflowIds, LATEST_ACTIVITY_SUMMARY_SQL);
    }

    public List<ActivitySummaryRow> findLatestFailedActivityRows(List<String> workflowIds) {
        return findActivitySummaryRows(workflowIds, LATEST_FAILED_ACTIVITY_SUMMARY_SQL);
    }

    public List<OperationRecordRow> findOperationRecordRows(String workflowId) {
        return jdbcTemplate.query(
            OPERATION_SQL,
            (rs, rowNum) -> new OperationRecordRow(
                rs.getLong("operation_id"),
                rs.getString("operation_type"),
                rs.getString("status"),
                rs.getString("input"),
                rs.getObject("gmt_created", LocalDateTime.class),
                rs.getObject("gmt_modified", LocalDateTime.class)
            ),
            workflowId
        );
    }

    public List<OperationHistoryRow> findOperationHistoryRows() {
        return jdbcTemplate.query(
            OPERATION_HISTORY_SQL,
            (rs, rowNum) -> new OperationHistoryRow(
                rs.getLong("operation_id"),
                rs.getString("workflow_id"),
                rs.getString("biz_id"),
                rs.getString("operation_type"),
                rs.getString("status"),
                rs.getString("input"),
                rs.getObject("gmt_created", LocalDateTime.class),
                rs.getObject("gmt_modified", LocalDateTime.class)
            )
        );
    }

    private WorkflowListRow mapWorkflowListRow(java.sql.ResultSet rs, int rowNum) throws java.sql.SQLException {
        return new WorkflowListRow(
            rs.getString("workflow_id"),
            rs.getString("biz_id"),
            rs.getString("biz_key"),
            rs.getString("workflow_name"),
            rs.getString("workflow_status"),
            rs.getObject("gmt_created", LocalDateTime.class),
            rs.getObject("gmt_modified", LocalDateTime.class),
            rs.getString("latest_activity_id"),
            rs.getString("latest_activity_name"),
            rs.getString("latest_executed_node"),
            rs.getString("latest_activity_status"),
            rs.getString("latest_failure_output")
        );
    }

    private List<ActivitySummaryRow> findActivitySummaryRows(List<String> workflowIds, String sql) {
        List<ActivitySummaryRow> rows = new ArrayList<ActivitySummaryRow>();
        if (workflowIds == null || workflowIds.isEmpty()) {
            return rows;
        }
        for (String workflowId : workflowIds) {
            if (isBlank(workflowId)) {
                continue;
            }
            rows.addAll(jdbcTemplate.query(sql, this::mapActivitySummaryRow, workflowId));
        }
        return rows;
    }

    private ActivitySummaryRow mapActivitySummaryRow(java.sql.ResultSet rs, int rowNum) throws java.sql.SQLException {
        return new ActivitySummaryRow(
            rs.getString("workflow_id"),
            rs.getString("activity_id"),
            rs.getString("activity_name"),
            rs.getString("executed_node"),
            rs.getString("status"),
            rs.getString("output")
        );
    }

    private void appendWorkflowListFilters(
        StringBuilder sql,
        List<Object> params,
        String workflowId,
        String bizId,
        String bizKey,
        String status,
        String workflowName,
        LocalDateTime createdFrom,
        LocalDateTime createdTo,
        LocalDateTime modifiedFrom,
        LocalDateTime modifiedTo
    ) {
        List<String> conditions = new ArrayList<String>();
        appendContainsFilter(conditions, params, "w.workflow_id", workflowId);
        appendContainsFilter(conditions, params, "w.biz_id", bizId);
        appendContainsFilter(conditions, params, "w.biz_key", bizKey);
        appendStatusFilter(conditions, params, status);
        appendContainsFilter(conditions, params, "w.workflow_name", workflowName);
        appendRangeFilter(conditions, params, "w.gmt_created", createdFrom, createdTo);
        appendRangeFilter(conditions, params, "w.gmt_modified", modifiedFrom, modifiedTo);
        if (!conditions.isEmpty()) {
            sql.append(" where ");
            for (int i = 0; i < conditions.size(); i++) {
                if (i > 0) {
                    sql.append(" and ");
                }
                sql.append(conditions.get(i));
            }
        }
    }

    private void appendContainsFilter(List<String> conditions, List<Object> params, String column, String value) {
        if (isBlank(value)) {
            return;
        }
        conditions.add("lower(" + column + ") like ? escape '!'");
        params.add("%" + escapeLike(value.trim().toLowerCase(Locale.ROOT)) + "%");
    }

    private void appendStatusFilter(List<String> conditions, List<Object> params, String status) {
        if (isBlank(status)) {
            return;
        }
        List<String> statuses = splitCsv(status);
        if (statuses.isEmpty()) {
            return;
        }
        StringBuilder condition = new StringBuilder("lower(w.status) in (");
        for (int i = 0; i < statuses.size(); i++) {
            if (i > 0) {
                condition.append(", ");
            }
            condition.append("?");
            params.add(statuses.get(i).toLowerCase(Locale.ROOT));
        }
        condition.append(")");
        conditions.add(condition.toString());
    }

    private void appendRangeFilter(
        List<String> conditions,
        List<Object> params,
        String column,
        LocalDateTime from,
        LocalDateTime to
    ) {
        if (from != null) {
            conditions.add(column + " >= ?");
            params.add(from);
        }
        if (to != null) {
            conditions.add(column + " <= ?");
            params.add(to);
        }
    }

    private List<String> splitCsv(String value) {
        List<String> values = new ArrayList<String>();
        for (String part : value.split(",")) {
            String normalized = part.trim();
            if (!normalized.isEmpty()) {
                values.add(normalized);
            }
        }
        return values;
    }

    private String escapeLike(String value) {
        return value
            .replace("!", "!!")
            .replace("%", "!%")
            .replace("_", "!_");
    }

    private String workflowListOrderClause(String order) {
        if ("asc".equalsIgnoreCase(trimToEmpty(order))) {
            return " order by w.gmt_modified asc, w.workflow_id asc";
        }
        return LIST_DEFAULT_ORDER_CLAUSE;
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private String trimToEmpty(String value) {
        return value == null ? "" : value.trim();
    }

    public static final class WorkflowListRow {

        private final String workflowId;
        private final String bizId;
        private final String bizKey;
        private final String workflowName;
        private final String workflowStatus;
        private final LocalDateTime gmtCreated;
        private final LocalDateTime gmtModified;
        private final String latestActivityId;
        private final String latestActivityName;
        private final String latestExecutedNode;
        private final String latestActivityStatus;
        private final String latestFailureOutput;

        public WorkflowListRow(
            String workflowId,
            String bizId,
            String bizKey,
            String workflowName,
            String workflowStatus,
            LocalDateTime gmtCreated,
            LocalDateTime gmtModified,
            String latestActivityId,
            String latestActivityName,
            String latestExecutedNode,
            String latestActivityStatus,
            String latestFailureOutput
        ) {
            this.workflowId = workflowId;
            this.bizId = bizId;
            this.bizKey = bizKey;
            this.workflowName = workflowName;
            this.workflowStatus = workflowStatus;
            this.gmtCreated = gmtCreated;
            this.gmtModified = gmtModified;
            this.latestActivityId = latestActivityId;
            this.latestActivityName = latestActivityName;
            this.latestExecutedNode = latestExecutedNode;
            this.latestActivityStatus = latestActivityStatus;
            this.latestFailureOutput = latestFailureOutput;
        }

        public String workflowId() {
            return workflowId;
        }

        public String bizId() {
            return bizId;
        }

        public String bizKey() {
            return bizKey;
        }

        public String workflowName() {
            return workflowName;
        }

        public String workflowStatus() {
            return workflowStatus;
        }

        public LocalDateTime gmtCreated() {
            return gmtCreated;
        }

        public LocalDateTime gmtModified() {
            return gmtModified;
        }

        public String latestActivityId() {
            return latestActivityId;
        }

        public String latestActivityName() {
            return latestActivityName;
        }

        public String latestExecutedNode() {
            return latestExecutedNode;
        }

        public String latestActivityStatus() {
            return latestActivityStatus;
        }

        public String latestFailureOutput() {
            return latestFailureOutput;
        }
    }

    public static final class WorkflowDetailRow {

        private final String workflowId;
        private final String bizId;
        private final String bizKey;
        private final String workflowName;
        private final String startNode;
        private final String latestActivityId;
        private final String latestExecutedNode;
        private final String workflowStatus;
        private final String workflowInput;
        private final LocalDateTime gmtCreated;
        private final LocalDateTime gmtModified;

        public WorkflowDetailRow(
            String workflowId,
            String bizId,
            String bizKey,
            String workflowName,
            String startNode,
            String latestActivityId,
            String latestExecutedNode,
            String workflowStatus,
            String workflowInput,
            LocalDateTime gmtCreated,
            LocalDateTime gmtModified
        ) {
            this.workflowId = workflowId;
            this.bizId = bizId;
            this.bizKey = bizKey;
            this.workflowName = workflowName;
            this.startNode = startNode;
            this.latestActivityId = latestActivityId;
            this.latestExecutedNode = latestExecutedNode;
            this.workflowStatus = workflowStatus;
            this.workflowInput = workflowInput;
            this.gmtCreated = gmtCreated;
            this.gmtModified = gmtModified;
        }

        public String workflowId() {
            return workflowId;
        }

        public String bizId() {
            return bizId;
        }

        public String bizKey() {
            return bizKey;
        }

        public String startNode() {
            return startNode;
        }

        public String latestActivityId() {
            return latestActivityId;
        }

        public String latestExecutedNode() {
            return latestExecutedNode;
        }

        public String workflowName() {
            return workflowName;
        }

        public String workflowStatus() {
            return workflowStatus;
        }

        public String workflowInput() {
            return workflowInput;
        }

        public LocalDateTime gmtCreated() {
            return gmtCreated;
        }

        public LocalDateTime gmtModified() {
            return gmtModified;
        }
    }

    public static final class ActivitySummaryRow {

        private final String workflowId;
        private final String activityId;
        private final String activityName;
        private final String executedNode;
        private final String status;
        private final String output;

        public ActivitySummaryRow(
            String workflowId,
            String activityId,
            String activityName,
            String executedNode,
            String status,
            String output
        ) {
            this.workflowId = workflowId;
            this.activityId = activityId;
            this.activityName = activityName;
            this.executedNode = executedNode;
            this.status = status;
            this.output = output;
        }

        public String workflowId() {
            return workflowId;
        }

        public String activityId() {
            return activityId;
        }

        public String activityName() {
            return activityName;
        }

        public String executedNode() {
            return executedNode;
        }

        public String status() {
            return status;
        }

        public String output() {
            return output;
        }
    }

    public static final class ActivityTimelineRow {

        private final String activityId;
        private final String activityName;
        private final String executedNode;
        private final String status;
        private final String input;
        private final String output;
        private final LocalDateTime gmtCreated;
        private final LocalDateTime gmtModified;

        public ActivityTimelineRow(
            String activityId,
            String activityName,
            String executedNode,
            String status,
            String input,
            String output,
            LocalDateTime gmtCreated,
            LocalDateTime gmtModified
        ) {
            this.activityId = activityId;
            this.activityName = activityName;
            this.executedNode = executedNode;
            this.status = status;
            this.input = input;
            this.output = output;
            this.gmtCreated = gmtCreated;
            this.gmtModified = gmtModified;
        }

        public String activityId() {
            return activityId;
        }

        public String activityName() {
            return activityName;
        }

        public String executedNode() {
            return executedNode;
        }

        public String status() {
            return status;
        }

        public String input() {
            return input;
        }

        public String output() {
            return output;
        }

        public LocalDateTime gmtCreated() {
            return gmtCreated;
        }

        public LocalDateTime gmtModified() {
            return gmtModified;
        }
    }

    public static final class OperationRecordRow {

        private final long operationId;
        private final String operationType;
        private final String status;
        private final String input;
        private final LocalDateTime gmtCreated;
        private final LocalDateTime gmtModified;

        public OperationRecordRow(
            long operationId,
            String operationType,
            String status,
            String input,
            LocalDateTime gmtCreated,
            LocalDateTime gmtModified
        ) {
            this.operationId = operationId;
            this.operationType = operationType;
            this.status = status;
            this.input = input;
            this.gmtCreated = gmtCreated;
            this.gmtModified = gmtModified;
        }

        public long operationId() {
            return operationId;
        }

        public String operationType() {
            return operationType;
        }

        public String status() {
            return status;
        }

        public String input() {
            return input;
        }

        public LocalDateTime gmtCreated() {
            return gmtCreated;
        }

        public LocalDateTime gmtModified() {
            return gmtModified;
        }
    }

    public static final class OperationHistoryRow {

        private final long operationId;
        private final String workflowId;
        private final String bizId;
        private final String operationType;
        private final String status;
        private final String input;
        private final LocalDateTime gmtCreated;
        private final LocalDateTime gmtModified;

        public OperationHistoryRow(
            long operationId,
            String workflowId,
            String bizId,
            String operationType,
            String status,
            String input,
            LocalDateTime gmtCreated,
            LocalDateTime gmtModified
        ) {
            this.operationId = operationId;
            this.workflowId = workflowId;
            this.bizId = bizId;
            this.operationType = operationType;
            this.status = status;
            this.input = input;
            this.gmtCreated = gmtCreated;
            this.gmtModified = gmtModified;
        }

        public long operationId() {
            return operationId;
        }

        public String workflowId() {
            return workflowId;
        }

        public String bizId() {
            return bizId;
        }

        public String operationType() {
            return operationType;
        }

        public String status() {
            return status;
        }

        public String input() {
            return input;
        }

        public LocalDateTime gmtCreated() {
            return gmtCreated;
        }

        public LocalDateTime gmtModified() {
            return gmtModified;
        }
    }
}
