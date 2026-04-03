package io.github.lnyocly.ai4j.coding.loop;

import lombok.Builder;
import lombok.Data;

@Data
@Builder(toBuilder = true)
public class CodingLoopDecision {

    public static final String CONTINUE_AFTER_TOOL_WORK = "CONTINUE_AFTER_TOOL_WORK";
    public static final String CONTINUE_AFTER_COMPACTION = "CONTINUE_AFTER_COMPACTION";
    public static final String CONTINUE_AUTONOMOUS_WORK = "CONTINUE_AUTONOMOUS_WORK";

    private int turnNumber;

    private boolean continueLoop;

    private String continueReason;

    private CodingStopReason stopReason;

    private String summary;

    private String continuationPrompt;

    private boolean compactApplied;

    public boolean isBlocked() {
        return stopReason == CodingStopReason.BLOCKED_BY_APPROVAL
                || stopReason == CodingStopReason.BLOCKED_BY_TOOL_ERROR;
    }
}
