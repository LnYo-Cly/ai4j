package io.github.lnyocly.ai4j.cli.sandbox;

/**
 * Non-sensitive sandbox binding selected inside one CLI session.
 */
public final class CliSandboxBinding {

    public static final String SOURCE_CLI_ATTACH = "cli-attach";

    private final String providerId;
    private final String sessionId;
    private final String workspaceId;
    private final long attachedAtEpochMs;
    private final String source;

    private CliSandboxBinding(String providerId,
                              String sessionId,
                              String workspaceId,
                              long attachedAtEpochMs,
                              String source) {
        this.providerId = requireText(providerId, "providerId is required");
        this.sessionId = requireText(sessionId, "sessionId is required");
        this.workspaceId = trimToNull(workspaceId);
        this.attachedAtEpochMs = attachedAtEpochMs <= 0 ? System.currentTimeMillis() : attachedAtEpochMs;
        this.source = isBlank(source) ? SOURCE_CLI_ATTACH : source.trim();
    }

    public static CliSandboxBinding attach(String providerId, String sessionId, String workspaceId) {
        return new CliSandboxBinding(providerId, sessionId, workspaceId, System.currentTimeMillis(), SOURCE_CLI_ATTACH);
    }

    public String getProviderId() {
        return providerId;
    }

    public String getSessionId() {
        return sessionId;
    }

    public String getWorkspaceId() {
        return workspaceId;
    }

    public long getAttachedAtEpochMs() {
        return attachedAtEpochMs;
    }

    public String getSource() {
        return source;
    }

    private static String requireText(String value, String message) {
        if (isBlank(value)) {
            throw new IllegalArgumentException(message);
        }
        return value.trim();
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
