package com.ywz.workflow.featherflow.starter;

import static org.assertj.core.api.Assertions.assertThat;

import com.ywz.workflow.featherflow.service.StaleRunningWorkflowRecoveryService;
import java.time.Clock;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class WorkflowRecoveryLifecycleTest {

    @Test
    void shouldRunRecoveryOnlyWithinStartupWindow() throws Exception {
        RecordingRecoveryService recoveryService = new RecordingRecoveryService();
        FeatherFlowProperties properties = new FeatherFlowProperties();
        properties.setRunningWorkflowRecoveryDelayMillis(1L);
        properties.setRunningWorkflowRecoveryIntervalMillis(20L);
        properties.setRunningWorkflowRecoveryWindowMillis(80L);
        properties.setRunningWorkflowRecoveryStaleMillis(300000L);
        properties.setRunningWorkflowRecoveryBatchSize(7);
        WorkflowRecoveryLifecycle lifecycle = new WorkflowRecoveryLifecycle(recoveryService, properties);

        lifecycle.start();

        assertThat(recoveryService.awaitFirstCall()).isTrue();
        assertThat(recoveryService.lastStaleTimeout.get()).isEqualTo(Duration.ofMinutes(5));
        assertThat(recoveryService.lastBatchSize.get()).isEqualTo(7);
        waitUntilStopped(lifecycle);
        int callsAfterWindow = recoveryService.calls.get();
        Thread.sleep(80L);

        assertThat(lifecycle.isRunning()).isFalse();
        assertThat(callsAfterWindow).isGreaterThanOrEqualTo(1);
        assertThat(recoveryService.calls.get()).isEqualTo(callsAfterWindow);
    }

    @Test
    void shouldNotStartWhenRecoveryIsDisabled() throws Exception {
        RecordingRecoveryService recoveryService = new RecordingRecoveryService();
        FeatherFlowProperties properties = new FeatherFlowProperties();
        properties.setAutoRecoverRunningWorkflows(false);
        properties.setRunningWorkflowRecoveryDelayMillis(1L);
        WorkflowRecoveryLifecycle lifecycle = new WorkflowRecoveryLifecycle(recoveryService, properties);

        lifecycle.start();
        Thread.sleep(30L);

        assertThat(lifecycle.isRunning()).isFalse();
        assertThat(recoveryService.calls.get()).isZero();
    }

    @Test
    void shouldKeepScanningWhenOneRecoveryScanFails() throws Exception {
        RecordingRecoveryService recoveryService = new RecordingRecoveryService();
        recoveryService.failuresBeforeSuccess.set(1);
        FeatherFlowProperties properties = new FeatherFlowProperties();
        properties.setRunningWorkflowRecoveryDelayMillis(1L);
        properties.setRunningWorkflowRecoveryIntervalMillis(20L);
        properties.setRunningWorkflowRecoveryWindowMillis(200L);
        WorkflowRecoveryLifecycle lifecycle = new WorkflowRecoveryLifecycle(recoveryService, properties);

        lifecycle.start();

        assertThat(recoveryService.awaitCalls(2)).isTrue();
        lifecycle.stop();
        assertThat(recoveryService.calls.get()).isGreaterThanOrEqualTo(2);
    }

    private static void waitUntilStopped(WorkflowRecoveryLifecycle lifecycle) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 1000L;
        while (System.currentTimeMillis() < deadline && lifecycle.isRunning()) {
            Thread.sleep(10L);
        }
    }

    private static final class RecordingRecoveryService extends StaleRunningWorkflowRecoveryService {

        private final AtomicInteger calls = new AtomicInteger();
        private final AtomicInteger failuresBeforeSuccess = new AtomicInteger();
        private final AtomicReference<Duration> lastStaleTimeout = new AtomicReference<Duration>();
        private final AtomicReference<Integer> lastBatchSize = new AtomicReference<Integer>();
        private final CountDownLatch firstCall = new CountDownLatch(1);

        private RecordingRecoveryService() {
            super(null, null, Clock.systemUTC());
        }

        @Override
        public int recover(Duration staleTimeout, int batchSize) {
            calls.incrementAndGet();
            lastStaleTimeout.set(staleTimeout);
            lastBatchSize.set(batchSize);
            firstCall.countDown();
            if (failuresBeforeSuccess.getAndUpdate(value -> Math.max(value - 1, 0)) > 0) {
                throw new IllegalStateException("temporary recovery failure");
            }
            return 0;
        }

        private boolean awaitFirstCall() throws InterruptedException {
            return firstCall.await(1, TimeUnit.SECONDS);
        }

        private boolean awaitCalls(int expectedCalls) throws InterruptedException {
            long deadline = System.currentTimeMillis() + 1000L;
            while (System.currentTimeMillis() < deadline) {
                if (calls.get() >= expectedCalls) {
                    return true;
                }
                Thread.sleep(10L);
            }
            return calls.get() >= expectedCalls;
        }
    }
}
