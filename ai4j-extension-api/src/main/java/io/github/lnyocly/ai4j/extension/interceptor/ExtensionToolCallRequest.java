package io.github.lnyocly.ai4j.extension.interceptor;

/**
 * The extension-facing view of one tool call, passed to {@link ExtensionToolCallInterceptor}.
 * Carries the portable fields only — provider-specific call metadata stays on the agent side.
 */
public final class ExtensionToolCallRequest {

    private final String name;
    private final String arguments;
    private final String callId;
    private final String type;

    public ExtensionToolCallRequest(String name, String arguments, String callId, String type) {
        this.name = name;
        this.arguments = arguments;
        this.callId = callId;
        this.type = type;
    }

    public String getName() {
        return name;
    }

    /** Raw JSON arguments string as emitted by the model. */
    public String getArguments() {
        return arguments;
    }

    public String getCallId() {
        return callId;
    }

    public String getType() {
        return type;
    }
}
