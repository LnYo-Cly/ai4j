package io.github.lnyocly.ai4j.mcp.server;

import com.alibaba.fastjson2.JSON;
import io.github.lnyocly.ai4j.mcp.client.McpClient;
import io.github.lnyocly.ai4j.mcp.entity.McpMessage;
import io.github.lnyocly.ai4j.mcp.entity.McpResponse;
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
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
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
    public void modernServerRejectsLegacyEndpointsAndValidatesNotifications() throws Exception {
        int port = findFreePort();
        StreamableHttpMcpServer server = modernServer(port, null);
        try {
            server.start().get();
            Assert.assertEquals(405, method(port, "GET"));
            Assert.assertEquals(405, method(port, "DELETE"));

            Map<String, Object> metadata = new HashMap<String, Object>();
            metadata.put("io.modelcontextprotocol/protocolVersion", "2026-07-28");
            metadata.put("io.modelcontextprotocol/clientCapabilities", new HashMap<String, Object>());
            Map<String, Object> params = new HashMap<String, Object>();
            params.put("_meta", metadata);
            Map<String, Object> notification = new HashMap<String, Object>();
            notification.put("jsonrpc", "2.0");
            notification.put("method", "notifications/example");
            notification.put("params", params);
            String body = JSON.toJSONString(notification);

            RawResponse missingHeaders = rawPost(port, body, null);
            assertHeaderMismatch(missingHeaders);

            RawResponse mismatch = rawPost(port, body, new String[] {
                    "MCP-Protocol-Version", "2026-07-28",
                    "Mcp-Method", "notifications/other"
            });
            assertHeaderMismatch(mismatch);

            RawResponse duplicate = rawSocketPost(port, body,
                    "MCP-Protocol-Version: 2026-07-28",
                    "Mcp-Method: notifications/example",
                    "Mcp-Method: notifications/example");
            assertHeaderMismatch(duplicate);

            RawResponse response = rawPost(port, body, new String[] {
                    "MCP-Protocol-Version", "2026-07-28",
                    "Mcp-Method", "notifications/example"
            });
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
    public void modernServerRejectsDuplicateMetadataHeaders() throws Exception {
        int port = findFreePort();
        StreamableHttpMcpServer server = modernServer(port, null, new SchemaModernEngine());
        try {
            server.start().get();

            assertHeaderMismatch(rawSocketPost(port,
                    modernRequest("server/discover", 1, new HashMap<String, Object>()),
                    "MCP-Protocol-Version: 2026-07-28",
                    "mcp-protocol-version: 2026-07-28",
                    "Mcp-Method: server/discover"));
            assertHeaderMismatch(rawSocketPost(port,
                    modernRequest("server/discover", 2, new HashMap<String, Object>()),
                    "MCP-Protocol-Version: 2026-07-28",
                    "Mcp-Method: server/discover",
                    "Mcp-Method: tools/list"));

            Map<String, Object> nameParams = new HashMap<String, Object>();
            nameParams.put("name", "test-tool");
            nameParams.put("arguments", new HashMap<String, Object>());
            assertHeaderMismatch(rawSocketPost(port, modernRequest("tools/call", 3, nameParams),
                    "MCP-Protocol-Version: 2026-07-28",
                    "Mcp-Method: tools/call",
                    "Mcp-Name: test-tool",
                    "Mcp-Name: attacker-selected"));

            Map<String, Object> arguments = new HashMap<String, Object>();
            arguments.put("region", "us-west1");
            Map<String, Object> parameterParams = new HashMap<String, Object>();
            parameterParams.put("name", "test-tool");
            parameterParams.put("arguments", arguments);
            assertHeaderMismatch(rawSocketPost(port, modernRequest("tools/call", 4, parameterParams),
                    "MCP-Protocol-Version: 2026-07-28",
                    "Mcp-Method: tools/call",
                    "Mcp-Name: test-tool",
                    "Mcp-Param-Region: us-west1",
                    "mcp-param-region: us-west1"));
        } finally {
            server.stop().get();
        }
    }

    @Test
    public void modernSseDisconnectCancelsInFlightRequest() throws Exception {
        int port = findFreePort();
        BlockingModernEngine engine = new BlockingModernEngine();
        StreamableHttpMcpServer server = modernServer(port, null, engine);
        Socket socket = null;
        try {
            server.start().get();
            socket = new Socket("127.0.0.1", port);
            socket.setSoTimeout(5000);
            writeRawPost(socket, modernRequest("server/discover", 1, new HashMap<String, Object>()),
                    "Accept: text/event-stream",
                    "MCP-Protocol-Version: 2026-07-28",
                    "Mcp-Method: server/discover");

            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            Assert.assertEquals("HTTP/1.1 200 OK", reader.readLine());
            String line;
            while ((line = reader.readLine()) != null && !line.isEmpty()) {
                // Consume response headers before asserting the actual SSE stream is open.
            }
            String firstSseLine = reader.readLine();
            if (firstSseLine.matches("[0-9a-fA-F]+")) {
                firstSseLine = reader.readLine();
            }
            Assert.assertEquals(":", firstSseLine);
            Assert.assertEquals("", reader.readLine());
            Assert.assertTrue("request worker did not start", engine.started.await(5, TimeUnit.SECONDS));

            socket.setSoLinger(true, 0);
            socket.close();
            socket = null;
            Assert.assertTrue("SSE disconnect did not interrupt in-flight work",
                    engine.cancelled.await(5, TimeUnit.SECONDS));
        } finally {
            if (socket != null) {
                socket.close();
            }
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

    @Test
    public void serverFactoryDefaultsToModernStatelessProfile() {
        McpServerFactory.ServerConfig config = new McpServerFactory.ServerConfig("modern", "1.0");

        Assert.assertEquals(McpProtocolProfile.MODERN_2026_07_28, config.getProtocolProfile());
    }

    @Test
    public void automaticServerServesModernAndInitializationEraClientsOnOneEndpoint() throws Exception {
        int port = findFreePort();
        StreamableHttpMcpServer server = new StreamableHttpMcpServer(
                "dual", "1.0", "127.0.0.1", port, null, null, McpProtocolProfile.AUTO);
        try {
            server.start().get();
            Map<String, Object> legacyParams = new HashMap<String, Object>();
            legacyParams.put("protocolVersion", "2025-11-25");
            legacyParams.put("capabilities", new HashMap<String, Object>());
            legacyParams.put("clientInfo", new HashMap<String, Object>());
            Map<String, Object> legacyRequest = new HashMap<String, Object>();
            legacyRequest.put("jsonrpc", "2.0");
            legacyRequest.put("id", 1);
            legacyRequest.put("method", "initialize");
            legacyRequest.put("params", legacyParams);
            RawResponse legacy = rawPost(port, JSON.toJSONString(legacyRequest), null);
            Assert.assertEquals(200, legacy.status);
            Assert.assertNotNull(legacy.sessionId);
            Assert.assertTrue(legacy.body.contains("2025-11-25"));

            legacyParams.put("protocolVersion", "2025-06-18");
            legacyRequest.put("id", 3);
            RawResponse legacyJune = rawPost(port, JSON.toJSONString(legacyRequest), null);
            Assert.assertEquals(200, legacyJune.status);
            Assert.assertNotNull(legacyJune.sessionId);
            Assert.assertTrue(legacyJune.body.contains("2025-06-18"));

            Map<String, Object> initialized = new HashMap<String, Object>();
            initialized.put("jsonrpc", "2.0");
            initialized.put("method", "notifications/initialized");
            initialized.put("params", new HashMap<String, Object>());
            RawResponse initializedResponse = rawPost(port, JSON.toJSONString(initialized), new String[] {
                    "MCP-Protocol-Version", "2025-06-18",
                    "mcp-session-id", legacyJune.sessionId
            });
            Assert.assertEquals(202, initializedResponse.status);

            Map<String, Object> toolsRequest = new HashMap<String, Object>();
            toolsRequest.put("jsonrpc", "2.0");
            toolsRequest.put("id", 5);
            toolsRequest.put("method", "tools/list");
            toolsRequest.put("params", new HashMap<String, Object>());
            RawResponse legacyTools = rawPost(port, JSON.toJSONString(toolsRequest), new String[] {
                    "MCP-Protocol-Version", "2025-06-18",
                    "mcp-session-id", legacyJune.sessionId
            });
            Assert.assertEquals(200, legacyTools.status);

            legacyParams.put("protocolVersion", "2024-11-05");
            legacyRequest.put("id", 6);
            RawResponse legacyNovember = rawPost(port, JSON.toJSONString(legacyRequest), null);
            Assert.assertEquals(200, legacyNovember.status);
            Assert.assertTrue(legacyNovember.body.contains("2024-11-05"));

            RawResponse modern = rawPost(port, modernRequest("server/discover", 2, new HashMap<String, Object>()),
                    new String[] {
                            "MCP-Protocol-Version", "2026-07-28",
                            "Mcp-Method", "server/discover",
                            "Accept", "application/json"
                    });
            Assert.assertEquals(200, modern.status);
            Map<String, Object> root = JSON.parseObject(modern.body, Map.class);
            Map<String, Object> result = (Map<String, Object>) root.get("result");
            Assert.assertTrue(String.valueOf(result.get("supportedVersions")).contains("2026-07-28"));
            Assert.assertNull("server identity belongs in result _meta, not the discover body", result.get("serverInfo"));
            Assert.assertTrue(String.valueOf(result.get("_meta")).contains("io.modelcontextprotocol/serverInfo"));

            RawResponse tools = post(port, "tools/list", 4, new HashMap<String, Object>(), null, null);
            Assert.assertEquals(200, tools.status);
            Assert.assertTrue(tools.body.contains("\"resultType\":\"complete\""));
        } finally {
            server.stop().get();
        }
    }

    @Test
    public void automaticServerAllowsOnlyDeclaredMcpParameterHeadersInCorsPreflight() throws Exception {
        int port = findFreePort();
        StreamableHttpMcpServer server = new StreamableHttpMcpServer(
                "dual", "1.0", "127.0.0.1", port, null, "https://allowed.example", McpProtocolProfile.AUTO);
        try {
            server.start().get();
            Request request = new Request.Builder()
                    .url("http://127.0.0.1:" + port + "/mcp")
                    .method("OPTIONS", null)
                    .header("Origin", "https://allowed.example")
                    .header("Access-Control-Request-Headers", "Mcp-Param-Region, X-Not-Allowed")
                    .build();
            Response response = new OkHttpClient().newCall(request).execute();
            try {
                Assert.assertEquals(204, response.code());
                String allowed = response.header("Access-Control-Allow-Headers");
                Assert.assertTrue(allowed.contains("Mcp-Param-Region"));
                Assert.assertFalse(allowed.contains("X-Not-Allowed"));
            } finally {
                response.close();
            }
        } finally {
            server.stop().get();
        }
    }

    private static StreamableHttpMcpServer modernServer(int port, String origin) {
        return new StreamableHttpMcpServer("modern", "1.0", "127.0.0.1", port, null, origin,
                McpProtocolProfile.MODERN_2026_07_28);
    }

    private static StreamableHttpMcpServer modernServer(int port, String origin, McpServerEngine engine) {
        return new StreamableHttpMcpServer("modern", "1.0", "127.0.0.1", port, null, origin,
                McpProtocolProfile.MODERN_2026_07_28, engine);
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

    private static RawResponse rawSocketPost(int port, String body, String... headers) throws IOException {
        Socket socket = new Socket("127.0.0.1", port);
        socket.setSoTimeout(5000);
        try {
            writeRawPost(socket, body, prepend("Connection: close", headers));
            String rawResponse = readText(socket.getInputStream());
            int statusLineEnd = rawResponse.indexOf("\r\n");
            int bodyStart = rawResponse.indexOf("\r\n\r\n");
            int status = Integer.parseInt(rawResponse.substring(0, statusLineEnd).split(" ")[1]);
            String responseBody = bodyStart < 0 ? "" : rawResponse.substring(bodyStart + 4);
            return new RawResponse(status, responseBody, null);
        } finally {
            socket.close();
        }
    }

    private static void writeRawPost(Socket socket, String body, String... headers) throws IOException {
        StringBuilder request = new StringBuilder();
        request.append("POST /mcp HTTP/1.1\r\n");
        request.append("Host: 127.0.0.1\r\n");
        request.append("Content-Type: application/json\r\n");
        for (String header : headers) {
            request.append(header).append("\r\n");
        }
        request.append("Content-Length: ").append(body.getBytes(StandardCharsets.UTF_8).length).append("\r\n\r\n");
        request.append(body);
        socket.getOutputStream().write(request.toString().getBytes(StandardCharsets.UTF_8));
        socket.getOutputStream().flush();
    }

    private static String[] prepend(String first, String[] rest) {
        String[] values = new String[rest.length + 1];
        values[0] = first;
        System.arraycopy(rest, 0, values, 1, rest.length);
        return values;
    }

    private static String modernRequest(String method, int id, Map<String, Object> params) {
        Map<String, Object> requestParams = new HashMap<String, Object>(params);
        Map<String, Object> metadata = new HashMap<String, Object>();
        metadata.put("io.modelcontextprotocol/protocolVersion", "2026-07-28");
        metadata.put("io.modelcontextprotocol/clientCapabilities", new HashMap<String, Object>());
        requestParams.put("_meta", metadata);
        Map<String, Object> request = new HashMap<String, Object>();
        request.put("jsonrpc", "2.0");
        request.put("id", id);
        request.put("method", method);
        request.put("params", requestParams);
        return JSON.toJSONString(request);
    }

    private static void assertHeaderMismatch(RawResponse response) {
        Assert.assertEquals(400, response.status);
        Assert.assertTrue(response.body.contains("-32020"));
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

    private static final class SchemaModernEngine extends McpServerEngine {
        private final Map<String, Object> inputSchema;

        private SchemaModernEngine() {
            super("test", "1.0", Arrays.asList("2026-07-28"), "2026-07-28", false, false, false);
            Map<String, Object> property = new HashMap<String, Object>();
            property.put("type", "string");
            property.put("x-mcp-header", "Region");
            Map<String, Object> properties = new HashMap<String, Object>();
            properties.put("region", property);
            inputSchema = new HashMap<String, Object>();
            inputSchema.put("type", "object");
            inputSchema.put("properties", properties);
        }

        @Override
        public McpMessage processModernMessage(McpMessage message) {
            return success(message.getId());
        }

        @Override
        public Map<String, Object> getToolInputSchema(String toolName) {
            return "test-tool".equals(toolName) ? inputSchema : null;
        }
    }

    private static final class BlockingModernEngine extends McpServerEngine {
        private final CountDownLatch started = new CountDownLatch(1);
        private final CountDownLatch cancelled = new CountDownLatch(1);

        private BlockingModernEngine() {
            super("test", "1.0", Arrays.asList("2026-07-28"), "2026-07-28", false, false, false);
        }

        @Override
        public McpMessage processModernMessage(McpMessage message) {
            started.countDown();
            try {
                new CountDownLatch(1).await();
            } catch (InterruptedException e) {
                cancelled.countDown();
                Thread.currentThread().interrupt();
            }
            return success(message.getId());
        }
    }

    private static McpMessage success(Object id) {
        McpResponse response = new McpResponse();
        response.setId(id);
        response.setResult(new HashMap<String, Object>());
        return response;
    }

}
