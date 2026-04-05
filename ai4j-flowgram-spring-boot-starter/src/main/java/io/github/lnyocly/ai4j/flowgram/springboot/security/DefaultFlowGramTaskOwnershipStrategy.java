package io.github.lnyocly.ai4j.flowgram.springboot.security;

import java.time.Duration;

public class DefaultFlowGramTaskOwnershipStrategy implements FlowGramTaskOwnershipStrategy {

    private final Duration retention;

    public DefaultFlowGramTaskOwnershipStrategy(Duration retention) {
        this.retention = retention;
    }

    @Override
    public FlowGramTaskOwnership createOwnership(String taskId, FlowGramCaller caller) {
        long createdAt = System.currentTimeMillis();
        long expiresAt = createdAt + Math.max(retention == null ? 0L : retention.toMillis(), 0L);
        return FlowGramTaskOwnership.builder()
                .creatorId(caller == null ? null : caller.getCallerId())
                .tenantId(caller == null ? null : caller.getTenantId())
                .createdAt(createdAt)
                .expiresAt(expiresAt)
                .build();
    }
}
