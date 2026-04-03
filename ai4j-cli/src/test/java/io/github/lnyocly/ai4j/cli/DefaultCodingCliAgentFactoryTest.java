package io.github.lnyocly.ai4j.cli.factory;

import io.github.lnyocly.ai4j.cli.config.CliWorkspaceConfig;
import io.github.lnyocly.ai4j.cli.CliProtocol;
import io.github.lnyocly.ai4j.cli.CliUiMode;
import io.github.lnyocly.ai4j.cli.command.CodeCommandOptions;
import io.github.lnyocly.ai4j.cli.provider.CliProviderConfigManager;
import io.github.lnyocly.ai4j.coding.definition.CodingAgentDefinitionRegistry;
import io.github.lnyocly.ai4j.coding.workspace.WorkspaceContext;
import io.github.lnyocly.ai4j.service.PlatformType;
import org.junit.Assert;
import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

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
}
