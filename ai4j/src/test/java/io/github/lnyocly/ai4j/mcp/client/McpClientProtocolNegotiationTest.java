package io.github.lnyocly.ai4j.mcp.client;

import io.github.lnyocly.ai4j.mcp.transport.McpProtocolProfile;
import io.github.lnyocly.ai4j.mcp.transport.StreamableHttpTransport;
import io.github.lnyocly.ai4j.mcp.transport.TransportConfig;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.After;
import org.junit.Assert;
import org.junit.Test;

import java.util.concurrent.TimeUnit;

public class McpClientProtocolNegotiationTest {

    private MockWebServer server;
    private McpClient client;

    @After
    public void tearDown() throws Exception {
        if (client != null) {
            client.disconnect().get(5, TimeUnit.SECONDS);
            client = null;
        }
        if (server != null) {
            server.shutdown();
            server = null;
        }
    }

    @Test
    public void autoSelectsModernAfterDiscoveryWithoutInitialize() throws Exception {
        server = new MockWebServer();
        server.enqueue(json(200, "{\"jsonrpc\":\"2.0\",\"id\":0,\"result\":{"
                + "\"resultType\":\"complete\",\"ttlMs\":0,\"cacheScope\":\"private\","
                + "\"supportedVersions\":[\"2026-07-28\"],\"capabilities\":{},"
                + "\"_meta\":{\"io.modelcontextprotocol/serverInfo\":{\"name\":\"official\",\"version\":\"2.0\"}}}}"));
        server.start();

        StreamableHttpTransport transport = autoTransport();
        client = new McpClient("interop-client", "1.0", transport, false);
        client.connect().get(5, TimeUnit.SECONDS);

        Assert.assertEquals(McpProtocolProfile.MODERN_2026_07_28, transport.getProtocolProfile());
        Assert.assertTrue(client.isInitialized());
        RecordedRequest discovery = server.takeRequest(5, TimeUnit.SECONDS);
        Assert.assertNotNull(discovery);
        Assert.assertEquals("server/discover", discovery.getHeader("Mcp-Method"));
        Assert.assertEquals("2026-07-28", discovery.getHeader("MCP-Protocol-Version"));
        Assert.assertNull(discovery.getHeader("Mcp-Session-Id"));
        Assert.assertTrue(discovery.getBody().readUtf8().contains("io.modelcontextprotocol/clientCapabilities"));
        Assert.assertNull("modern negotiation must not send initialize", server.takeRequest(250, TimeUnit.MILLISECONDS));
    }

    @Test
    public void autoSelectsModernWhenDiscoveryUsesSseResponse() throws Exception {
        server = new MockWebServer();
        server.enqueue(new MockResponse().setResponseCode(200)
                .setHeader("Content-Type", "text/event-stream")
                .setBody(": keep-alive\n\n"
                        + "data: {\"jsonrpc\":\"2.0\",\"id\":0,\"result\":{"
                        + "\"resultType\":\"complete\",\"ttlMs\":0,\"cacheScope\":\"private\","
                        + "\"supportedVersions\":[\"2026-07-28\"],\"capabilities\":{},"
                        + "\"_meta\":{\"io.modelcontextprotocol/serverInfo\":{\"name\":\"official\",\"version\":\"2.0\"}}}}\n\n"));
        server.start();

        StreamableHttpTransport transport = autoTransport();
        client = new McpClient("interop-client", "1.0", transport, false);
        client.connect().get(5, TimeUnit.SECONDS);

        Assert.assertEquals(McpProtocolProfile.MODERN_2026_07_28, transport.getProtocolProfile());
        Assert.assertNotNull(server.takeRequest(5, TimeUnit.SECONDS));
        Assert.assertNull("SSE discovery must not trigger initialize", server.takeRequest(250, TimeUnit.MILLISECONDS));
    }

