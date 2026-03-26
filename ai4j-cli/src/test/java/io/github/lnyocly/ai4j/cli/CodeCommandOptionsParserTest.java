package io.github.lnyocly.ai4j.cli;

import io.github.lnyocly.ai4j.service.PlatformType;
import org.junit.Assert;
import org.junit.Test;

import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

public class CodeCommandOptionsParserTest {

    private final CodeCommandOptionsParser parser = new CodeCommandOptionsParser();

    @Test
    public void test_parse_resolves_env_and_defaults() {
        Map<String, String> env = new HashMap<String, String>();
        env.put("AI4J_WORKSPACE", "workspace-from-env");
        env.put("ZHIPU_API_KEY", "zhipu-key-from-env");

        CodeCommandOptions options = parser.parse(
                Arrays.asList("--provider", "zhipu", "--model", "GLM-4.5-Flash"),
                env,
                new Properties(),
                Paths.get(".")
        );

        Assert.assertFalse(options.isHelp());
        Assert.assertEquals(CliUiMode.CLI, options.getUiMode());
        Assert.assertEquals(PlatformType.ZHIPU, options.getProvider());
        Assert.assertEquals(CliProtocol.CHAT, options.getProtocol());
        Assert.assertEquals("GLM-4.5-Flash", options.getModel());
        Assert.assertEquals("zhipu-key-from-env", options.getApiKey());
        Assert.assertEquals("workspace-from-env", options.getWorkspace());
        Assert.assertEquals(12, options.getMaxSteps());
        Assert.assertEquals(32, options.getMaxToolCalls());
        Assert.assertEquals(Boolean.FALSE, options.getParallelToolCalls());
    }

    @Test
    public void test_help_does_not_require_model() {
        CodeCommandOptions options = parser.parse(
                Collections.singletonList("--help"),
                Collections.<String, String>emptyMap(),
                new Properties(),
                Paths.get(".")
        );

        Assert.assertTrue(options.isHelp());
        Assert.assertEquals(CliUiMode.CLI, options.getUiMode());
        Assert.assertNull(options.getModel());
    }

    @Test
    public void test_parse_ui_mode_from_env() {
        Map<String, String> env = new HashMap<String, String>();
        env.put("AI4J_UI", "tui");

        CodeCommandOptions options = parser.parse(
                Arrays.asList("--model", "demo-model"),
                env,
                new Properties(),
                Paths.get(".")
        );

        Assert.assertEquals(CliUiMode.TUI, options.getUiMode());
    }

    @Test
    public void test_parse_session_options() {
        CodeCommandOptions options = parser.parse(
                Arrays.asList(
                        "--model", "demo-model",
                        "--workspace", "workspace-root",
                        "--session-id", "session-alpha",
                        "--fork", "session-beta",
                        "--theme", "amber",
                        "--approval", "safe",
                        "--no-session", "false",
                        "--session-dir", "custom-sessions",
                        "--auto-save-session", "false",
                        "--auto-compact", "true",
                        "--compact-context-window-tokens", "64000",
                        "--compact-reserve-tokens", "8000",
                        "--compact-keep-recent-tokens", "12000",
                        "--compact-summary-max-output-tokens", "512"
                ),
                Collections.<String, String>emptyMap(),
                new Properties(),
                Paths.get(".")
        );

        Assert.assertEquals("session-alpha", options.getSessionId());
        Assert.assertEquals("session-beta", options.getForkSessionId());
        Assert.assertEquals("amber", options.getTheme());
        Assert.assertEquals(ApprovalMode.SAFE, options.getApprovalMode());
        Assert.assertFalse(options.isNoSession());
        Assert.assertEquals("custom-sessions", options.getSessionStoreDir());
        Assert.assertFalse(options.isAutoSaveSession());
        Assert.assertTrue(options.isAutoCompact());
        Assert.assertEquals(64000, options.getCompactContextWindowTokens());
        Assert.assertEquals(8000, options.getCompactReserveTokens());
        Assert.assertEquals(12000, options.getCompactKeepRecentTokens());
        Assert.assertEquals(512, options.getCompactSummaryMaxOutputTokens());
    }

