package io.github.lnyocly.ai4j.coding;

import io.github.lnyocly.ai4j.agent.AgentResult;
import io.github.lnyocly.ai4j.agent.AgentExecutionStatus;
import io.github.lnyocly.ai4j.agent.tool.AgentToolCall;
import io.github.lnyocly.ai4j.agent.tool.AgentToolResult;
import io.github.lnyocly.ai4j.coding.loop.CodingStopReason;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CodingAgentResult {

    private String runId;

    private String sessionId;

    private String turnId;

    private String outputText;

    private Object rawResponse;

    private List<AgentToolCall> toolCalls;

    private List<AgentToolResult> toolResults;

    private int steps;

    @Builder.Default
    private int turns = 1;

    private CodingStopReason stopReason;

    private boolean autoContinued;

    private int autoFollowUpCount;

    private boolean lastCompactApplied;

    private Long inputTokens;

    private Long outputTokens;

    private Long totalTokens;

    private Long uncachedInputTokens;

    private Long cacheReadInputTokens;

    private Long cacheWriteInputTokens;

    private Long cacheCreationInputTokens;

    private Long reasoningTokens;

    private Double inputCost;

    private Double cacheReadInputCost;

    private Double cacheCreationInputCost;

    private Double outputCost;

    private Double totalCost;

    private String currency;

    /** Structured status propagated from the underlying Agent slice. */
    private AgentExecutionStatus executionStatus;

    /** Stable operation identity when a coding tool is waiting asynchronously. */
    private String operationId;

    /** Stable wait identity used by the Harness host to resume this slice. */
    private String waitId;

    public CodingAgentResult(String runId,
                              String sessionId,
                              String turnId,
                              String outputText,
                              Object rawResponse,
                              List<AgentToolCall> toolCalls,
                              List<AgentToolResult> toolResults,
                              int steps,
                              int turns,
                              CodingStopReason stopReason,
                              boolean autoContinued,
                              int autoFollowUpCount,
                              boolean lastCompactApplied) {
        this.runId = runId;
        this.sessionId = sessionId;
        this.turnId = turnId;
        this.outputText = outputText;
        this.rawResponse = rawResponse;
        this.toolCalls = toolCalls;
        this.toolResults = toolResults;
        this.steps = steps;
        this.turns = turns;
        this.stopReason = stopReason;
        this.autoContinued = autoContinued;
        this.autoFollowUpCount = autoFollowUpCount;
        this.lastCompactApplied = lastCompactApplied;
    }

    public static CodingAgentResult from(String sessionId, AgentResult result) {
        if (result == null) {
            return CodingAgentResult.builder()
                    .sessionId(sessionId)
                    .runId(null)
                    .turnId(null)
                    .build();
        }
        return CodingAgentResult.builder()
                .runId(result.getRunId())
                .sessionId(sessionId)
                .turnId(result.getTurnId())
                .outputText(result.getOutputText())
                .rawResponse(result.getRawResponse())
                .toolCalls(result.getToolCalls())
                .toolResults(result.getToolResults())
                .steps(result.getSteps() == null ? 0 : result.getSteps())
                .turns(1)
                .inputTokens(result.getInputTokens())
                .outputTokens(result.getOutputTokens())
                .totalTokens(result.getTotalTokens())
                .uncachedInputTokens(result.getUncachedInputTokens())
                .cacheReadInputTokens(result.getCacheReadInputTokens())
                .cacheWriteInputTokens(result.getCacheWriteInputTokens())
                .cacheCreationInputTokens(result.getCacheCreationInputTokens())
                .reasoningTokens(result.getReasoningTokens())
                .inputCost(result.getInputCost())
                .cacheReadInputCost(result.getCacheReadInputCost())
                .cacheCreationInputCost(result.getCacheCreationInputCost())
                .outputCost(result.getOutputCost())
                .totalCost(result.getTotalCost())
                .currency(result.getCurrency())
                .executionStatus(result.getExecutionStatus())
                .operationId(result.getOperationId())
                .waitId(result.getWaitId())
                .build();
    }
}
