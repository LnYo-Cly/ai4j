package io.github.lnyocly.ai4j.model;

import java.util.Map;
import java.util.Objects;

/**
 * The authoritative tool-call value object.
 *
 * <p>{@link ToolCallItem} (top-level item) and {@link ToolCallBlock} (inside a
 * message) both hold a reference to the same instance; there is no second
 * mutable copy of the fact (design 4.5).</p>
 */
public final class ToolCallValue {

    private final String callId;
    private final String name;
    private final String argumentsJson;
    private final Map<String, Object> protocolData;

    private ToolCallValue(String callId, String name, String argumentsJson, Map<String, Object> protocolData) {
        this.callId = Objects.requireNonNull(callId, "callId is required");
        this.name = name == null ? "" : name;
        this.argumentsJson = argumentsJson == null ? "" : argumentsJson;
        this.protocolData = JsonValues.copyObject(protocolData);
    }

    public static ToolCallValue of(String callId, String name, String argumentsJson) {
        return new ToolCallValue(callId, name, argumentsJson, null);
    }

    public static ToolCallValue of(String callId, String name, String argumentsJson,
                                   Map<String, Object> protocolData) {
        return new ToolCallValue(callId, name, argumentsJson, protocolData);
    }

    public String getCallId() {
        return callId;
    }

    public String getName() {
        return name;
    }

    /** Raw JSON argument string exactly as produced or declared; never re-parsed silently. */
    public String getArgumentsJson() {
        return argumentsJson;
    }

    public Map<String, Object> getProtocolData() {
        return protocolData;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ToolCallValue)) {
            return false;
        }
        ToolCallValue other = (ToolCallValue) o;
        return callId.equals(other.callId)
                && name.equals(other.name)
                && argumentsJson.equals(other.argumentsJson)
                && protocolData.equals(other.protocolData);
    }

    @Override
    public int hashCode() {
        return Objects.hash(callId, name, argumentsJson, protocolData);
    }

    @Override
    public String toString() {
        return "ToolCallValue{callId=" + callId + ", name=" + name + '}';
    }
}
