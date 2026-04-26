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
}
