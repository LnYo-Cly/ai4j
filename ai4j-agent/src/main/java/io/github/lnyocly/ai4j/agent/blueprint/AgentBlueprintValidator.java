package io.github.lnyocly.ai4j.agent.blueprint;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Deterministic validator for P1-A single-agent blueprints.
 */
public class AgentBlueprintValidator {

    private static final Pattern ID_PATTERN = Pattern.compile("^[A-Za-z0-9._-]+$");
    private static final Set<String> WORKFLOW_MODES = new LinkedHashSet<String>(Arrays.asList("react", "codeact"));
    private static final Set<String> KNOWN_APPROVALS = new LinkedHashSet<String>(Arrays.asList("always", "safe", "never", "manual", "auto", "deny"));

    public AgentBlueprintValidationReport validate(AgentBlueprint blueprint) {
        List<AgentBlueprintValidationIssue> issues = new ArrayList<AgentBlueprintValidationIssue>();
        if (blueprint == null) {
            issues.add(AgentBlueprintValidationIssue.error("$", "blueprint.required", "Agent Blueprint document is required."));
            return new AgentBlueprintValidationReport(issues);
        }

        validateVersion(blueprint, issues);
        validateId(blueprint, issues);
        validateModel(blueprint.getModel(), issues);
        validatePlugins(blueprint.getPlugins(), issues);
        validateTools(blueprint.getTools(), issues);
        validateSession(blueprint.getSession(), issues);
        validateSandbox(blueprint.getSandbox(), issues);
        validateWorkflow(blueprint.getWorkflow(), issues);
        validateUnknownTopLevel(blueprint, issues);
        return new AgentBlueprintValidationReport(issues);
    }

    private void validateVersion(AgentBlueprint blueprint, List<AgentBlueprintValidationIssue> issues) {
        if (isBlank(blueprint.getVersion())) {
            issues.add(error("$.version", "blueprint.version.required", "version is required and must be ai4j.agent/v1."));
        } else if (!AgentBlueprint.VERSION_V1.equals(trim(blueprint.getVersion()))) {
            issues.add(error("$.version", "blueprint.version.unsupported", "Unsupported blueprint version: " + trim(blueprint.getVersion()) + "."));
        }
    }

    private void validateId(AgentBlueprint blueprint, List<AgentBlueprintValidationIssue> issues) {
        if (isBlank(blueprint.getId())) {
            issues.add(error("$.id", "blueprint.id.required", "id is required."));
        } else if (!ID_PATTERN.matcher(trim(blueprint.getId())).matches()) {
            issues.add(error("$.id", "blueprint.id.invalid", "id must contain only letters, digits, '.', '_' or '-'."));
        }
    }

    private void validateModel(AgentBlueprintModel model, List<AgentBlueprintValidationIssue> issues) {
        if (model == null) {
            issues.add(error("$.model", "blueprint.model.required", "model is required."));
            return;
        }
        if (isBlank(model.getProvider())) {
            issues.add(error("$.model.provider", "blueprint.model.provider.required", "model.provider is required."));
        }
        if (isBlank(model.getModel()) && isBlank(model.getProfile())) {
            issues.add(error("$.model.model", "blueprint.model.selector.required", "model.model or model.profile is required."));
        }
    }

    private void validatePlugins(List<AgentBlueprintPlugin> plugins, List<AgentBlueprintValidationIssue> issues) {
        if (plugins == null) {
            return;
        }
        for (int i = 0; i < plugins.size(); i++) {
            AgentBlueprintPlugin plugin = plugins.get(i);
            if (plugin == null || isBlank(plugin.getId())) {
                issues.add(error("$.plugins[" + i + "].id", "blueprint.plugin.id.required", "plugins[].id is required."));
            }
        }
    }

