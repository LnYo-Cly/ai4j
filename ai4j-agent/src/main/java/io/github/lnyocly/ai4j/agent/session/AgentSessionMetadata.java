package io.github.lnyocly.ai4j.agent.session;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Metadata for one long-running agent session.
 */
public class AgentSessionMetadata {

    private String sessionId;
    private long createdAtEpochMs;
    private long updatedAtEpochMs;
    private Map<String, Object> attributes;

    public AgentSessionMetadata() {
        this(newSessionId(), System.currentTimeMillis(), System.currentTimeMillis(), null);
    }

    public AgentSessionMetadata(String sessionId, long createdAtEpochMs, long updatedAtEpochMs, Map<String, Object> attributes) {
        long now = System.currentTimeMillis();
        this.sessionId = trimToNull(sessionId) == null ? newSessionId() : sessionId;
        this.createdAtEpochMs = createdAtEpochMs <= 0 ? now : createdAtEpochMs;
        this.updatedAtEpochMs = updatedAtEpochMs <= 0 ? this.createdAtEpochMs : updatedAtEpochMs;
        this.attributes = copyMap(attributes);
    }

    public static AgentSessionMetadata create() {
        return new AgentSessionMetadata();
    }

    public AgentSessionMetadata copy() {
        return new AgentSessionMetadata(sessionId, createdAtEpochMs, updatedAtEpochMs, attributes);
    }

    public void touch() {
        this.updatedAtEpochMs = System.currentTimeMillis();
    }

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        String value = trimToNull(sessionId);
        this.sessionId = value == null ? newSessionId() : value;
        touch();
    }

    public long getCreatedAtEpochMs() {
        return createdAtEpochMs;
    }

    public void setCreatedAtEpochMs(long createdAtEpochMs) {
        this.createdAtEpochMs = createdAtEpochMs;
    }

    public long getUpdatedAtEpochMs() {
        return updatedAtEpochMs;
    }

    public void setUpdatedAtEpochMs(long updatedAtEpochMs) {
        this.updatedAtEpochMs = updatedAtEpochMs;
    }

    public Map<String, Object> getAttributes() {
        return new LinkedHashMap<String, Object>(attributes);
    }

    public void setAttributes(Map<String, Object> attributes) {
        this.attributes = copyMap(attributes);
        touch();
    }

    public Object getAttribute(String key) {
        if (key == null) {
            return null;
        }
        return attributes.get(key);
    }

    public void putAttribute(String key, Object value) {
        String normalized = trimToNull(key);
        if (normalized == null) {
            return;
        }
        attributes.put(normalized, value);
        touch();
    }

    public void removeAttribute(String key) {
        if (key == null) {
            return;
        }
        attributes.remove(key);
        touch();
    }

    private static Map<String, Object> copyMap(Map<String, Object> source) {
        return source == null ? new LinkedHashMap<String, Object>() : new LinkedHashMap<String, Object>(source);
    }

    private static String newSessionId() {
        return "session_" + UUID.randomUUID().toString().replace("-", "");
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
