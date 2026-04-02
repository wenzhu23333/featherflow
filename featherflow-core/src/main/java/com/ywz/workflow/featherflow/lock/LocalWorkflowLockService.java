package com.ywz.workflow.featherflow.lock;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

public class LocalWorkflowLockService implements WorkflowLockService {

    private final Map<String, ReentrantLock> lockMap = new ConcurrentHashMap<String, ReentrantLock>();

    @Override
    public boolean tryLock(String key) {
        ReentrantLock lock = lockMap.computeIfAbsent(key, value -> new ReentrantLock());
        return lock.tryLock();
    }

    @Override
    public void unlock(String key) {
        ReentrantLock lock = lockMap.get(key);
        if (lock != null && lock.isHeldByCurrentThread()) {
            lock.unlock();
        }
    }
}
