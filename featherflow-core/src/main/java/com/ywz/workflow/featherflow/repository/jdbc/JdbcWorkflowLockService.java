package com.ywz.workflow.featherflow.repository.jdbc;

import com.ywz.workflow.featherflow.lock.WorkflowLockService;
import java.sql.Timestamp;
import java.time.Instant;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import com.ywz.workflow.featherflow.support.WorkflowNodeIdentity;

public class JdbcWorkflowLockService implements WorkflowLockService {

    private final JdbcTemplate jdbcTemplate;
    private final String instanceId;

    public JdbcWorkflowLockService(JdbcTemplate jdbcTemplate) {
        this(jdbcTemplate, null);
    }

    public JdbcWorkflowLockService(JdbcTemplate jdbcTemplate, String instanceId) {
        this.jdbcTemplate = jdbcTemplate;
        this.instanceId = WorkflowNodeIdentity.normalizeInstanceId(instanceId);
    }

    @Override
    public boolean tryLock(String key) {
        try {
            Instant now = Instant.now();
            return jdbcTemplate.update(
                "insert into workflow_lock (lock_key, owner, gmt_created, gmt_modified) values (?, ?, ?, ?)",
                key,
                currentOwner(),
                Timestamp.from(now),
                Timestamp.from(now)
            ) == 1;
        } catch (DuplicateKeyException duplicateKeyException) {
            return false;
        }
    }

    @Override
    public void unlock(String key) {
        jdbcTemplate.update(
            "delete from workflow_lock where lock_key = ? and owner = ?",
            key,
            currentOwner()
        );
    }

    private String currentOwner() {
        return instanceId + ":" + Thread.currentThread().getId();
    }
}
