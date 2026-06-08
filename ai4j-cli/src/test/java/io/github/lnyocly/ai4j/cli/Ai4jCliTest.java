package io.github.lnyocly.ai4j.cli;

import io.github.lnyocly.ai4j.cli.command.CodeCommandOptions;
import io.github.lnyocly.ai4j.cli.fixture.CliExtensionTestExtension;
import io.github.lnyocly.ai4j.coding.CodingAgent;
import io.github.lnyocly.ai4j.coding.CodingAgents;
import io.github.lnyocly.ai4j.coding.workspace.WorkspaceContext;
import io.github.lnyocly.ai4j.agent.model.AgentModelClient;
import io.github.lnyocly.ai4j.agent.model.AgentModelResult;
import io.github.lnyocly.ai4j.agent.model.AgentModelStreamListener;
import io.github.lnyocly.ai4j.agent.model.AgentPrompt;
import io.github.lnyocly.ai4j.cli.factory.CodingCliAgentFactory;
import io.github.lnyocly.ai4j.cli.factory.CodingCliAgentFactory.PreparedCodingAgent;
import org.junit.Assert;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Properties;

public class Ai4jCliTest {

    @Test
    public void test_top_level_help() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();

        int exitCode = new Ai4jCli().run(
                new String[]{"help"},
                new ByteArrayInputStream(new byte[0]),
                out,
                err,
                Collections.<String, String>emptyMap(),
                new Properties()
        );

