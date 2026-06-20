package io.github.lnyocly.ai4j.agent.sandbox;

/**
 * Base checked exception for sandbox provider/session operations.
 */
public class SandboxException extends Exception {

    public SandboxException(String message) {
        super(message);
    }

    public SandboxException(String message, Throwable cause) {
        super(message, cause);
    }
}
