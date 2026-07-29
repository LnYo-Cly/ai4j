package io.github.lnyocly.ai4j.agent.model;

import io.github.lnyocly.ai4j.agent.tool.AgentToolCall;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentModelResult {

    private String reasoningText;

    private String outputText;

    private List<AgentToolCall> toolCalls;

    private List<Object> memoryItems;

    private Object rawResponse;

    private Long inputTokens;

    private Long outputTokens;

    private Long totalTokens;

    private Long uncachedInputTokens;

    private Long cacheReadInputTokens;

    private Long cacheCreationInputTokens;

    private Long reasoningTokens;

    /** Compatibility constructor retained for the pre-cache-accounting result shape. */
    public AgentModelResult(String reasoningText,
                            String outputText,
                            List<AgentToolCall> toolCalls,
                            List<Object> memoryItems,
                            Object rawResponse,
                            Long inputTokens,
                            Long outputTokens) {
        this.reasoningText = reasoningText;
        this.outputText = outputText;
        this.toolCalls = toolCalls;
        this.memoryItems = memoryItems;
        this.rawResponse = rawResponse;
        this.inputTokens = inputTokens;
        this.outputTokens = outputTokens;
    }
}
