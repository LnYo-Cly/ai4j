package io.github.lnyocly.ai4j.harness;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Host-selected budget for one bounded slice or a runReady batch. */
@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class HarnessRunBudget {

    @Builder.Default
    private int maxExecutions = 1;

    /** 0 means retain the Agent's configured maxSteps. */
    @Builder.Default
    private int maxSteps = 0;

    /** 0 means retain the Agent's configured wall clock timeout. */
    @Builder.Default
    private long maxWallTimeMillis = 0L;

    /** -1 means retain the Agent's configured token budget. */
    @Builder.Default
    private long maxTokenBudget = -1L;

    @Builder.Default
    private long leaseDurationMillis = 60_000L;

    private String workerId;
}
