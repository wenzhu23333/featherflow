package com.ywz.workflow.featherflow.support;

import java.lang.management.ManagementFactory;
import java.net.InetAddress;
import java.util.UUID;

public final class WorkflowNodeIdentity {

    private static final String CURRENT_INSTANCE_ID = normalizeInstanceId(null);

    private final String instanceId;

    public WorkflowNodeIdentity(String instanceId) {
        this.instanceId = normalizeInstanceId(instanceId);
    }

    public static String currentInstanceId() {
        return CURRENT_INSTANCE_ID;
    }

    public String getInstanceId() {
        return instanceId;
    }

    public static String normalizeInstanceId(String instanceId) {
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
            // Fall back to a generic marker when the local IP cannot be resolved.
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
            // Fall back to a generic marker when the local hostname cannot be resolved.
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
