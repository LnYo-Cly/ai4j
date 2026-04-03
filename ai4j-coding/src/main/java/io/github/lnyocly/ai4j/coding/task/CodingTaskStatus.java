package io.github.lnyocly.ai4j.coding.task;

public enum CodingTaskStatus {
    QUEUED,
    STARTING,
    RUNNING,
    COMPLETED,
    FAILED,
    CANCELLED;

    public boolean isTerminal() {
        return this == COMPLETED || this == FAILED || this == CANCELLED;
    }
}
