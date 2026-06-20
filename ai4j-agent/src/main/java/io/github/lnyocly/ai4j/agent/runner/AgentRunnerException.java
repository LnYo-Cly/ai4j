package io.github.lnyocly.ai4j.agent.runner;

/**
 * Base checked exception for remote Agent Runner provider/session operations.
 */
public class AgentRunnerException extends Exception {

    public AgentRunnerException(String message) {
        super(message);
    }

    public AgentRunnerException(String message, Throwable cause) {
        super(message, cause);
    }
}
