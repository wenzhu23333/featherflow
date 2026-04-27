package com.ywz.workflow.featherflow.repository.jdbc;

import com.ywz.workflow.featherflow.model.OperationStatus;
import com.ywz.workflow.featherflow.model.OperationType;
import com.ywz.workflow.featherflow.model.WorkflowOperation;
import com.ywz.workflow.featherflow.repository.WorkflowOperationRepository;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;

public class JdbcWorkflowOperationRepository implements WorkflowOperationRepository {

    private final JdbcTemplate jdbcTemplate;

    public JdbcWorkflowOperationRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void savePendingOperation(WorkflowOperation workflowOperation) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(
                "insert into workflow_operation (workflow_id, operation_type, input, status, gmt_created, gmt_modified) values (?, ?, ?, ?, ?, ?)",
                Statement.RETURN_GENERATED_KEYS
            );
            ps.setString(1, workflowOperation.getWorkflowId());
            ps.setString(2, workflowOperation.getOperationType().name());
            ps.setString(3, workflowOperation.getInput());
            ps.setString(4, workflowOperation.getStatus() == null ? OperationStatus.PENDING.name() : workflowOperation.getStatus().name());
            ps.setTimestamp(5, Timestamp.from(workflowOperation.getGmtCreated()));
            ps.setTimestamp(6, Timestamp.from(workflowOperation.getGmtModified()));
            return ps;
        }, keyHolder);
        Number key = keyHolder.getKey();
        if (key != null) {
            workflowOperation.setOperationId(Long.valueOf(key.longValue()));
        }
    }

    @Override
    public List<WorkflowOperation> findDuePendingOperations(Instant now) {
        return jdbcTemplate.query(
            "select operation_id, workflow_id, operation_type, input, status, gmt_created, gmt_modified from workflow_operation where status = ? and gmt_modified <= ? order by operation_id",
            new WorkflowOperationRowMapper(),
            OperationStatus.PENDING.name(),
            Timestamp.from(now)
        );
    }

    @Override
    public boolean claimPendingOperation(Long operationId, String claimedInput, Instant modifiedAt) {
        return jdbcTemplate.update(
            "update workflow_operation set status = ?, input = ?, gmt_modified = ? where operation_id = ? and status = ?",
            OperationStatus.PROCESSING.name(),
            claimedInput,
            Timestamp.from(modifiedAt),
            operationId,
            OperationStatus.PENDING.name()
        ) == 1;
    }

    @Override
    public List<WorkflowOperation> findPendingByWorkflowId(String workflowId) {
        return jdbcTemplate.query(
            "select operation_id, workflow_id, operation_type, input, status, gmt_created, gmt_modified from workflow_operation where status = ? and workflow_id = ? order by operation_id",
            new WorkflowOperationRowMapper(),
            OperationStatus.PENDING.name(),
            workflowId
        );
    }

    @Override
    public List<WorkflowOperation> findAll() {
        return jdbcTemplate.query(
            "select operation_id, workflow_id, operation_type, input, status, gmt_created, gmt_modified from workflow_operation order by operation_id",
            new WorkflowOperationRowMapper()
        );
    }

    @Override
    public void markSuccessful(Long operationId, Instant modifiedAt) {
        jdbcTemplate.update(
            "update workflow_operation set status = ?, gmt_modified = ? where operation_id = ? and status = ?",
            OperationStatus.SUCCESSFUL.name(),
            Timestamp.from(modifiedAt),
            operationId,
            OperationStatus.PROCESSING.name()
        );
    }

    @Override
    public void markFailed(Long operationId, Instant modifiedAt) {
        jdbcTemplate.update(
            "update workflow_operation set status = ?, gmt_modified = ? where operation_id = ? and status = ?",
            OperationStatus.FAILED.name(),
            Timestamp.from(modifiedAt),
            operationId,
            OperationStatus.PROCESSING.name()
        );
    }

    private static final class WorkflowOperationRowMapper implements RowMapper<WorkflowOperation> {
        @Override
        public WorkflowOperation mapRow(ResultSet rs, int rowNum) throws SQLException {
            String status = rs.getString("status");
            return new WorkflowOperation(
                Long.valueOf(rs.getLong("operation_id")),
                rs.getString("workflow_id"),
                OperationType.valueOf(rs.getString("operation_type")),
                rs.getString("input"),
                status == null ? null : OperationStatus.valueOf(status),
                rs.getTimestamp("gmt_created").toInstant(),
                rs.getTimestamp("gmt_modified").toInstant()
            );
        }
    }
}
