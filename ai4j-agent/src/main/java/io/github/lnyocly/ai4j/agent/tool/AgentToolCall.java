package io.github.lnyocly.ai4j.agent.tool;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentToolCall {

    private String name;

    private String arguments;

    private String callId;

    private String type;
}
