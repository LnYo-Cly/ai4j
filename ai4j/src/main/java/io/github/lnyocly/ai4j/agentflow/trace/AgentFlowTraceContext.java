package io.github.lnyocly.ai4j.agentflow.trace;

import io.github.lnyocly.ai4j.agentflow.AgentFlowType;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder(toBuilder = true)
@NoArgsConstructor(access = AccessLevel.PRIVATE)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class AgentFlowTraceContext {

    private String executionId;

    private AgentFlowType type;

    private String operation;

    private boolean streaming;

    private long startedAt;

    private String baseUrl;

    private String webhookUrl;

    private String botId;

    private String workflowId;

    private String appId;

    private String configuredUserId;

    private String configuredConversationId;

    private Object request;
}