        String output = new String(out.toByteArray(), StandardCharsets.UTF_8);
        Assert.assertEquals(0, exitCode);
        Assert.assertTrue(output.contains("ai4j-cli"));
        Assert.assertTrue(output.contains("code"));
        Assert.assertTrue(output.contains("acp"));
        Assert.assertTrue(output.contains("extension"));
    }

    @Test
    public void test_unknown_command_returns_argument_error() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();

        int exitCode = new Ai4jCli().run(
                new String[]{"unknown"},
                new ByteArrayInputStream(new byte[0]),
                out,
                err,
                Collections.<String, String>emptyMap(),
                new Properties()
        );

        String error = new String(err.toByteArray(), StandardCharsets.UTF_8);
        Assert.assertEquals(2, exitCode);
        Assert.assertTrue(error.contains("Unknown command: unknown"));
    }

    @Test
    public void test_extension_list_shows_discovered_extension_manifest() {
        CliExtensionTestExtension.resetApplyCount();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();

        int exitCode = new Ai4jCli().run(
                new String[]{"extension", "list"},
                new ByteArrayInputStream(new byte[0]),
                out,
                err,
                Collections.<String, String>emptyMap(),
                new Properties()
        );

        String output = new String(out.toByteArray(), StandardCharsets.UTF_8);
        Assert.assertEquals(0, exitCode);
        Assert.assertTrue(output.contains("extensions:"));
        Assert.assertTrue(output.contains("id=cli-test-pack"));
        Assert.assertTrue(output.contains("name=CLI Test Pack"));
        Assert.assertTrue(output.contains("capabilities=tool,command,skill,prompt,guardrail"));
        Assert.assertTrue(output.contains("source=io.github.lnyocly.ai4j.cli.fixture.CliExtensionTestExtension"));
        Assert.assertEquals(0, CliExtensionTestExtension.getApplyCount());
    }

    @Test
    public void test_extension_list_rejects_unexpected_arguments() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();

        int exitCode = new Ai4jCli().run(
                new String[]{"extension", "list", "extra"},
                new ByteArrayInputStream(new byte[0]),
                out,
                err,
                Collections.<String, String>emptyMap(),
                new Properties()
        );

        String error = new String(err.toByteArray(), StandardCharsets.UTF_8);
        Assert.assertEquals(2, exitCode);
        Assert.assertTrue(error.contains("unexpected argument for list: extra"));
    }

    @Test
    public void test_extension_inspect_defaults_to_manifest_only() {
        CliExtensionTestExtension.resetApplyCount();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();

        int exitCode = new Ai4jCli().run(
                new String[]{"extension", "inspect", "cli-test-pack"},
                new ByteArrayInputStream(new byte[0]),
                out,
                err,
                Collections.<String, String>emptyMap(),
                new Properties()
        );

        String output = new String(out.toByteArray(), StandardCharsets.UTF_8);
        Assert.assertEquals(0, exitCode);
        Assert.assertTrue(output.contains("extension:"));
        Assert.assertTrue(output.contains("id=cli-test-pack"));
        Assert.assertTrue(output.contains("permissions=network:example.test"));
        Assert.assertTrue(output.contains("configPrefix=ai4j.extensions.cli-test"));
        Assert.assertTrue(output.contains("runtime=not-inspected"));
        Assert.assertFalse(output.contains("tools=cli.echo"));
        Assert.assertEquals(0, CliExtensionTestExtension.getApplyCount());
    }

    @Test
    public void test_extension_inspect_runtime_lists_contributed_resources() {
        CliExtensionTestExtension.resetApplyCount();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();

        int exitCode = new Ai4jCli().run(
                new String[]{"extensions", "inspect", "cli-test-pack", "--runtime"},
                new ByteArrayInputStream(new byte[0]),
                out,
                err,
                Collections.<String, String>emptyMap(),
                new Properties()
        );

        String output = new String(out.toByteArray(), StandardCharsets.UTF_8);
        Assert.assertEquals(0, exitCode);
        Assert.assertTrue(output.contains("runtime=inspected"));
        Assert.assertTrue(output.contains("tools=cli.echo"));
        Assert.assertTrue(output.contains("commands=cli-echo"));
        Assert.assertTrue(output.contains("skills=cli-skill@skills/cli/SKILL.md"));
        Assert.assertTrue(output.contains("prompts=cli-prompt@prompts/cli.md"));
        Assert.assertTrue(output.contains("guardrails=cli-guardrail"));
        Assert.assertEquals(1, CliExtensionTestExtension.getApplyCount());
    }

    @Test
    public void test_extension_inspect_unknown_id_returns_argument_error() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();

        int exitCode = new Ai4jCli().run(
                new String[]{"extension", "inspect", "missing-pack"},
                new ByteArrayInputStream(new byte[0]),
                out,
                err,
                Collections.<String, String>emptyMap(),
                new Properties()
        );

        String error = new String(err.toByteArray(), StandardCharsets.UTF_8);
        Assert.assertEquals(2, exitCode);
        Assert.assertTrue(error.contains("extension not discovered: missing-pack"));
    }

    @Test
    public void test_top_level_tui_command_routes_to_tui_mode() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();

        Ai4jCli cli = new Ai4jCli(new StubCodingCliAgentFactory(), Paths.get(".").toAbsolutePath().normalize());
        int exitCode = cli.run(
                new String[]{"tui", "--model", "fake-model", "--prompt", "hello"},
                new ByteArrayInputStream(new byte[0]),
                out,
                err,
                Collections.<String, String>emptyMap(),
                new Properties()
        );

        String output = new String(out.toByteArray(), StandardCharsets.UTF_8);
        Assert.assertEquals(0, exitCode);
        Assert.assertTrue(output.contains("AI4J"));
        Assert.assertTrue(output.contains("fake-model"));
        Assert.assertTrue(output.contains("Echo: hello"));
        Assert.assertFalse(output.contains("EVENTS"));
    }

    private static final class StubCodingCliAgentFactory implements CodingCliAgentFactory {

        @Override
        public PreparedCodingAgent prepare(CodeCommandOptions options) {
            CodingAgent agent = CodingAgents.builder()
                    .modelClient(new StubModelClient())
                    .model(options.getModel())
                    .workspaceContext(WorkspaceContext.builder().rootPath(options.getWorkspace()).build())
                    .build();
            return new PreparedCodingAgent(agent, CliProtocol.CHAT);
        }
    }

    private static final class StubModelClient implements AgentModelClient {

        @Override
        public AgentModelResult create(AgentPrompt prompt) {
            return AgentModelResult.builder().outputText("Echo: " + findLastUserText(prompt)).build();
        }

        @Override
        public AgentModelResult createStream(AgentPrompt prompt, AgentModelStreamListener listener) {
            AgentModelResult result = create(prompt);
            if (listener != null) {
                listener.onDeltaText(result.getOutputText());
                listener.onComplete(result);
            }
            return result;
        }

        private String findLastUserText(AgentPrompt prompt) {
            if (prompt == null || prompt.getItems() == null) {
                return "";
            }
            List<Object> items = prompt.getItems();
            for (int i = items.size() - 1; i >= 0; i--) {
                Object item = items.get(i);
                if (!(item instanceof Map)) {
                    continue;
                }
                Map<?, ?> map = (Map<?, ?>) item;
                if (!"message".equals(map.get("type")) || !"user".equals(map.get("role"))) {
                    continue;
                }
                Object content = map.get("content");
                if (!(content instanceof List)) {
                    continue;
                }
                List<?> parts = (List<?>) content;
                for (Object part : parts) {
                    if (!(part instanceof Map)) {
                        continue;
                    }
                    Map<?, ?> partMap = (Map<?, ?>) part;
                    if ("input_text".equals(partMap.get("type"))) {
                        Object text = partMap.get("text");
                        return text == null ? "" : String.valueOf(text);
                    }
                }
            }
            return "";
        }
    }
}