    @Test
    public void test_invalid_provider_should_fail_fast() {
        try {
            parser.parse(
                    Arrays.asList("--provider", "unknown", "--model", "demo-model"),
                    Collections.<String, String>emptyMap(),
                    new Properties(),
                    Paths.get(".")
            );
            Assert.fail("Expected invalid provider to fail fast");
        } catch (IllegalArgumentException ex) {
            Assert.assertEquals("Unsupported provider: unknown", ex.getMessage());
        }
    }

    @Test
    public void test_no_session_cannot_resume_or_fork_on_startup() {
        try {
            parser.parse(
                    Arrays.asList("--model", "demo-model", "--no-session", "--resume", "session-alpha"),
                    Collections.<String, String>emptyMap(),
                    new Properties(),
                    Paths.get(".")
            );
            Assert.fail("Expected conflicting no-session and resume");
        } catch (IllegalArgumentException ex) {
            Assert.assertEquals("--no-session cannot be combined with --resume", ex.getMessage());
        }

        try {
            parser.parse(
                    Arrays.asList("--model", "demo-model", "--no-session", "--fork", "session-alpha"),
                    Collections.<String, String>emptyMap(),
                    new Properties(),
                    Paths.get(".")
            );
            Assert.fail("Expected conflicting no-session and fork");
        } catch (IllegalArgumentException ex) {
            Assert.assertEquals("--no-session cannot be combined with --fork", ex.getMessage());
        }
    }

    @Test
    public void test_parse_prefers_workspace_and_global_profile_config_before_env_fallbacks() throws Exception {
        java.nio.file.Path home = java.nio.file.Files.createTempDirectory("ai4j-cli-parser-home");
        java.nio.file.Path workspace = java.nio.file.Files.createTempDirectory("ai4j-cli-parser-workspace");
        String previousUserHome = System.getProperty("user.home");
        try {
            System.setProperty("user.home", home.toString());
            CliProviderConfigManager manager = new CliProviderConfigManager(workspace);
            CliProvidersConfig providersConfig = CliProvidersConfig.builder()
                    .defaultProfile("openai-default")
                    .build();
            providersConfig.getProfiles().put("openai-default", CliProviderProfile.builder()
                    .provider("openai")
                    .protocol("responses")
                    .model("gpt-5-mini")
                    .apiKey("openai-default-key")
                    .build());
            providersConfig.getProfiles().put("zhipu-workspace", CliProviderProfile.builder()
                    .provider("zhipu")
                    .protocol("chat")
                    .model("glm-4.7")
                    .baseUrl("https://open.bigmodel.cn/api/coding/paas/v4")
                    .apiKey("zhipu-workspace-key")
                    .build());
            manager.saveProvidersConfig(providersConfig);
            manager.saveWorkspaceConfig(CliWorkspaceConfig.builder()
                    .activeProfile("zhipu-workspace")
                    .modelOverride("glm-4.7-plus")
                    .build());

            Map<String, String> env = new HashMap<String, String>();
            env.put("AI4J_PROVIDER", "deepseek");
            env.put("AI4J_MODEL", "deepseek-chat");
            env.put("AI4J_API_KEY", "generic-env-key");
            env.put("DEEPSEEK_API_KEY", "deepseek-env-key");

            CodeCommandOptions options = parser.parse(
                    Arrays.asList("--workspace", workspace.toString()),
                    env,
                    new Properties(),
                    workspace
            );

            Assert.assertEquals(PlatformType.ZHIPU, options.getProvider());
            Assert.assertEquals(CliProtocol.CHAT, options.getProtocol());
            Assert.assertEquals("glm-4.7-plus", options.getModel());
            Assert.assertEquals("zhipu-workspace-key", options.getApiKey());
            Assert.assertEquals("https://open.bigmodel.cn/api/coding/paas/v4", options.getBaseUrl());
        } finally {
            if (previousUserHome == null) {
                System.clearProperty("user.home");
            } else {
                System.setProperty("user.home", previousUserHome);
            }
        }
    }

