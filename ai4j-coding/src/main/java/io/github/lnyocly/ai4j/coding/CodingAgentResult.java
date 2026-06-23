package io.github.lnyocly.ai4j.coding;

import io.github.lnyocly.ai4j.agent.AgentResult;
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
                .steps(result.getSteps())
                .turns(1)
                .build();
    }
}
