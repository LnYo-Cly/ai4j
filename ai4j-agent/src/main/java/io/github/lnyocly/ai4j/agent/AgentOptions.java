package io.github.lnyocly.ai4j.agent;

import io.github.lnyocly.ai4j.listener.StreamExecutionOptions;
import lombok.Builder;
import lombok.Data;

@Data
@Builder(toBuilder = true)
public class AgentOptions {

    @Builder.Default
    private int maxSteps = 0;

    @Builder.Default
    private boolean stream = false;

    @Builder.Default
    private StreamExecutionOptions streamExecution = StreamExecutionOptions.builder().build();
}
