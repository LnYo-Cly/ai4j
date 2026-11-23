package io.github.lnyocly.ai4j.cli.config;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Edge node capability, affinity tag, and scope configuration descriptor for Fleet Edge registration in {@code ai4j-cli}.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CliFleetEdgeDescriptor {

    private Boolean enabled;
    private String nodeId;
    private String centerUrl;
    private List<String> tags;
    private List<String> capabilities;
    private List<String> allowedAgents;
    private List<String> allowedScopeKeys;
    private Integer maxConcurrentExecutions;
    private Map<String, String> labels;

    public CliFleetEdgeDescriptor() {
    }

    public CliFleetEdgeDescriptor(Boolean enabled,
                                  String nodeId,
                                  String centerUrl,
                                  List<String> tags,
                                  List<String> capabilities,
                                  List<String> allowedAgents,
                                  List<String> allowedScopeKeys,
                                  Integer maxConcurrentExecutions,
                                  Map<String, String> labels) {
        this.enabled = enabled;
        this.nodeId = trimToNull(nodeId);
        this.centerUrl = trimToNull(centerUrl);
        this.tags = copyNormalizedList(tags);
        this.capabilities = copyNormalizedList(capabilities);
        this.allowedAgents = copyNormalizedList(allowedAgents);
        this.allowedScopeKeys = copyNormalizedList(allowedScopeKeys);
        this.maxConcurrentExecutions = normalizeConcurrency(maxConcurrentExecutions);
        this.labels = copyNormalizedMap(labels);
    }

    public static Builder builder() {
        return new Builder();
    }

    public Builder toBuilder() {
        return new Builder()
                .enabled(enabled)
                .nodeId(nodeId)
                .centerUrl(centerUrl)
                .tags(tags)
                .capabilities(capabilities)
                .allowedAgents(allowedAgents)
                .allowedScopeKeys(allowedScopeKeys)
                .maxConcurrentExecutions(maxConcurrentExecutions)
                .labels(labels);
    }

    public CliFleetEdgeDescriptor normalized() {
        return new CliFleetEdgeDescriptor(
                enabled,
                nodeId,
                centerUrl,
                tags,
                capabilities,
                allowedAgents,
                allowedScopeKeys,
                maxConcurrentExecutions,
                labels
        );
    }

    public CliFleetEdgeDescriptor mergeWith(CliFleetEdgeDescriptor override) {
        if (override == null) {
            return normalized();
        }
        CliFleetEdgeDescriptor base = normalized();
        CliFleetEdgeDescriptor next = override.normalized();
        return new CliFleetEdgeDescriptor(
                next.enabled != null ? next.enabled : base.enabled,
                next.nodeId != null ? next.nodeId : base.nodeId,
                next.centerUrl != null ? next.centerUrl : base.centerUrl,
                hasEntries(next.tags) ? next.tags : base.tags,
                hasEntries(next.capabilities) ? next.capabilities : base.capabilities,
                hasEntries(next.allowedAgents) ? next.allowedAgents : base.allowedAgents,
                hasEntries(next.allowedScopeKeys) ? next.allowedScopeKeys : base.allowedScopeKeys,
                next.maxConcurrentExecutions != null ? next.maxConcurrentExecutions : base.maxConcurrentExecutions,
                mergeMaps(base.labels, next.labels)
        );
    }

    @JsonIgnore
    public boolean isActive() {
        return Boolean.TRUE.equals(enabled) && trimToNull(nodeId) != null;
    }

    public boolean hasTag(String tag) {
        return containsIgnoreCase(tags, tag);
    }

    public boolean hasCapability(String capability) {
        return containsIgnoreCase(capabilities, capability);
    }

    public boolean supportsAgent(String agentId) {
        if (allowedAgents == null || allowedAgents.isEmpty()) {
            return true;
        }
        return containsIgnoreCase(allowedAgents, agentId);
    }

    public boolean supportsScopeKey(String scopeKey) {
        if (allowedScopeKeys == null || allowedScopeKeys.isEmpty()) {
            return true;
        }
        return containsIgnoreCase(allowedScopeKeys, scopeKey);
    }

    public boolean matchesAffinity(Collection<String> requiredCapabilities,
                                   Collection<String> requiredTags,
                                   String agentId,
                                   String scopeKey) {
        if (!supportsAgent(agentId) || !supportsScopeKey(scopeKey)) {
            return false;
        }
        if (requiredCapabilities != null) {
            for (String capability : requiredCapabilities) {
                if (trimToNull(capability) != null && !hasCapability(capability)) {
                    return false;
                }
            }
        }
        if (requiredTags != null) {
            for (String tag : requiredTags) {
                if (trimToNull(tag) != null && !hasTag(tag)) {
                    return false;
                }
            }
        }
        return true;
    }

    public Boolean getEnabled() {
        return enabled;
    }

    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
    }

    public String getNodeId() {
        return nodeId;
    }

    public void setNodeId(String nodeId) {
        this.nodeId = trimToNull(nodeId);
    }

    public String getCenterUrl() {
        return centerUrl;
    }

    public void setCenterUrl(String centerUrl) {
        this.centerUrl = trimToNull(centerUrl);
    }

    public List<String> getTags() {
        return tags == null ? null : Collections.unmodifiableList(tags);
    }

    public void setTags(List<String> tags) {
        this.tags = copyNormalizedList(tags);
    }

    public List<String> getCapabilities() {
        return capabilities == null ? null : Collections.unmodifiableList(capabilities);
    }

    public void setCapabilities(List<String> capabilities) {
        this.capabilities = copyNormalizedList(capabilities);
    }

    public List<String> getAllowedAgents() {
        return allowedAgents == null ? null : Collections.unmodifiableList(allowedAgents);
    }

    public void setAllowedAgents(List<String> allowedAgents) {
        this.allowedAgents = copyNormalizedList(allowedAgents);
    }

    public List<String> getAllowedScopeKeys() {
        return allowedScopeKeys == null ? null : Collections.unmodifiableList(allowedScopeKeys);
    }

    public void setAllowedScopeKeys(List<String> allowedScopeKeys) {
        this.allowedScopeKeys = copyNormalizedList(allowedScopeKeys);
    }

    public Integer getMaxConcurrentExecutions() {
        return maxConcurrentExecutions;
    }

    public void setMaxConcurrentExecutions(Integer maxConcurrentExecutions) {
        this.maxConcurrentExecutions = normalizeConcurrency(maxConcurrentExecutions);
    }

    public Map<String, String> getLabels() {
        return labels == null ? null : Collections.unmodifiableMap(labels);
    }

    public void setLabels(Map<String, String> labels) {
        this.labels = copyNormalizedMap(labels);
    }

    private static boolean hasEntries(List<String> list) {
        return list != null && !list.isEmpty();
    }

    private static boolean containsIgnoreCase(List<String> values, String candidate) {
        String normalizedCandidate = trimToNull(candidate);
        if (values == null || values.isEmpty() || normalizedCandidate == null) {
            return false;
        }
        String lower = normalizedCandidate.toLowerCase(Locale.ROOT);
        for (String value : values) {
            String trimmed = trimToNull(value);
            if (trimmed != null && trimmed.toLowerCase(Locale.ROOT).equals(lower)) {
                return true;
            }
        }
        return false;
    }

    private static Integer normalizeConcurrency(Integer value) {
        if (value == null || value.intValue() <= 0) {
            return null;
        }
        return value;
    }

    private static List<String> copyNormalizedList(List<String> values) {
        if (values == null) {
            return null;
        }
        LinkedHashSet<String> deduped = new LinkedHashSet<String>();
        for (String value : values) {
            String trimmed = trimToNull(value);
            if (trimmed != null) {
                deduped.add(trimmed);
            }
        }
        return new ArrayList<String>(deduped);
    }

    private static Map<String, String> copyNormalizedMap(Map<String, String> values) {
        if (values == null) {
            return null;
        }
        Map<String, String> copy = new LinkedHashMap<String, String>();
        for (Map.Entry<String, String> entry : values.entrySet()) {
            String key = trimToNull(entry.getKey());
            String val = trimToNull(entry.getValue());
            if (key != null && val != null) {
                copy.put(key, val);
            }
        }
        return copy;
    }

    private static Map<String, String> mergeMaps(Map<String, String> base, Map<String, String> override) {
        if (base == null && override == null) {
            return null;
        }
        Map<String, String> merged = new LinkedHashMap<String, String>();
        if (base != null) {
            merged.putAll(base);
        }
        if (override != null) {
            merged.putAll(override);
        }
        return merged;
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    public static final class Builder {

        private Boolean enabled;
        private String nodeId;
        private String centerUrl;
        private List<String> tags;
        private List<String> capabilities;
        private List<String> allowedAgents;
        private List<String> allowedScopeKeys;
        private Integer maxConcurrentExecutions;
        private Map<String, String> labels;

        private Builder() {
        }

        public Builder enabled(Boolean enabled) {
            this.enabled = enabled;
            return this;
        }

        public Builder nodeId(String nodeId) {
            this.nodeId = nodeId;
            return this;
        }

        public Builder centerUrl(String centerUrl) {
            this.centerUrl = centerUrl;
            return this;
        }

        public Builder tags(List<String> tags) {
            this.tags = copyNormalizedList(tags);
            return this;
        }

        public Builder capabilities(List<String> capabilities) {
            this.capabilities = copyNormalizedList(capabilities);
            return this;
        }

        public Builder allowedAgents(List<String> allowedAgents) {
            this.allowedAgents = copyNormalizedList(allowedAgents);
            return this;
        }

        public Builder allowedScopeKeys(List<String> allowedScopeKeys) {
            this.allowedScopeKeys = copyNormalizedList(allowedScopeKeys);
            return this;
        }

        public Builder maxConcurrentExecutions(Integer maxConcurrentExecutions) {
            this.maxConcurrentExecutions = normalizeConcurrency(maxConcurrentExecutions);
            return this;
        }

        public Builder labels(Map<String, String> labels) {
            this.labels = copyNormalizedMap(labels);
            return this;
        }

        public CliFleetEdgeDescriptor build() {
            return new CliFleetEdgeDescriptor(
                    enabled,
                    nodeId,
                    centerUrl,
                    tags,
                    capabilities,
                    allowedAgents,
                    allowedScopeKeys,
                    maxConcurrentExecutions,
                    labels
            );
        }
    }
}
