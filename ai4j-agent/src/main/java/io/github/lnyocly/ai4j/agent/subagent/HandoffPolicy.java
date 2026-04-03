package io.github.lnyocly.ai4j.agent.subagent;

import lombok.Builder;
import lombok.Data;

import java.util.Set;

@Data
@Builder(toBuilder = true)
public class HandoffPolicy {

    @Builder.Default
    private boolean enabled = true;

    /**
     * Maximum nested handoff depth. 1 means lead -> subagent only.
     */
    @Builder.Default
    private int maxDepth = 1;

    /**
     * Retry count after the first failed attempt.
     */
    @Builder.Default
    private int maxRetries = 0;

    /**
     * Timeout for one handoff attempt in milliseconds, 0 disables timeout.
     */
    @Builder.Default
    private long timeoutMillis = 0L;

    /**
     * Optional allow-list by subagent tool name. Empty means allow all.
     */
    private Set<String> allowedTools;

    /**
     * Optional deny-list by subagent tool name.
     */
    private Set<String> deniedTools;

    @Builder.Default
    private HandoffFailureAction onDenied = HandoffFailureAction.FAIL;

    @Builder.Default
    private HandoffFailureAction onError = HandoffFailureAction.FAIL;

    private HandoffInputFilter inputFilter;
}
