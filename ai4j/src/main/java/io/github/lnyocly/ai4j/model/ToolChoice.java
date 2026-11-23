package io.github.lnyocly.ai4j.model;

import java.util.Objects;

/**
 * Tool selection policy for a request.
 *
 * <p>Only the portable shapes are modelled. Protocol-specific choice objects
 * are carried as {@link #nativeChoice(Protocol, java.util.Map)} and forwarded
 * only to the matching protocol.</p>
 */
public final class ToolChoice {

    public enum Mode {
        /** Provider default. */
        AUTO,

        /** Model must not call tools. */
        NONE,

        /** Model must call at least one tool. */
        REQUIRED
    }

    private final Mode mode;
    private final String functionName;
    private final Protocol nativeProtocol;
    private final java.util.Map<String, Object> nativeChoice;

    private ToolChoice(Mode mode, String functionName, Protocol nativeProtocol,
                       java.util.Map<String, Object> nativeChoice) {
        this.mode = mode;
        this.functionName = functionName;
        this.nativeProtocol = nativeProtocol;
        this.nativeChoice = JsonValues.copyObject(nativeChoice);
    }

    public static ToolChoice auto() {
        return new ToolChoice(Mode.AUTO, null, null, null);
    }

    public static ToolChoice none() {
        return new ToolChoice(Mode.NONE, null, null, null);
    }

    public static ToolChoice required() {
        return new ToolChoice(Mode.REQUIRED, null, null, null);
    }

    public static ToolChoice function(String functionName) {
        return new ToolChoice(null, Objects.requireNonNull(functionName, "functionName is required"),
                null, null);
    }

    public static ToolChoice nativeChoice(Protocol protocol, java.util.Map<String, Object> nativeChoice) {
        return new ToolChoice(null, null,
                Objects.requireNonNull(protocol, "protocol is required"),
                Objects.requireNonNull(nativeChoice, "nativeChoice is required"));
    }

    public Mode getMode() {
        return mode;
    }

    /** Named function when the choice targets one function, else {@code null}. */
    public String getFunctionName() {
        return functionName;
    }

    public Protocol getNativeProtocol() {
        return nativeProtocol;
    }

    public java.util.Map<String, Object> getNativeChoice() {
        return nativeChoice;
    }

    public boolean isFunctionChoice() {
        return functionName != null;
    }

    public boolean isNativeChoice() {
        return nativeProtocol != null;
    }
}
