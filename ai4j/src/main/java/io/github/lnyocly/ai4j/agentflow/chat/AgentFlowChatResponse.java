package io.github.lnyocly.ai4j.agentflow.chat;

import io.github.lnyocly.ai4j.agentflow.AgentFlowUsage;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder(toBuilder = true)
@NoArgsConstructor(access = AccessLevel.PRIVATE)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class AgentFlowChatResponse {

    private String content;

    private String conversationId;

    private String messageId;

    private String taskId;

    private AgentFlowUsage usage;

    private Object raw;
}
