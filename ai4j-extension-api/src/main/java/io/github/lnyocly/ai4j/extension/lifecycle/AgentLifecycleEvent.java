package io.github.lnyocly.ai4j.extension.lifecycle;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public final class AgentLifecycleEvent {

    private final AgentLifecycleEventType type;
    private final String runtime;
    private final String sessionId;
    private final Integer step;
    private final String message;
    private final Object payload;
    private final Map<String, Object> attributes;

    private AgentLifecycleEvent(Builder builder) {
        if (builder.type == null) {
            throw new IllegalArgumentException("lifecycle event type must not be null");
        }
        this.type = builder.type;
        this.runtime = emptyToNull(builder.runtime);
        this.sessionId = emptyToNull(builder.sessionId);
        this.step = builder.step;
        this.message = emptyToNull(builder.message);
        this.payload = builder.payload;
        this.attributes = builder.attributes == null
                ? Collections.<String, Object>emptyMap()
                : Collections.unmodifiableMap(new LinkedHashMap<String, Object>(builder.attributes));
    }

    public static Builder builder(AgentLifecycleEventType type) {
        return new Builder(type);
    }

    public AgentLifecycleEventType getType() {
        return type;
    }

    public String getRuntime() {
        return runtime;
    }

    public String getSessionId() {
        return sessionId;
    }

    public Integer getStep() {
        return step;
    }

    public String getMessage() {
        return message;
    }

    public Object getPayload() {
        return payload;
    }

    public Map<String, Object> getAttributes() {
        return attributes;
    }

    private static String emptyToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    public static final class Builder {
        private final AgentLifecycleEventType type;
        private String runtime;
        private String sessionId;
        private Integer step;
        private String message;
        private Object payload;
        private Map<String, Object> attributes;

        private Builder(AgentLifecycleEventType type) {
            this.type = type;
        }

        public Builder runtime(String runtime) {
            this.runtime = runtime;
            return this;
        }

        public Builder sessionId(String sessionId) {
            this.sessionId = sessionId;
            return this;
        }

        public Builder step(Integer step) {
            this.step = step;
            return this;
        }

        public Builder message(String message) {
            this.message = message;
            return this;
        }

        public Builder payload(Object payload) {
            this.payload = payload;
            return this;
        }

        public Builder attribute(String key, Object value) {
            if (key == null || key.trim().isEmpty()) {
                return this;
            }
            if (this.attributes == null) {
                this.attributes = new LinkedHashMap<String, Object>();
            }
            this.attributes.put(key.trim(), value);
            return this;
        }

        public Builder attributes(Map<String, Object> attributes) {
            if (attributes != null && !attributes.isEmpty()) {
                if (this.attributes == null) {
                    this.attributes = new LinkedHashMap<String, Object>();
                }
                this.attributes.putAll(attributes);
            }
            return this;
        }

        public AgentLifecycleEvent build() {
            return new AgentLifecycleEvent(this);
        }
    }
}
