package io.github.lnyocly.ai4j.mcp.gateway;

import io.github.lnyocly.ai4j.mcp.client.McpClient;
import io.github.lnyocly.ai4j.mcp.config.McpConfigManager;
import io.github.lnyocly.ai4j.mcp.config.McpServerConfig;
import io.github.lnyocly.ai4j.mcp.entity.McpMessage;
import io.github.lnyocly.ai4j.mcp.entity.McpToolDefinition;
import io.github.lnyocly.ai4j.mcp.transport.McpTransport;
import org.junit.Assert;
import org.junit.Test;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class McpGatewayConfigSourceTest {

    @Test
    public void shouldLoadConfigSourceOnlyOnceDuringInitialize() {
        AtomicInteger createCount = new AtomicInteger(0);
        McpGateway gateway = new McpGateway(new CountingClientFactory(createCount));
        McpConfigManager configManager = new McpConfigManager();
        configManager.addConfig("demo", createStdioConfig("npx"));

        gateway.setConfigSource(configManager);
        gateway.initialize().join();

        Assert.assertTrue(gateway.isInitialized());
        Assert.assertEquals(1, createCount.get());
        Assert.assertEquals(1, ((Number) gateway.getGatewayStatus().get("totalClients")).intValue());

        gateway.shutdown().join();
    }

    @Test
    public void shouldDisconnectPreviousClientWhenReplacingSameKey() {
        McpGateway gateway = new McpGateway(new McpGatewayClientFactory());
        TestMcpClient firstClient = new TestMcpClient("demo", "first_tool");
        TestMcpClient secondClient = new TestMcpClient("demo", "second_tool");

        gateway.addMcpClient("demo", firstClient).join();
        gateway.addMcpClient("demo", secondClient).join();

        Assert.assertFalse(firstClient.isConnected());
        Assert.assertTrue(secondClient.isConnected());
        Assert.assertFalse(gateway.getToolToClientMap().containsKey("first_tool"));
        Assert.assertEquals("demo", gateway.getToolToClientMap().get("second_tool"));

        gateway.shutdown().join();
    }

    private McpServerConfig.McpServerInfo createStdioConfig(String command) {
        McpServerConfig.McpServerInfo serverInfo = new McpServerConfig.McpServerInfo();
        serverInfo.setName("filesystem");
        serverInfo.setType("stdio");
        serverInfo.setCommand(command);
        return serverInfo;
    }

    private static final class CountingClientFactory extends McpGatewayClientFactory {

        private final AtomicInteger createCount;

        private CountingClientFactory(AtomicInteger createCount) {
            this.createCount = createCount;
        }

        @Override
        public McpClient create(String serverId, McpServerConfig.McpServerInfo serverInfo) {
            createCount.incrementAndGet();
            return new TestMcpClient(serverId, serverId + "_tool");
        }
    }

    private static final class TestMcpClient extends McpClient {

        private final AtomicBoolean connected = new AtomicBoolean(false);
        private final List<McpToolDefinition> tools;

        private TestMcpClient(String clientName, String toolName) {
            super(clientName, "test", new NoopTransport());
            this.tools = Collections.singletonList(McpToolDefinition.builder()
                    .name(toolName)
                    .description("test tool")
                    .build());
        }

        @Override
        public CompletableFuture<Void> connect() {
            connected.set(true);
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletableFuture<Void> disconnect() {
            connected.set(false);
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public boolean isConnected() {
            return connected.get();
        }

        @Override
        public boolean isInitialized() {
            return connected.get();
        }

        @Override
        public CompletableFuture<List<McpToolDefinition>> getAvailableTools() {
            return CompletableFuture.completedFuture(tools);
        }
    }

    private static final class NoopTransport implements McpTransport {

        @Override
        public CompletableFuture<Void> start() {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletableFuture<Void> stop() {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletableFuture<Void> sendMessage(McpMessage message) {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public void setMessageHandler(McpMessageHandler handler) {
        }

        @Override
        public boolean isConnected() {
            return true;
        }

        @Override
        public boolean needsHeartbeat() {
            return false;
        }

        @Override
        public String getTransportType() {
            return "test";
        }
    }
}
