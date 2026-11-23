package io.github.lnyocly.ai4j.agent.codeact;

/**
 * SPI for discovering and constructing default {@link CodeExecutor} implementations
 * via {@link java.util.ServiceLoader}.
 */
public interface CodeExecutorProvider {

    default int priority() {
        return 0;
    }

    default boolean isAvailable() {
        return true;
    }

    CodeExecutor create();
}
