package io.github.lnyocly.ai4j.cli.mcp;

import io.github.lnyocly.ai4j.cli.config.CliWorkspaceConfig;
import org.junit.Assert;
import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public class CliMcpConfigManagerTest {

    @Test
    public void saveLoadAndResolveMcpConfigs() throws Exception {
        Path home = Files.createTempDirectory("ai4j-cli-mcp-home");
        Path workspace = Files.createTempDirectory("ai4j-cli-mcp-workspace");
        String previousUserHome = System.getProperty("user.home");
        try {
            System.setProperty("user.home", home.toString());
            CliMcpConfigManager manager = new CliMcpConfigManager(workspace);

            Map<String, CliMcpServerDefinition> servers = new LinkedHashMap<String, CliMcpServerDefinition>();
            servers.put("fetch", CliMcpServerDefinition.builder()
                    .type("sse")
                    .url(" https://mcp.api-inference.modelscope.net/1e1a663049b340/sse ")
                    .build());
            servers.put("time", CliMcpServerDefinition.builder()
                    .command(" uvx ")
                    .args(Arrays.asList(" mcp-server-time ", ""))
                    .build());
            servers.put("bing-cn-mcp-server", CliMcpServerDefinition.builder()
                    .type("http")
                    .url("https://mcp.api-inference.modelscope.net/0904773a8c2045/mcp")
                    .build());
            manager.saveGlobalConfig(CliMcpConfig.builder()
                    .mcpServers(servers)
                    .build());

            manager.saveWorkspaceConfig(CliWorkspaceConfig.builder()
                    .enabledMcpServers(Arrays.asList(" fetch ", "time", "fetch", "missing", ""))
                    .skillDirectories(Arrays.asList(" .ai4j/skills ", "C:/skills/team ", ".ai4j/skills"))
                    .agentDirectories(Arrays.asList(" .ai4j/agents ", "C:/agents/team ", ".ai4j/agents"))
                    .build());

            CliMcpConfig loadedGlobal = manager.loadGlobalConfig();
            CliWorkspaceConfig loadedWorkspace = manager.loadWorkspaceConfig();
            CliResolvedMcpConfig resolved = manager.resolve(Collections.singleton("fetch"));

            Assert.assertEquals("sse", loadedGlobal.getMcpServers().get("fetch").getType());
            Assert.assertEquals("stdio", loadedGlobal.getMcpServers().get("time").getType());
            Assert.assertEquals("uvx", loadedGlobal.getMcpServers().get("time").getCommand());
            Assert.assertEquals(Arrays.asList("mcp-server-time"), loadedGlobal.getMcpServers().get("time").getArgs());
            Assert.assertEquals("streamable_http", loadedGlobal.getMcpServers().get("bing-cn-mcp-server").getType());

            Assert.assertEquals(Arrays.asList("fetch", "time", "missing"), loadedWorkspace.getEnabledMcpServers());
            Assert.assertEquals(Arrays.asList(".ai4j/skills", "C:/skills/team"), loadedWorkspace.getSkillDirectories());
            Assert.assertEquals(Arrays.asList(".ai4j/agents", "C:/agents/team"), loadedWorkspace.getAgentDirectories());

            CliResolvedMcpServer fetch = resolved.getServers().get("fetch");
            CliResolvedMcpServer time = resolved.getServers().get("time");
            CliResolvedMcpServer bing = resolved.getServers().get("bing-cn-mcp-server");

            Assert.assertNotNull(fetch);
            Assert.assertTrue(fetch.isWorkspaceEnabled());
            Assert.assertTrue(fetch.isSessionPaused());
            Assert.assertFalse(fetch.isActive());
            Assert.assertTrue(fetch.isValid());

            Assert.assertNotNull(time);
            Assert.assertTrue(time.isWorkspaceEnabled());
            Assert.assertFalse(time.isSessionPaused());
            Assert.assertTrue(time.isActive());
            Assert.assertTrue(time.isValid());

            Assert.assertNotNull(bing);
            Assert.assertFalse(bing.isWorkspaceEnabled());
            Assert.assertFalse(bing.isActive());

            Assert.assertEquals(Arrays.asList("fetch", "time"), resolved.getEnabledServerNames());
            Assert.assertEquals(Collections.singletonList("fetch"), resolved.getPausedServerNames());
            Assert.assertEquals(Collections.singletonList("missing"), resolved.getUnknownEnabledServerNames());

            String persisted = new String(Files.readAllBytes(manager.globalMcpPath()), StandardCharsets.UTF_8);
            Assert.assertTrue(persisted.contains("\"mcpServers\""));
            Assert.assertFalse(persisted.contains("\"version\""));
            Assert.assertFalse(persisted.contains("\"enabled\""));
        } finally {
            restoreUserHome(previousUserHome);
        }
    }

    @Test
    public void resolveMarksInvalidDefinitionsWithoutBlockingOtherServers() throws Exception {
        Path home = Files.createTempDirectory("ai4j-cli-mcp-invalid-home");
        Path workspace = Files.createTempDirectory("ai4j-cli-mcp-invalid-workspace");
        String previousUserHome = System.getProperty("user.home");
        try {
            System.setProperty("user.home", home.toString());
            CliMcpConfigManager manager = new CliMcpConfigManager(workspace);

            Map<String, CliMcpServerDefinition> servers = new LinkedHashMap<String, CliMcpServerDefinition>();
            servers.put("broken-stdio", CliMcpServerDefinition.builder()
                    .type("stdio")
                    .build());
            servers.put("broken-sse", CliMcpServerDefinition.builder()
                    .type("sse")
                    .build());
            servers.put("ambiguous", CliMcpServerDefinition.builder()
                    .url("http://localhost:8080/mcp")
                    .build());
            servers.put("working", CliMcpServerDefinition.builder()
                    .command("uvx")
                    .args(Collections.singletonList("mcp-server-time"))
                    .build());
            manager.saveGlobalConfig(CliMcpConfig.builder()
                    .mcpServers(servers)
                    .build());

            manager.saveWorkspaceConfig(CliWorkspaceConfig.builder()
                    .enabledMcpServers(Arrays.asList("broken-stdio", "broken-sse", "ambiguous", "working"))
                    .build());

            CliResolvedMcpConfig resolved = manager.resolve(Collections.<String>emptyList());

            Assert.assertEquals("stdio transport requires command", resolved.getServers().get("broken-stdio").getValidationError());
            Assert.assertEquals("sse transport requires url", resolved.getServers().get("broken-sse").getValidationError());
            Assert.assertEquals("missing MCP transport type", resolved.getServers().get("ambiguous").getValidationError());
            Assert.assertFalse(resolved.getServers().get("broken-stdio").isActive());
            Assert.assertFalse(resolved.getServers().get("broken-sse").isActive());
            Assert.assertFalse(resolved.getServers().get("ambiguous").isActive());
            Assert.assertTrue(resolved.getServers().get("working").isActive());
            Assert.assertEquals("stdio", resolved.getServers().get("working").getTransportType());
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
