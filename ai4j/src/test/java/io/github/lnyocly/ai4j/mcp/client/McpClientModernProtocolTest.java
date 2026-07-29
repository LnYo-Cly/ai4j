package io.github.lnyocly.ai4j.mcp.client;

import io.github.lnyocly.ai4j.mcp.entity.McpMessage;
import io.github.lnyocly.ai4j.mcp.entity.McpResponse;
import io.github.lnyocly.ai4j.mcp.transport.McpProtocolProfile;
import io.github.lnyocly.ai4j.mcp.transport.McpTransport;
import org.junit.Assert;
import org.junit.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public class McpClientModernProtocolTest {

    @Test
    public void modernClientDoesNotHandshakeAndAddsHonestMetadataToEachRequest() throws Exception {
        CapturingTransport transport = new CapturingTransport();
        McpClient client = new McpClient("test-client", "1.0", transport, false);
        client.connect().get();

        Assert.assertTrue(client.isInitialized());
        Assert.assertEquals(0, transport.sent.size());

        client.getAvailableTools().get();
        Assert.assertEquals(1, transport.sent.size());
        McpMessage request = transport.sent.get(0);
        Assert.assertEquals("tools/list", request.getMethod());
        Map<String, Object> params = asMap(request.getParams());
        Map<String, Object> metadata = asMap(params.get("_meta"));
        Assert.assertEquals("2026-07-28", metadata.get("io.modelcontextprotocol/protocolVersion"));
        Assert.assertEquals("test-client", asMap(metadata.get("io.modelcontextprotocol/clientInfo")).get("name"));
        Assert.assertTrue(asMap(metadata.get("io.modelcontextprotocol/clientCapabilities")).isEmpty());
    }

    private static Map<String, Object> asMap(Object value) {
        Map<String, Object> result = new HashMap<String, Object>();
        if (value instanceof Map<?, ?>) {
            for (Map.Entry<?, ?> entry : ((Map<?, ?>) value).entrySet()) {
                result.put(String.valueOf(entry.getKey()), entry.getValue());
            }
        }
        return result;
    }

    private static final class CapturingTransport implements McpTransport {
        private final List<McpMessage> sent = new ArrayList<McpMessage>();
        private McpMessageHandler handler;

        @Override
        public CompletableFuture<Void> start() {
            if (handler != null) {
                handler.onConnected();
            }
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletableFuture<Void> stop() {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletableFuture<Void> sendMessage(McpMessage message) {
            sent.add(message);
            McpResponse response = new McpResponse();
            response.setId(message.getId());
            Map<String, Object> result = new HashMap<String, Object>();
            result.put("resultType", "complete");
            result.put("tools", new ArrayList<Object>());
            response.setResult(result);
            handler.handleMessage(response);
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public void setMessageHandler(McpMessageHandler handler) {
            this.handler = handler;
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
            return "streamable_http";
        }

        @Override
        public McpProtocolProfile getProtocolProfile() {
            return McpProtocolProfile.MODERN_2026_07_28;
        }
    }
}
