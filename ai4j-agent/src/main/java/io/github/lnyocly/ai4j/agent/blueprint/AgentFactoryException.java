package io.github.lnyocly.ai4j.agent.blueprint;

/**
 * Stable exception type for AgentFactory mapping failures.
 */
public class AgentFactoryException extends RuntimeException {

    private final String code;
    private final AgentBlueprintValidationReport validationReport;

    public AgentFactoryException(String code, String message) {
        this(code, message, null, null);
    }

    public AgentFactoryException(String code, String message, AgentBlueprintValidationReport validationReport) {
        this(code, message, validationReport, null);
    }

    public AgentFactoryException(String code, String message, AgentBlueprintValidationReport validationReport, Throwable cause) {
        super(message, cause);
        this.code = code;
        this.validationReport = validationReport;
    }

    public String getCode() {
        return code;
    }

    public AgentBlueprintValidationReport getValidationReport() {
        return validationReport;
    }
}
