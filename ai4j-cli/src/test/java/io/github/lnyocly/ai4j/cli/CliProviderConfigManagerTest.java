package io.github.lnyocly.ai4j.cli;

import io.github.lnyocly.ai4j.cli.config.CliWorkspaceConfig;
import io.github.lnyocly.ai4j.cli.provider.CliProviderConfigManager;
import io.github.lnyocly.ai4j.cli.provider.CliProviderProfile;
import io.github.lnyocly.ai4j.cli.provider.CliProvidersConfig;
import io.github.lnyocly.ai4j.cli.provider.CliResolvedProviderConfig;
import io.github.lnyocly.ai4j.service.PlatformType;
import org.junit.Assert;
import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.Properties;

public class CliProviderConfigManagerTest {

    @Test
    public void saveAndLoadProviderAndWorkspaceConfigs() throws Exception {
        Path home = Files.createTempDirectory("ai4j-cli-home");
        Path workspace = Files.createTempDirectory("ai4j-cli-workspace");
        String previousUserHome = System.getProperty("user.home");
        try {
            System.setProperty("user.home", home.toString());
            CliProviderConfigManager manager = new CliProviderConfigManager(workspace);

            CliProvidersConfig providersConfig = CliProvidersConfig.builder()
                    .defaultProfile("zhipu-main")
                    .build();
            providersConfig.getProfiles().put("zhipu-main", CliProviderProfile.builder()
                    .provider("zhipu")
                    .protocol("chat")
                    .model("glm-4.7")
                    .baseUrl("https://open.bigmodel.cn/api/coding/paas/v4")
                    .apiKey("secret-zhipu")
                    .build());
            manager.saveProvidersConfig(providersConfig);
            manager.saveWorkspaceConfig(CliWorkspaceConfig.builder()
                    .activeProfile("zhipu-main")
                    .modelOverride("glm-4.7-plus")
                    .enabledMcpServers(Arrays.asList(" fetch ", "time", "fetch"))
                    .skillDirectories(Arrays.asList(" .ai4j/skills ", "C:/skills/team ", ".ai4j/skills"))
                    .agentDirectories(Arrays.asList(" .ai4j/agents ", "C:/agents/team ", ".ai4j/agents"))
                    .build());

            CliProvidersConfig loadedProviders = manager.loadProvidersConfig();
            CliWorkspaceConfig loadedWorkspace = manager.loadWorkspaceConfig();
            CliResolvedProviderConfig resolved = manager.resolve(
                    null,
                    null,
                    null,
                    null,
                    null,
                    Collections.<String, String>emptyMap(),
                    new Properties()
            );

            Assert.assertEquals("zhipu-main", loadedProviders.getDefaultProfile());
            Assert.assertEquals(1, loadedProviders.getProfiles().size());
            Assert.assertEquals("zhipu-main", loadedWorkspace.getActiveProfile());
            Assert.assertEquals("glm-4.7-plus", loadedWorkspace.getModelOverride());
            Assert.assertEquals(Arrays.asList("fetch", "time"), loadedWorkspace.getEnabledMcpServers());
            Assert.assertEquals(Arrays.asList(".ai4j/skills", "C:/skills/team"), loadedWorkspace.getSkillDirectories());
            Assert.assertEquals(Arrays.asList(".ai4j/agents", "C:/agents/team"), loadedWorkspace.getAgentDirectories());
            Assert.assertEquals(PlatformType.ZHIPU, resolved.getProvider());
            Assert.assertEquals(CliProtocol.CHAT, resolved.getProtocol());
            Assert.assertEquals("glm-4.7-plus", resolved.getModel());
            Assert.assertEquals("secret-zhipu", resolved.getApiKey());
        } finally {
            restoreUserHome(previousUserHome);
        }
    }

    @Test
    public void loadProvidersConfigMigratesLegacyAutoProtocol() throws Exception {
        Path home = Files.createTempDirectory("ai4j-cli-home-auto");
        Path workspace = Files.createTempDirectory("ai4j-cli-workspace-auto");
        String previousUserHome = System.getProperty("user.home");
        try {
            System.setProperty("user.home", home.toString());
            CliProviderConfigManager manager = new CliProviderConfigManager(workspace);

            CliProvidersConfig providersConfig = CliProvidersConfig.builder()
                    .defaultProfile("openai-main")
                    .build();
            providersConfig.getProfiles().put("openai-main", CliProviderProfile.builder()
                    .provider("openai")
                    .protocol("auto")
                    .model("gpt-5-mini")
                    .build());
            manager.saveProvidersConfig(providersConfig);

            CliProvidersConfig loadedProviders = manager.loadProvidersConfig();
            CliProviderProfile loadedProfile = loadedProviders.getProfiles().get("openai-main");
            CliResolvedProviderConfig resolved = manager.resolve(
                    null,
                    null,
                    null,
                    null,
                    null,
                    Collections.<String, String>emptyMap(),
                    new Properties()
            );

            Assert.assertNotNull(loadedProfile);
            Assert.assertEquals("responses", loadedProfile.getProtocol());
            Assert.assertEquals(CliProtocol.RESPONSES, resolved.getProtocol());
        } finally {
            restoreUserHome(previousUserHome);
        }
    }

    private void restoreUserHome(String value) {
        if (value == null) {
            System.clearProperty("user.home");
            return;
        }
        System.setProperty("user.home", value);
    }
}
