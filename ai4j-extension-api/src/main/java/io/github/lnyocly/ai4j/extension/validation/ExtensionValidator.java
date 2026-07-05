package io.github.lnyocly.ai4j.extension.validation;

import io.github.lnyocly.ai4j.extension.DiscoveredExtension;
import io.github.lnyocly.ai4j.extension.ExtensionCapability;
import io.github.lnyocly.ai4j.extension.ExtensionInspectionSnapshot;
import io.github.lnyocly.ai4j.extension.ExtensionManifest;
import io.github.lnyocly.ai4j.extension.ExtensionRegistry;
import io.github.lnyocly.ai4j.extension.command.ExtensionCommandSpec;
import io.github.lnyocly.ai4j.extension.prompt.ExtensionPromptResource;
import io.github.lnyocly.ai4j.extension.resource.ExtensionResourceResolver;
import io.github.lnyocly.ai4j.extension.skill.ExtensionSkillResource;
import io.github.lnyocly.ai4j.extension.tool.ExtensionToolSpec;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class ExtensionValidator {

    private ExtensionValidator() {
    }

    public static ExtensionValidationReport validate(ExtensionRegistry registry, String extensionId) {
        if (registry == null) {
            throw new IllegalArgumentException("extension registry must not be null");
        }
        String normalized = ExtensionManifest.requireExtensionId(extensionId, "extension id");
        ExtensionManifest manifest = registry.inspect(normalized);
        DiscoveredExtension discovered = find(registry.list(), normalized);
        ExtensionValidationReport.Builder report = ExtensionValidationReport.builder()
                .extensionId(normalized)
                .sourceClassName(discovered == null ? null : discovered.getSourceClassName());

        validateManifest(manifest, report);
        ExtensionInspectionSnapshot snapshot = null;
        try {
            snapshot = registry.inspectRuntime(normalized);
        } catch (RuntimeException ex) {
            report.issue(error(
                    "runtime.apply.failed",
                    "extension apply failed during runtime inspection: " + safeMessage(ex),
                    normalized
            ));
        }
        if (snapshot != null) {
            validateRuntime(manifest, snapshot, registry.getExtensionClassLoader(normalized), report);
        }
        return report.build();
    }

    public static List<ExtensionValidationReport> validateAll(ExtensionRegistry registry) {
        if (registry == null) {
            throw new IllegalArgumentException("extension registry must not be null");
        }
        List<ExtensionValidationReport> reports = new ArrayList<ExtensionValidationReport>();
        for (DiscoveredExtension extension : registry.list()) {
            if (extension != null) {
                reports.add(validate(registry, extension.getManifest().getId()));
            }
        }
        return Collections.unmodifiableList(reports);
    }

    private static void validateManifest(ExtensionManifest manifest, ExtensionValidationReport.Builder report) {
        if (isBlank(manifest.getName())) {
            report.issue(warning("manifest.name.missing", "extension manifest should declare a human-readable name", manifest.getId()));
        }
        if (isBlank(manifest.getVersion())) {
            report.issue(warning("manifest.version.missing", "extension manifest should declare a version", manifest.getId()));
        }
        if (isBlank(manifest.getVendor())) {
            report.issue(warning("manifest.vendor.missing", "extension manifest should declare a vendor", manifest.getId()));
        }
    }

    private static void validateRuntime(ExtensionManifest manifest,
                                        ExtensionInspectionSnapshot snapshot,
                                        ClassLoader classLoader,
                                        ExtensionValidationReport.Builder report) {
        validateDeclaredCapability(manifest, ExtensionCapability.TOOL, snapshot.getTools().isEmpty(), report);
        validateDeclaredCapability(manifest, ExtensionCapability.COMMAND, snapshot.getCommands().isEmpty(), report);
        validateDeclaredCapability(manifest, ExtensionCapability.SKILL, snapshot.getSkills().isEmpty(), report);
        validateDeclaredCapability(manifest, ExtensionCapability.PROMPT, snapshot.getPrompts().isEmpty(), report);
        validateDeclaredCapability(manifest, ExtensionCapability.GUARDRAIL, snapshot.getGuardrails().isEmpty(), report);
        validateDeclaredCapability(manifest, ExtensionCapability.LIFECYCLE, snapshot.getLifecycleHooks().isEmpty(), report);

        for (ExtensionToolSpec tool : snapshot.getTools()) {
            validateTool(tool, report);
        }
        for (ExtensionCommandSpec command : snapshot.getCommands()) {
            validateCommand(command, report);
        }
        for (ExtensionSkillResource skill : snapshot.getSkills()) {
            validateSkill(skill, classLoader, report);
        }
        for (ExtensionPromptResource prompt : snapshot.getPrompts()) {
            validatePrompt(prompt, classLoader, report);
        }
    }

    private static void validateDeclaredCapability(ExtensionManifest manifest,
                                                   ExtensionCapability capability,
                                                   boolean emptyContribution,
                                                   ExtensionValidationReport.Builder report) {
        if (manifest.hasCapability(capability) && emptyContribution) {
            report.issue(warning(
                    "capability.empty",
                    "extension declares capability but contributes no " + capability.getId() + " resources",
                    manifest.getId() + ":" + capability.getId()
            ));
        }
    }

    private static void validateTool(ExtensionToolSpec tool, ExtensionValidationReport.Builder report) {
        String target = "tool:" + tool.getName();
        if (isBlank(tool.getDescription())) {
            report.issue(warning("tool.description.missing", "tool should declare a concise description", target));
        }
        String schema = tool.getInputSchema();
        if (isBlank(schema)) {
            report.issue(error("tool.input_schema.missing", "tool must declare an input schema", target));
            return;
        }
        String schemaError = ExtensionToolSchemaValidator.validate(schema);
        if (schemaError != null) {
            report.issue(error("tool.input_schema.invalid", schemaError, target));
        }
    }

    private static void validateCommand(ExtensionCommandSpec command, ExtensionValidationReport.Builder report) {
        String target = "command:" + command.getName();
        if (isBlank(command.getDescription())) {
            report.issue(warning("command.description.missing", "command should declare a concise description", target));
        }
        if (isBlank(command.getUsage())) {
            report.issue(warning("command.usage.missing", "command should declare CLI usage text", target));
        }
    }

    private static void validateSkill(ExtensionSkillResource skill,
                                      ClassLoader classLoader,
                                      ExtensionValidationReport.Builder report) {
        String target = "skill:" + skill.getName();
        if (isBlank(skill.getDescription())) {
            report.issue(warning("skill.description.missing", "skill should declare a concise description", target));
        }
        validateClasspathResource("skill.resource.missing", skill.getResourcePath(), classLoader, target, report);
    }

    private static void validatePrompt(ExtensionPromptResource prompt,
                                       ClassLoader classLoader,
                                       ExtensionValidationReport.Builder report) {
        String target = "prompt:" + prompt.getName();
        if (isBlank(prompt.getDescription())) {
            report.issue(warning("prompt.description.missing", "prompt should declare a concise description", target));
        }
        validateClasspathResource("prompt.resource.missing", prompt.getResourcePath(), classLoader, target, report);
    }

    private static void validateClasspathResource(String code,
                                                  String resourcePath,
                                                  ClassLoader classLoader,
                                                  String target,
                                                  ExtensionValidationReport.Builder report) {
        try {
            if (!ExtensionResourceResolver.existsStrict(resourcePath, classLoader)) {
                report.issue(error(code, "classpath resource not found: " + resourcePath, target));
            }
        } catch (RuntimeException ex) {
            report.issue(error(code, "classpath resource is not readable: " + safeMessage(ex), target));
        }
    }

    private static DiscoveredExtension find(List<DiscoveredExtension> extensions, String extensionId) {
        if (extensions == null) {
            return null;
        }
        for (DiscoveredExtension extension : extensions) {
            if (extension != null && extension.getManifest().getId().equals(extensionId)) {
                return extension;
            }
        }
        return null;
    }

    private static ExtensionValidationIssue error(String code, String message, String target) {
        return issue(ExtensionValidationSeverity.ERROR, code, message, target);
    }

    private static ExtensionValidationIssue warning(String code, String message, String target) {
        return issue(ExtensionValidationSeverity.WARNING, code, message, target);
    }

    private static ExtensionValidationIssue issue(ExtensionValidationSeverity severity, String code, String message, String target) {
        return ExtensionValidationIssue.builder()
                .severity(severity)
                .code(code)
                .message(message)
                .target(target)
                .build();
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private static String safeMessage(Throwable throwable) {
        String message = throwable == null ? null : throwable.getMessage();
        return isBlank(message) ? "unknown error" : message.trim();
    }
}
