package io.github.lnyocly.ai4j.mcp;

import io.github.lnyocly.ai4j.extension.Ai4jExtension;
import io.github.lnyocly.ai4j.extension.ExtensionCapability;
import io.github.lnyocly.ai4j.extension.ExtensionManifest;
import io.github.lnyocly.ai4j.extension.ExtensionRegistry;
import io.github.lnyocly.ai4j.extension.ServiceLoaderExtensionLoader;
import io.github.lnyocly.ai4j.extension.tool.ExtensionToolCall;
import io.github.lnyocly.ai4j.extension.tool.ExtensionToolExecutor;
import io.github.lnyocly.ai4j.extension.validation.ExtensionValidationReport;
import io.github.lnyocly.ai4j.extension.validation.ExtensionValidator;
import io.github.lnyocly.ai4j.mcp.client.McpClient;
import io.github.lnyocly.ai4j.mcp.entity.McpMessage;
import io.github.lnyocly.ai4j.mcp.entity.McpToolDefinition;
import io.github.lnyocly.ai4j.mcp.gateway.McpGateway;
import io.github.lnyocly.ai4j.mcp.transport.McpTransport;
import io.github.lnyocly.ai4j.platform.openai.tool.Tool;
import io.github.lnyocly.ai4j.tool.McpToolBridge;
import io.github.lnyocly.ai4j.tool.ToolUtil;
import org.junit.Assert;
import org.junit.Test;

import java.util.Collections;
import java.util.List;
import java.util.ServiceLoader;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

public class McpExtensionAndBridgeTest {

    @Test
    public void serviceLoaderDiscoversBridgeAndExtension() {
        McpToolBridge bridge = null;
        for (McpToolBridge candidate : ServiceLoader.load(McpToolBridge.class)) {
            bridge = candidate;
            break;
        }
        Assert.assertNotNull("Expected DefaultMcpToolBridge via ServiceLoader", bridge);
        Assert.assertTrue(bridge instanceof DefaultMcpToolBridge);

        List<Ai4jExtension> extensions = new ServiceLoaderExtensionLoader(McpExtension.class.getClassLoader()).load();
        boolean found = false;
        for (Ai4jExtension extension : extensions) {
            if (McpExtension.EXTENSION_ID.equals(extension.manifest().getId())) {
                found = true;
            }
        }
        Assert.assertTrue("Expected McpExtension via ServiceLoader", found);
    }

    @Test
    public void extensionPassesValidationProvidesGatewayAndDisposesOnDisable() throws Exception {
        final AtomicBoolean shutdownCalled = new AtomicBoolean(false);
        McpGateway gateway = new McpGateway() {
            @Override
            public boolean isInitialized() {
                return true;
            }

            @Override
            public CompletableFuture<String> callTool(String toolName, Object arguments) {
                return CompletableFuture.completedFuture("ok:" + toolName + ":" + String.valueOf(arguments));
            }

            @Override
            public CompletableFuture<Void> shutdown() {
                shutdownCalled.set(true);
                return CompletableFuture.completedFuture(null);
            }
        };

        McpExtension extension = new McpExtension(gateway);
        ExtensionManifest manifest = extension.manifest();
        Assert.assertEquals("mcp", manifest.getId());
        Assert.assertTrue(manifest.hasCapability(ExtensionCapability.TOOL));

        ExtensionValidationReport report = ExtensionValidator.validate(ExtensionRegistry.of(extension), "mcp");
        Assert.assertTrue(report.getIssues().toString(), report.isValid());
        Assert.assertEquals(0, report.getWarningCount());

        ExtensionRegistry registry = ExtensionRegistry.of(extension)
                .enable("mcp")
                .exposeTool(McpExtension.NAMESPACED_CALL_TOOL_NAME);

        Assert.assertSame(gateway, registry.service(McpExtension.GATEWAY_SERVICE_KEY));

        ExtensionToolExecutor executor = registry.snapshot().getToolExecutors().get(McpExtension.NAMESPACED_CALL_TOOL_NAME);
        Assert.assertNotNull(executor);
        String result = executor.execute(new ExtensionToolCall(
                McpExtension.NAMESPACED_CALL_TOOL_NAME,
                "{\"toolName\":\"ping\",\"arguments\":{\"k\":\"v\"}}"));
        Assert.assertTrue(result.startsWith("ok:ping:"));

        registry.disable("mcp");
        Assert.assertTrue("Disabling McpExtension must dispose and shut down McpGateway", shutdownCalled.get());
        Assert.assertFalse(registry.findService(McpExtension.GATEWAY_SERVICE_KEY).isPresent());
    }

    @Test
    public void toolUtilDelegatesLocalAndGatewayMcpToolsThroughBridge() {
        List<Tool> localTools = ToolUtil.getLocalMcpTools();
        Assert.assertFalse("Expected TestMcpService local tools via SPI bridge", localTools.isEmpty());

        String greet = ToolUtil.invoke("TestService_greet", "{\"name\":\"Cordis\"}");
        Assert.assertEquals("\"Hello, Cordis! Welcome to MCP service!\"", greet);

        String sum = ToolUtil.invoke("TestService_add", "{\"a\":10,\"b\":32}");
        Assert.assertEquals("42", sum);

        McpGateway previous = null;
        final AtomicBoolean disconnected = new AtomicBoolean(false);
        McpGateway customGateway = new McpGateway() {
            @Override
            public boolean isInitialized() {
                return true;
            }
        };
        McpClient client = new McpClient("demo", "1.0", new NoopTransport()) {
            @Override
            public CompletableFuture<Void> connect() {
                return CompletableFuture.completedFuture(null);
            }

            @Override
            public CompletableFuture<Void> disconnect() {
                disconnected.set(true);
                return CompletableFuture.completedFuture(null);
            }

            @Override
            public boolean isConnected() {
                return true;
            }

            @Override
            public boolean isInitialized() {
                return true;
            }

            @Override
            public CompletableFuture<List<McpToolDefinition>> getAvailableTools() {
                return CompletableFuture.completedFuture(Collections.singletonList(
                        McpToolDefinition.builder()
                                .name("remote_echo")
                                .description("Echo from fake remote")
                                .inputSchema(Collections.<String, Object>emptyMap())
                                .build()));
            }

            @Override
            public CompletableFuture<String> callTool(String toolName, Object arguments) {
                return CompletableFuture.completedFuture("remote:" + toolName);
            }
        };

        try {
            customGateway.addMcpClient("github", client).join();
            McpGateway.setGlobalInstance(customGateway);

            List<Tool> remoteTools = ToolUtil.getAllMcpTools(Collections.singletonList("github"));
            Assert.assertEquals(1, remoteTools.size());
            Assert.assertEquals("remote_echo", remoteTools.get(0).getFunction().getName());
            Assert.assertEquals("remote:remote_echo", ToolUtil.invoke("remote_echo", "{}"));
        } finally {
            customGateway.shutdown().join();
            McpGateway.clearGlobalInstance();
        }
        Assert.assertTrue(disconnected.get());
        Assert.assertNull(previous);
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
            return "noop";
        }
    }
}
