package io.github.lnyocly.ai4j.extension.validation;

import io.github.lnyocly.ai4j.extension.Ai4jExtension;
import io.github.lnyocly.ai4j.extension.ExtensionCapability;
import io.github.lnyocly.ai4j.extension.ExtensionContext;
import io.github.lnyocly.ai4j.extension.ExtensionManifest;
import io.github.lnyocly.ai4j.extension.ExtensionRegistry;
import io.github.lnyocly.ai4j.extension.command.ExtensionCommandSpec;
import io.github.lnyocly.ai4j.extension.guardrail.ExtensionGuardrail;
import io.github.lnyocly.ai4j.extension.guardrail.GuardrailDecision;
import io.github.lnyocly.ai4j.extension.guardrail.GuardrailRequest;
import io.github.lnyocly.ai4j.extension.prompt.ExtensionPromptResource;
import io.github.lnyocly.ai4j.extension.skill.ExtensionSkillResource;
import io.github.lnyocly.ai4j.extension.tool.ExtensionToolCall;
import io.github.lnyocly.ai4j.extension.tool.ExtensionToolExecutor;
import io.github.lnyocly.ai4j.extension.tool.ExtensionToolSpec;
import org.junit.Assert;
import org.junit.Test;

import java.util.List;

public class ExtensionValidatorTest {

    @Test
    public void shouldPassCompleteExtensionWithResources() {
        ExtensionRegistry registry = ExtensionRegistry.of(new CompleteExtension());

        ExtensionValidationReport report = ExtensionValidator.validate(registry, "complete-pack");

        Assert.assertTrue(report.isValid());
        Assert.assertEquals("pass", report.getStatus());
        Assert.assertEquals(0, report.getErrorCount());
        Assert.assertEquals(0, report.getWarningCount());
    }

    @Test
    public void shouldReportAuthoringErrorsAndWarnings() {
        ExtensionRegistry registry = ExtensionRegistry.of(new BrokenExtension());

        ExtensionValidationReport report = ExtensionValidator.validate(registry, "broken-pack");

        Assert.assertFalse(report.isValid());
        Assert.assertEquals("fail", report.getStatus());
        Assert.assertTrue(report.getErrorCount() >= 3);
        Assert.assertTrue(report.getWarningCount() >= 3);
        Assert.assertTrue(hasIssue(report, "tool.input_schema.invalid"));
        Assert.assertTrue(hasIssue(report, "skill.resource.missing"));
        Assert.assertTrue(hasIssue(report, "prompt.resource.missing"));
        Assert.assertTrue(hasIssue(report, "command.usage.missing"));
    }

    @Test
    public void shouldReportApplyFailure() {
        ExtensionRegistry registry = ExtensionRegistry.of(new CapabilityMismatchExtension());

        ExtensionValidationReport report = ExtensionValidator.validate(registry, "capability-mismatch");

        Assert.assertFalse(report.isValid());
        Assert.assertTrue(hasIssue(report, "runtime.apply.failed"));
    }

    @Test
    public void shouldValidateAllDiscoveredExtensions() {
        ExtensionRegistry registry = ExtensionRegistry.of(new CompleteExtension(), new BrokenExtension());

        List<ExtensionValidationReport> reports = ExtensionValidator.validateAll(registry);

        Assert.assertEquals(2, reports.size());
        Assert.assertEquals("complete-pack", reports.get(0).getExtensionId());
        Assert.assertEquals("broken-pack", reports.get(1).getExtensionId());
    }

    private static boolean hasIssue(ExtensionValidationReport report, String code) {
        for (ExtensionValidationIssue issue : report.getIssues()) {
            if (code.equals(issue.getCode())) {
                return true;
            }
        }
        return false;
    }

    private static class CompleteExtension implements Ai4jExtension {
        public ExtensionManifest manifest() {
            return ExtensionManifest.builder()
                    .id("complete-pack")
                    .name("Complete Pack")
                    .version("1.0.0")
                    .vendor("tests")
                    .capability(ExtensionCapability.TOOL)
                    .capability(ExtensionCapability.COMMAND)
                    .capability(ExtensionCapability.SKILL)
                    .capability(ExtensionCapability.PROMPT)
                    .capability(ExtensionCapability.GUARDRAIL)
                    .build();
        }

        public void apply(ExtensionContext context) {
            context.tools().register(ExtensionToolSpec.builder()
                            .name("complete.echo")
                            .description("Echo arguments")
                            .inputSchema("{\"type\":\"object\",\"properties\":{\"text\":{\"type\":\"string\"}}}")
                            .build(),
                    new ExtensionToolExecutor() {
                        public String execute(ExtensionToolCall call) {
                            return call == null ? "" : call.getArguments();
                        }
                    });
            context.commands().register(ExtensionCommandSpec.builder()
                            .name("complete-echo")
                            .description("Echo command")
                            .usage("/complete-echo <text>")
                            .build(),
                    request -> request == null ? "" : request.getArguments());
            context.skills().register(ExtensionSkillResource.builder()
                    .name("complete-skill")
                    .description("Complete skill")
                    .resourcePath("skills/validator/SKILL.md")
                    .build());
            context.prompts().register(ExtensionPromptResource.builder()
                    .name("complete-prompt")
                    .description("Complete prompt")
                    .resourcePath("prompts/validator.md")
                    .build());
            context.guardrails().register(new ExtensionGuardrail() {
                public String name() {
                    return "complete-guardrail";
                }

                public GuardrailDecision evaluate(GuardrailRequest request) {
                    return GuardrailDecision.allow();
                }
            });
        }
    }

    private static class BrokenExtension implements Ai4jExtension {
        public ExtensionManifest manifest() {
            return ExtensionManifest.builder()
                    .id("broken-pack")
                    .capability(ExtensionCapability.TOOL)
                    .capability(ExtensionCapability.COMMAND)
                    .capability(ExtensionCapability.SKILL)
                    .capability(ExtensionCapability.PROMPT)
                    .build();
        }

        public void apply(ExtensionContext context) {
            context.tools().register(ExtensionToolSpec.builder()
                            .name("broken.tool")
                            .description("")
                            .inputSchema("not-json")
                            .build(),
                    new ExtensionToolExecutor() {
                        public String execute(ExtensionToolCall call) {
                            return "";
                        }
                    });
            context.commands().register(ExtensionCommandSpec.builder()
                            .name("broken-command")
                            .description("")
                            .build(),
                    request -> "");
            context.skills().register(ExtensionSkillResource.builder()
                    .name("broken-skill")
                    .resourcePath("skills/missing/SKILL.md")
                    .build());
            context.prompts().register(ExtensionPromptResource.builder()
                    .name("broken-prompt")
                    .resourcePath("prompts/missing.md")
                    .build());
        }
    }

    private static class CapabilityMismatchExtension implements Ai4jExtension {
        public ExtensionManifest manifest() {
            return ExtensionManifest.builder()
                    .id("capability-mismatch")
                    .name("Capability Mismatch")
                    .version("1.0.0")
                    .vendor("tests")
                    .capability(ExtensionCapability.TOOL)
                    .build();
        }

        public void apply(ExtensionContext context) {
            context.commands().register(ExtensionCommandSpec.builder()
                            .name("bad")
                            .description("bad")
                            .usage("/bad")
                            .build(),
                    request -> "");
        }
    }
}
