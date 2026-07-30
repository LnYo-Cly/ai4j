package io.github.lnyocly.ai4j.mcp.server;

import com.alibaba.fastjson2.JSON;
import io.github.lnyocly.ai4j.mcp.client.McpClient;
import io.github.lnyocly.ai4j.mcp.entity.McpToolDefinition;
import io.github.lnyocly.ai4j.mcp.transport.McpProtocolProfile;
import io.github.lnyocly.ai4j.mcp.transport.StreamableHttpTransport;
import io.github.lnyocly.ai4j.mcp.transport.TransportConfig;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.junit.Assert;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.ServerSocket;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class StreamableHttpModernProtocolTest {

    @Test
    public void modernServerServesStatelessRequestAndDiscover() throws Exception {
        int port = findFreePort();
        StreamableHttpMcpServer server = modernServer(port, null);
        try {
            server.start().get();
            RawResponse discover = post(port, "server/discover", 1, new HashMap<String, Object>(), null, null);
            Assert.assertEquals(200, discover.status);
            Assert.assertTrue(discover.body.contains("2026-07-28"));
            Assert.assertTrue(discover.body.contains("\"supportedVersions\""));
            Assert.assertTrue(discover.body.contains("resultType"));
            Assert.assertTrue(discover.body.contains("\"ttlMs\":3600000"));
            Assert.assertTrue(discover.body.contains("\"cacheScope\":\"public\""));
            Assert.assertFalse(discover.body.contains("listChanged"));
            Assert.assertNull(discover.sessionId);

            RawResponse tools = post(port, "tools/list", 2, new HashMap<String, Object>(), null, null);
            Assert.assertEquals(200, tools.status);
            Assert.assertTrue(tools.body.contains("\"resultType\":\"complete\""));
            Assert.assertTrue(tools.body.contains("\"ttlMs\":30000"));
            Assert.assertTrue(tools.body.contains("\"cacheScope\":\"private\""));
            Assert.assertNull(tools.sessionId);
        } finally {
            server.stop().get();
        }
    }

    @Test
    public void modernServerRejectsBadOriginAndHeaderMismatch() throws Exception {
        int port = findFreePort();
        StreamableHttpMcpServer server = modernServer(port, "https://allowed.example");
        try {
            server.start().get();
            RawResponse origin = post(port, "server/discover", 1, new HashMap<String, Object>(),
                    "https://forbidden.example", null);
            Assert.assertEquals(403, origin.status);

            RawResponse preflight = options(port, "/mcp", "https://forbidden.example");
            Assert.assertEquals(403, preflight.status);
            Assert.assertEquals(403, options(port, "/health", "https://forbidden.example").status);
            Assert.assertEquals(403, options(port, "/", "https://forbidden.example").status);

            RawResponse mismatch = post(port, "server/discover", 2, new HashMap<String, Object>(),
                    null, "tools/list");
            Assert.assertEquals(400, mismatch.status);
            Assert.assertTrue(mismatch.body.contains("-32020"));
        } finally {
            server.stop().get();
        }
    }

    @Test
    public void modernServerReturnsParseErrorForMalformedJson() throws Exception {
        int port = findFreePort();
        StreamableHttpMcpServer server = modernServer(port, null);
        try {
            server.start().get();
            RawResponse response = rawPost(port, "{", null);
            Assert.assertEquals(400, response.status);
            Assert.assertTrue(response.body.contains("-32700"));
        } finally {
            server.stop().get();
        }
    }

    @Test
    public void modernServerReturnsNotFoundForUnknownRpcMethod() throws Exception {
        int port = findFreePort();
        StreamableHttpMcpServer server = modernServer(port, null);
        try {
            server.start().get();
            RawResponse response = post(port, "unknown/method", 1, new HashMap<String, Object>(), null, null);
            Assert.assertEquals(404, response.status);
            Assert.assertTrue(response.body.contains("-32601"));
        } finally {
            server.stop().get();
        }
    }

    @Test
    public void modernServerRejectsLegacyEndpointsAndAcceptsNotification() throws Exception {
        int port = findFreePort();
        StreamableHttpMcpServer server = modernServer(port, null);
        try {
            server.start().get();
            Assert.assertEquals(405, method(port, "GET"));
            Assert.assertEquals(405, method(port, "DELETE"));

            Map<String, Object> notification = new HashMap<String, Object>();
            notification.put("jsonrpc", "2.0");
            notification.put("method", "notifications/example");
            RawResponse response = rawPost(port, JSON.toJSONString(notification), null);
            Assert.assertEquals(202, response.status);
            Assert.assertEquals("", response.body);
        } finally {
            server.stop().get();
        }
    }

    @Test
    public void modernClientAndServerInteroperateWithoutSessionHandshake() throws Exception {
        int port = findFreePort();
        StreamableHttpMcpServer server = modernServer(port, null);
        McpClient client = null;
        try {
            server.start().get();
            TransportConfig config = TransportConfig.streamableHttp("http://127.0.0.1:" + port + "/mcp")
                    .withProtocolProfile(McpProtocolProfile.MODERN_2026_07_28);
            client = new McpClient("modern-client", "1.0", new StreamableHttpTransport(config), false);
            client.connect().get(5, TimeUnit.SECONDS);

            List<McpToolDefinition> tools = client.getAvailableTools().get(45, TimeUnit.SECONDS);
            Assert.assertTrue(client.isInitialized());
            Assert.assertNotNull(tools);
        } finally {
            if (client != null) {
                client.disconnect().get(5, TimeUnit.SECONDS);
            }
            server.stop().get();
        }
    }

    @Test
    public void modernServerRejectsRequestWithoutRequiredClientCapabilities() throws Exception {
        int port = findFreePort();
        StreamableHttpMcpServer server = modernServer(port, null);
        try {
            server.start().get();
            Map<String, Object> metadata = new HashMap<String, Object>();
            metadata.put("io.modelcontextprotocol/protocolVersion", "2026-07-28");
            Map<String, Object> params = new HashMap<String, Object>();
            params.put("_meta", metadata);
            Map<String, Object> request = new HashMap<String, Object>();
            request.put("jsonrpc", "2.0");
            request.put("id", 1);
            request.put("method", "server/discover");
            request.put("params", params);

            RawResponse response = rawPost(port, JSON.toJSONString(request), new String[] {
                    "MCP-Protocol-Version", "2026-07-28",
                    "Mcp-Method", "server/discover"
            });
            Assert.assertEquals(400, response.status);
            Assert.assertTrue(response.body.contains("-32602"));
        } finally {
            server.stop().get();
        }
    }

    @Test
    public void explicitLegacyProfileStillMintsSessionForInitialize() throws Exception {
        int port = findFreePort();
        StreamableHttpMcpServer server = new StreamableHttpMcpServer(
                "legacy", "1.0", "127.0.0.1", port, null, null,
                McpProtocolProfile.LEGACY_2025_03_26);
        try {
            server.start().get();
            Map<String, Object> params = new HashMap<String, Object>();
            params.put("protocolVersion", "2025-03-26");
            params.put("capabilities", new HashMap<String, Object>());
            params.put("clientInfo", new HashMap<String, Object>());
            Map<String, Object> request = new HashMap<String, Object>();
            request.put("jsonrpc", "2.0");
            request.put("id", 1);
            request.put("method", "initialize");
            request.put("params", params);
            RawResponse response = rawPost(port, JSON.toJSONString(request), null);
            Assert.assertEquals(200, response.status);
            Assert.assertNotNull(response.sessionId);
        } finally {
            server.stop().get();
        }
    }

    private static StreamableHttpMcpServer modernServer(int port, String origin) {
        return new StreamableHttpMcpServer("modern", "1.0", "127.0.0.1", port, null, origin,
                McpProtocolProfile.MODERN_2026_07_28);
    }

    private static RawResponse post(int port, String method, int id, Map<String, Object> params,
                                 String origin, String methodHeader) throws IOException {
        Map<String, Object> metadata = new HashMap<String, Object>();
        metadata.put("io.modelcontextprotocol/protocolVersion", "2026-07-28");
        metadata.put("io.modelcontextprotocol/clientCapabilities", new HashMap<String, Object>());
        params.put("_meta", metadata);
        Map<String, Object> request = new HashMap<String, Object>();
        request.put("jsonrpc", "2.0");
        request.put("id", id);
        request.put("method", method);
        request.put("params", params);
        return rawPost(port, JSON.toJSONString(request), new String[] {
                "MCP-Protocol-Version", "2026-07-28",
                "Mcp-Method", methodHeader == null ? method : methodHeader,
                "Accept", "application/json, text/event-stream",
                "Origin", origin
        });
    }

    private static RawResponse rawPost(int port, String body, String[] headers) throws IOException {
        Request.Builder builder = new Request.Builder()
                .url("http://127.0.0.1:" + port + "/mcp")
                .post(RequestBody.create(MediaType.get("application/json"), body));
        if (headers != null) {
            for (int i = 0; i < headers.length; i += 2) {
                if (headers[i + 1] != null) {
                    builder.header(headers[i], headers[i + 1]);
                }
            }
        }
        Response response = new OkHttpClient.Builder()
                .readTimeout(30, TimeUnit.SECONDS)
                .build()
                .newCall(builder.build())
                .execute();
        try {
            return new RawResponse(response.code(), response.body() == null ? "" : response.body().string(),
                    response.header("mcp-session-id"));
        } finally {
            response.close();
        }
    }

    private static RawResponse options(int port, String path, String origin) throws IOException {
        Request.Builder builder = new Request.Builder()
                .url("http://127.0.0.1:" + port + path)
                .method("OPTIONS", null);
        if (origin != null) {
            builder.header("Origin", origin);
        }
        Response response = new OkHttpClient.Builder()
                .readTimeout(30, TimeUnit.SECONDS)
                .build()
                .newCall(builder.build())
                .execute();
        try {
            return new RawResponse(response.code(), response.body() == null ? "" : response.body().string(),
                    response.header("mcp-session-id"));
        } finally {
            response.close();
        }
    }

    private static int method(int port, String method) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL("http://127.0.0.1:" + port + "/mcp").openConnection();
        connection.setRequestMethod(method);
        return read(connection).status;
    }

    private static RawResponse read(HttpURLConnection connection) throws IOException {
        try {
            int status = connection.getResponseCode();
            InputStream stream = status >= 400 ? connection.getErrorStream() : connection.getInputStream();
            return new RawResponse(status, stream == null ? "" : readText(stream), connection.getHeaderField("mcp-session-id"));
        } finally {
            connection.disconnect();
        }
    }

    private static String readText(InputStream input) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[256];
        int read;
        while ((read = input.read(buffer)) >= 0) {
            output.write(buffer, 0, read);
        }
        input.close();
        return new String(output.toByteArray(), StandardCharsets.UTF_8);
    }

    private static int findFreePort() throws IOException {
        ServerSocket socket = new ServerSocket(0);
        try {
            return socket.getLocalPort();
        } finally {
            socket.close();
        }
    }

    private static class RawResponse {
        private final int status;
        private final String body;
        private final String sessionId;

        private RawResponse(int status, String body, String sessionId) {
            this.status = status;
            this.body = body;
            this.sessionId = sessionId;
        }
    }

}
