package io.github.lnyocly.ai4j.mcp.config;

import org.junit.Assert;
import org.junit.Test;

import java.util.concurrent.atomic.AtomicInteger;

public class McpConfigManagerTest {

    @Test
    public void shouldValidateSseConfigWithModernTypeField() {
        McpConfigManager configManager = new McpConfigManager();
        McpServerConfig.McpServerInfo serverInfo = new McpServerConfig.McpServerInfo();
        serverInfo.setName("weather");
        serverInfo.setType("sse");
        serverInfo.setUrl("http://localhost:8080/sse");

        Assert.assertTrue(configManager.validateConfig(serverInfo));
    }

    @Test
    public void shouldRejectUnknownTransportType() {
        McpConfigManager configManager = new McpConfigManager();
        McpServerConfig.McpServerInfo serverInfo = new McpServerConfig.McpServerInfo();
        serverInfo.setName("weather");
        serverInfo.setType("custom-bus");
        serverInfo.setCommand("npx");

        Assert.assertFalse(configManager.validateConfig(serverInfo));
    }

    @Test
    public void shouldNotifyListenerWhenUpdatingExistingConfig() {
        McpConfigManager configManager = new McpConfigManager();
        AtomicInteger addedCount = new AtomicInteger(0);
        AtomicInteger updatedCount = new AtomicInteger(0);

        configManager.addConfigChangeListener(new McpConfigSource.ConfigChangeListener() {
            @Override
            public void onConfigAdded(String serverId, McpServerConfig.McpServerInfo config) {
                addedCount.incrementAndGet();
            }

            @Override
            public void onConfigRemoved(String serverId) {
            }

            @Override
            public void onConfigUpdated(String serverId, McpServerConfig.McpServerInfo config) {
                updatedCount.incrementAndGet();
            }
        });

        configManager.addConfig("demo", createStdioConfig("npx"));
        configManager.updateConfig("demo", createStdioConfig("uvx"));

        Assert.assertEquals(1, addedCount.get());
        Assert.assertEquals(1, updatedCount.get());
        Assert.assertEquals("uvx", configManager.getConfig("demo").getCommand());
    }

    private McpServerConfig.McpServerInfo createStdioConfig(String command) {
        McpServerConfig.McpServerInfo serverInfo = new McpServerConfig.McpServerInfo();
        serverInfo.setName("filesystem");
        serverInfo.setType("stdio");
        serverInfo.setCommand(command);
        return serverInfo;
    }
}
