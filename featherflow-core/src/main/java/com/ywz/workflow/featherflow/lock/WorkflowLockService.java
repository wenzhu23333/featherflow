package com.ywz.workflow.featherflow.lock;

/**
 * Guards one workflow activity from concurrent execution across threads or nodes.
 */
public interface WorkflowLockService {

    boolean tryLock(String key);

    void unlock(String key);
}
