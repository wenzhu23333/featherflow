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
            "insert into activity_instance (activity_id, workflow_id, activity_name, gmt_created, gmt_modified, input, output, status) values (?, ?, ?, ?, ?, ?, ?, ?)",
            new BatchPreparedStatementSetter() {
                @Override
                public void setValues(java.sql.PreparedStatement ps, int index) throws SQLException {
                    ActivityInstance activityInstance = activityInstances.get(index);
                    ps.setString(1, activityInstance.getActivityId());
                    ps.setString(2, activityInstance.getWorkflowId());
                    ps.setString(3, activityInstance.getActivityName());
                    ps.setTimestamp(4, Timestamp.from(activityInstance.getGmtCreated()));
                    ps.setTimestamp(5, Timestamp.from(activityInstance.getGmtModified()));
                    ps.setString(6, activityInstance.getInput());
                    ps.setString(7, activityInstance.getOutput());
                    ps.setString(8, activityInstance.getStatus() == null ? null : activityInstance.getStatus().name());
                }

                @Override
                public int getBatchSize() {
                    return activityInstances.size();
                }
            }
        );
    }

    @Override
    public void saveOrUpdateResult(
        String activityId,
        String workflowId,
        String activityName,
        String input,
        String output,
        ActivityExecutionStatus status,
        Instant modifiedAt
    ) {
        ActivityInstance existing = findByActivityId(activityId);
        if (existing == null) {
            jdbcTemplate.update(
                "insert into activity_instance (activity_id, workflow_id, activity_name, gmt_created, gmt_modified, input, output, status) values (?, ?, ?, ?, ?, ?, ?, ?)",
                activityId,
                workflowId,
                activityName,
                Timestamp.from(modifiedAt),
                Timestamp.from(modifiedAt),
                input,
                output,
                status == null ? null : status.name()
            );
            return;
        }
        updateResult(activityId, input, output, status, modifiedAt);
    }

    @Override
    public List<ActivityInstance> findByWorkflowId(String workflowId) {
        return jdbcTemplate.query(
            "select activity_id, workflow_id, activity_name, gmt_created, gmt_modified, input, output, status from activity_instance where workflow_id = ? order by activity_id",
            new ActivityInstanceRowMapper(),
            workflowId
        );
    }

    @Override
    public ActivityInstance findByWorkflowIdAndActivityName(String workflowId, String activityName) {
        List<ActivityInstance> results = jdbcTemplate.query(
            "select activity_id, workflow_id, activity_name, gmt_created, gmt_modified, input, output, status from activity_instance where workflow_id = ? and activity_name = ?",
            new ActivityInstanceRowMapper(),
            workflowId,
            activityName
        );
        return results.isEmpty() ? null : results.get(0);
    }

    @Override
    public ActivityInstance findByActivityId(String activityId) {
        List<ActivityInstance> results = jdbcTemplate.query(
            "select activity_id, workflow_id, activity_name, gmt_created, gmt_modified, input, output, status from activity_instance where activity_id = ?",
            new ActivityInstanceRowMapper(),
            activityId
        );
        return results.isEmpty() ? null : results.get(0);
    }

    @Override
    public void update(ActivityInstance activityInstance) {
        jdbcTemplate.update(
            "update activity_instance set gmt_modified = ?, input = ?, output = ?, status = ? where activity_id = ?",
            Timestamp.from(activityInstance.getGmtModified()),
            activityInstance.getInput(),
            activityInstance.getOutput(),
            activityInstance.getStatus() == null ? null : activityInstance.getStatus().name(),
            activityInstance.getActivityId()
        );
    }

    @Override
    public void markSuccessful(String workflowId, String activityName, String output, Instant modifiedAt) {
        jdbcTemplate.update(
            "update activity_instance set output = ?, status = ?, gmt_modified = ? where workflow_id = ? and activity_name = ?",
            output,
            ActivityExecutionStatus.SUCCESSFUL.name(),
            Timestamp.from(modifiedAt),
            workflowId,
            activityName
        );
    }

    @Override
    public void updateResult(String activityId, String input, String output, ActivityExecutionStatus status, Instant modifiedAt) {
        jdbcTemplate.update(
            "update activity_instance set input = ?, output = ?, status = ?, gmt_modified = ? where activity_id = ?",
            input,
            output,
            status == null ? null : status.name(),
            Timestamp.from(modifiedAt),
            activityId
        );
    }

    private static final class ActivityInstanceRowMapper implements RowMapper<ActivityInstance> {
        @Override
        public ActivityInstance mapRow(ResultSet rs, int rowNum) throws SQLException {
            String status = rs.getString("status");
            return new ActivityInstance(
                rs.getString("activity_id"),
                rs.getString("workflow_id"),
                rs.getString("activity_name"),
                rs.getTimestamp("gmt_created").toInstant(),
                rs.getTimestamp("gmt_modified").toInstant(),
                rs.getString("input"),
                rs.getString("output"),
                status == null ? null : ActivityExecutionStatus.valueOf(status)
            );
        }
    }
}
