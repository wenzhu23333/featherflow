package com.ywz.workflow.featherflow.persistence;

import java.util.function.Supplier;

/**
 * Retries framework-owned persistence writes when the failure looks transient.
 */
public interface PersistenceWriteRetrier {

    void run(String operationName, Runnable action);

    <T> T call(String operationName, Supplier<T> supplier);
}
