package io.github.lnyocly.ai4j.agent.sandbox;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * One command request to execute in a sandbox session.
 */
public final class SandboxCommand {

    private final String commandId;
    private final String command;
    private final String workingDirectory;
    private final String stdin;
    private final Long timeoutMillis;
    private final Map<String, String> environment;
    private final Map<String, Object> metadata;

    private SandboxCommand(Builder builder) {
        this.commandId = isBlank(builder.commandId) ? UUID.randomUUID().toString() : builder.commandId.trim();
        this.command = requireText(builder.command, "sandbox command must not be blank");
        this.workingDirectory = trimToNull(builder.workingDirectory);
        this.stdin = builder.stdin;
        this.timeoutMillis = builder.timeoutMillis;
        this.environment = SandboxSpec.copyStringMap(builder.environment);
        this.metadata = SandboxSpec.copyObjectMap(builder.metadata);
    }

    public static Builder builder() {
        return new Builder();
    }

    public String getCommandId() {
        return commandId;
    }

    public String getCommand() {
        return command;
    }

    public String getWorkingDirectory() {
        return workingDirectory;
    }

    public String getStdin() {
        return stdin;
    }

    public Long getTimeoutMillis() {
        return timeoutMillis;
    }

    public Map<String, String> getEnvironment() {
        return SandboxSpec.copyStringMap(environment);
    }

    public Map<String, Object> getMetadata() {
        return SandboxSpec.copyObjectMap(metadata);
    }

    public SandboxCommand copy() {
        return builder()
                .commandId(commandId)
                .command(command)
                .workingDirectory(workingDirectory)
                .stdin(stdin)
                .timeoutMillis(timeoutMillis)
                .environment(environment)
                .metadata(metadata)
                .build();
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

    public static final class Builder {
        private String commandId;
        private String command;
        private String workingDirectory;
        private String stdin;
        private Long timeoutMillis;
        private Map<String, String> environment;
        private Map<String, Object> metadata;

        private Builder() {
        }

        public Builder commandId(String commandId) {
            this.commandId = commandId;
            return this;
        }

        public Builder command(String command) {
            this.command = command;
            return this;
        }

        public Builder workingDirectory(String workingDirectory) {
            this.workingDirectory = workingDirectory;
            return this;
        }

        public Builder stdin(String stdin) {
            this.stdin = stdin;
            return this;
        }

        public Builder timeoutMillis(Long timeoutMillis) {
            this.timeoutMillis = timeoutMillis;
            return this;
        }

        public Builder environment(String key, String value) {
            if (environment == null) {
                environment = new LinkedHashMap<String, String>();
            }
            if (key != null) {
                environment.put(key, value);
            }
            return this;
        }

        public Builder environment(Map<String, String> environment) {
            this.environment = environment;
            return this;
        }

        public Builder metadata(String key, Object value) {
            if (metadata == null) {
                metadata = new LinkedHashMap<String, Object>();
            }
            if (key != null) {
                metadata.put(key, value);
            }
            return this;
        }

        public Builder metadata(Map<String, Object> metadata) {
            this.metadata = metadata;
            return this;
        }

        public SandboxCommand build() {
            return new SandboxCommand(this);
        }
    }
}