    @Test
    public void autoFallsBackToLegacyInitializeOnlyAfterUnrecognizedCompatibilityResponse() throws Exception {
        server = new MockWebServer();
        server.enqueue(new MockResponse().setResponseCode(400));
        server.enqueue(json(200, "{\"jsonrpc\":\"2.0\",\"id\":1,\"result\":{"
                + "\"protocolVersion\":\"2025-11-25\",\"capabilities\":{},"
                + "\"serverInfo\":{\"name\":\"legacy\",\"version\":\"1.0\"}}}"));
        server.enqueue(new MockResponse().setResponseCode(202));
        server.start();

        StreamableHttpTransport transport = autoTransport();
        client = new McpClient("interop-client", "1.0", transport, false);
        client.connect().get(5, TimeUnit.SECONDS);

        Assert.assertEquals(McpProtocolProfile.LEGACY_2025_11_25, transport.getProtocolProfile());
        RecordedRequest discovery = server.takeRequest(5, TimeUnit.SECONDS);
        RecordedRequest initialize = server.takeRequest(5, TimeUnit.SECONDS);
        RecordedRequest initialized = server.takeRequest(5, TimeUnit.SECONDS);
        Assert.assertEquals("server/discover", discovery.getHeader("Mcp-Method"));
        Assert.assertNull("legacy initialize does not carry modern headers", initialize.getHeader("Mcp-Method"));
        String initializeBody = initialize.getBody().readUtf8();
        Assert.assertTrue(initializeBody.contains("\"method\":\"initialize\""));
        Assert.assertTrue(initializeBody.contains("2025-11-25"));
        Assert.assertTrue(initialized.getBody().readUtf8().contains("notifications/initialized"));
    }

    @Test
    public void autoFallsBackWhenLegacyServerDoesNotImplementDiscover() throws Exception {
        server = new MockWebServer();
        server.enqueue(json(200, "{\"jsonrpc\":\"2.0\",\"id\":0,\"error\":{"
                + "\"code\":-32601,\"message\":\"Method not found: server/discover\"}}"));
        server.enqueue(json(200, "{\"jsonrpc\":\"2.0\",\"id\":1,\"result\":{"
                + "\"protocolVersion\":\"2025-11-25\",\"capabilities\":{},"
                + "\"serverInfo\":{\"name\":\"legacy\",\"version\":\"1.0\"}}}"));
        server.enqueue(new MockResponse().setResponseCode(202));
        server.start();

        StreamableHttpTransport transport = autoTransport();
        client = new McpClient("interop-client", "1.0", transport, false);
        client.connect().get(5, TimeUnit.SECONDS);

        Assert.assertEquals(McpProtocolProfile.LEGACY_2025_11_25, transport.getProtocolProfile());
        Assert.assertEquals("server/discover", server.takeRequest(5, TimeUnit.SECONDS).getHeader("Mcp-Method"));
        RecordedRequest initialize = server.takeRequest(5, TimeUnit.SECONDS);
        Assert.assertTrue(initialize.getBody().readUtf8().contains("\"method\":\"initialize\""));
        Assert.assertNotNull(server.takeRequest(5, TimeUnit.SECONDS));
    }

    @Test
    public void legacyClientUsesProtocolVersionSelectedByServer() throws Exception {
        server = new MockWebServer();
        server.enqueue(new MockResponse().setResponseCode(400));
        server.enqueue(json(200, "{\"jsonrpc\":\"2.0\",\"id\":1,\"result\":{"
                + "\"protocolVersion\":\"2025-06-18\",\"capabilities\":{},"
                + "\"serverInfo\":{\"name\":\"legacy\",\"version\":\"1.0\"}}}"));
        server.enqueue(new MockResponse().setResponseCode(202));
        server.enqueue(json(200, "{\"jsonrpc\":\"2.0\",\"id\":2,\"result\":{\"tools\":[]}}"));
        server.start();

        StreamableHttpTransport transport = autoTransport();
        client = new McpClient("interop-client", "1.0", transport, false);
        client.connect().get(5, TimeUnit.SECONDS);
        client.getAvailableTools().get(5, TimeUnit.SECONDS);

        Assert.assertEquals(McpProtocolProfile.LEGACY_2025_06_18, transport.getProtocolProfile());
        server.takeRequest(5, TimeUnit.SECONDS);
        RecordedRequest initialize = server.takeRequest(5, TimeUnit.SECONDS);
        RecordedRequest initialized = server.takeRequest(5, TimeUnit.SECONDS);
        RecordedRequest toolsList = server.takeRequest(5, TimeUnit.SECONDS);
        Assert.assertNull(initialize.getHeader("MCP-Protocol-Version"));
        Assert.assertEquals("2025-06-18", initialized.getHeader("MCP-Protocol-Version"));
        Assert.assertEquals("2025-06-18", toolsList.getHeader("MCP-Protocol-Version"));
    }

    @Test
    public void autoDoesNotDowngradeAuthenticationFailure() throws Exception {
        server = new MockWebServer();
        server.enqueue(new MockResponse().setResponseCode(401).setBody("unauthorized"));
        server.start();

        client = new McpClient("interop-client", "1.0", autoTransport(), false);
        try {
            client.connect().get(5, TimeUnit.SECONDS);
            Assert.fail("expected discovery authentication failure");
        } catch (Exception expected) {
            Assert.assertTrue(String.valueOf(expected.getMessage()).contains("401"));
        }
        Assert.assertNotNull(server.takeRequest(5, TimeUnit.SECONDS));
        Assert.assertNull("authentication failure must not trigger initialize", server.takeRequest(250, TimeUnit.MILLISECONDS));
    }

