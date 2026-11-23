package io.github.lnyocly.ai4j.harness.fleet;

import io.github.lnyocly.ai4j.harness.HarnessJson;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Node registration and capability descriptor in a multi-node Harness Fleet.
 */
@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class FleetNodeRecord {

    private String nodeId;
    private FleetTopology topology;
    private String displayName;
    private FleetNodeStatus status;
    private String credentialToken;
    private long writerEpochAtJoin;
    private long lastSyncSeq;
    private int maxConcurrentExecutions;
    private int activeExecutions;
    private long joinedAtEpochMs;
    private long lastHeartbeatEpochMs;
    private long unregisteredAtEpochMs;
    private String unregisterReason;
    @Builder.Default
    private long version = 1L;

    @Builder.Default
    private List<String> tags = new ArrayList<String>();

    @Builder.Default
    private List<String> allowedAgents = new ArrayList<String>();

    @Builder.Default
    private List<String> allowedScopeKeys = new ArrayList<String>();

    public boolean supportsScope(String scopeKey) {
        if (allowedScopeKeys == null || allowedScopeKeys.isEmpty()) {
            return true;
        }
        if (scopeKey == null || scopeKey.trim().isEmpty()) {
            return true;
        }
        return allowedScopeKeys.contains(scopeKey.trim());
    }

    public boolean supportsTags(List<String> requiredTags) {
        if (requiredTags == null || requiredTags.isEmpty()) {
            return true;
        }
        Set<String> available = tags == null ? new LinkedHashSet<String>() : new LinkedHashSet<String>(tags);
        for (String required : requiredTags) {
            if (required != null && !required.trim().isEmpty() && !available.contains(required.trim())) {
                return false;
            }
        }
        return true;
    }

    public FleetNodeRecord copy() {
        return HarnessJson.copy(this, FleetNodeRecord.class);
    }
}
