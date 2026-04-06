package io.github.lnyocly.ai4j.agentflow;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder(toBuilder = true)
@NoArgsConstructor(access = AccessLevel.PRIVATE)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class AgentFlowUsage {

    private Integer inputTokens;

    private Integer outputTokens;

    private Integer totalTokens;

    private Object raw;
}
