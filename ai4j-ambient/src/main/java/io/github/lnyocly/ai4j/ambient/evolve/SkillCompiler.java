package io.github.lnyocly.ai4j.ambient.evolve;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.lnyocly.ai4j.agent.extension.ExtensionAgentTools;
import io.github.lnyocly.ai4j.agent.tool.AgentToolCall;
import io.github.lnyocly.ai4j.agent.tool.ToolExecutor;
import io.github.lnyocly.ai4j.ambient.support.AmbientSupport;
import io.github.lnyocly.ai4j.codec.JsonCodec;
import io.github.lnyocly.ai4j.extension.Ai4jExtension;
import io.github.lnyocly.ai4j.extension.ExtensionManifest;
import io.github.lnyocly.ai4j.extension.ExtensionRegistry;
import io.github.lnyocly.ai4j.extension.validation.ExtensionValidationReport;
import io.github.lnyocly.ai4j.extension.validation.ExtensionValidator;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * L3 RSI {@code SkillCompiler}: compiles repetitive multi-step tool trajectories into
 * a deterministic {@link CompiledSkillCandidate} tool descriptor and verifies it inside an
 * isolated Cordis {@code ExtensionRegistry} / forked Creator Mode sandbox without mutating
 * the parent runtime.
 */
public class SkillCompiler {

    public CompiledSkillCandidate compileFromTrajectories(String skillName,
                                                          String toolName,
                                                          List<TrajectoryRecord> trajectories,
                                                          String description) {
        return compileFromTrajectories(skillName, toolName, trajectories, description, "default", null, "meta-v1");
    }

    public CompiledSkillCandidate compileFromTrajectories(String skillName,
                                                          String toolName,
                                                          List<TrajectoryRecord> trajectories,
                                                          String description,
                                                          String scopeKey,
                                                          String diagnosisId,
                                                          String metaVersion) {
        String normalizedTool = ExtensionManifest.requireToolName(toolName, "compiled toolName");
        String normalizedSkill = AmbientSupport.trimToNull(skillName) == null ? normalizedTool : skillName.trim();
        if (trajectories == null || trajectories.isEmpty()) {
            throw new IllegalArgumentException("trajectories must not be empty for skill compilation");
        }

        // Group successful multi-step trajectories by tool signature and choose the dominant chain.
        Map<String, List<TrajectoryRecord>> bySignature = new LinkedHashMap<String, List<TrajectoryRecord>>();
        for (TrajectoryRecord trace : trajectories) {
            if (trace == null || !trace.isSuccess() || trace.getToolSteps() == null || trace.getToolSteps().size() < 2) {
                continue;
            }
            String sig = trace.toolSignature();
            List<TrajectoryRecord> group = bySignature.get(sig);
            if (group == null) {
                group = new ArrayList<TrajectoryRecord>();
                bySignature.put(sig, group);
            }
            group.add(trace);
        }
        if (bySignature.isEmpty()) {
            throw new IllegalArgumentException("no successful multi-step trajectories available to compile");
        }

        List<TrajectoryRecord> dominantGroup = null;
        for (List<TrajectoryRecord> group : bySignature.values()) {
            if (dominantGroup == null || group.size() > dominantGroup.size()) {
                dominantGroup = group;
            }
        }

        int stepCount = dominantGroup.get(0).getToolSteps().size();
        Set<String> schemaProps = new LinkedHashSet<String>();
        Map<String, String> propTypes = new LinkedHashMap<String, String>();
        List<CompiledSkillCandidate.CompiledStep> compiledSteps = new ArrayList<CompiledSkillCandidate.CompiledStep>(stepCount);

        for (int i = 0; i < stepCount; i++) {
            String stepTool = dominantGroup.get(0).getToolSteps().get(i).getToolName();
            List<Map<String, JsonNode>> parsedStepArgs = new ArrayList<Map<String, JsonNode>>();
            Set<String> allKeysAtStep = new LinkedHashSet<String>();
            for (TrajectoryRecord trace : dominantGroup) {
                Map<String, JsonNode> parsed = parseObjectMap(trace.getToolSteps().get(i).getArgumentsJson());
                parsedStepArgs.add(parsed);
                allKeysAtStep.addAll(parsed.keySet());
            }

            ObjectNode templateObj = JsonCodec.createObjectNode();
            for (String key : allKeysAtStep) {
                boolean varies = false;
                JsonNode firstVal = null;
                for (Map<String, JsonNode> args : parsedStepArgs) {
                    JsonNode val = args.get(key);
                    if (firstVal == null) {
                        firstVal = val;
                    } else if (!firstVal.equals(val)) {
                        varies = true;
                    }
                }
                if (varies || parsedStepArgs.size() == 1) {
                    schemaProps.add(key);
                    propTypes.put(key, inferJsonType(firstVal));
                    templateObj.put(key, "{{" + key + "}}");
                } else if (firstVal != null) {
                    templateObj.set(key, firstVal);
                }
            }

            compiledSteps.add(CompiledSkillCandidate.CompiledStep.builder()
                    .stepIndex(i)
                    .toolName(stepTool)
                    .argumentsTemplate(JsonCodec.toJson(templateObj))
                    .outputAlias("step_" + i)
                    .build());
        }

        String schemaJson = buildSchemaJson(schemaProps, propTypes);
        return CompiledSkillCandidate.builder()
                .candidateId("comp_" + UUID.randomUUID().toString().replace("-", ""))
                .scopeKey(scopeKey == null ? "default" : scopeKey)
                .diagnosisId(diagnosisId)
                .metaVersion(metaVersion == null ? "meta-v1" : metaVersion)
                .extensionId("compiled-" + normalizedTool.replace('_', '-'))
                .skillName(normalizedSkill)
                .toolName(normalizedTool)
                .description(AmbientSupport.trimToNull(description) == null
                        ? "Compiled deterministic multi-step pipeline for " + normalizedSkill
                        : description.trim())
                .inputSchemaJson(schemaJson)
                .steps(compiledSteps)
                .status(CandidateStatus.PROPOSED)
                .build();
    }

