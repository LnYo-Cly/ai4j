package io.github.lnyocly.ai4j.cli.agent;

import io.github.lnyocly.ai4j.coding.definition.CodingAgentDefinition;
import io.github.lnyocly.ai4j.coding.definition.CodingIsolationMode;
import io.github.lnyocly.ai4j.coding.tool.CodingToolNames;
import org.junit.Assert;
import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

public class CliCodingAgentRegistryTest {

    @Test
    public void loadRegistryParsesWorkspaceAndConfiguredAgentDirectories() throws Exception {
        Path workspace = Files.createTempDirectory("ai4j-cli-agent-workspace");
        Path configuredDirectory = Files.createTempDirectory("ai4j-cli-agent-configured");
        Path workspaceAgents = workspace.resolve(".ai4j").resolve("agents");
        Files.createDirectories(workspaceAgents);

        Files.write(workspaceAgents.resolve("reviewer.md"), (
                "---\n" +
                        "name: reviewer\n" +
                        "description: Review code changes for regressions.\n" +
                        "tools: read-only\n" +
                        "background: true\n" +
                        "---\n" +
                        "Inspect diffs and summarize correctness, risk, and missing tests.\n"
        ).getBytes(StandardCharsets.UTF_8));

        Files.write(configuredDirectory.resolve("planner.prompt"), (
                "---\n" +
                        "name: planner\n" +
                        "toolName: delegate_planner_custom\n" +
                        "model: gpt-5-mini\n" +
                        "---\n" +
                        "Read the workspace and return a concrete implementation plan.\n"
        ).getBytes(StandardCharsets.UTF_8));

        CliCodingAgentRegistry registry = new CliCodingAgentRegistry(
                workspace,
                Arrays.asList(configuredDirectory.toString())
        );

        List<CodingAgentDefinition> definitions = registry.listDefinitions();
        CodingAgentDefinition reviewer = registry.loadRegistry().getDefinition("reviewer");
        CodingAgentDefinition planner = registry.loadRegistry().getDefinition("delegate_planner_custom");

        Assert.assertEquals(2, definitions.size());
        Assert.assertNotNull(reviewer);
        Assert.assertEquals("delegate_reviewer", reviewer.getToolName());
        Assert.assertEquals(CodingToolNames.readOnlyBuiltIn(), reviewer.getAllowedToolNames());
        Assert.assertEquals(CodingIsolationMode.READ_ONLY, reviewer.getIsolationMode());
        Assert.assertTrue(reviewer.isBackground());
        Assert.assertNotNull(planner);
        Assert.assertEquals("planner", planner.getName());
        Assert.assertEquals("gpt-5-mini", planner.getModel());
    }
}
