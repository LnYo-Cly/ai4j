package io.github.lnyocly.ai4j.extension.validation;

import io.github.lnyocly.ai4j.extension.ExtensionManifest;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class ExtensionValidationReport {

    private final String extensionId;
    private final String sourceClassName;
    private final List<ExtensionValidationIssue> issues;

    private ExtensionValidationReport(Builder builder) {
        this.extensionId = ExtensionManifest.requireExtensionId(builder.extensionId, "extension id");
        this.sourceClassName = ExtensionManifest.emptyToNull(builder.sourceClassName);
        this.issues = builder.issues == null ? Collections.<ExtensionValidationIssue>emptyList()
                : Collections.unmodifiableList(new ArrayList<ExtensionValidationIssue>(builder.issues));
    }

    public String getExtensionId() {
        return extensionId;
    }

    public String getSourceClassName() {
        return sourceClassName;
    }

    public List<ExtensionValidationIssue> getIssues() {
        return issues;
    }

    public boolean isValid() {
        return getErrorCount() == 0;
    }

    public boolean hasWarnings() {
        return getWarningCount() > 0;
    }

    public int getErrorCount() {
        return count(ExtensionValidationSeverity.ERROR);
    }

    public int getWarningCount() {
        return count(ExtensionValidationSeverity.WARNING);
    }

    public String getStatus() {
        if (!isValid()) {
            return "fail";
        }
        return hasWarnings() ? "warn" : "pass";
    }

    public static Builder builder() {
        return new Builder();
    }

    private int count(ExtensionValidationSeverity severity) {
        int count = 0;
        for (ExtensionValidationIssue issue : issues) {
            if (issue != null && severity.equals(issue.getSeverity())) {
                count++;
            }
        }
        return count;
    }

    public static final class Builder {
        private String extensionId;
        private String sourceClassName;
        private final List<ExtensionValidationIssue> issues = new ArrayList<ExtensionValidationIssue>();

        private Builder() {
        }

        public Builder extensionId(String extensionId) {
            this.extensionId = extensionId;
            return this;
        }

        public Builder sourceClassName(String sourceClassName) {
            this.sourceClassName = sourceClassName;
            return this;
        }

        public Builder issue(ExtensionValidationIssue issue) {
            if (issue != null) {
                this.issues.add(issue);
            }
            return this;
        }

        public Builder issues(List<ExtensionValidationIssue> issues) {
            if (issues != null) {
                for (ExtensionValidationIssue issue : issues) {
                    issue(issue);
                }
            }
            return this;
        }

        public ExtensionValidationReport build() {
            return new ExtensionValidationReport(this);
        }
    }
}