    @Test
    public void autoDoesNotDowngradeRecognizedModernValidationFailure() throws Exception {
        server = new MockWebServer();
        server.enqueue(json(400, "{\"jsonrpc\":\"2.0\",\"id\":0,\"error\":{"
                + "\"code\":-32020,\"message\":\"Header mismatch\"}}"));
        server.start();

        client = new McpClient("interop-client", "1.0", autoTransport(), false);
        try {
            client.connect().get(5, TimeUnit.SECONDS);
            Assert.fail("expected modern validation failure");
        } catch (Exception expected) {
            Assert.assertTrue(String.valueOf(expected.getMessage()).contains("400"));
        }
        Assert.assertNotNull(server.takeRequest(5, TimeUnit.SECONDS));
        Assert.assertNull("recognized modern error must not trigger initialize", server.takeRequest(250, TimeUnit.MILLISECONDS));
    }

    @Test
    public void autoDoesNotDowngradeMissingRequiredClientCapabilityFailure() throws Exception {
        server = new MockWebServer();
        server.enqueue(json(400, "{\"jsonrpc\":\"2.0\",\"id\":0,\"error\":{"
                + "\"code\":-32021,\"message\":\"Missing required client capability\"}}"));
        server.start();

        client = new McpClient("interop-client", "1.0", autoTransport(), false);
        try {
            client.connect().get(5, TimeUnit.SECONDS);
            Assert.fail("expected modern capability failure");
        } catch (Exception expected) {
            Assert.assertTrue(String.valueOf(expected.getMessage()).contains("400"));
        }
        Assert.assertNotNull(server.takeRequest(5, TimeUnit.SECONDS));
        Assert.assertNull("modern capability failure must not trigger initialize", server.takeRequest(250, TimeUnit.MILLISECONDS));
    }

    @Test
    public void autoDoesNotTreatNotFoundAsLegacyStreamableHttp() throws Exception {
        assertNoLegacyFallbackForStatus(404);
    }

    @Test
    public void autoDoesNotTreatMethodNotAllowedAsLegacyStreamableHttp() throws Exception {
        assertNoLegacyFallbackForStatus(405);
    }

    @Test
    public void autoDoesNotDowngradeSuccessfulJsonRpcError() throws Exception {
        server = new MockWebServer();
        server.enqueue(json(200, "{\"jsonrpc\":\"2.0\",\"id\":0,\"error\":{"
                + "\"code\":-32602,\"message\":\"Invalid params\"}}"));
        server.start();

        client = new McpClient("interop-client", "1.0", autoTransport(), false);
        try {
            client.connect().get(5, TimeUnit.SECONDS);
            Assert.fail("expected discovery JSON-RPC failure");
        } catch (Exception expected) {
            Assert.assertTrue(String.valueOf(expected.getMessage()).contains("JSON-RPC error"));
        }
        Assert.assertNotNull(server.takeRequest(5, TimeUnit.SECONDS));
        Assert.assertNull("a JSON-RPC error must not trigger initialize", server.takeRequest(250, TimeUnit.MILLISECONDS));
    }

    private StreamableHttpTransport autoTransport() {
        TransportConfig config = TransportConfig.streamableHttp(server.url("/mcp").toString())
                .withProtocolProfile(McpProtocolProfile.AUTO);
        return new StreamableHttpTransport(config);
    }

    private void assertNoLegacyFallbackForStatus(int status) throws Exception {
        server = new MockWebServer();
        server.enqueue(new MockResponse().setResponseCode(status));
        server.start();

        client = new McpClient("interop-client", "1.0", autoTransport(), false);
        try {
            client.connect().get(5, TimeUnit.SECONDS);
            Assert.fail("expected discovery failure");
        } catch (Exception expected) {
            Assert.assertTrue(String.valueOf(expected.getMessage()).contains(String.valueOf(status)));
        }
        Assert.assertNotNull(server.takeRequest(5, TimeUnit.SECONDS));
        Assert.assertNull("endpoint mismatch must not trigger initialize", server.takeRequest(250, TimeUnit.MILLISECONDS));
    }

    private static MockResponse json(int code, String body) {
        return new MockResponse().setResponseCode(code)
                .setHeader("Content-Type", "application/json")
                .setBody(body);
    }
}
