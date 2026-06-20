package io.github.lnyocly.ai4j.cli.command;

import io.github.lnyocly.ai4j.agent.model.AgentModelClient;
import io.github.lnyocly.ai4j.agent.model.AgentModelResult;
import io.github.lnyocly.ai4j.agent.model.AgentModelStreamListener;
import io.github.lnyocly.ai4j.agent.model.AgentPrompt;
import io.github.lnyocly.ai4j.tui.StreamsTerminalIO;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Properties;

public class AgentBlueprintRunCommandTest {

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void shouldRunBlueprintOnceWithHostSuppliedModelClient() throws Exception {
        Path workspace = temporaryFolder.newFolder("workspace").toPath();
        Path yaml = workspace.resolve("agent.yaml");
        Files.write(yaml, ("version: ai4j.agent/v1\n"
                + "id: cli-agent\n"
                + "model:\n"
                + "  provider: openai-compatible\n"
                + "  model: gpt-test\n"
                + "instructions:\n"
                + "  system: System text\n"
                + "  developer: Developer text\n"
                + "workflow:\n"
                + "  mode: react\n"
                + "  maxTurns: 2\n").getBytes(StandardCharsets.UTF_8));
        RecordingModelClientFactory factory = new RecordingModelClientFactory("blueprint-ok");
        AgentBlueprintRunCommand command = new AgentBlueprintRunCommand(
                Collections.<String, String>emptyMap(),
                new Properties(),
                workspace,
                null,
                null,
                factory
        );
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();

        int exitCode = command.run(asList("agent.yaml", "--input", "hello"), terminal(out, err));

        Assert.assertEquals(new String(err.toByteArray(), StandardCharsets.UTF_8), 0, exitCode);
        Assert.assertEquals("blueprint-ok" + System.lineSeparator(), new String(out.toByteArray(), StandardCharsets.UTF_8));
        Assert.assertNotNull(factory.options);
        Assert.assertEquals("gpt-test", factory.options.getModel());
        Assert.assertEquals("hello", factory.options.getInput());
        Assert.assertEquals("openai", factory.options.getProvider().getPlatform());
        Assert.assertEquals("gpt-test", factory.client.lastPrompt.getModel());
        Assert.assertEquals("Developer text", factory.client.lastPrompt.getInstructions());
    }

    @Test
    public void shouldUseConfiguredProfileFromBlueprintWithoutPuttingSecretInYaml() throws Exception {
        Path home = temporaryFolder.newFolder("home").toPath();
        Path workspace = temporaryFolder.newFolder("workspace-profile").toPath();
        String previousUserHome = System.getProperty("user.home");
        try {
            System.setProperty("user.home", home.toString());
            io.github.lnyocly.ai4j.cli.provider.CliProviderConfigManager manager = new io.github.lnyocly.ai4j.cli.provider.CliProviderConfigManager(workspace);
            io.github.lnyocly.ai4j.cli.provider.CliProvidersConfig config = io.github.lnyocly.ai4j.cli.provider.CliProvidersConfig.builder().build();
            config.getProfiles().put("dev", io.github.lnyocly.ai4j.cli.provider.CliProviderProfile.builder()
                    .provider("zhipu")
                    .protocol("chat")
                    .model("glm-test")
                    .baseUrl("https://example.invalid/v4")
                    .apiKey("secret-from-profile")
                    .build());
            manager.saveProvidersConfig(config);

            Path yaml = workspace.resolve("agent.yaml");
            Files.write(yaml, ("version: ai4j.agent/v1\n"
                    + "id: profile-agent\n"
                    + "model:\n"
                    + "  provider: openai-compatible\n"
                    + "  profile: dev\n"
                    + "  model: yaml-model\n").getBytes(StandardCharsets.UTF_8));
            RecordingModelClientFactory factory = new RecordingModelClientFactory("profile-ok");
            AgentBlueprintRunCommand command = new AgentBlueprintRunCommand(
                    Collections.<String, String>emptyMap(),
                    new Properties(),
                    workspace,
                    null,
                    null,
                    factory
            );
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ByteArrayOutputStream err = new ByteArrayOutputStream();

            int exitCode = command.run(asList("agent.yaml", "--input", "hi"), terminal(out, err));

            Assert.assertEquals(new String(err.toByteArray(), StandardCharsets.UTF_8), 0, exitCode);
            Assert.assertEquals("zhipu", factory.options.getProvider().getPlatform());
            Assert.assertEquals("yaml-model", factory.options.getModel());
            Assert.assertEquals("secret-from-profile", factory.options.getApiKey());
            Assert.assertEquals("dev", factory.options.getProfile());
        } finally {
            if (previousUserHome == null) {
                System.clearProperty("user.home");
            } else {
                System.setProperty("user.home", previousUserHome);
            }
        }
    }