    @Test
    public void test_parse_cli_runtime_values_override_profile_config() throws Exception {
        java.nio.file.Path home = java.nio.file.Files.createTempDirectory("ai4j-cli-parser-home-override");
        java.nio.file.Path workspace = java.nio.file.Files.createTempDirectory("ai4j-cli-parser-workspace-override");
        String previousUserHome = System.getProperty("user.home");
        try {
            System.setProperty("user.home", home.toString());
            CliProviderConfigManager manager = new CliProviderConfigManager(workspace);
            CliProvidersConfig providersConfig = CliProvidersConfig.builder()
                    .defaultProfile("zhipu-default")
                    .build();
            providersConfig.getProfiles().put("zhipu-default", CliProviderProfile.builder()
                    .provider("zhipu")
                    .protocol("chat")
                    .model("glm-4.7")
                    .apiKey("zhipu-config-key")
                    .build());
            manager.saveProvidersConfig(providersConfig);

            CodeCommandOptions options = parser.parse(
                    Arrays.asList(
                            "--workspace", workspace.toString(),
                            "--provider", "openai",
                            "--protocol", "responses",
                            "--model", "gpt-5",
                            "--api-key", "openai-cli-key",
                            "--base-url", "https://api.openai.com/v1"
                    ),
                    Collections.<String, String>emptyMap(),
                    new Properties(),
                    workspace
            );

            Assert.assertEquals(PlatformType.OPENAI, options.getProvider());
            Assert.assertEquals(CliProtocol.RESPONSES, options.getProtocol());
            Assert.assertEquals("gpt-5", options.getModel());
            Assert.assertEquals("openai-cli-key", options.getApiKey());
            Assert.assertEquals("https://api.openai.com/v1", options.getBaseUrl());
        } finally {
            if (previousUserHome == null) {
                System.clearProperty("user.home");
            } else {
                System.setProperty("user.home", previousUserHome);
            }
        }
    }

    @Test
    public void test_parse_rejects_explicit_auto_protocol_flag() {
        try {
            parser.parse(
                    Arrays.asList("--provider", "openai", "--protocol", "auto", "--model", "gpt-5-mini"),
                    Collections.<String, String>emptyMap(),
                    new Properties(),
                    Paths.get(".")
            );
            Assert.fail("Expected auto protocol flag to be rejected");
        } catch (IllegalArgumentException ex) {
            Assert.assertEquals("Unsupported protocol: auto. Expected: chat, responses", ex.getMessage());
        }
    }

    @Test
    public void test_parse_provider_override_does_not_reuse_mismatched_profile_credentials() throws Exception {
        java.nio.file.Path home = java.nio.file.Files.createTempDirectory("ai4j-cli-parser-home-provider-only");
        java.nio.file.Path workspace = java.nio.file.Files.createTempDirectory("ai4j-cli-parser-workspace-provider-only");
        String previousUserHome = System.getProperty("user.home");
        try {
            System.setProperty("user.home", home.toString());
            CliProviderConfigManager manager = new CliProviderConfigManager(workspace);
            CliProvidersConfig providersConfig = CliProvidersConfig.builder()
                    .defaultProfile("zhipu-default")
                    .build();
            providersConfig.getProfiles().put("zhipu-default", CliProviderProfile.builder()
                    .provider("zhipu")
                    .protocol("chat")
                    .model("glm-4.7")
                    .apiKey("zhipu-config-key")
                    .baseUrl("https://open.bigmodel.cn/api/coding/paas/v4")
                    .build());
            manager.saveProvidersConfig(providersConfig);

            Map<String, String> env = new HashMap<String, String>();
            env.put("OPENAI_API_KEY", "openai-env-key");

            CodeCommandOptions options = parser.parse(
                    Arrays.asList("--workspace", workspace.toString(), "--provider", "openai", "--model", "gpt-5-mini"),
                    env,
                    new Properties(),
                    workspace
            );

            Assert.assertEquals(PlatformType.OPENAI, options.getProvider());
            Assert.assertEquals("gpt-5-mini", options.getModel());
            Assert.assertEquals("openai-env-key", options.getApiKey());
            Assert.assertNull(options.getBaseUrl());
        } finally {
            if (previousUserHome == null) {
                System.clearProperty("user.home");
            } else {
                System.setProperty("user.home", previousUserHome);
            }
        }
    }
}
