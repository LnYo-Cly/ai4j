package io.github.lnyocly.ai4j.agent.a2a;

/**
 * A2A 1.0 task states — the lifecycle states of a task in the A2A protocol.
 *
 * <p>State transitions (per A2A spec):</p>
 * <ul>
 *   <li>{@link #WORKING} → {@link #COMPLETED}</li>
 *   <li>{@link #WORKING} → {@link #FAILED}</li>
 *   <li>{@link #WORKING} → {@link #CANCELED}</li>
 * </ul>
 *
 * <p>The client should poll for status updates until the task reaches a terminal state
 * ({@link #COMPLETED}, {@link #FAILED}, or {@link #CANCELED}).</p>
 */
public enum TaskState {

    /**
     * Task is currently being processed.
     */
    WORKING("working"),

    /**
     * Task completed successfully.
     */
    COMPLETED("completed"),

    /**
     * Task failed due to an error.
     */
    FAILED("failed"),

    /**
     * Task was canceled by the client.
     */
    CANCELED("canceled");

    private final String value;

    TaskState(String value) {
        this.value = value;
    }

    /**
     * Returns the JSON value for this state (lowercase, as per A2A spec).
     */
    public String getValue() {
        return value;
    }

    /**
     * Parses a string value to a TaskState. Case-insensitive; returns {@code null} if not recognized.
     */
    public static TaskState fromValue(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        String normalized = value.trim().toLowerCase();
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
        return this == COMPLETED || this == FAILED || this == CANCELED;
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
        return this == FAILED || this == CANCELED;
    }

    public boolean isTransitionValid(TaskState next) {
        return this == WORKING && next != null && next != WORKING;
    }

    @Override
    public String toString() {
        return value;
    }
}