    @Test
    public void shouldRejectMissingProfileInsteadOfFallingBackToDefault() throws Exception {
        Path home = temporaryFolder.newFolder("home-missing-profile").toPath();
        Path workspace = temporaryFolder.newFolder("workspace-missing-profile").toPath();
        String previousUserHome = System.getProperty("user.home");
        try {
            System.setProperty("user.home", home.toString());
            io.github.lnyocly.ai4j.cli.provider.CliProviderConfigManager manager = new io.github.lnyocly.ai4j.cli.provider.CliProviderConfigManager(workspace);
            io.github.lnyocly.ai4j.cli.provider.CliProvidersConfig config = io.github.lnyocly.ai4j.cli.provider.CliProvidersConfig.builder().build();
            config.setDefaultProfile("default");
            config.getProfiles().put("default", io.github.lnyocly.ai4j.cli.provider.CliProviderProfile.builder()
                    .provider("openai")
                    .protocol("responses")
                    .model("default-model")
                    .apiKey("default-secret")
                    .build());
            manager.saveProvidersConfig(config);

            Path yaml = workspace.resolve("agent.yaml");
            Files.write(yaml, ("version: ai4j.agent/v1\n"
                    + "id: missing-profile-agent\n"
                    + "model:\n"
                    + "  provider: openai-compatible\n"
                    + "  profile: missing\n"
                    + "  model: yaml-model\n").getBytes(StandardCharsets.UTF_8));
            RecordingModelClientFactory factory = new RecordingModelClientFactory("unused");
            AgentBlueprintRunCommand command = new AgentBlueprintRunCommand(
                    Collections.<String, String>emptyMap(),
                    new Properties(),
                    workspace,
                    null,
                    null,
                    factory
            );
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ByteArrayOutputStream err = new ByteArrayOutputStream();

            int exitCode = command.run(asList("agent.yaml", "--input", "hi"), terminal(out, err));

            Assert.assertEquals(2, exitCode);
            Assert.assertNull(factory.options);
            String error = new String(err.toByteArray(), StandardCharsets.UTF_8);
            Assert.assertTrue(error.contains("profile not found or incompatible with provider: missing"));
        } finally {
            if (previousUserHome == null) {
                System.clearProperty("user.home");
            } else {
                System.setProperty("user.home", previousUserHome);
            }
        }
    }

    @Test
    public void shouldRejectSandboxDeclarationByDefault() throws Exception {
        Path workspace = temporaryFolder.newFolder("workspace-sandbox").toPath();
        Path yaml = workspace.resolve("agent.yaml");
        Files.write(yaml, ("version: ai4j.agent/v1\n"
                + "id: sandbox-agent\n"
                + "model:\n"
                + "  provider: openai-compatible\n"
                + "  model: gpt-test\n"
                + "sandbox:\n"
                + "  enabled: true\n"
                + "  provider: remote-vm\n").getBytes(StandardCharsets.UTF_8));
        AgentBlueprintRunCommand command = new AgentBlueprintRunCommand(
                Collections.<String, String>emptyMap(),
                new Properties(),
                workspace,
                null,
                null,
                new RecordingModelClientFactory("unused")
        );
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();

        int exitCode = command.run(asList("agent.yaml", "--input", "hi"), terminal(out, err));

        Assert.assertEquals(2, exitCode);
        String error = new String(err.toByteArray(), StandardCharsets.UTF_8);
        Assert.assertTrue(error.contains("blueprint.sandbox.unsupported"));
    }

    @Test
    public void shouldReportValidationErrors() throws Exception {
        Path workspace = temporaryFolder.newFolder("workspace-invalid").toPath();
        Path yaml = workspace.resolve("agent.yaml");
        Files.write(yaml, ("version: ai4j.agent/v1\n"
                + "id: bad id\n"
                + "model:\n"
                + "  provider: openai-compatible\n"
                + "  model: gpt-test\n").getBytes(StandardCharsets.UTF_8));
        AgentBlueprintRunCommand command = new AgentBlueprintRunCommand(
                Collections.<String, String>emptyMap(),
                new Properties(),
                workspace,
                null,
                null,
                new RecordingModelClientFactory("unused")
        );
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();

        int exitCode = command.run(asList("agent.yaml", "--input", "hi"), terminal(out, err));

        Assert.assertEquals(2, exitCode);
        String error = new String(err.toByteArray(), StandardCharsets.UTF_8);
        Assert.assertTrue(error.contains("blueprint.validation.failed"));
        Assert.assertTrue(error.contains("blueprint.id.invalid"));
    }

    private StreamsTerminalIO terminal(ByteArrayOutputStream out, ByteArrayOutputStream err) {
        return new StreamsTerminalIO(new ByteArrayInputStream(new byte[0]), out, err);
    }

    private List<String> asList(String... values) {
        return java.util.Arrays.asList(values);
    }

    private static final class RecordingModelClientFactory implements AgentBlueprintRunModelClientFactory {
        private final RecordingModelClient client;
        private AgentBlueprintRunOptions options;

        private RecordingModelClientFactory(String output) {
            this.client = new RecordingModelClient(output);
        }

        @Override
        public AgentModelClient create(AgentBlueprintRunOptions options) {
            this.options = options;
            return client;
        }
    }

    private static final class RecordingModelClient implements AgentModelClient {
        private final String output;
        private AgentPrompt lastPrompt;

        private RecordingModelClient(String output) {
            this.output = output;
        }

        @Override
        public AgentModelResult create(AgentPrompt prompt) {
            this.lastPrompt = prompt;
            return AgentModelResult.builder().outputText(output).build();
        }

        @Override
        public AgentModelResult createStream(AgentPrompt prompt, AgentModelStreamListener listener) {
            this.lastPrompt = prompt;
            return create(prompt);
        }
    }
}
