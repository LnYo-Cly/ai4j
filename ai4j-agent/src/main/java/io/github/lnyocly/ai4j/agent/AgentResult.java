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

    private String outputText;

    private Object rawResponse;

    private List<AgentToolCall> toolCalls;

    private List<AgentToolResult> toolResults;

    private Integer steps;
}
