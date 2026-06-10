package io.github.lnyocly.ai4j.cli;

import io.github.lnyocly.ai4j.cli.command.CodeCommandOptions;
import io.github.lnyocly.ai4j.cli.fixture.CliExtensionTestExtension;
import io.github.lnyocly.ai4j.extension.Ai4jExtension;
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
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Arrays;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.ServiceLoader;

import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;

public class Ai4jCliTest {

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

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
    public void test_extension_plan_previews_activation_state() {
        CliExtensionTestExtension.resetApplyCount();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();

        int exitCode = new Ai4jCli().run(
                new String[]{
                        "extension", "plan", "cli-test-pack",
                        "--enable",
                        "--expose-tool", "cli.echo",
                        "--allow-command", "cli-echo",
                        "--allow-skill", "missing-skill",
                        "--allow-prompt", "cli-prompt",
                        "--allow-guardrail", "cli-guardrail",
                        "--strict"
                },
                new ByteArrayInputStream(new byte[0]),
                out,
                err,
                Collections.<String, String>emptyMap(),
                new Properties()
        );

        String output = new String(out.toByteArray(), StandardCharsets.UTF_8);
        Assert.assertEquals(0, exitCode);
        Assert.assertTrue(output.contains("activation-plan:"));
        Assert.assertTrue(output.contains("id=cli-test-pack"));
        Assert.assertTrue(output.contains("enabled=true"));
        Assert.assertTrue(output.contains("explicitResourceActivation=true"));
        Assert.assertTrue(output.contains("permissions=network:example.test"));
        Assert.assertTrue(output.contains("name=cli.echo state=active reason=exposeTool allowlist"));
        Assert.assertTrue(output.contains("name=cli-echo state=active reason=resource allowlist"));
        Assert.assertTrue(output.contains("name=cli-skill state=inactive reason=not allowed"));
        Assert.assertTrue(output.contains("name=missing-skill state=inactive reason=not registered by extension"));
        Assert.assertTrue(output.contains("name=cli-prompt state=active reason=resource allowlist"));
        Assert.assertTrue(output.contains("name=cli-guardrail state=active reason=resource allowlist"));
        Assert.assertEquals(1, CliExtensionTestExtension.getApplyCount());
    }

    @Test
    public void test_extension_validate_reports_pass_for_discovered_extension() {
        CliExtensionTestExtension.resetApplyCount();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();

        int exitCode = new Ai4jCli().run(
                new String[]{"extension", "validate", "cli-test-pack"},
                new ByteArrayInputStream(new byte[0]),
                out,
                err,
                Collections.<String, String>emptyMap(),
                new Properties()
        );

        String output = new String(out.toByteArray(), StandardCharsets.UTF_8);
        Assert.assertEquals(0, exitCode);
        Assert.assertTrue(output.contains("validation:"));
        Assert.assertTrue(output.contains("id=cli-test-pack status=pass errors=0 warnings=0"));
        Assert.assertTrue(output.contains("issues=-"));
        Assert.assertEquals(1, CliExtensionTestExtension.getApplyCount());
    }

    @Test
    public void test_extension_validate_all_reports_discovered_extensions() {
        CliExtensionTestExtension.resetApplyCount();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();

        int exitCode = new Ai4jCli().run(
                new String[]{"extensions", "validate", "--all"},
                new ByteArrayInputStream(new byte[0]),
                out,
                err,
                Collections.<String, String>emptyMap(),
                new Properties()
        );

        String output = new String(out.toByteArray(), StandardCharsets.UTF_8);
        Assert.assertEquals(0, exitCode);
        Assert.assertTrue(output.contains("validation:"));
        Assert.assertTrue(output.contains("count=1"));
        Assert.assertTrue(output.contains("id=cli-test-pack status=pass"));
        Assert.assertEquals(1, CliExtensionTestExtension.getApplyCount());
    }

    @Test
    public void test_extension_validate_unknown_id_returns_extension_error() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();

