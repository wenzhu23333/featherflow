package com.ywz.workflow.featherflow.repository.jdbc;

import com.ywz.workflow.featherflow.model.WorkflowInstance;
import com.ywz.workflow.featherflow.model.WorkflowStatus;
import com.ywz.workflow.featherflow.repository.WorkflowRepository;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

public class JdbcWorkflowRepository implements WorkflowRepository {

    private final JdbcTemplate jdbcTemplate;

    public JdbcWorkflowRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void save(WorkflowInstance workflowInstance) {
        jdbcTemplate.update(
            "insert into workflow_instance (workflow_id, biz_id, workflow_name, start_node, gmt_created, gmt_modified, input, status) values (?, ?, ?, ?, ?, ?, ?, ?)",
            workflowInstance.getWorkflowId(),
            workflowInstance.getBizId(),
            workflowInstance.getWorkflowName(),
            workflowInstance.getStartNode(),
            Timestamp.from(workflowInstance.getGmtCreated()),
            Timestamp.from(workflowInstance.getGmtModified()),
            workflowInstance.getInput(),
            workflowInstance.getStatus().name()
        );
    }

    @Override
    public void update(WorkflowInstance workflowInstance) {
        jdbcTemplate.update(
            "update workflow_instance set biz_id = ?, workflow_name = ?, start_node = ?, gmt_modified = ?, input = ?, status = ? where workflow_id = ?",
            workflowInstance.getBizId(),
            workflowInstance.getWorkflowName(),
            workflowInstance.getStartNode(),
            Timestamp.from(workflowInstance.getGmtModified()),
            workflowInstance.getInput(),
            workflowInstance.getStatus().name(),
            workflowInstance.getWorkflowId()
        );
    }

    @Override
    public WorkflowInstance find(String workflowId) {
        List<WorkflowInstance> results = jdbcTemplate.query(
            "select workflow_id, biz_id, workflow_name, start_node, gmt_created, gmt_modified, input, status from workflow_instance where workflow_id = ?",
            new WorkflowInstanceRowMapper(),
            workflowId
        );
        return results.isEmpty() ? null : results.get(0);
    }

    @Override
    public WorkflowInstance findRequired(String workflowId) {
        WorkflowInstance workflowInstance = find(workflowId);
        if (workflowInstance == null) {
            throw new IllegalArgumentException("Workflow not found: " + workflowId);
        }
        return workflowInstance;
    }

    @Override
    public void updateStatus(String workflowId, WorkflowStatus status, Instant modifiedAt) {
        jdbcTemplate.update(
            "update workflow_instance set status = ?, gmt_modified = ? where workflow_id = ?",
            status.name(),
            Timestamp.from(modifiedAt),
            workflowId
        );
    }

    private static final class WorkflowInstanceRowMapper implements RowMapper<WorkflowInstance> {
        @Override
        public WorkflowInstance mapRow(ResultSet rs, int rowNum) throws SQLException {
            return new WorkflowInstance(
                rs.getString("workflow_id"),
                rs.getString("biz_id"),
                rs.getString("workflow_name"),
                rs.getString("start_node"),
                rs.getTimestamp("gmt_created").toInstant(),
                rs.getTimestamp("gmt_modified").toInstant(),
                rs.getString("input"),
                WorkflowStatus.fromDatabaseValue(rs.getString("status"))
            );
        }
    }
}
