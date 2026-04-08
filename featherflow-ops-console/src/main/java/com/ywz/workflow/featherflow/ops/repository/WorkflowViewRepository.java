package com.ywz.workflow.featherflow.ops.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class WorkflowViewRepository {

    private static final String LIST_SQL =
        "with latest_activity as ("
            + " select"
            + "     a.workflow_id,"
            + "     a.activity_id,"
            + "     a.activity_name,"
            + "     a.status,"
            + "     row_number() over ("
            + "         partition by a.workflow_id"
            + "         order by a.gmt_created desc, a.gmt_modified desc, a.activity_id desc"
            + "     ) as rn"
            + " from activity_instance a"
            + "),"
            + " latest_failure as ("
            + " select"
            + "     a.workflow_id,"
            + "     a.output,"
            + "     row_number() over ("
            + "         partition by a.workflow_id"
            + "         order by a.gmt_created desc, a.gmt_modified desc, a.activity_id desc"
            + "     ) as rn"
            + " from activity_instance a"
            + " where a.status = 'FAILED'"
            + ")"
            + " select"
            + "     w.workflow_id,"
            + "     w.biz_id,"
            + "     w.workflow_name,"
            + "     w.status as workflow_status,"
            + "     w.gmt_created,"
            + "     w.gmt_modified,"
            + "     la.activity_id as latest_activity_id,"
            + "     la.activity_name as latest_activity_name,"
            + "     la.status as latest_activity_status,"
            + "     lf.output as latest_failure_output"
            + " from workflow_instance w"
            + " left join latest_activity la on la.workflow_id = w.workflow_id and la.rn = 1"
            + " left join latest_failure lf on lf.workflow_id = w.workflow_id and lf.rn = 1"
            + " order by w.gmt_modified desc, w.workflow_id desc";

    private static final String DETAIL_SQL =
        "select"
            + " w.workflow_id,"
            + " w.biz_id,"
            + " w.workflow_name,"
            + " w.start_node,"
            + " w.status as workflow_status,"
            + " w.input as workflow_input,"
            + " w.gmt_created,"
            + " w.gmt_modified"
            + " from workflow_instance w"
            + " where w.workflow_id = ?";

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
            + " order by a.gmt_created desc, a.gmt_modified desc, a.activity_id desc"
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

    public List<WorkflowListRow> findWorkflowListRows() {
        return jdbcTemplate.query(
            LIST_SQL,
            (rs, rowNum) -> new WorkflowListRow(
                rs.getString("workflow_id"),
                rs.getString("biz_id"),
                rs.getString("workflow_name"),
                rs.getString("workflow_status"),
                rs.getObject("gmt_created", LocalDateTime.class),
                rs.getObject("gmt_modified", LocalDateTime.class),
                rs.getString("latest_activity_id"),
                rs.getString("latest_activity_name"),
                rs.getString("latest_activity_status"),
                rs.getString("latest_failure_output")
            )
        );
    }

    public Optional<WorkflowDetailRow> findWorkflowDetailRow(String workflowId) {
        List<WorkflowDetailRow> rows = jdbcTemplate.query(
            DETAIL_SQL,
            (rs, rowNum) -> new WorkflowDetailRow(
                rs.getString("workflow_id"),
                rs.getString("biz_id"),
                rs.getString("workflow_name"),
                rs.getString("start_node"),
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

    public static final class WorkflowListRow {

        private final String workflowId;
        private final String bizId;
        private final String workflowName;
        private final String workflowStatus;
        private final LocalDateTime gmtCreated;
        private final LocalDateTime gmtModified;
        private final String latestActivityId;
        private final String latestActivityName;
        private final String latestActivityStatus;
        private final String latestFailureOutput;

        public WorkflowListRow(
            String workflowId,
            String bizId,
            String workflowName,
            String workflowStatus,
            LocalDateTime gmtCreated,
            LocalDateTime gmtModified,
            String latestActivityId,
            String latestActivityName,
            String latestActivityStatus,
            String latestFailureOutput
        ) {
            this.workflowId = workflowId;
            this.bizId = bizId;
            this.workflowName = workflowName;
            this.workflowStatus = workflowStatus;
            this.gmtCreated = gmtCreated;
            this.gmtModified = gmtModified;
            this.latestActivityId = latestActivityId;
            this.latestActivityName = latestActivityName;
            this.latestActivityStatus = latestActivityStatus;
            this.latestFailureOutput = latestFailureOutput;
        }

        public String workflowId() {
            return workflowId;
        }

        public String bizId() {
            return bizId;
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
        private final String workflowName;
        private final String startNode;
        private final String workflowStatus;
        private final String workflowInput;
        private final LocalDateTime gmtCreated;
        private final LocalDateTime gmtModified;

        public WorkflowDetailRow(
            String workflowId,
            String bizId,
            String workflowName,
            String startNode,
            String workflowStatus,
            String workflowInput,
            LocalDateTime gmtCreated,
            LocalDateTime gmtModified
        ) {
            this.workflowId = workflowId;
            this.bizId = bizId;
            this.workflowName = workflowName;
            this.startNode = startNode;
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

        public String startNode() {
            return startNode;
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
