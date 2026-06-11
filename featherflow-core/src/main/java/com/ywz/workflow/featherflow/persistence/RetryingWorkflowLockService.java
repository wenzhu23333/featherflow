package com.ywz.workflow.featherflow.persistence;

import com.ywz.workflow.featherflow.lock.WorkflowLockService;
import java.time.Instant;

/**
 * Retries JDBC-backed lock writes when the database is briefly unavailable.
 */
public class RetryingWorkflowLockService implements WorkflowLockService {

    private final WorkflowLockService delegate;
    private final PersistenceWriteRetrier retrier;

    public RetryingWorkflowLockService(WorkflowLockService delegate, PersistenceWriteRetrier retrier) {
        this.delegate = delegate;
        this.retrier = retrier;
    }

    @Override
    public boolean tryLock(String key) {
        return retrier.call("workflowLockService.tryLock", () -> Boolean.valueOf(delegate.tryLock(key))).booleanValue();
    }

    @Override
    public void unlock(String key) {
        retrier.run("workflowLockService.unlock", () -> delegate.unlock(key));
    }

    @Override
    public int cleanExpiredLocks(Instant modifiedBefore) {
        return retrier.call(
            "workflowLockService.cleanExpiredLocks",
            () -> Integer.valueOf(delegate.cleanExpiredLocks(modifiedBefore))
        ).intValue();
    }
}
