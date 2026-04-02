package com.ywz.workflow.featherflow.persistence;

import java.time.Duration;

@FunctionalInterface
public interface PersistenceRetrySleeper {

    void sleep(Duration delay) throws InterruptedException;
}
