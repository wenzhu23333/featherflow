package com.ywz.workflow.featherflow.lock;

import java.time.Instant;

/**
 * Guards one workflow activity from concurrent execution across threads or nodes.
 */
public interface WorkflowLockService {

    boolean tryLock(String key);

    void unlock(String key);

    /**
     * Remove stale locks left by a crashed process before startup recovery resubmits RUNNING workflows.
     */
    default int cleanExpiredLocks(Instant modifiedBefore) {
        return 0;
    }
}