        int exitCode = new Ai4jCli().run(
                new String[]{"extension", "validate", "missing-pack"},
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
    public void test_extension_run_requires_explicit_enable() {
        CliExtensionTestExtension.resetApplyCount();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();

        int exitCode = new Ai4jCli().run(
                new String[]{"extension", "run", "cli-echo", "hello"},
                new ByteArrayInputStream(new byte[0]),
                out,
                err,
                Collections.<String, String>emptyMap(),
                new Properties()
        );

        String error = new String(err.toByteArray(), StandardCharsets.UTF_8);
        Assert.assertEquals(2, exitCode);
        Assert.assertTrue(error.contains("at least one --enable <extension-id> is required"));
        Assert.assertEquals(0, CliExtensionTestExtension.getApplyCount());
    }

    @Test
    public void test_extension_run_executes_command_from_enabled_extension() {
        CliExtensionTestExtension.resetApplyCount();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();

        int exitCode = new Ai4jCli().run(
                new String[]{"extension", "run", "--enable", "cli-test-pack", "cli-echo", "hello", "world"},
                new ByteArrayInputStream(new byte[0]),
                out,
                err,
                Collections.<String, String>emptyMap(),
                new Properties()
        );

        String output = new String(out.toByteArray(), StandardCharsets.UTF_8);
        Assert.assertEquals(0, exitCode);
        Assert.assertTrue(output.contains("hello world"));
        Assert.assertEquals(1, CliExtensionTestExtension.getApplyCount());
    }

    @Test
    public void test_extension_run_can_require_explicit_command_allowlist() {
        CliExtensionTestExtension.resetApplyCount();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();

        int blocked = new Ai4jCli().run(
                new String[]{"extension", "run", "--enable", "cli-test-pack", "--strict", "cli-echo", "hello"},
                new ByteArrayInputStream(new byte[0]),
                out,
                err,
                Collections.<String, String>emptyMap(),
                new Properties()
        );
        String blockedError = new String(err.toByteArray(), StandardCharsets.UTF_8);
        Assert.assertEquals(2, blocked);
        Assert.assertTrue(blockedError.contains("command not registered by enabled extensions: cli-echo"));

        out.reset();
        err.reset();
        CliExtensionTestExtension.resetApplyCount();
        int allowed = new Ai4jCli().run(
                new String[]{"extension", "run", "--enable", "cli-test-pack", "--allow-command", "cli-echo", "cli-echo", "hello"},
                new ByteArrayInputStream(new byte[0]),
                out,
                err,
                Collections.<String, String>emptyMap(),
                new Properties()
        );

        String output = new String(out.toByteArray(), StandardCharsets.UTF_8);
        Assert.assertEquals(0, allowed);
        Assert.assertTrue(output.contains("hello"));
        Assert.assertEquals(1, CliExtensionTestExtension.getApplyCount());
    }

    @Test
    public void test_extension_run_accepts_slash_prefixed_command_name() {
        CliExtensionTestExtension.resetApplyCount();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();

        int exitCode = new Ai4jCli().run(
                new String[]{"extensions", "run", "--enable", "cli-test-pack", "/cli-echo", "slash"},
                new ByteArrayInputStream(new byte[0]),
                out,
                err,
                Collections.<String, String>emptyMap(),
                new Properties()
        );

        String output = new String(out.toByteArray(), StandardCharsets.UTF_8);
        Assert.assertEquals(0, exitCode);
        Assert.assertTrue(output.contains("slash"));
        Assert.assertEquals(1, CliExtensionTestExtension.getApplyCount());
    }

    @Test
    public void test_extension_run_passes_option_like_arguments_to_handler() {
        CliExtensionTestExtension.resetApplyCount();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();

        int exitCode = new Ai4jCli().run(
                new String[]{"extension", "run", "--enable", "cli-test-pack", "cli-echo", "--name", "demo"},
                new ByteArrayInputStream(new byte[0]),
                out,
                err,
                Collections.<String, String>emptyMap(),
                new Properties()
        );

        String output = new String(out.toByteArray(), StandardCharsets.UTF_8);
        Assert.assertEquals(0, exitCode);
        Assert.assertTrue(output.contains("--name demo"));
        Assert.assertEquals(1, CliExtensionTestExtension.getApplyCount());
    }

    @Test
    public void test_extension_run_unknown_command_returns_extension_error() {
        CliExtensionTestExtension.resetApplyCount();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();

        int exitCode = new Ai4jCli().run(
                new String[]{"extension", "run", "--enable", "cli-test-pack", "missing-command"},
                new ByteArrayInputStream(new byte[0]),
                out,
                err,
                Collections.<String, String>emptyMap(),
                new Properties()
        );

        String error = new String(err.toByteArray(), StandardCharsets.UTF_8);
        Assert.assertEquals(2, exitCode);
        Assert.assertTrue(error.contains("command not registered by enabled extensions: missing-command"));
        Assert.assertEquals(1, CliExtensionTestExtension.getApplyCount());
    }

    @Test
    public void test_extension_run_rejects_invalid_enable_id_and_command_name() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();

        int badExtensionId = new Ai4jCli().run(
                new String[]{"extension", "run", "--enable", "bad id", "cli-echo"},
                new ByteArrayInputStream(new byte[0]),
                out,
                err,
                Collections.<String, String>emptyMap(),
                new Properties()
        );
        String badExtensionError = new String(err.toByteArray(), StandardCharsets.UTF_8);
        Assert.assertEquals(2, badExtensionId);
        Assert.assertTrue(badExtensionError.contains("extension id must start with a letter or digit"));

        out.reset();
        err.reset();
        int badCommandName = new Ai4jCli().run(
                new String[]{"extension", "run", "--enable", "cli-test-pack", "/bad/command"},
                new ByteArrayInputStream(new byte[0]),
                out,
                err,
                Collections.<String, String>emptyMap(),
                new Properties()
        );
        String badCommandError = new String(err.toByteArray(), StandardCharsets.UTF_8);
        Assert.assertEquals(2, badCommandName);
        Assert.assertTrue(badCommandError.contains("command name must start with a letter or digit"));
    }

