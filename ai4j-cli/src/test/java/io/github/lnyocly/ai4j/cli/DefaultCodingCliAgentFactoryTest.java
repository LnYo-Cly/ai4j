package io.github.lnyocly.ai4j.cli.factory;

import io.github.lnyocly.ai4j.cli.config.CliWorkspaceConfig;
import io.github.lnyocly.ai4j.cli.CliProtocol;
import io.github.lnyocly.ai4j.cli.CliUiMode;
import io.github.lnyocly.ai4j.cli.command.CodeCommandOptions;
import io.github.lnyocly.ai4j.cli.factory.CodingCliAgentFactory;
import io.github.lnyocly.ai4j.cli.provider.CliProviderConfigManager;
import io.github.lnyocly.ai4j.coding.definition.CodingAgentDefinitionRegistry;
import io.github.lnyocly.ai4j.agent.model.AgentModelClient;
import io.github.lnyocly.ai4j.agent.model.AgentModelResult;
import io.github.lnyocly.ai4j.agent.model.AgentModelStreamListener;
import io.github.lnyocly.ai4j.agent.model.AgentPrompt;
import io.github.lnyocly.ai4j.agent.tool.AgentToolCall;
import io.github.lnyocly.ai4j.coding.workspace.WorkspaceContext;
import io.github.lnyocly.ai4j.platform.openai.tool.Tool;
import io.github.lnyocly.ai4j.service.PlatformType;
import org.junit.Assert;
import org.junit.Test;

