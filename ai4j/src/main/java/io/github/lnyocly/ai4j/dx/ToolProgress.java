package io.github.lnyocly.ai4j.dx;

/**
 * Live progress callback optionally injected into {@link AiTool} methods.
 */
@FunctionalInterface
public interface ToolProgress {

    void emit(String message);

    default void emit(String message, Object details) {
        emit(message);
    }

    static ToolProgress noop() {
        return new ToolProgress() {
            @Override
            public void emit(String message) {
            }
        };
    }
}