    @Test
    public void test_extension_resource_requires_explicit_enable() {
        CliExtensionTestExtension.resetApplyCount();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();

        int exitCode = new Ai4jCli().run(
                new String[]{"extension", "resource", "skill", "cli-skill"},
                new ByteArrayInputStream(new byte[0]),
                out,
                err,
                Collections.<String, String>emptyMap(),
                new Properties()
        );

        String error = new String(err.toByteArray(), StandardCharsets.UTF_8);
        Assert.assertEquals(2, exitCode);
        Assert.assertTrue(error.contains("at least one --enable <extension-id> is required"));
        Assert.assertEquals(0, CliExtensionTestExtension.getApplyCount());
    }

    @Test
    public void test_extension_resource_prints_enabled_skill_content() {
        CliExtensionTestExtension.resetApplyCount();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();

        int exitCode = new Ai4jCli().run(
                new String[]{"extension", "resource", "--enable", "cli-test-pack", "skill", "cli-skill"},
                new ByteArrayInputStream(new byte[0]),
                out,
                err,
                Collections.<String, String>emptyMap(),
                new Properties()
        );

        String output = new String(out.toByteArray(), StandardCharsets.UTF_8);
        Assert.assertEquals(0, exitCode);
        Assert.assertTrue(output.contains("name: cli-skill"));
        Assert.assertTrue(output.contains("Use this fixture skill to verify extension resource loading."));
        Assert.assertEquals(1, CliExtensionTestExtension.getApplyCount());
    }

    @Test
    public void test_extension_resource_can_require_explicit_resource_allowlist() {
        CliExtensionTestExtension.resetApplyCount();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();

        int blocked = new Ai4jCli().run(
                new String[]{"extension", "resource", "--enable", "cli-test-pack", "--strict", "skill", "cli-skill"},
                new ByteArrayInputStream(new byte[0]),
                out,
                err,
                Collections.<String, String>emptyMap(),
                new Properties()
        );
        String blockedError = new String(err.toByteArray(), StandardCharsets.UTF_8);
        Assert.assertEquals(2, blocked);
        Assert.assertTrue(blockedError.contains("skill not registered by enabled extensions: cli-skill"));

        out.reset();
        err.reset();
        CliExtensionTestExtension.resetApplyCount();
        int allowed = new Ai4jCli().run(
                new String[]{"extension", "resource", "--enable", "cli-test-pack", "--allow-skill", "cli-skill", "skill", "cli-skill"},
                new ByteArrayInputStream(new byte[0]),
                out,
                err,
                Collections.<String, String>emptyMap(),
                new Properties()
        );

        String output = new String(out.toByteArray(), StandardCharsets.UTF_8);
        Assert.assertEquals(0, allowed);
        Assert.assertTrue(output.contains("name: cli-skill"));
        Assert.assertEquals(1, CliExtensionTestExtension.getApplyCount());
    }

