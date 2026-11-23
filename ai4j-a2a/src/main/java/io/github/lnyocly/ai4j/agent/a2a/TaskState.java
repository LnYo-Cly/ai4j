package io.github.lnyocly.ai4j.agent.a2a;

import java.util.Locale;

/**
 * A2A 1.0 task states — the lifecycle states of a task in the A2A protocol.
 *
 * <p>Accepted A2A 1.0 lifecycle values include submission, work, input/auth pauses, and terminal
 * completion, failure, cancellation, or rejection.</p>
 * <ul>
 *   <li>{@link #SUBMITTED} → {@link #WORKING}</li>
 *   <li>{@link #WORKING} → {@link #INPUT_REQUIRED} or {@link #AUTH_REQUIRED}</li>
 *   <li>{@link #WORKING} → {@link #COMPLETED}, {@link #FAILED}, or {@link #CANCELED}</li>
 * </ul>
 *
 * <p>The client should treat {@link #COMPLETED}, {@link #FAILED}, {@link #CANCELED}, and
 * {@link #REJECTED} as terminal.</p>
 */
public enum TaskState {

    UNSPECIFIED("unspecified", "TASK_STATE_UNSPECIFIED"),

    SUBMITTED("submitted", "TASK_STATE_SUBMITTED"),

    /**
     * Task is currently being processed.
     */
    WORKING("working", "TASK_STATE_WORKING"),

    INPUT_REQUIRED("input_required", "TASK_STATE_INPUT_REQUIRED"),

    AUTH_REQUIRED("auth_required", "TASK_STATE_AUTH_REQUIRED"),

    /**
     * Task completed successfully.
     */
    COMPLETED("completed", "TASK_STATE_COMPLETED"),

    /**
     * Task failed due to an error.
     */
    FAILED("failed", "TASK_STATE_FAILED"),

    /**
     * Task was canceled by the client.
     */
    CANCELED("canceled", "TASK_STATE_CANCELED"),

    REJECTED("rejected", "TASK_STATE_REJECTED");

    private final String value;
    private final String officialValue;

    TaskState(String value, String officialValue) {
        this.value = value;
        this.officialValue = officialValue;
    }

    /**
     * Returns the JSON value for this state (lowercase, as per A2A spec).
     */
    public String getValue() {
        return value;
    }

    /** Returns the A2A 1.0 protobuf/JSON enum value. */
    public String getOfficialValue() {
        return officialValue;
    }

    /**
     * Parses a string value to a TaskState. Case-insensitive; returns {@code null} if not recognized.
     */
    public static TaskState fromValue(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        if (normalized.startsWith("task_state_")) {
            normalized = normalized.substring("task_state_".length());
        }
        for (TaskState state : values()) {
            if (state.value.equals(normalized)) {
                return state;
            }
        }
        return null;
    }

    /**
     * Checks if this is a terminal state (no further transitions possible).
     */
    public boolean isTerminal() {
        return this == COMPLETED || this == FAILED || this == CANCELED || this == REJECTED;
    }

    /**
     * Checks if this is a success state.
     */
    public boolean isSuccess() {
        return this == COMPLETED;
    }

    /**
     * Checks if this is a failure state.
     */
    public boolean isFailure() {
        return this == FAILED || this == CANCELED || this == REJECTED;
    }

    public boolean isTransitionValid(TaskState next) {
        if (next == null || next == this || isTerminal()) {
            return false;
        }
        switch (this) {
            case UNSPECIFIED:
                return next == SUBMITTED;
            case SUBMITTED:
                return next == WORKING || next == INPUT_REQUIRED || next == AUTH_REQUIRED
                    || next == REJECTED || next == FAILED || next == CANCELED;
            case WORKING:
                return next == INPUT_REQUIRED || next == AUTH_REQUIRED || next == COMPLETED
                    || next == FAILED || next == CANCELED;
            case INPUT_REQUIRED:
            case AUTH_REQUIRED:
                return next == WORKING || next == REJECTED || next == FAILED || next == CANCELED;
            default:
                return false;
        }
    }

    @Override
    public String toString() {
        return value;
    }
}
