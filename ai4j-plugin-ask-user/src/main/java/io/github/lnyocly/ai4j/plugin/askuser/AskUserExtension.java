package io.github.lnyocly.ai4j.plugin.askuser;

import io.github.lnyocly.ai4j.extension.Ai4jExtension;
import io.github.lnyocly.ai4j.extension.ExtensionCapability;
import io.github.lnyocly.ai4j.extension.ExtensionContext;
import io.github.lnyocly.ai4j.extension.ExtensionManifest;
import io.github.lnyocly.ai4j.extension.command.ExtensionCommandRequest;
import io.github.lnyocly.ai4j.extension.command.ExtensionCommandSpec;
import io.github.lnyocly.ai4j.extension.prompt.ExtensionPromptResource;
import io.github.lnyocly.ai4j.extension.skill.ExtensionSkillResource;
import io.github.lnyocly.ai4j.extension.tool.ExtensionToolCall;
import io.github.lnyocly.ai4j.extension.tool.ExtensionToolExecutor;
import io.github.lnyocly.ai4j.extension.tool.ExtensionToolSpec;

/**
 * Official sample extension for host-mediated agent-to-user questions.
 */
public final class AskUserExtension implements Ai4jExtension {

    public static final String EXTENSION_ID = "ask-user";
    public static final String TOOL_NAME = "ask_user";
    public static final String COMMAND_NAME = "ask-user";
    public static final String SKILL_NAME = "ask-user-collaboration";
    public static final String PROMPT_NAME = "ask-user-question";

    private static final String VERSION = "2.3.0";

    public ExtensionManifest manifest() {
        return ExtensionManifest.builder()
                .id(EXTENSION_ID)
                .name("Ask User")
                .version(VERSION)
                .vendor("ai4j")
                .capability(ExtensionCapability.TOOL)
                .capability(ExtensionCapability.COMMAND)
                .capability(ExtensionCapability.SKILL)
                .capability(ExtensionCapability.PROMPT)
                .permission("ui.prompt")
                .configPrefix("ai4j.extensions.ask-user")
                .build();
    }

    public void apply(ExtensionContext context) {
        context.tools().register(toolSpec(), new ExtensionToolExecutor() {
            public String execute(ExtensionToolCall call) {
                String arguments = call == null ? null : call.getArguments();
                return AskUserPayloads.toolRequest(arguments);
            }
        });

        context.commands().register(commandSpec(), request -> AskUserPayloads.commandRequest(commandArguments(request)));

        context.skills().register(ExtensionSkillResource.builder()
                .name(SKILL_NAME)
                .description("Workflow for deciding when an AI4J agent should ask the application user a structured question.")
                .resourcePath("skills/ask-user/SKILL.md")
                .build());

        context.prompts().register(ExtensionPromptResource.builder()
                .name(PROMPT_NAME)
                .description("Prompt template for producing concise host-mediated user questions.")
                .resourcePath("prompts/ask-user-question.md")
                .build());
    }

    public static ExtensionToolSpec toolSpec() {
        return ExtensionToolSpec.builder()
                .name(TOOL_NAME)
                .description("Request a structured clarification from the application user without blocking the extension runtime.")
                .inputSchema("{\"type\":\"object\",\"properties\":{\"question\":{\"type\":\"string\",\"description\":\"The exact question to show to the user\"},\"reason\":{\"type\":\"string\",\"description\":\"Why the agent needs this answer before continuing\"},\"choices\":{\"type\":\"array\",\"items\":{\"type\":\"string\"},\"description\":\"Optional short choices the host can render\"},\"defaultChoice\":{\"type\":\"string\",\"description\":\"Optional recommended default choice\"},\"blocking\":{\"type\":\"boolean\",\"description\":\"Whether the agent should pause until the host receives an answer\"}},\"required\":[\"question\"]}")
                .build();
    }

    public static ExtensionCommandSpec commandSpec() {
        return ExtensionCommandSpec.builder()
                .name(COMMAND_NAME)
                .description("Create a host-mediated ask-user request from CLI command arguments.")
                .usage("/ask-user <question>")
                .build();
    }

    private static String commandArguments(ExtensionCommandRequest request) {
        return request == null ? null : request.getArguments();
    }
}
