package io.github.lnyocly.ai4j.extension;

import io.github.lnyocly.ai4j.extension.command.CommandRegistry;
import io.github.lnyocly.ai4j.extension.guardrail.GuardrailRegistry;
import io.github.lnyocly.ai4j.extension.prompt.PromptRegistry;
import io.github.lnyocly.ai4j.extension.skill.SkillRegistry;
import io.github.lnyocly.ai4j.extension.tool.ToolRegistry;

public interface ExtensionContext {

    ExtensionManifest manifest();

    ToolRegistry tools();

    CommandRegistry commands();

    SkillRegistry skills();

    PromptRegistry prompts();

    GuardrailRegistry guardrails();
}