    private void validateTools(List<AgentBlueprintTool> tools, List<AgentBlueprintValidationIssue> issues) {
        if (tools == null) {
            return;
        }
        for (int i = 0; i < tools.size(); i++) {
            AgentBlueprintTool tool = tools.get(i);
            if (tool == null || isBlank(tool.getRef())) {
                issues.add(error("$.tools[" + i + "].ref", "blueprint.tool.ref.required", "tools[].ref is required."));
                continue;
            }
            String approval = trim(tool.getApproval());
            if (approval != null && !KNOWN_APPROVALS.contains(approval)) {
                issues.add(warning("$.tools[" + i + "].approval", "blueprint.tool.approval.unknown", "Unknown tool approval value: " + approval + "."));
            }
        }
    }

    private void validateSession(AgentBlueprintSession session, List<AgentBlueprintValidationIssue> issues) {
        if (session == null) {
            return;
        }
        AgentBlueprintMemory memory = session.getMemory();
        if (memory != null && Boolean.TRUE.equals(memory.getEnabled()) && isBlank(memory.getScope()) && isBlank(memory.getStore())) {
            issues.add(warning("$.session.memory", "blueprint.memory.scope.warning", "session.memory.enabled is true but neither scope nor store is declared."));
        }
        AgentBlueprintCompact compact = session.getCompact();
        if (compact == null || compact.getTrigger() == null) {
            return;
        }
        AgentBlueprintCompactTrigger trigger = compact.getTrigger();
        Double contextRatio = trigger.getContextRatio();
        if (contextRatio != null && (contextRatio.doubleValue() <= 0.0d || contextRatio.doubleValue() > 1.0d)) {
            issues.add(error("$.session.compact.trigger.contextRatio", "blueprint.compact.contextRatio.invalid", "contextRatio must be greater than 0 and less than or equal to 1."));
        }
        Integer maxTurns = trigger.getMaxTurns();
        if (maxTurns != null && maxTurns.intValue() <= 0) {
            issues.add(error("$.session.compact.trigger.maxTurns", "blueprint.compact.maxTurns.invalid", "compact trigger maxTurns must be positive."));
        }
    }

    private void validateSandbox(AgentBlueprintSandbox sandbox, List<AgentBlueprintValidationIssue> issues) {
        if (sandbox == null || !Boolean.TRUE.equals(sandbox.getEnabled())) {
            return;
        }
        if (isBlank(sandbox.getProvider()) && isBlank(sandbox.getProfile())) {
            issues.add(error("$.sandbox", "blueprint.sandbox.selector.required", "sandbox.provider or sandbox.profile is required when sandbox.enabled is true."));
        }
    }

    private void validateWorkflow(AgentBlueprintWorkflow workflow, List<AgentBlueprintValidationIssue> issues) {
        if (workflow == null) {
            return;
        }
        String mode = trim(workflow.getMode());
        if (mode != null && !WORKFLOW_MODES.contains(mode)) {
            issues.add(error("$.workflow.mode", "blueprint.workflow.mode.invalid", "workflow.mode must be one of: react, codeact."));
        }
        Integer maxTurns = workflow.getMaxTurns();
        if (maxTurns != null && maxTurns.intValue() <= 0) {
            issues.add(error("$.workflow.maxTurns", "blueprint.workflow.maxTurns.invalid", "workflow.maxTurns must be positive."));
        }
    }

    private void validateUnknownTopLevel(AgentBlueprint blueprint, List<AgentBlueprintValidationIssue> issues) {
        List<String> unknownFields = blueprint.getUnknownFields();
        if (unknownFields == null) {
            return;
        }
        for (String field : unknownFields) {
            if (!isBlank(field)) {
                issues.add(warning("$." + field, "blueprint.field.unknown", "Unknown top-level field: " + field + "."));
            }
        }
    }

    private static AgentBlueprintValidationIssue error(String path, String code, String message) {
        return AgentBlueprintValidationIssue.error(path, code, message);
    }

    private static AgentBlueprintValidationIssue warning(String path, String code, String message) {
        return AgentBlueprintValidationIssue.warning(path, code, message);
    }

    private static String trim(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.length() == 0 ? null : trimmed;
    }

    private static boolean isBlank(String value) {
        return trim(value) == null;
    }
}
