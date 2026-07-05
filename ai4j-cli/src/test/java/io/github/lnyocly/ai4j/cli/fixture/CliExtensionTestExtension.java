package io.github.lnyocly.ai4j.cli.fixture;

import io.github.lnyocly.ai4j.extension.Ai4jExtension;
import io.github.lnyocly.ai4j.extension.ExtensionCapability;
import io.github.lnyocly.ai4j.extension.ExtensionContext;
import io.github.lnyocly.ai4j.extension.ExtensionManifest;
import io.github.lnyocly.ai4j.extension.command.ExtensionCommandSpec;
import io.github.lnyocly.ai4j.extension.guardrail.ExtensionGuardrail;
import io.github.lnyocly.ai4j.extension.guardrail.GuardrailDecision;
import io.github.lnyocly.ai4j.extension.guardrail.GuardrailRequest;
import io.github.lnyocly.ai4j.extension.lifecycle.AgentLifecycleEvent;
import io.github.lnyocly.ai4j.extension.lifecycle.AgentLifecycleHook;
import io.github.lnyocly.ai4j.extension.prompt.ExtensionPromptResource;
import io.github.lnyocly.ai4j.extension.skill.ExtensionSkillResource;
import io.github.lnyocly.ai4j.extension.tool.ExtensionToolCall;
import io.github.lnyocly.ai4j.extension.tool.ExtensionToolExecutor;
import io.github.lnyocly.ai4j.extension.tool.ExtensionToolSpec;

public class CliExtensionTestExtension implements Ai4jExtension {

    private static int applyCount;
    private static boolean invalidToolSchema;

    public ExtensionManifest manifest() {
        return ExtensionManifest.builder()
                .id("cli-test-pack")
                .name("CLI Test Pack")
                .version("1.0.0")
                .vendor("tests")
                .capability(ExtensionCapability.TOOL)
                .capability(ExtensionCapability.COMMAND)
                .capability(ExtensionCapability.SKILL)
                .capability(ExtensionCapability.PROMPT)
                .capability(ExtensionCapability.GUARDRAIL)
                .capability(ExtensionCapability.LIFECYCLE)
                .permission("network:example.test")
                .configPrefix("ai4j.extensions.cli-test")
                .build();
    }

    public void apply(ExtensionContext context) {
        applyCount++;
        context.tools().register(ExtensionToolSpec.builder()
                        .name("cli.echo")
                        .description("Echo arguments")
                        .inputSchema(invalidToolSchema ? "{\"type\":\"array\"}" : "{\"type\":\"object\"}")
                        .build(),
                new ExtensionToolExecutor() {
                    public String execute(ExtensionToolCall call) {
                        return call == null ? "" : call.getArguments();
                    }
                });
        context.commands().register(ExtensionCommandSpec.builder()
                        .name("cli-echo")
                        .description("Echo command")
                        .usage("/cli-echo <text>")
                        .build(),
                request -> request == null ? "" : request.getArguments());
        context.skills().register(ExtensionSkillResource.builder()
                .name("cli-skill")
                .description("CLI skill")
                .resourcePath("skills/cli/SKILL.md")
                .build());
        context.prompts().register(ExtensionPromptResource.builder()
                .name("cli-prompt")
                .description("CLI prompt")
                .resourcePath("prompts/cli.md")
                .build());
        context.guardrails().register(new ExtensionGuardrail() {
            public String name() {
                return "cli-guardrail";
            }

            public GuardrailDecision evaluate(GuardrailRequest request) {
                return GuardrailDecision.allow();
            }
        });
        context.lifecycle().register(new AgentLifecycleHook() {
            public String name() {
                return "cli-lifecycle";
            }

            public void onEvent(AgentLifecycleEvent event) {
            }
        });
    }

    public static void resetApplyCount() {
        applyCount = 0;
        invalidToolSchema = false;
    }

    public static int getApplyCount() {
        return applyCount;
    }

    public static void useInvalidToolSchema() {
        invalidToolSchema = true;
    }
}
