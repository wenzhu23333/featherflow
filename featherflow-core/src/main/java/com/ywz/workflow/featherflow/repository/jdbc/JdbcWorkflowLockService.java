package com.ywz.workflow.featherflow.repository.jdbc;

import com.ywz.workflow.featherflow.lock.WorkflowLockService;
import java.lang.management.ManagementFactory;
import java.net.InetAddress;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;

public class JdbcWorkflowLockService implements WorkflowLockService {

    private final JdbcTemplate jdbcTemplate;
    private final String instanceId;

    public JdbcWorkflowLockService(JdbcTemplate jdbcTemplate) {
        this(jdbcTemplate, null);
    }

    public JdbcWorkflowLockService(JdbcTemplate jdbcTemplate, String instanceId) {
        this.jdbcTemplate = jdbcTemplate;
        this.instanceId = normalizeInstanceId(instanceId);
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

    private static String normalizeInstanceId(String instanceId) {
        if (instanceId != null && !instanceId.trim().isEmpty()) {
            return instanceId.trim();
        }
        return sanitizeComponent(resolveHostAddress())
            + ":"
            + sanitizeComponent(resolveHostName())
            + ":"
            + sanitizeComponent(resolveProcessId())
            + ":"
            + shortRandomId();
    }

    private static String resolveHostAddress() {
        try {
            String hostAddress = InetAddress.getLocalHost().getHostAddress();
            if (hostAddress != null && !hostAddress.trim().isEmpty()) {
                return hostAddress;
            }
        } catch (Exception ignored) {
            // Fallback to a generic marker when the local IP cannot be resolved.
        }
        return "unknown-host";
    }

    private static String resolveHostName() {
        try {
            String hostName = InetAddress.getLocalHost().getHostName();
            if (hostName != null && !hostName.trim().isEmpty()) {
                return hostName;
            }
        } catch (Exception ignored) {
            // Fallback to a generic marker when the local hostname cannot be resolved.
        }
        return "unknown-hostname";
    }

    private static String resolveProcessId() {
        String runtimeName = ManagementFactory.getRuntimeMXBean().getName();
        int splitIndex = runtimeName.indexOf('@');
        if (splitIndex > 0) {
            return runtimeName.substring(0, splitIndex);
        }
        return "unknown-pid";
    }

    private static String shortRandomId() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    }

    private static String sanitizeComponent(String value) {
        return value.replace(':', '_').replace(' ', '_');
    }
}
