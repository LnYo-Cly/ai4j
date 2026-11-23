package io.github.lnyocly.ai4j.ambient.evolve;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.lnyocly.ai4j.agent.tool.AgentToolCall;
import io.github.lnyocly.ai4j.agent.tool.ToolExecutor;
import io.github.lnyocly.ai4j.ambient.support.AmbientSupport;
import io.github.lnyocly.ai4j.codec.JsonCodec;
import io.github.lnyocly.ai4j.extension.Ai4jExtension;
import io.github.lnyocly.ai4j.extension.ExtensionCapability;
import io.github.lnyocly.ai4j.extension.ExtensionContext;
import io.github.lnyocly.ai4j.extension.ExtensionManifest;
import io.github.lnyocly.ai4j.extension.ServiceKey;
import io.github.lnyocly.ai4j.extension.tool.ExtensionToolCall;
import io.github.lnyocly.ai4j.extension.tool.ExtensionToolExecutor;
import io.github.lnyocly.ai4j.extension.tool.ExtensionToolSpec;
import io.github.lnyocly.ai4j.platform.openai.tool.Tool;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * L3 RSI artifact: a multi-step repetitive reasoning/tool pattern compiled into a
 * single deterministic tool descriptor and pipeline executor.
 */
@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class CompiledSkillCandidate implements RsiCandidate {

    public static final ServiceKey<CompiledSkillCandidate> SANDBOX_CANDIDATE_KEY =
            ServiceKey.of("ai4j.ambient.evolve.compiled-skill", CompiledSkillCandidate.class);

    private String candidateId;
    private String scopeKey;
    private String diagnosisId;
    @Builder.Default
    private String baseVersion = "v1";
    @Builder.Default
    private String targetVersion = "v2";
    @Builder.Default
    private String metaVersion = "meta-v1";
    private String extensionId;
    private String skillName;
    private String toolName;
    private String description;
    private String inputSchemaJson;
    @Builder.Default
    private List<CompiledStep> steps = new ArrayList<CompiledStep>();
    @Builder.Default
    private CandidateStatus status = CandidateStatus.PROPOSED;

    @Override
    public CandidateTier getTier() {
        return CandidateTier.L3_COMPILED_SKILL;
    }

    @Override
    public String getDigest() {
        StringBuilder sb = new StringBuilder();
        sb.append("L3|").append(AmbientSupport.emptyToEmpty(toolName))
                .append('|').append(AmbientSupport.emptyToEmpty(targetVersion))
                .append('|').append(AmbientSupport.emptyToEmpty(inputSchemaJson));
        if (steps != null) {
            for (CompiledStep step : steps) {
                sb.append('|').append(step.getToolName()).append(':').append(step.getArgumentsTemplate());
            }
        }
        return AmbientSupport.sha256Hex(sb.toString());
    }

    public ExtensionToolSpec toExtensionToolSpec() {
        return ExtensionToolSpec.builder()
                .name(AmbientSupport.requireNonBlank(toolName, "compiled toolName"))
                .description(description == null ? "Compiled deterministic skill tool" : description)
                .inputSchema(inputSchemaJson == null ? "{\"type\":\"object\"}" : inputSchemaJson)
                .build();
    }

    /**
     * Builds an OpenAI-compatible {@link Tool} descriptor for direct injection into {@code AgentToolRegistry}.
     */
    public Tool toOpenAiTool() {
        Map<String, Tool.Function.Property> props = new LinkedHashMap<String, Tool.Function.Property>();
        List<String> required = new ArrayList<String>();
        if (inputSchemaJson != null && !inputSchemaJson.trim().isEmpty()) {
            JsonNode schema = JsonCodec.readTree(inputSchemaJson);
            JsonNode propsNode = schema.get("properties");
            if (propsNode != null && propsNode.isObject()) {
                Iterator<Map.Entry<String, JsonNode>> fields = propsNode.fields();
                while (fields.hasNext()) {
                    Map.Entry<String, JsonNode> entry = fields.next();
                    JsonNode prop = entry.getValue();
                    String type = prop != null && prop.has("type") ? prop.get("type").asText("string") : "string";
                    String desc = prop != null && prop.has("description") ? prop.get("description").asText(null) : null;
                    props.put(entry.getKey(), new Tool.Function.Property(type, desc, null, null));
                }
            }
            JsonNode reqNode = schema.get("required");
            if (reqNode != null && reqNode.isArray()) {
                for (int i = 0; i < reqNode.size(); i++) {
                    String req = reqNode.get(i).asText();
                    if (req != null && !req.trim().isEmpty()) {
                        required.add(req);
                    }
                }
            }
        }
        Tool.Function.Parameter param = new Tool.Function.Parameter("object", props, required);
        Tool.Function fn = new Tool.Function(toolName, description, param);
        return new Tool("function", fn);
    }

    /**
     * Builds a deterministic {@link ToolExecutor} executing all compiled steps in one tool invocation.
     */
    public ToolExecutor toExecutor(final ToolExecutor underlyingDelegate) {
        if (underlyingDelegate == null) {
            throw new IllegalArgumentException("underlying tool executor must not be null");
        }
        final List<CompiledStep> compiledSteps = steps == null
                ? Collections.<CompiledStep>emptyList()
                : new ArrayList<CompiledStep>(steps);
        return new ToolExecutor() {
            @Override
            public String execute(AgentToolCall call) throws Exception {
                Map<String, String> variables = extractInitialVariables(call == null ? null : call.getArguments());
                String lastOutput = "";
                List<Map<String, Object>> traceOutputs = new ArrayList<Map<String, Object>>();

                for (int i = 0; i < compiledSteps.size(); i++) {
                    CompiledStep step = compiledSteps.get(i);
                    String renderedArgs = renderTemplate(step.getArgumentsTemplate(), variables);
                    AgentToolCall stepCall = AgentToolCall.builder()
                            .name(step.getToolName())
                            .arguments(renderedArgs)
                            .callId((call == null || call.getCallId() == null ? "compiled" : call.getCallId())
                                    + "_step_" + i)
                            .type("function")
                            .build();
                    String stepOutput = underlyingDelegate.execute(stepCall);
                    lastOutput = stepOutput == null ? "" : stepOutput;

                    String alias = AmbientSupport.trimToNull(step.getOutputAlias());
                    if (alias == null) {
                        alias = "step_" + i;
                    }
                    String extracted = extractValue(lastOutput, step);
                    variables.put("prev.output", lastOutput);
                    variables.put("prev.extracted", extracted);
                    variables.put(alias + ".output", lastOutput);
                    variables.put(alias + ".extracted", extracted);
                    captureJsonVariables(variables, "prev", lastOutput);
                    captureJsonVariables(variables, alias, lastOutput);

                    Map<String, Object> record = new LinkedHashMap<String, Object>();
                    record.put("step", Integer.valueOf(i));
                    record.put("tool", step.getToolName());
                    record.put("extracted", extracted);
                    record.put("output", lastOutput);
                    traceOutputs.add(record);
                }

                ObjectNode result = JsonCodec.createObjectNode();
                result.put("compiledTool", toolName);
                result.put("stepsExecuted", compiledSteps.size());
                result.put("finalOutput", lastOutput);
                result.set("stepOutputs", JsonCodec.valueToTree(traceOutputs));
                return JsonCodec.toJson(result);
            }
        };
    }

    public ExtensionToolExecutor toExtensionToolExecutor(ToolExecutor underlyingDelegate) {
        final ToolExecutor delegate = toExecutor(underlyingDelegate);
        return new ExtensionToolExecutor() {
            @Override
            public String execute(ExtensionToolCall call) {
                try {
                    AgentToolCall agentCall = AgentToolCall.builder()
                            .name(call == null ? toolName : call.getName())
                            .arguments(call == null ? null : call.getArguments())
                            .callId("ext_" + toolName)
                            .type("function")
                            .build();
                    return delegate.execute(agentCall);
                } catch (RuntimeException ex) {
                    throw ex;
                } catch (Exception ex) {
                    throw new IllegalStateException("compiled skill execution failed: " + ex.getMessage(), ex);
                }
            }
        };
    }

    /**
     * Wraps this compiled candidate into an isolated {@link Ai4jExtension} suitable
     * for verification inside a Cordis Creator Mode sandbox.
     */
    public Ai4jExtension toSandboxExtension(final ToolExecutor underlyingDelegate) {
        final String effectiveExtensionId = AmbientSupport.trimToNull(extensionId) == null
                ? "compiled-" + sanitizeExtensionId(skillName != null ? skillName : toolName)
                : extensionId.trim();
        final CompiledSkillCandidate self = this;
        return new Ai4jExtension() {
            @Override
            public ExtensionManifest manifest() {
                return ExtensionManifest.builder()
                        .id(effectiveExtensionId)
                        .name("Compiled Skill: " + toolName)
                        .version(targetVersion == null ? "v2" : targetVersion)
                        .vendor("ai4j-ambient-rsi")
                        .capability(ExtensionCapability.TOOL)
                        .build();
            }

            @Override
            public void apply(ExtensionContext context) {
                context.provide(SANDBOX_CANDIDATE_KEY, self);
                context.tools().register(toExtensionToolSpec(), toExtensionToolExecutor(underlyingDelegate));
            }
        };
    }

    private static Map<String, String> extractInitialVariables(String argumentsJson) {
        Map<String, String> vars = new LinkedHashMap<String, String>();
        if (argumentsJson == null || argumentsJson.trim().isEmpty()) {
            return vars;
        }
        JsonNode root = JsonCodec.readTreeLenient(argumentsJson);
        if (root != null && root.isObject()) {
            Iterator<Map.Entry<String, JsonNode>> fields = root.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> entry = fields.next();
                JsonNode val = entry.getValue();
                if (val != null && !val.isNull()) {
                    vars.put(entry.getKey(), val.isValueNode() ? val.asText() : JsonCodec.toJson(val));
                }
            }
        }
        return vars;
    }

    private static void captureJsonVariables(Map<String, String> vars, String prefix, String rawOutput) {
        if (rawOutput == null || !rawOutput.trim().startsWith("{")) {
            return;
        }
        try {
            JsonNode node = JsonCodec.readTreeLenient(rawOutput);
            if (node != null && node.isObject()) {
                Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
                while (fields.hasNext()) {
                    Map.Entry<String, JsonNode> entry = fields.next();
                    JsonNode val = entry.getValue();
                    if (val != null && !val.isNull()) {
                        vars.put(prefix + "." + entry.getKey(),
                                val.isValueNode() ? val.asText() : JsonCodec.toJson(val));
                    }
                }
            }
        } catch (RuntimeException ignored) {
            // Output may be plain text rather than JSON.
        }
    }

    private static String extractValue(String output, CompiledStep step) {
        if (output == null) {
            return "";
        }
        String jsonField = AmbientSupport.trimToNull(step.getJsonFieldExtract());
        if (jsonField != null && output.trim().startsWith("{")) {
            try {
                JsonNode node = JsonCodec.readTreeLenient(output);
                if (node != null && node.has(jsonField) && !node.get(jsonField).isNull()) {
                    JsonNode field = node.get(jsonField);
                    return field.isValueNode() ? field.asText() : JsonCodec.toJson(field);
                }
            } catch (RuntimeException ignored) {
                // Fall back to regex/full output.
            }
        }
        String regex = AmbientSupport.trimToNull(step.getRegexExtractPattern());
        if (regex != null) {
            Matcher matcher = Pattern.compile(regex).matcher(output);
            if (matcher.find()) {
                return matcher.groupCount() >= 1 ? matcher.group(1) : matcher.group(0);
            }
        }
        return output;
    }

    static String renderTemplate(String template, Map<String, String> variables) {
        if (template == null || template.trim().isEmpty()) {
            return "{}";
        }
        String rendered = template;
        for (Map.Entry<String, String> entry : variables.entrySet()) {
            String token = "{{" + entry.getKey() + "}}";
            if (rendered.contains(token)) {
                rendered = rendered.replace(token, escapeJsonString(entry.getValue()));
            }
        }
        return rendered;
    }

    private static String escapeJsonString(String value) {
        if (value == null) {
            return "";
        }
        String serialized = JsonCodec.toJson(value);
        if (serialized.length() >= 2 && serialized.startsWith("\"") && serialized.endsWith("\"")) {
            return serialized.substring(1, serialized.length() - 1);
        }
        return serialized;
    }

    private static String sanitizeExtensionId(String raw) {
        if (raw == null || raw.trim().isEmpty()) {
            return "skill";
        }
        StringBuilder sb = new StringBuilder();
        String trimmed = raw.trim().toLowerCase(java.util.Locale.ROOT);
        for (int i = 0; i < trimmed.length(); i++) {
            char c = trimmed.charAt(i);
            if ((c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '.' || c == '_' || c == '-') {
                sb.append(c);
            } else {
                sb.append('-');
            }
        }
        return sb.length() == 0 || !Character.isLetterOrDigit(sb.charAt(0))
                ? "skill-" + sb.toString()
                : sb.toString();
    }

    @Data
    @Builder(toBuilder = true)
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CompiledStep {
        private int stepIndex;
        private String toolName;
        private String argumentsTemplate;
        private String outputAlias;
        private String jsonFieldExtract;
        private String regexExtractPattern;
    }
}