import java.util.ArrayList;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class DefaultCodingCliAgentFactoryTest {

    private final DefaultCodingCliAgentFactory factory = new DefaultCodingCliAgentFactory();

    @Test
    public void test_default_protocol_prefers_chat_for_openai_compatible_base_url() {
        CodeCommandOptions options = new CodeCommandOptions(
                false,
                CliUiMode.CLI,
                PlatformType.OPENAI,
                null,
                "deepseek-chat",
                null,
                "https://api.deepseek.com",
                ".",
                null,
                null,
                null,
                null,
                12,
                null,
                null,
                null,
                Boolean.FALSE,
                false,
                false
        );

        Assert.assertEquals(CliProtocol.CHAT, factory.resolveProtocol(options));
    }

    @Test
    public void test_default_protocol_prefers_responses_for_official_openai() {
        CodeCommandOptions options = new CodeCommandOptions(
                false,
                CliUiMode.CLI,
                PlatformType.OPENAI,
                null,
                "gpt-5-mini",
                null,
                "https://api.openai.com",
                ".",
                null,
                null,
                null,
                null,
                12,
                null,
                null,
                null,
                Boolean.FALSE,
                false,
                false
        );

        Assert.assertEquals(CliProtocol.RESPONSES, factory.resolveProtocol(options));
    }

    @Test
    public void test_normalize_zhipu_coding_plan_base_url() {
        Assert.assertEquals(
                "https://open.bigmodel.cn/api/coding/paas/",
                factory.normalizeZhipuBaseUrl("https://open.bigmodel.cn/api/coding/paas/v4")
        );
        Assert.assertEquals(
                "https://open.bigmodel.cn/api/coding/paas/",
                factory.normalizeZhipuBaseUrl("https://open.bigmodel.cn/api/coding/paas/v4/chat/completions")
        );
    }

    @Test(expected = IllegalArgumentException.class)
    public void test_explicit_responses_rejected_for_zhipu() {
        CodeCommandOptions options = new CodeCommandOptions(
                false,
                CliUiMode.CLI,
                PlatformType.ZHIPU,
                CliProtocol.RESPONSES,
                "GLM-4.5-Flash",
                null,
                null,
                ".",
                null,
                null,
                null,
                null,
                12,
                null,
                null,
                null,
                Boolean.FALSE,
                false,
                false
        );

        factory.resolveProtocol(options);
    }

    @Test
    public void test_build_workspace_context_includes_workspace_skill_directories() throws Exception {
        Path workspace = Files.createTempDirectory("ai4j-cli-workspace-skills");
        CliProviderConfigManager manager = new CliProviderConfigManager(workspace);
        manager.saveWorkspaceConfig(CliWorkspaceConfig.builder()
                .skillDirectories(Arrays.asList(" .ai4j/skills ", "C:/skills/team ", ".ai4j/skills"))
                .build());

        CodeCommandOptions options = new CodeCommandOptions(
                false,
                CliUiMode.CLI,
                PlatformType.OPENAI,
                CliProtocol.RESPONSES,
                "gpt-5-mini",
                null,
                null,
                workspace.toString(),
                "workspace description",
                null,
                null,
                null,
                12,
                null,
                null,
                null,
                Boolean.FALSE,
                false,
                false
        );

        WorkspaceContext workspaceContext = factory.buildWorkspaceContext(options);

        Assert.assertEquals(workspace.toAbsolutePath().normalize().toString(), workspaceContext.getRoot().toString());
        Assert.assertEquals("workspace description", workspaceContext.getDescription());
        Assert.assertEquals(Arrays.asList(".ai4j/skills", "C:/skills/team"), workspaceContext.getSkillDirectories());
    }

    @Test
    public void test_load_definition_registry_merges_built_ins_with_workspace_agents() throws Exception {
        Path workspace = Files.createTempDirectory("ai4j-cli-workspace-agents");
        Path agentsDirectory = workspace.resolve(".ai4j").resolve("agents");
        Files.createDirectories(agentsDirectory);
        Files.write(agentsDirectory.resolve("reviewer.md"), (
                "---\n" +
                        "name: reviewer\n" +
                        "description: Review diffs.\n" +
                        "tools: read-only\n" +
                        "---\n" +
                        "Review code changes for correctness and missing tests.\n"
        ).getBytes(StandardCharsets.UTF_8));

        CodeCommandOptions options = new CodeCommandOptions(
                false,
                CliUiMode.CLI,
                PlatformType.OPENAI,
                CliProtocol.RESPONSES,
                "gpt-5-mini",
                null,
                null,
                workspace.toString(),
                null,
                null,
                null,
                null,
                12,
                null,
                null,
                null,
                Boolean.FALSE,
                false,
                false
        );

        CodingAgentDefinitionRegistry registry = factory.loadDefinitionRegistry(options);

        Assert.assertNotNull(registry.getDefinition("general-purpose"));
        Assert.assertNotNull(registry.getDefinition("delegate_general_purpose"));
        Assert.assertNotNull(registry.getDefinition("reviewer"));
        Assert.assertNotNull(registry.getDefinition("delegate_reviewer"));
    }

    @Test
    public void test_prepare_includes_experimental_subagent_and_team_tools_by_default() throws Exception {
        Path workspace = Files.createTempDirectory("ai4j-cli-experimental-default");
        TestFactory testFactory = new TestFactory();

        CodeCommandOptions options = new CodeCommandOptions(
                false,
                CliUiMode.CLI,
                PlatformType.OPENAI,
                CliProtocol.RESPONSES,
                "gpt-5-mini",
                null,
                null,
                workspace.toString(),
                null,
                null,
                null,
                null,
                12,
                null,
                null,
                null,
                Boolean.FALSE,
                false,
                false
        );

        CodingCliAgentFactory.PreparedCodingAgent prepared = testFactory.prepare(options);
        List<String> toolNames = toolNames(prepared);

        Assert.assertTrue(toolNames.contains(DefaultCodingCliAgentFactory.EXPERIMENTAL_SUBAGENT_TOOL_NAME));
        Assert.assertTrue(toolNames.contains(DefaultCodingCliAgentFactory.EXPERIMENTAL_TEAM_TOOL_NAME));
    }

    @Test
    public void test_prepare_respects_workspace_experimental_toggles() throws Exception {
        Path workspace = Files.createTempDirectory("ai4j-cli-experimental-disabled");
        new CliProviderConfigManager(workspace).saveWorkspaceConfig(CliWorkspaceConfig.builder()
                .experimentalSubagentsEnabled(Boolean.FALSE)
                .experimentalAgentTeamsEnabled(Boolean.FALSE)
                .build());
        TestFactory testFactory = new TestFactory();

        CodeCommandOptions options = new CodeCommandOptions(
                false,
                CliUiMode.CLI,
                PlatformType.OPENAI,
                CliProtocol.RESPONSES,
                "gpt-5-mini",
                null,
                null,
                workspace.toString(),
                null,
                null,
                null,
                null,
                12,
                null,
                null,
                null,
                Boolean.FALSE,
                false,
                false
        );

        CodingCliAgentFactory.PreparedCodingAgent prepared = testFactory.prepare(options);
        List<String> toolNames = toolNames(prepared);

        Assert.assertFalse(toolNames.contains(DefaultCodingCliAgentFactory.EXPERIMENTAL_SUBAGENT_TOOL_NAME));
        Assert.assertFalse(toolNames.contains(DefaultCodingCliAgentFactory.EXPERIMENTAL_TEAM_TOOL_NAME));
    }

    private List<String> toolNames(CodingCliAgentFactory.PreparedCodingAgent prepared) {
        if (prepared == null || prepared.getAgent() == null) {
            return Collections.emptyList();
        }
        List<String> names = new ArrayList<String>();
        List<Object> tools = prepared.getAgent().newSession().getDelegate().getContext().getToolRegistry().getTools();
        if (tools == null) {
            return names;
        }
        for (Object tool : tools) {
            if (!(tool instanceof Tool)) {
                continue;
            }
            Tool typedTool = (Tool) tool;
            if (typedTool.getFunction() != null && typedTool.getFunction().getName() != null) {
                names.add(typedTool.getFunction().getName());
            }
        }
        return names;
    }

    private static final class TestFactory extends DefaultCodingCliAgentFactory {

        @Override
        protected AgentModelClient createModelClient(CodeCommandOptions options, CliProtocol protocol) {
            return new AgentModelClient() {
                @Override
                public AgentModelResult create(AgentPrompt prompt) {
                    return emptyResult();
                }

                @Override
                public AgentModelResult createStream(AgentPrompt prompt, AgentModelStreamListener listener) {
                    return emptyResult();
                }
            };
        }

        private AgentModelResult emptyResult() {
            return AgentModelResult.builder()
                    .outputText("")
                    .toolCalls(new ArrayList<AgentToolCall>())
                    .memoryItems(new ArrayList<Object>())
                    .build();
        }
    }
}
