package io.github.lnyocly.ai4j.extension.interceptor;

/**
 * The verdict an {@link ExtensionToolCallInterceptor} returns for one tool call. Immutable;
 * construct via the static factories. Mirrors the agent runtime's tool-interceptor semantics:
 * allow / block (reason fed back to the model) / modify (rewritten call executes) /
 * routeTo (execute in a named sandbox instead).
 */
public final class ExtensionToolCallDecision {

    public enum Type { ALLOW, BLOCK, MODIFY, ROUTE_TO }

    private final Type type;
    private final String reason;
    private final String modifiedName;
    private final String modifiedArguments;
    private final String sandboxProviderId;
    private final String sandboxProfile;
    private final String sandboxCommand;

    private ExtensionToolCallDecision(Type type,
                                      String reason,
                                      String modifiedName,
                                      String modifiedArguments,
                                      String sandboxProviderId,
                                      String sandboxProfile,
                                      String sandboxCommand) {
        this.type = type;
        this.reason = reason;
        this.modifiedName = modifiedName;
        this.modifiedArguments = modifiedArguments;
        this.sandboxProviderId = sandboxProviderId;
        this.sandboxProfile = sandboxProfile;
        this.sandboxCommand = sandboxCommand;
    }

    /** Proceed with the original call. */
    public static ExtensionToolCallDecision allow() {
        return new ExtensionToolCallDecision(Type.ALLOW, null, null, null, null, null, null);
    }

    /** Veto the call; {@code reason} is fed back to the model as the tool result. */
    public static ExtensionToolCallDecision block(String reason) {
        return new ExtensionToolCallDecision(Type.BLOCK, reason, null, null, null, null, null);
    }

    /**
     * Rewrite the call before execution. {@code newName} may be null to keep the tool name;
     * {@code newArguments} replaces the raw arguments JSON (null keeps the original).
     */
    public static ExtensionToolCallDecision modify(String newName, String newArguments) {
        if (newName == null && newArguments == null) {
            throw new IllegalArgumentException("modify requires a new name or new arguments");
        }
        return new ExtensionToolCallDecision(Type.MODIFY, null, newName, newArguments, null, null, null);
    }

    /**
     * Redirect execution to a sandbox: {@code providerId} selects the sandbox provider
     * (Daytona/E2B/...), {@code command} is the shell command to run there. The runtime owns
     * session creation; the extension owns the tool→command mapping.
     */
    public static ExtensionToolCallDecision routeTo(String providerId, String profile, String command) {
        if (providerId == null || providerId.trim().isEmpty()) {
            throw new IllegalArgumentException("sandbox providerId must not be blank");
        }
        if (command == null || command.trim().isEmpty()) {
            throw new IllegalArgumentException("sandbox command must not be blank");
        }
        return new ExtensionToolCallDecision(Type.ROUTE_TO, null, null, null,
                providerId.trim(), profile == null ? null : profile.trim(), command);
    }

    public Type getType() {
        return type;
    }

    public String getReason() {
        return reason;
    }

    public String getModifiedName() {
        return modifiedName;
    }

    public String getModifiedArguments() {
        return modifiedArguments;
    }

    public String getSandboxProviderId() {
        return sandboxProviderId;
    }

    public String getSandboxProfile() {
        return sandboxProfile;
    }

    public String getSandboxCommand() {
        return sandboxCommand;
    }
}