    @Test
    public void test_extension_resource_prints_enabled_prompt_content() {
        CliExtensionTestExtension.resetApplyCount();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();

        int exitCode = new Ai4jCli().run(
                new String[]{"extensions", "resource", "--enable", "cli-test-pack", "prompt", "cli-prompt"},
                new ByteArrayInputStream(new byte[0]),
                out,
                err,
                Collections.<String, String>emptyMap(),
                new Properties()
        );

        String output = new String(out.toByteArray(), StandardCharsets.UTF_8);
        Assert.assertEquals(0, exitCode);
        Assert.assertTrue(output.contains("CLI extension prompt fixture"));
        Assert.assertTrue(output.contains("Summarize extension resources."));
        Assert.assertEquals(1, CliExtensionTestExtension.getApplyCount());
    }

    @Test
    public void test_extension_init_generates_maven_plugin_scaffold() throws Exception {
        Path root = temporaryFolder.newFolder("extension-init").toPath();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();

        Ai4jCli cli = new Ai4jCli(new StubCodingCliAgentFactory(), root);
        int exitCode = cli.run(
                new String[]{
                        "extension", "init", "weather-plugin",
                        "--id", "weather-pack",
                        "--package", "com.example.ai4j.weather",
                        "--name", "Weather Pack",
                        "--vendor", "Example"
                },
                new ByteArrayInputStream(new byte[0]),
                out,
                err,
                Collections.<String, String>emptyMap(),
                new Properties()
        );

        Path plugin = root.resolve("weather-plugin");
        String output = new String(out.toByteArray(), StandardCharsets.UTF_8);
        Assert.assertEquals(new String(err.toByteArray(), StandardCharsets.UTF_8), 0, exitCode);
        Assert.assertTrue(output.contains("extension scaffold:"));
        Assert.assertTrue(output.contains("id=weather-pack"));
        Assert.assertTrue(output.contains("package=com.example.ai4j.weather"));
        Assert.assertTrue(Files.exists(plugin.resolve("pom.xml")));
        Assert.assertTrue(Files.exists(plugin.resolve("README.md")));
        Assert.assertTrue(Files.exists(plugin.resolve("src/main/java/com/example/ai4j/weather/WeatherPackExtension.java")));
        Assert.assertTrue(Files.exists(plugin.resolve("src/main/resources/META-INF/services/io.github.lnyocly.ai4j.extension.Ai4jExtension")));
        Assert.assertTrue(Files.exists(plugin.resolve("src/main/resources/skills/weather-pack/SKILL.md")));
        Assert.assertTrue(Files.exists(plugin.resolve("src/main/resources/prompts/weather-pack-summary.md")));
        Assert.assertTrue(Files.exists(plugin.resolve("src/test/java/com/example/ai4j/weather/WeatherPackExtensionTest.java")));

        String extensionSource = read(plugin.resolve("src/main/java/com/example/ai4j/weather/WeatherPackExtension.java"));
        String testSource = read(plugin.resolve("src/test/java/com/example/ai4j/weather/WeatherPackExtensionTest.java"));
        String readme = read(plugin.resolve("README.md"));
        String service = read(plugin.resolve("src/main/resources/META-INF/services/io.github.lnyocly.ai4j.extension.Ai4jExtension"));
        Assert.assertTrue(extensionSource.contains("implements Ai4jExtension"));
        Assert.assertTrue(extensionSource.contains(".id(\"weather-pack\")"));
        Assert.assertTrue(extensionSource.contains(".capability(ExtensionCapability.TOOL)"));
        Assert.assertTrue(extensionSource.contains(".capability(ExtensionCapability.GUARDRAIL)"));
        Assert.assertTrue(extensionSource.contains(".resourcePath(\"skills/weather-pack/SKILL.md\")"));
        Assert.assertTrue(testSource.contains("ExtensionValidator.validate(registry, \"weather-pack\")"));
        Assert.assertTrue(readme.contains("## Package Metadata"));
        Assert.assertTrue(readme.contains("| Extension id | `weather-pack` |"));
        Assert.assertTrue(readme.contains("## Author Workflow"));
        Assert.assertTrue(readme.contains("ai4j-cli extension validate weather-pack"));
        Assert.assertTrue(readme.contains("ai4j-cli extension plan weather-pack --enable --expose-tool weather.pack.echo"));
        Assert.assertTrue(readme.contains("ai4j-cli extension resource --enable weather-pack --allow-skill weather-pack-skill skill weather-pack-skill"));
        Assert.assertTrue(readme.contains(".requireExplicitResourceActivation()"));
        Assert.assertTrue(readme.contains("explicit-resource-activation: true"));
        Assert.assertTrue(readme.contains("Classpath discovery does not enable this extension."));
        Assert.assertTrue(readme.contains("## Security And Side Effects"));
        Assert.assertTrue(readme.contains("## Publish Checklist"));
        Assert.assertTrue(service.contains("com.example.ai4j.weather.WeatherPackExtension"));
        assertGeneratedExtensionCompilesAndLoads(plugin);
    }

