package io.github.lnyocly.ai4j.mcp.transport;

import io.github.lnyocly.ai4j.mcp.entity.McpMessage;
import io.github.lnyocly.ai4j.mcp.entity.McpRequest;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.After;
import org.junit.Assert;
import org.junit.Test;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

public class StreamableHttpTransportTest {

    private MockWebServer server;

    @After
    public void tearDown() throws Exception {
        if (server != null) {
            server.shutdown();
            server = null;
        }
    }

    @Test
    public void parsesFramedMultilineSseDataWithoutSpaceAndKeepsEventId() throws Exception {
        server = new MockWebServer();
        server.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "text/event-stream")
                .setBody(": keep-alive\n\n"
                        + "id: stream-1\n"
                        + "event: message\n"
                        + "data:{\"jsonrpc\":\"2.0\",\"id\":3,\n"
                        + "data: \"result\":{\"value\":\"multi-line\"}}\n\n"));
        server.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("{\"jsonrpc\":\"2.0\",\"id\":4,\"result\":{\"value\":\"json\"}}"));
        server.start();

        TransportConfig config = TransportConfig.streamableHttp(server.url("/mcp").toString())
                .withProtocolProfile(McpProtocolProfile.LEGACY_2025_03_26);
        StreamableHttpTransport transport = new StreamableHttpTransport(config);
        CapturingHandler handler = new CapturingHandler();
        transport.setMessageHandler(handler);

        transport.start().get(5, TimeUnit.SECONDS);
        transport.sendMessage(new McpRequest("initialize", 3L, Collections.<String, Object>emptyMap()))
                .get(5, TimeUnit.SECONDS);

        Assert.assertTrue("expected the SSE response", handler.firstMessageLatch.await(5, TimeUnit.SECONDS));
        Assert.assertNull("unexpected transport error", handler.lastError);
        Assert.assertNotNull(handler.firstMessage.get());
        Assert.assertEquals(3L, ((Number) handler.firstMessage.get().getId()).longValue());
        Assert.assertTrue(String.valueOf(handler.firstMessage.get().getResult()).contains("multi-line"));

        transport.sendMessage(new McpRequest("ping", 4L, Collections.<String, Object>emptyMap()))
                .get(5, TimeUnit.SECONDS);

        Assert.assertTrue("expected the JSON response", handler.secondMessageLatch.await(5, TimeUnit.SECONDS));
        Assert.assertNull("unexpected transport error", handler.lastError);
        Assert.assertNotNull(handler.secondMessage.get());
        Assert.assertEquals(4L, ((Number) handler.secondMessage.get().getId()).longValue());

        RecordedRequest firstRequest = server.takeRequest(5, TimeUnit.SECONDS);
        RecordedRequest secondRequest = server.takeRequest(5, TimeUnit.SECONDS);
        Assert.assertNotNull(firstRequest);
        Assert.assertNotNull(secondRequest);
        Assert.assertEquals("POST", firstRequest.getMethod());
        Assert.assertEquals("stream-1", secondRequest.getHeader("last-event-id"));

        transport.stop().get(5, TimeUnit.SECONDS);
    }

    @Test
    public void modernProfileSendsPerRequestMetadataAndNoLegacySessionHeaders() throws Exception {
        server = new MockWebServer();
        server.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("{\"jsonrpc\":\"2.0\",\"id\":9,\"result\":{\"resultType\":\"complete\"}}"));
        server.start();

        StreamableHttpTransport transport = new StreamableHttpTransport(server.url("/mcp").toString());
        transport.setMessageHandler(new CapturingHandler());
        transport.start().get(5, TimeUnit.SECONDS);

        Map<String, Object> metadata = new HashMap<String, Object>();
        metadata.put("io.modelcontextprotocol/protocolVersion", "2026-07-28");
        metadata.put("io.modelcontextprotocol/clientCapabilities", new HashMap<String, Object>());
        Map<String, Object> params = new HashMap<String, Object>();
        params.put("_meta", metadata);
        transport.sendMessage(new McpRequest("tools/list", 9L, params)).get(5, TimeUnit.SECONDS);

        RecordedRequest request = server.takeRequest(5, TimeUnit.SECONDS);
        Assert.assertEquals("2026-07-28", request.getHeader("MCP-Protocol-Version"));
        Assert.assertEquals("tools/list", request.getHeader("Mcp-Method"));
        Assert.assertNull(request.getHeader("mcp-session-id"));
        Assert.assertNull(request.getHeader("last-event-id"));
        Assert.assertTrue(request.getBody().readUtf8().contains("io.modelcontextprotocol/protocolVersion"));

        transport.stop().get(5, TimeUnit.SECONDS);
    }

    @Test
    public void modernProfileMirrorsSchemaParameterHeaders() throws Exception {
        server = new MockWebServer();
        server.enqueue(new MockResponse().setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("{\"jsonrpc\":\"2.0\",\"id\":10,\"result\":{\"resultType\":\"complete\"}}"));
        server.start();

        StreamableHttpTransport transport = new StreamableHttpTransport(server.url("/mcp").toString());
        transport.setMessageHandler(new CapturingHandler());
        transport.start().get(5, TimeUnit.SECONDS);
        Map<String, Object> metadata = new HashMap<String, Object>();
        metadata.put("io.modelcontextprotocol/protocolVersion", "2026-07-28");
        metadata.put("io.modelcontextprotocol/clientCapabilities", new HashMap<String, Object>());
        Map<String, Object> params = new HashMap<String, Object>();
        params.put("name", "weather");
        params.put("arguments", Collections.singletonMap("region", "cn"));
        params.put("_meta", metadata);
        transport.sendMessage(new McpRequest("tools/call", 10L, params),
                Collections.singletonMap("Mcp-Param-Region", "cn")).get(5, TimeUnit.SECONDS);

        RecordedRequest request = server.takeRequest(5, TimeUnit.SECONDS);
        Assert.assertEquals("weather", request.getHeader("Mcp-Name"));
        Assert.assertEquals("cn", request.getHeader("Mcp-Param-Region"));
        transport.stop().get(5, TimeUnit.SECONDS);
    }

    private static final class CapturingHandler implements McpTransport.McpMessageHandler {

        private final CountDownLatch firstMessageLatch = new CountDownLatch(1);
        private final CountDownLatch secondMessageLatch = new CountDownLatch(1);
        private final AtomicReference<McpMessage> firstMessage = new AtomicReference<McpMessage>();
        private final AtomicReference<McpMessage> secondMessage = new AtomicReference<McpMessage>();
        private volatile Throwable lastError;

        @Override
        public void handleMessage(McpMessage message) {
            if (firstMessage.compareAndSet(null, message)) {
                firstMessageLatch.countDown();
            } else {
                secondMessage.set(message);
                secondMessageLatch.countDown();
            }
        }

        @Override
        public void onConnected() {
        }

        @Override
        public void onDisconnected(String reason) {
        }

        @Override
        public void onError(Throwable error) {
            lastError = error;
            firstMessageLatch.countDown();
            secondMessageLatch.countDown();
        }
    }
}
