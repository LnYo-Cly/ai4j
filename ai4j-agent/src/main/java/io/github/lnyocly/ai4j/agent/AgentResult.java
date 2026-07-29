package io.github.lnyocly.ai4j.agent;

import io.github.lnyocly.ai4j.agent.tool.AgentToolCall;
import io.github.lnyocly.ai4j.agent.tool.AgentToolResult;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentResult {

    private String runId;

    private String sessionId;

    private String turnId;

    private String outputText;

    private Object rawResponse;

    private List<AgentToolCall> toolCalls;

    private List<AgentToolResult> toolResults;

    private Integer steps;

    private Long inputTokens;

    private Long outputTokens;

    private Long totalTokens;

    private Long uncachedInputTokens;

    private Long cacheReadInputTokens;

    private Long cacheCreationInputTokens;

    private Long reasoningTokens;

    private Double inputCost;

    private Double cacheReadInputCost;

    private Double cacheCreationInputCost;

    private Double outputCost;

    private Double totalCost;

    private String currency;

    /** Compatibility constructor retained for the pre-cache-accounting result shape. */
    public AgentResult(String runId,
                       String sessionId,
                       String turnId,
                       String outputText,
                       Object rawResponse,
                       List<AgentToolCall> toolCalls,
                       List<AgentToolResult> toolResults,
                       Integer steps,
                       Long inputTokens,
                       Long outputTokens) {
        this.runId = runId;
        this.sessionId = sessionId;
        this.turnId = turnId;
        this.outputText = outputText;
        this.rawResponse = rawResponse;
        this.toolCalls = toolCalls;
        this.toolResults = toolResults;
        this.steps = steps;
        this.inputTokens = inputTokens;
        this.outputTokens = outputTokens;
    }
}
