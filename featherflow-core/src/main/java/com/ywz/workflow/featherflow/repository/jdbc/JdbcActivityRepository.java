package com.ywz.workflow.featherflow.repository.jdbc;

import com.ywz.workflow.featherflow.model.ActivityExecutionStatus;
import com.ywz.workflow.featherflow.model.ActivityInstance;
import com.ywz.workflow.featherflow.repository.ActivityRepository;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

public class JdbcActivityRepository implements ActivityRepository {

    private final JdbcTemplate jdbcTemplate;

    public JdbcActivityRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void saveAll(final List<ActivityInstance> activityInstances) {
        jdbcTemplate.batchUpdate(
            "insert into activity_instance "
                + "(activity_id, workflow_id, activity_name, executed_node, gmt_created, gmt_modified, input, output, status) "
                + "values (?, ?, ?, ?, ?, ?, ?, ?, ?)",
            new BatchPreparedStatementSetter() {
                @Override
                public void setValues(java.sql.PreparedStatement ps, int index) throws SQLException {
                    ActivityInstance activityInstance = activityInstances.get(index);
                    ps.setString(1, activityInstance.getActivityId());
                    ps.setString(2, activityInstance.getWorkflowId());
                    ps.setString(3, activityInstance.getActivityName());
                    ps.setString(4, activityInstance.getExecutedNode());
                    ps.setTimestamp(5, Timestamp.from(activityInstance.getGmtCreated()));
                    ps.setTimestamp(6, Timestamp.from(activityInstance.getGmtModified()));
                    ps.setString(7, activityInstance.getInput());
                    ps.setString(8, activityInstance.getOutput());
                    ps.setString(9, activityInstance.getStatus() == null ? null : activityInstance.getStatus().name());
                }

                @Override
                public int getBatchSize() {
                    return activityInstances.size();
                }
            }
        );
    }

    @Override
    public void saveAttempt(
        String activityId,
        String workflowId,
        String activityName,
        String executedNode,
        String input,
        String output,
        ActivityExecutionStatus status,
        Instant modifiedAt
    ) {
        jdbcTemplate.update(
            "insert into activity_instance "
                + "(activity_id, workflow_id, activity_name, executed_node, gmt_created, gmt_modified, input, output, status) "
                + "values (?, ?, ?, ?, ?, ?, ?, ?, ?)",
            activityId,
            workflowId,
            activityName,
            executedNode,
            Timestamp.from(modifiedAt),
            Timestamp.from(modifiedAt),
            input,
            output,
            status == null ? null : status.name()
        );
    }

    @Override
    public List<ActivityInstance> findByWorkflowId(String workflowId) {
        return jdbcTemplate.query(
            "select activity_id, workflow_id, activity_name, executed_node, gmt_created, gmt_modified, input, output, status "
                + "from activity_instance where workflow_id = ? order by gmt_created asc, activity_id asc",
            new ActivityInstanceRowMapper(),
            workflowId
        );
    }

    @Override
    public ActivityInstance findLatestByWorkflowIdAndActivityName(String workflowId, String activityName) {
        List<ActivityInstance> results = jdbcTemplate.query(
            "select activity_id, workflow_id, activity_name, executed_node, gmt_created, gmt_modified, input, output, status "
                + "from activity_instance where workflow_id = ? and activity_name = ? "
                + "order by gmt_created desc, activity_id desc",
            new ActivityInstanceRowMapper(),
            workflowId,
            activityName
        );
        return results.isEmpty() ? null : results.get(0);
    }

    @Override
    public ActivityInstance findByActivityId(String activityId) {
        List<ActivityInstance> results = jdbcTemplate.query(
            "select activity_id, workflow_id, activity_name, executed_node, gmt_created, gmt_modified, input, output, status "
                + "from activity_instance where activity_id = ?",
            new ActivityInstanceRowMapper(),
            activityId
        );
        return results.isEmpty() ? null : results.get(0);
    }

    @Override
    public long countByWorkflowIdAndActivityNameAndStatus(String workflowId, String activityName, ActivityExecutionStatus status) {
        Long count = jdbcTemplate.queryForObject(
            "select count(*) from activity_instance where workflow_id = ? and activity_name = ? and status = ?",
            Long.class,
            workflowId,
            activityName,
            status.name()
        );
        return count == null ? 0L : count.longValue();
    }

    private static final class ActivityInstanceRowMapper implements RowMapper<ActivityInstance> {
        @Override
        public ActivityInstance mapRow(ResultSet rs, int rowNum) throws SQLException {
            String status = rs.getString("status");
            return new ActivityInstance(
                rs.getString("activity_id"),
                rs.getString("workflow_id"),
                rs.getString("activity_name"),
                rs.getString("executed_node"),
                rs.getTimestamp("gmt_created").toInstant(),
                rs.getTimestamp("gmt_modified").toInstant(),
                rs.getString("input"),
                rs.getString("output"),
                status == null ? null : ActivityExecutionStatus.valueOf(status)
            );
        }
    }
}
