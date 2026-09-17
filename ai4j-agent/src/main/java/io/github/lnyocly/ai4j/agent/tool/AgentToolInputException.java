package io.github.lnyocly.ai4j.agent.tool;

/**
 * Signals that a tool call is invalid before the tool can perform its
 * external side effect.
 *
 * <p>This marker lets a durable host distinguish a deterministic caller
 * error from an execution failure whose side-effect state is unknown. Hosts
 * may record this exception as a terminal {@code FAILED} invocation and let
 * the model correct its arguments; all other failures should remain
 * reconciliation-required until the business system is checked.</p>
 */
public class AgentToolInputException extends IllegalArgumentException {

    public AgentToolInputException(String message) {
        super(message);
    }

    public AgentToolInputException(String message, Throwable cause) {
        super(message, cause);
    }
}
