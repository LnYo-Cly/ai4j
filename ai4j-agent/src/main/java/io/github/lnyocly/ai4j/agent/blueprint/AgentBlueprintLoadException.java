package io.github.lnyocly.ai4j.agent.blueprint;

/**
 * Raised when a blueprint document cannot be parsed or mapped.
 */
public class AgentBlueprintLoadException extends RuntimeException {

    private final String code;

    public AgentBlueprintLoadException(String code, String message) {
        super(message);
        this.code = code == null ? "blueprint.load" : code;
    }

    public AgentBlueprintLoadException(String code, String message, Throwable cause) {
        super(message, cause);
        this.code = code == null ? "blueprint.load" : code;
    }

    public String getCode() {
        return code;
    }
}
