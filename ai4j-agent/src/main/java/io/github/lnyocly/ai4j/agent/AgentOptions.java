package io.github.lnyocly.ai4j.agent;

import io.github.lnyocly.ai4j.listener.StreamExecutionOptions;
import lombok.Builder;
import lombok.Data;

@Data
@Builder(toBuilder = true)
public class AgentOptions {

    public static final int DEFAULT_MAX_STEPS = 20;
    public static final long DEFAULT_WALL_CLOCK_TIMEOUT_MS = 300_000L;
    public static final long UNLIMITED_TOKEN_BUDGET = -1L;

    @Builder.Default
    private int maxSteps = DEFAULT_MAX_STEPS;

    @Builder.Default
    private boolean stream = false;

    @Builder.Default
    private StreamExecutionOptions streamExecution = StreamExecutionOptions.builder().build();

    @Builder.Default
    private long wallClockTimeoutMillis = DEFAULT_WALL_CLOCK_TIMEOUT_MS;

    @Builder.Default
    private long maxTokenBudget = UNLIMITED_TOKEN_BUDGET;
}
