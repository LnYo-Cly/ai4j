package io.github.lnyocly.ai4j.cli.sandbox;

import io.github.lnyocly.ai4j.agent.sandbox.SandboxSession;
import io.github.lnyocly.ai4j.agent.sandbox.SandboxSpec;
import io.github.lnyocly.ai4j.agent.sandbox.SandboxStatus;

/**
 * Non-secret CLI-visible summary of the active sandbox binding.
 */
public final class CliSandboxBinding {

    private final String providerId;
    private final String sessionId;
    private final String workspaceId;
    private final String image;
    private final boolean deleteOnClose;
    private final boolean createIfMissing;
    private final SandboxStatus status;

    private CliSandboxBinding(Builder builder) {
        this.providerId = trimToNull(builder.providerId);
        this.sessionId = trimToNull(builder.sessionId);
        this.workspaceId = trimToNull(builder.workspaceId);
        this.image = trimToNull(builder.image);
        this.deleteOnClose = builder.deleteOnClose;
        this.createIfMissing = builder.createIfMissing;
        this.status = builder.status;
    }

    public static CliSandboxBinding from(SandboxSession session, CliSandboxCommand command) {
        SandboxSpec spec = session == null ? null : session.getSpec();
        return builder()
                .providerId(firstNonBlank(session == null ? null : session.getProviderId(),
                        command == null ? null : command.getProviderId()))
                .sessionId(firstNonBlank(session == null ? null : session.getSessionId(),
                        command == null ? null : command.getSandboxIdOrName()))
                .workspaceId(firstNonBlank(command == null ? null : command.getWorkspaceId(),
                        spec == null ? null : spec.getWorkspaceId()))
                .image(firstNonBlank(command == null ? null : command.getImage(),
                        spec == null ? null : spec.getImage()))
                .deleteOnClose(command != null && command.isDeleteOnClose())
                .createIfMissing(command != null && command.isCreateIfMissing())
                .status(session == null ? null : session.getStatus())
                .build();
    }

    public static Builder builder() {
        return new Builder();
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

    public String getImage() {
        return image;
    }

    public boolean isDeleteOnClose() {
        return deleteOnClose;
    }

    public boolean isCreateIfMissing() {
        return createIfMissing;
    }

    public SandboxStatus getStatus() {
        return status;
    }

    private static String firstNonBlank(String first, String second) {
        String normalized = trimToNull(first);
        return normalized == null ? trimToNull(second) : normalized;
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    public static final class Builder {
        private String providerId;
        private String sessionId;
        private String workspaceId;
        private String image;
        private boolean deleteOnClose;
        private boolean createIfMissing;
        private SandboxStatus status;

        private Builder() {
        }

        public Builder providerId(String providerId) {
            this.providerId = providerId;
            return this;
        }

        public Builder sessionId(String sessionId) {
            this.sessionId = sessionId;
            return this;
        }

        public Builder workspaceId(String workspaceId) {
            this.workspaceId = workspaceId;
            return this;
        }

        public Builder image(String image) {
            this.image = image;
            return this;
        }

        public Builder deleteOnClose(boolean deleteOnClose) {
            this.deleteOnClose = deleteOnClose;
            return this;
        }

        public Builder createIfMissing(boolean createIfMissing) {
            this.createIfMissing = createIfMissing;
            return this;
        }

        public Builder status(SandboxStatus status) {
            this.status = status;
            return this;
        }

        public CliSandboxBinding build() {
            return new CliSandboxBinding(this);
        }
    }
}
