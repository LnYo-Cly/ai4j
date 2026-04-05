package io.github.lnyocly.ai4j.agent.team;

import io.github.lnyocly.ai4j.agent.Agent;
import lombok.Builder;
import lombok.Data;

@Data
@Builder(toBuilder = true)
public class AgentTeamMember {

    private String id;

    private String name;

    private String description;

    private Agent agent;

    public String resolveId() {
        String candidate = normalize(id);
        if (candidate != null) {
            return candidate;
        }
        candidate = normalize(name);
        if (candidate != null) {
            return candidate;
        }
        throw new IllegalArgumentException("team member id or name is required");
    }

    private String normalize(String raw) {
        if (raw == null) {
            return null;
        }
        String normalized = raw.trim().toLowerCase();
        if (normalized.isEmpty()) {
            return null;
        }
        normalized = normalized.replaceAll("[^a-z0-9_\\-]", "_");
        normalized = normalized.replaceAll("_+", "_");
        normalized = normalized.replaceAll("^_+", "");
        normalized = normalized.replaceAll("_+$", "");
        return normalized.isEmpty() ? null : normalized;
    }
}
