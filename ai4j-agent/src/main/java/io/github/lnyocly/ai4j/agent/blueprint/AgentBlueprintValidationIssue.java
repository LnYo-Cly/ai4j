package io.github.lnyocly.ai4j.agent.blueprint;

/**
 * One deterministic validation issue produced by {@link AgentBlueprintValidator}.
 */
public final class AgentBlueprintValidationIssue {

    private final AgentBlueprintValidationSeverity severity;
    private final String path;
    private final String code;
    private final String message;

    public AgentBlueprintValidationIssue(AgentBlueprintValidationSeverity severity, String path, String code, String message) {
        if (severity == null) {
            throw new IllegalArgumentException("severity must not be null");
        }
        this.severity = severity;
        this.path = path == null ? "$" : path;
        this.code = code == null ? "blueprint.validation" : code;
        this.message = message == null ? "" : message;
    }

    public static AgentBlueprintValidationIssue error(String path, String code, String message) {
        return new AgentBlueprintValidationIssue(AgentBlueprintValidationSeverity.ERROR, path, code, message);
    }

    public static AgentBlueprintValidationIssue warning(String path, String code, String message) {
        return new AgentBlueprintValidationIssue(AgentBlueprintValidationSeverity.WARNING, path, code, message);
    }

    public AgentBlueprintValidationSeverity getSeverity() {
        return severity;
    }

    public String getPath() {
        return path;
    }

    public String getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }

    @Override
    public String toString() {
        return severity + " " + path + " " + code + ": " + message;
    }
}
