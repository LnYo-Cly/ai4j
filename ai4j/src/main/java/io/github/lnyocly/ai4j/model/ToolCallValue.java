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
    private final String thoughtSignature;
    private final Protocol sourceProtocol;
    private final Map<String, Object> protocolData;

    private ToolCallValue(String callId, String name, String argumentsJson,
                          String thoughtSignature, Protocol sourceProtocol,
                          Map<String, Object> protocolData) {
        this.callId = Objects.requireNonNull(callId, "callId is required");
        this.name = name == null ? "" : name;
        this.argumentsJson = argumentsJson == null ? "" : argumentsJson;
        this.thoughtSignature = thoughtSignature;
        this.sourceProtocol = sourceProtocol;
        this.protocolData = JsonValues.copyObject(protocolData);
    }

    public static ToolCallValue of(String callId, String name, String argumentsJson) {
        return new ToolCallValue(callId, name, argumentsJson, null, null, null);
    }

    public static ToolCallValue of(String callId, String name, String argumentsJson,
                                   Map<String, Object> protocolData) {
        return new ToolCallValue(callId, name, argumentsJson, null, null, protocolData);
    }

    public static ToolCallValue of(String callId, String name, String argumentsJson,
                                   String thoughtSignature, Protocol sourceProtocol) {
        return new ToolCallValue(callId, name, argumentsJson, thoughtSignature, sourceProtocol, null);
    }

    public static ToolCallValue of(String callId, String name, String argumentsJson,
                                   String thoughtSignature, Protocol sourceProtocol,
                                   Map<String, Object> protocolData) {
        return new ToolCallValue(callId, name, argumentsJson, thoughtSignature, sourceProtocol, protocolData);
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

    /** Optional encrypted thought signature attached to this tool call (e.g. Gemini 2.5+/3). */
    public String getThoughtSignature() {
        if (thoughtSignature != null) {
            return thoughtSignature;
        }
        Object sig = protocolData.get("thoughtSignature");
        return sig == null ? null : String.valueOf(sig);
    }

    /** Originating wire protocol for signature replay compatibility, or {@code null} when unspecified. */
    public Protocol getSourceProtocol() {
        if (sourceProtocol != null) {
            return sourceProtocol;
        }
        Object raw = protocolData.get("_protocol");
        if (raw instanceof Protocol) {
            return (Protocol) raw;
        }
        if (raw instanceof String) {
            try {
                return Protocol.valueOf((String) raw);
            } catch (IllegalArgumentException ignored) {
                return null;
            }
        }
        return null;
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
                && Objects.equals(thoughtSignature, other.thoughtSignature)
                && sourceProtocol == other.sourceProtocol
                && protocolData.equals(other.protocolData);
    }

    @Override
    public int hashCode() {
        return Objects.hash(callId, name, argumentsJson, thoughtSignature, sourceProtocol, protocolData);
    }

    @Override
    public String toString() {
        return "ToolCallValue{callId=" + callId + ", name=" + name + '}';
    }
}
