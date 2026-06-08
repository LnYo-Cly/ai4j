package io.github.lnyocly.ai4j.extension.runtime;

import io.github.lnyocly.ai4j.extension.ExtensionCapability;
import io.github.lnyocly.ai4j.extension.ExtensionContext;
import io.github.lnyocly.ai4j.extension.ExtensionException;
import io.github.lnyocly.ai4j.extension.ExtensionManifest;
import io.github.lnyocly.ai4j.extension.command.CommandRegistry;
import io.github.lnyocly.ai4j.extension.command.ExtensionCommandHandler;
import io.github.lnyocly.ai4j.extension.command.ExtensionCommandSpec;
import io.github.lnyocly.ai4j.extension.guardrail.ExtensionGuardrail;
import io.github.lnyocly.ai4j.extension.guardrail.GuardrailRegistry;
import io.github.lnyocly.ai4j.extension.prompt.ExtensionPromptResource;
import io.github.lnyocly.ai4j.extension.prompt.PromptRegistry;
import io.github.lnyocly.ai4j.extension.skill.ExtensionSkillResource;
import io.github.lnyocly.ai4j.extension.skill.SkillRegistry;
import io.github.lnyocly.ai4j.extension.tool.ExtensionToolExecutor;
import io.github.lnyocly.ai4j.extension.tool.ExtensionToolSpec;
import io.github.lnyocly.ai4j.extension.tool.ToolRegistry;

public final class DefaultExtensionContext implements ExtensionContext {

    private final ExtensionManifest manifest;
    private final ExtensionRuntimeState runtimeState;
    private final ToolRegistry tools;
    private final CommandRegistry commands;
    private final SkillRegistry skills;
    private final PromptRegistry prompts;
    private final GuardrailRegistry guardrails;

    public DefaultExtensionContext(final ExtensionManifest manifest, final ExtensionRuntimeState runtimeState) {
        if (manifest == null) {
            throw new IllegalArgumentException("extension manifest must not be null");
        }
        if (runtimeState == null) {
            throw new IllegalArgumentException("extension runtime state must not be null");
        }
        this.manifest = manifest;
        this.runtimeState = runtimeState;
        this.tools = new ToolRegistry() {
            public void register(ExtensionToolSpec spec, ExtensionToolExecutor executor) {
                requireCapability(ExtensionCapability.TOOL);
                runtimeState.registerTool(manifest, spec, executor);
            }
        };
        this.commands = new CommandRegistry() {
            public void register(ExtensionCommandSpec spec, ExtensionCommandHandler handler) {
                requireCapability(ExtensionCapability.COMMAND);
                runtimeState.registerCommand(manifest, spec, handler);
            }
        };
        this.skills = new SkillRegistry() {
            public void register(ExtensionSkillResource resource) {
                requireCapability(ExtensionCapability.SKILL);
                runtimeState.registerSkill(manifest, resource);
            }
        };
        this.prompts = new PromptRegistry() {
            public void register(ExtensionPromptResource resource) {
                requireCapability(ExtensionCapability.PROMPT);
                runtimeState.registerPrompt(manifest, resource);
            }
        };
        this.guardrails = new GuardrailRegistry() {
            public void register(ExtensionGuardrail guardrail) {
                requireCapability(ExtensionCapability.GUARDRAIL);
                runtimeState.registerGuardrail(manifest, guardrail);
            }
        };
    }

    public ExtensionManifest manifest() {
        return manifest;
    }

    public ToolRegistry tools() {
        return tools;
    }

    public CommandRegistry commands() {
        return commands;
    }

    public SkillRegistry skills() {
        return skills;
    }

    public PromptRegistry prompts() {
        return prompts;
    }

    public GuardrailRegistry guardrails() {
        return guardrails;
    }

    private void requireCapability(ExtensionCapability capability) {
        if (!manifest.hasCapability(capability)) {
            throw new ExtensionException("extension " + manifest.getId() + " did not declare capability: " + capability.getId());
        }
    }
}
