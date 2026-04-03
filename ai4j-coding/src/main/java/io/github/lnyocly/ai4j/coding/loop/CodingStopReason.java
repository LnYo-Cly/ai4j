package io.github.lnyocly.ai4j.coding.loop;

public enum CodingStopReason {
    COMPLETED,
    NEEDS_USER_INPUT,
    BLOCKED_BY_APPROVAL,
    BLOCKED_BY_TOOL_ERROR,
    MAX_AUTO_FOLLOWUPS_REACHED,
    MAX_TOTAL_TURNS_REACHED,
    INTERRUPTED,
    ERROR
}
