package io.github.lnyocly.ai4j.agent.permission;

import io.github.lnyocly.ai4j.agent.tool.AgentToolCall;

/**
 * Immutable policy input for one tool execution attempt.
 */
public final class AgentPermissionRequest {

    private final AgentToolCall toolCall;
    private final AgentExecutionEnvironment environment;

    private AgentPermissionRequest(Builder builder) {
        this.toolCall = builder.toolCall;
        this.environment = builder.environment == null ? AgentExecutionEnvironment.LOCAL : builder.environment;
    }

    public static Builder builder() {
        return new Builder();
    }

    public AgentToolCall getToolCall() {
        return toolCall;
    }

    public AgentExecutionEnvironment getEnvironment() {
        return environment;
    }

    public String getToolName() {
        return toolCall == null ? null : toolCall.getName();
    }

    public String getArguments() {
        return toolCall == null ? null : toolCall.getArguments();
    }

    public String getCallId() {
        return toolCall == null ? null : toolCall.getCallId();
    }

    public static final class Builder {
        private AgentToolCall toolCall;
        private AgentExecutionEnvironment environment;

        private Builder() {
        }

        public Builder toolCall(AgentToolCall toolCall) {
            this.toolCall = toolCall;
            return this;
        }

        public Builder environment(AgentExecutionEnvironment environment) {
            this.environment = environment;
            return this;
        }

        public AgentPermissionRequest build() {
            return new AgentPermissionRequest(this);
        }
    }
}
