package io.github.lnyocly.ai4j.agent.blueprint;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Validation result for one Agent Blueprint.
 */
public final class AgentBlueprintValidationReport {

    private final List<AgentBlueprintValidationIssue> issues;

    public AgentBlueprintValidationReport(List<AgentBlueprintValidationIssue> issues) {
        this.issues = issues == null
                ? new ArrayList<AgentBlueprintValidationIssue>()
                : new ArrayList<AgentBlueprintValidationIssue>(issues);
    }

    public static AgentBlueprintValidationReport valid() {
        return new AgentBlueprintValidationReport(Collections.<AgentBlueprintValidationIssue>emptyList());
    }

    public boolean isValid() {
        return getErrors().isEmpty();
    }

    public List<AgentBlueprintValidationIssue> getIssues() {
        return Collections.unmodifiableList(issues);
    }

    public List<AgentBlueprintValidationIssue> getErrors() {
        return bySeverity(AgentBlueprintValidationSeverity.ERROR);
    }

    public List<AgentBlueprintValidationIssue> getWarnings() {
        return bySeverity(AgentBlueprintValidationSeverity.WARNING);
    }

    public boolean hasIssueCode(String code) {
        for (AgentBlueprintValidationIssue issue : issues) {
            if (issue.getCode().equals(code)) {
                return true;
            }
        }
        return false;
    }

    private List<AgentBlueprintValidationIssue> bySeverity(AgentBlueprintValidationSeverity severity) {
        List<AgentBlueprintValidationIssue> result = new ArrayList<AgentBlueprintValidationIssue>();
        for (AgentBlueprintValidationIssue issue : issues) {
            if (issue.getSeverity() == severity) {
                result.add(issue);
            }
        }
        return Collections.unmodifiableList(result);
    }
}