    /**
     * Verifies a {@link CompiledSkillCandidate} inside an isolated Cordis sandbox
     * ({@code parentRegistry.fork()} + disposable candidate {@code ExtensionRegistry})
     * without polluting {@code parentRegistry} or any live services/tools.
     */
    public SandboxVerificationResult verifyInIsolatedSandbox(ExtensionRegistry parentRegistry,
                                                             CompiledSkillCandidate candidate,
                                                             ToolExecutor underlyingDelegate,
                                                             List<SandboxCheck> checks) {
        if (candidate == null) {
            throw new IllegalArgumentException("compiled skill candidate must not be null");
        }
        Ai4jExtension sandboxExtension = candidate.toSandboxExtension(underlyingDelegate);
        String extId = sandboxExtension.manifest().getId();

        // Fork parent registry if present so any parent service lookups stay read-only and isolated.
        ExtensionRegistry forkedParent = parentRegistry == null ? null : parentRegistry.fork();
        ExtensionRegistry isolatedSandbox = ExtensionRegistry.of(sandboxExtension)
                .enable(extId)
                .exposeTool(candidate.getToolName());

        List<String> stepOutputs = new ArrayList<String>();
        boolean allChecksPassed = true;
        String failureReason = null;
        ExtensionValidationReport validationReport = null;

        try {
            validationReport = ExtensionValidator.validate(isolatedSandbox, extId);
            if (!validationReport.isValid()) {
                allChecksPassed = false;
                failureReason = "extension validation failed: " + validationReport.getIssues();
            } else {
                ExtensionAgentTools agentTools = ExtensionAgentTools.from(isolatedSandbox);
                String namespacedName = "plugin__" + extId + "__" + candidate.getToolName();
                if (checks != null) {
                    for (SandboxCheck check : checks) {
                        if (check == null) {
                            continue;
                        }
                        String output = agentTools.getToolExecutor().execute(AgentToolCall.builder()
                                .name(namespacedName)
                                .arguments(check.getArgumentsJson())
                                .callId("sandbox_" + stepOutputs.size())
                                .type("function")
                                .build());
                        stepOutputs.add(output);
                        if (check.getExpectedOutputContains() != null
                                && (output == null || !output.contains(check.getExpectedOutputContains()))) {
                            allChecksPassed = false;
                            failureReason = "sandbox output mismatch: expected '"
                                    + check.getExpectedOutputContains() + "' in " + output;
                            break;
                        }
                    }
                }
            }
        } catch (Exception e) {
            allChecksPassed = false;
            failureReason = "sandbox execution threw: " + e.getMessage();
        } finally {
            isolatedSandbox.close();
            if (forkedParent != null) {
                forkedParent.close();
            }
        }

        return SandboxVerificationResult.builder()
                .candidateId(candidate.getCandidateId())
                .toolName(candidate.getToolName())
                .passed(allChecksPassed)
                .validationValid(validationReport != null && validationReport.isValid())
                .outputs(stepOutputs)
                .failureReason(failureReason)
                .build();
    }

    private static Map<String, JsonNode> parseObjectMap(String json) {
        if (json == null || json.trim().isEmpty()) {
            return Collections.emptyMap();
        }
        try {
            JsonNode node = JsonCodec.readTreeLenient(json);
            if (node == null || !node.isObject()) {
                return Collections.emptyMap();
            }
            Map<String, JsonNode> map = new LinkedHashMap<String, JsonNode>();
            Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> entry = fields.next();
                map.put(entry.getKey(), entry.getValue());
            }
            return map;
        } catch (RuntimeException ignored) {
            return Collections.emptyMap();
        }
    }

    private static String inferJsonType(JsonNode value) {
        if (value == null || value.isNull()) {
            return "string";
        }
        if (value.isBoolean()) {
            return "boolean";
        }
        if (value.isIntegralNumber()) {
            return "integer";
        }
        if (value.isNumber()) {
            return "number";
        }
        if (value.isArray()) {
            return "array";
        }
        if (value.isObject()) {
            return "object";
        }
        return "string";
    }

    private static String buildSchemaJson(Set<String> props, Map<String, String> propTypes) {
        ObjectNode root = JsonCodec.createObjectNode();
        root.put("type", "object");
        ObjectNode properties = JsonCodec.createObjectNode();
        ArrayNode required = JsonCodec.createArrayNode();
        for (String prop : props) {
            ObjectNode propDef = JsonCodec.createObjectNode();
            propDef.put("type", propTypes.get(prop) == null ? "string" : propTypes.get(prop));
            propDef.put("description", "Compiled parameter " + prop);
            properties.set(prop, propDef);
            required.add(prop);
        }
        root.set("properties", properties);
        root.set("required", required);
        return JsonCodec.toJson(root);
    }

    @Data
    @Builder(toBuilder = true)
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SandboxCheck {
        private String argumentsJson;
        private String expectedOutputContains;
    }

    @Data
    @Builder(toBuilder = true)
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SandboxVerificationResult {
        private String candidateId;
        private String toolName;
        private boolean passed;
        private boolean validationValid;
        @Builder.Default
        private List<String> outputs = new ArrayList<String>();
        private String failureReason;
    }
}
