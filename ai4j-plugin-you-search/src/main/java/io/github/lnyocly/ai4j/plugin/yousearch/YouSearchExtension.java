package io.github.lnyocly.ai4j.plugin.yousearch;

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
 * Optional You.com web search extension plugin.
 *
 * <p>Registers a {@code you_web_search} tool that queries the You.com Search API
 * ({@code https://api.you.com/api/search}) so an AI4J agent can ground answers in
 * current web results. The plugin is fully opt-in: hosts must call
 * {@code ExtensionRegistry.discover().enable("you-search").exposeTool("you_web_search")}
 * before the tool becomes visible to any agent. It performs no network activity
 * in {@link #apply(ExtensionContext)}; requests happen only inside the tool
 * executor when the model actually calls the tool.</p>
 */
public final class YouSearchExtension implements Ai4jExtension {

    public static final String EXTENSION_ID = "you-search";
    public static final String TOOL_NAME = "you_web_search";
    public static final String COMMAND_NAME = "you-search";
    public static final String SKILL_NAME = "you-web-search";
    public static final String PROMPT_NAME = "you-search-answer";

    /** Environment variable holding the You.com API key. */
    public static final String API_KEY_ENV = "YDC_API_KEY";

    /** You.com Search API endpoint. Also configurable through {@code ai4j.extensions.you-search.baseUrl}. */
    public static final String DEFAULT_SEARCH_ENDPOINT = "https://api.you.com/api/search";

    private static final String VERSION = "0.1.0";

    public ExtensionManifest manifest() {
        return ExtensionManifest.builder()
                .id(EXTENSION_ID)
                .name("You.com Search")
                .version(VERSION)
                .vendor("you-search contributors")
                .capability(ExtensionCapability.TOOL)
                .capability(ExtensionCapability.COMMAND)
                .capability(ExtensionCapability.SKILL)
                .capability(ExtensionCapability.PROMPT)
                .permission("network:you.com")
                .configPrefix("ai4j.extensions.you-search")
                .build();
    }

    public void apply(ExtensionContext context) {
        context.tools().register(toolSpec(), new ExtensionToolExecutor() {
            public String execute(ExtensionToolCall call) {
                String arguments = call == null ? null : call.getArguments();
                return YouSearchClient.search(arguments);
            }
        });

        context.commands().register(commandSpec(), new io.github.lnyocly.ai4j.extension.command.ExtensionCommandHandler() {
            public String handle(ExtensionCommandRequest request) {
                String arguments = request == null ? null : request.getArguments();
                return YouSearchClient.search(YouSearchPayloads.queryOnlyArguments(arguments));
            }
        });

        context.skills().register(ExtensionSkillResource.builder()
                .name(SKILL_NAME)
                .description("Workflow for deciding when an AI4J agent should call the you_web_search tool and how to cite results.")
                .resourcePath("skills/you-search/SKILL.md")
                .build());

        context.prompts().register(ExtensionPromptResource.builder()
                .name(PROMPT_NAME)
                .description("Prompt template for writing a concise answer from You.com search results.")
                .resourcePath("prompts/you-search-answer.md")
                .build());
    }

    public static ExtensionToolSpec toolSpec() {
        return ExtensionToolSpec.builder()
                .name(TOOL_NAME)
                .description("Search the current web with You.com and return titled result snippets with URLs for grounding an answer.")
                .inputSchema("{\"type\":\"object\",\"properties\":{"
                        + "\"query\":{\"type\":\"string\",\"description\":\"The web search query\"},"
                        + "\"numResults\":{\"type\":\"string\",\"description\":\"Optional number of results to return, between 1 and 20\"}"
                        + "},\"required\":[\"query\"]}")
                .build();
    }

    public static ExtensionCommandSpec commandSpec() {
        return ExtensionCommandSpec.builder()
                .name(COMMAND_NAME)
                .description("Run a You.com web search from CLI command arguments.")
                .usage("/you-search <query>")
                .build();
    }
}