    @Test
    public void test_extension_init_rejects_non_empty_directory() throws Exception {
        Path root = temporaryFolder.newFolder("extension-init-non-empty").toPath();
        Path plugin = root.resolve("weather-plugin");
        Files.createDirectories(plugin);
        Files.write(plugin.resolve("existing.txt"), "keep".getBytes(StandardCharsets.UTF_8));
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();

        Ai4jCli cli = new Ai4jCli(new StubCodingCliAgentFactory(), root);
        int exitCode = cli.run(
                new String[]{
                        "extension", "init", "weather-plugin",
                        "--id", "weather-pack",
                        "--package", "com.example.ai4j.weather"
                },
                new ByteArrayInputStream(new byte[0]),
                out,
                err,
                Collections.<String, String>emptyMap(),
                new Properties()
        );

        String error = new String(err.toByteArray(), StandardCharsets.UTF_8);
        Assert.assertEquals(2, exitCode);
        Assert.assertTrue(error.contains("target directory must be empty"));
        Assert.assertTrue(Files.exists(plugin.resolve("existing.txt")));
        Assert.assertFalse(Files.exists(plugin.resolve("pom.xml")));
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

    private static String read(Path path) throws Exception {
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    }

    private static void assertGeneratedExtensionCompilesAndLoads(Path plugin) throws Exception {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        Assert.assertNotNull("JDK JavaCompiler is required for scaffold smoke test", compiler);
        Path classes = Files.createDirectories(plugin.resolve("target/test-classes"));
        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<JavaFileObject>();
        StandardJavaFileManager fileManager = compiler.getStandardFileManager(diagnostics, null, StandardCharsets.UTF_8);
        Boolean compiled;
        try {
            Iterable<? extends JavaFileObject> units = fileManager.getJavaFileObjects(
                    plugin.resolve("src/main/java/com/example/ai4j/weather/WeatherPackExtension.java").toFile(),
                    plugin.resolve("src/test/java/com/example/ai4j/weather/WeatherPackExtensionTest.java").toFile()
            );
            List<String> options = Arrays.asList(
                    "-classpath", System.getProperty("java.class.path"),
                    "-d", classes.toString(),
                    "-source", "1.8",
                    "-target", "1.8"
            );
            compiled = compiler.getTask(null, fileManager, diagnostics, options, null, units).call();
        } finally {
            fileManager.close();
        }
        Assert.assertTrue(formatDiagnostics(diagnostics), Boolean.TRUE.equals(compiled));

        URLClassLoader loader = new URLClassLoader(new URL[]{
                classes.toUri().toURL(),
                plugin.resolve("src/main/resources").toUri().toURL()
        }, Ai4jCliTest.class.getClassLoader());
        try {
            ServiceLoader<Ai4jExtension> serviceLoader = ServiceLoader.load(Ai4jExtension.class, loader);
            boolean foundGeneratedExtension = false;
            for (Ai4jExtension extension : serviceLoader) {
                if ("weather-pack".equals(extension.manifest().getId())) {
                    foundGeneratedExtension = true;
                    break;
                }
            }
            Assert.assertTrue("generated extension should be loadable through ServiceLoader", foundGeneratedExtension);
        } finally {
            loader.close();
        }
    }

    private static String formatDiagnostics(DiagnosticCollector<JavaFileObject> diagnostics) {
        StringBuilder builder = new StringBuilder();
        for (Diagnostic<? extends JavaFileObject> diagnostic : diagnostics.getDiagnostics()) {
            if (builder.length() > 0) {
                builder.append('\n');
            }
            builder.append(diagnostic.getKind())
                    .append(":")
                    .append(diagnostic.getLineNumber())
                    .append(":")
                    .append(diagnostic.getMessage(null));
        }
        return builder.toString();
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
