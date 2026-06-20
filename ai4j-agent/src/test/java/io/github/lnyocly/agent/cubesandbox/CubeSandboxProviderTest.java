package io.github.lnyocly.agent.cubesandbox;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import io.github.lnyocly.ai4j.agent.sandbox.SandboxCommand;
import io.github.lnyocly.ai4j.agent.sandbox.SandboxException;
import io.github.lnyocly.ai4j.agent.sandbox.SandboxResult;
import io.github.lnyocly.ai4j.agent.sandbox.SandboxSpec;
import io.github.lnyocly.ai4j.agent.sandbox.SandboxStatus;
import io.github.lnyocly.ai4j.agent.sandbox.cubesandbox.CubeSandboxConfig;
import io.github.lnyocly.ai4j.agent.sandbox.cubesandbox.CubeSandboxProvider;
import io.github.lnyocly.ai4j.agent.sandbox.cubesandbox.CubeSandboxSession;
import org.junit.Assert;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.Executors;

public class CubeSandboxProviderTest {

    @Test
    public void providerShouldCreateExecuteAndDestroyViaHttpProtocol() throws Exception {
        TestCubeServer server = new TestCubeServer();
        server.start();
        try {
            CubeSandboxProvider provider = new CubeSandboxProvider(CubeSandboxConfig.builder()
                    .apiUrl(server.baseUrl())
                    .apiKey("unit-secret")
                    .templateId("tpl-default")
                    .proxyNodeIp("127.0.0.1")
                    .proxyPortHttp(server.port())
                    .sandboxDomain("cube.test")
                    .requestTimeoutMillis(5000)
                    .build());

            Map<String, Object> config = new LinkedHashMap<String, Object>();
            Map<String, String> envVars = new LinkedHashMap<String, String>();
            envVars.put("AI4J_TEST", "1");
            config.put("envVars", envVars);
            config.put("allowInternetAccess", Boolean.FALSE);
            config.put("apiKey", "must-not-persist");

            CubeSandboxSession session = provider.createSession(SandboxSpec.builder()
                    .providerId("cubesandbox")
                    .image("tpl-java")
                    .workspaceId("/workspace")
                    .label("task", "unit")
                    .label("apiKey", "must-not-persist")
                    .config(config)
                    .build());

            Assert.assertEquals("cubesandbox", session.getProviderId());
            Assert.assertEquals("sb-unit", session.getSessionId());
            Assert.assertEquals(SandboxStatus.RUNNING, session.getStatus());
            Assert.assertEquals("tpl-java", server.createPayload.getString("templateID"));
            Assert.assertEquals(Boolean.FALSE, server.createPayload.getBoolean("allowInternetAccess"));
            Assert.assertEquals("Bearer unit-secret", server.createAuthorization);
            Assert.assertFalse(session.getSpec().getLabels().containsKey("apiKey"));
            Assert.assertFalse(session.getSpec().getLabels().containsKey("cube.apiUrl"));
            Assert.assertFalse(session.getSpec().getLabels().containsKey("cube.proxyNodeIp"));
            Assert.assertFalse(server.createPayload.getJSONObject("metadata").containsKey("apiKey"));
            Assert.assertFalse(server.createPayload.getJSONObject("metadata").containsKey("ai4jWorkspaceId"));

            SandboxResult result = session.execute(SandboxCommand.builder()
                    .commandId("cmd-1")
                    .command("echo ai4j-cubesandbox-ok")
                    .workingDirectory("/workspace")
                    .environment("LANG", "C")
                    .timeoutMillis(2000L)
                    .build());

            Assert.assertEquals(Integer.valueOf(0), result.getExitCode());
            Assert.assertEquals("ai4j-cubesandbox-ok\n", result.getStdout());
            Assert.assertEquals("", result.getStderr());
            Assert.assertEquals("49983-sb-unit.cube.test", server.processHostHeader);
            Assert.assertEquals("Basic cm9vdDo=", server.processAuthorization);
            Assert.assertEquals("1", server.processConnectProtocol);
            Assert.assertEquals("2000", server.processConnectTimeout);
            Assert.assertEquals("envd-token", server.processAccessToken);
            Assert.assertEquals("echo ai4j-cubesandbox-ok", server.processPayload.getJSONObject("process").getJSONArray("args").getString(2));
            Assert.assertEquals("/workspace", server.processPayload.getJSONObject("process").getString("cwd"));
            Assert.assertEquals(1, session.listArtifacts().size());

            session.close();
            Assert.assertEquals(SandboxStatus.CLOSED, session.getStatus());
            Assert.assertTrue(server.deleted);
        } finally {
            server.stop();
        }
    }

    @Test
    public void providerShouldFailFastWhenTemplateIsMissing() throws Exception {
        CubeSandboxProvider provider = new CubeSandboxProvider(CubeSandboxConfig.builder()
                .apiUrl("http://127.0.0.1:1")
                .build());
        try {
            provider.createSession(SandboxSpec.builder().providerId("cubesandbox").build());
            Assert.fail("expected sandbox exception");
        } catch (SandboxException expected) {
            Assert.assertTrue(expected.getMessage().contains("templateID is required"));
        }
    }

    @Test
    public void explicitRequestTimeoutMillisShouldRemainMilliseconds() throws Exception {
        Map<String, Object> config = new LinkedHashMap<String, Object>();
        config.put("requestTimeoutMillis", Integer.valueOf(750));

        CubeSandboxConfig resolved = CubeSandboxConfig.builder().build().withSpec(SandboxSpec.builder()
                .providerId("cubesandbox")
                .config(config)
                .build());

        Assert.assertEquals(750, resolved.getRequestTimeoutMillis());
    }

    @Test
    public void providerShouldUseConfiguredEnvdPortForProcessHost() throws Exception {
        TestCubeServer server = new TestCubeServer();
        server.start();
        try {
            CubeSandboxProvider provider = new CubeSandboxProvider(CubeSandboxConfig.builder()
                    .apiUrl(server.baseUrl())
                    .apiKey("unit-secret")
                    .templateId("tpl-default")
                    .proxyNodeIp("127.0.0.1")
                    .proxyPortHttp(server.port())
                    .sandboxDomain("cube.test")
                    .envdPort(Integer.valueOf(49999))
                    .requestTimeoutMillis(5000)
                    .build());

            CubeSandboxSession session = provider.createSession(SandboxSpec.builder()
                    .providerId("cubesandbox")
                    .image("tpl-java")
                    .build());
            SandboxResult result = session.execute(SandboxCommand.builder()
                    .commandId("cmd-envd-port")
                    .command("echo ai4j-cubesandbox-ok")
                    .timeoutMillis(2000L)
                    .build());

            Assert.assertEquals(Integer.valueOf(0), result.getExitCode());
            Assert.assertEquals("49999-sb-unit.cube.test", server.processHostHeader);
            session.close();
        } finally {
            server.stop();
        }
    }

    @Test
    public void providerShouldConnectExistingWithoutDestroyingOnClose() throws Exception {
        TestCubeServer server = new TestCubeServer();
        server.start();
        try {
            CubeSandboxProvider provider = new CubeSandboxProvider(CubeSandboxConfig.builder()
                    .apiUrl(server.baseUrl())
                    .apiKey("unit-secret")
                    .proxyNodeIp("127.0.0.1")
                    .proxyPortHttp(server.port())
                    .sandboxDomain("cube.test")
                    .requestTimeoutMillis(5000)
                    .build());

            CubeSandboxSession session = provider.connect("sb-unit", SandboxSpec.builder()
                    .providerId("cubesandbox")
                    .workspaceId("/workspace")
                    .build());

            Assert.assertTrue(session.isConnectedExisting());
            Assert.assertEquals("sb-unit", session.getSessionId());
            Assert.assertEquals(1, server.connectCount);

            session.close();
            Assert.assertEquals(SandboxStatus.CLOSED, session.getStatus());
            Assert.assertFalse("closing an attached existing sandbox should not destroy the remote sandbox", server.deleted);
        } finally {
            server.stop();
        }
    }

    @Test
    public void invalidRemoteDomainShouldNotReachRawHostHeader() throws Exception {
        TestCubeServer server = new TestCubeServer();
        server.responseDomain = "cube.test\r\nX-Injected: yes";
        server.start();
        try {
            CubeSandboxSession session = newProvider(server).createSession(SandboxSpec.builder()
                    .providerId("cubesandbox")
                    .image("tpl-java")
                    .build());
            try {
                session.execute(SandboxCommand.builder()
                        .commandId("cmd-invalid-domain")
                        .command("echo ignored")
                        .timeoutMillis(2000L)
                        .build());
                Assert.fail("expected sandbox exception");
            } catch (SandboxException expected) {
                Assert.assertTrue(expected.getMessage(), expected.getMessage().contains("invalid domain"));
            }
            Assert.assertNull("process endpoint should not be reached when virtual host is invalid", server.processHostHeader);
        } finally {
            server.stop();
        }
    }

    @Test
    public void connectEndStreamErrorShouldFailCommand() throws Exception {
        TestCubeServer server = new TestCubeServer();
        server.processMode = "connect-error";
        server.start();
        try {
            CubeSandboxSession session = newProvider(server).createSession(SandboxSpec.builder()
                    .providerId("cubesandbox")
                    .image("tpl-java")
                    .build());
            try {
                session.execute(SandboxCommand.builder()
                        .commandId("cmd-error")
                        .command("echo ignored")
                        .timeoutMillis(2000L)
                        .build());
                Assert.fail("expected sandbox exception");
            } catch (SandboxException expected) {
                Assert.assertTrue(expected.getMessage(), expected.getMessage().contains("permission denied"));
            }
        } finally {
            server.stop();
        }
    }

    @Test
    public void partialConnectEnvelopeShouldFailCommand() throws Exception {
        TestCubeServer server = new TestCubeServer();
        server.processMode = "partial";
        server.start();
        try {
            CubeSandboxSession session = newProvider(server).createSession(SandboxSpec.builder()
                    .providerId("cubesandbox")
                    .image("tpl-java")
                    .build());
            try {
                session.execute(SandboxCommand.builder()
                        .commandId("cmd-partial")
                        .command("echo ignored")
                        .timeoutMillis(2000L)
                        .build());
                Assert.fail("expected sandbox exception");
            } catch (SandboxException expected) {
                Assert.assertTrue(expected.getMessage(), expected.getMessage().contains("process start failed"));
            }
        } finally {
            server.stop();
        }
    }

    private static CubeSandboxProvider newProvider(TestCubeServer server) {
        return new CubeSandboxProvider(CubeSandboxConfig.builder()
                .apiUrl(server.baseUrl())
                .apiKey("unit-secret")
                .templateId("tpl-default")
                .proxyNodeIp("127.0.0.1")
                .proxyPortHttp(server.port())
                .sandboxDomain("cube.test")
                .requestTimeoutMillis(5000)
                .build());
    }

    private static class TestCubeServer {
        private HttpServer server;
        private JSONObject createPayload;
        private JSONObject processPayload;
        private String createAuthorization;
        private String processAuthorization;
        private String processAccessToken;
        private String processHostHeader;
        private String processConnectProtocol;
        private String processConnectTimeout;
        private boolean deleted;
        private int connectCount;
        private String processMode = "ok";
        private String responseDomain = "cube.test";

        void start() throws IOException {
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/health", new JsonHandler(200, "{\"status\":\"ok\"}"));
            server.createContext("/sandboxes", new HttpHandler() {
                @Override
                public void handle(HttpExchange exchange) throws IOException {
                    if ("POST".equals(exchange.getRequestMethod())) {
                        createAuthorization = exchange.getRequestHeaders().getFirst("Authorization");
                        createPayload = JSON.parseObject(read(exchange));
                        writeJson(exchange, 201, "{\"templateID\":\"" + createPayload.getString("templateID") + "\",\"sandboxID\":\"sb-unit\",\"clientID\":\"client-unit\",\"envdVersion\":\"v0\",\"envdAccessToken\":\"envd-token\",\"domain\":" + JSON.toJSONString(responseDomain) + "}");
                        return;
                    }
                    writeJson(exchange, 405, "{\"message\":\"method not allowed\"}");
                }
            });
            server.createContext("/sandboxes/sb-unit", new HttpHandler() {
                @Override
                public void handle(HttpExchange exchange) throws IOException {
                    if ("DELETE".equals(exchange.getRequestMethod())) {
                        deleted = true;
                        writeJson(exchange, 204, "");
                        return;
                    }
                    writeJson(exchange, 200, "{\"templateID\":\"tpl-java\",\"sandboxID\":\"sb-unit\",\"clientID\":\"client-unit\",\"state\":\"running\",\"envdVersion\":\"v0\"}");
                }
            });
            server.createContext("/sandboxes/sb-unit/connect", new HttpHandler() {
                @Override
                public void handle(HttpExchange exchange) throws IOException {
                    if ("POST".equals(exchange.getRequestMethod())) {
                        connectCount++;
                        writeJson(exchange, 200, "{\"templateID\":\"tpl-java\",\"sandboxID\":\"sb-unit\",\"clientID\":\"client-unit\",\"envdVersion\":\"v0\",\"envdAccessToken\":\"envd-token\",\"domain\":" + JSON.toJSONString(responseDomain) + "}");
                        return;
                    }
                    writeJson(exchange, 405, "{\"message\":\"method not allowed\"}");
                }
            });
            server.createContext("/process.Process/Start", new HttpHandler() {
                @Override
                public void handle(HttpExchange exchange) throws IOException {
                    processHostHeader = exchange.getRequestHeaders().getFirst("Host");
                    processAuthorization = exchange.getRequestHeaders().getFirst("Authorization");
                    processAccessToken = exchange.getRequestHeaders().getFirst("X-Access-Token");
                    processConnectProtocol = exchange.getRequestHeaders().getFirst("Connect-Protocol-Version");
                    processConnectTimeout = exchange.getRequestHeaders().getFirst("Connect-Timeout-Ms");
                    processPayload = JSON.parseObject(readConnectEnvelope(exchange.getRequestBody()));
                    exchange.getResponseHeaders().add("Content-Type", "application/connect+json");
                    byte[] body = connectStream(processMode);
                    exchange.sendResponseHeaders(200, body.length);
                    OutputStream os = exchange.getResponseBody();
                    os.write(body);
                    os.close();
                }
            });
            server.setExecutor(Executors.newCachedThreadPool());
            server.start();
        }

        String baseUrl() {
            return "http://127.0.0.1:" + port();
        }

        int port() {
            return server.getAddress().getPort();
        }

        void stop() {
            if (server != null) {
                server.stop(0);
            }
        }

        private static byte[] connectStream(String mode) throws IOException {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            if ("connect-error".equals(mode)) {
                writeEnvelope(out, "{\"error\":{\"code\":\"permission_denied\",\"message\":\"permission denied by envd\"}}", (byte) 0x02);
                return out.toByteArray();
            }
            if ("partial".equals(mode)) {
                DataOutputStream data = new DataOutputStream(out);
                data.writeByte(0);
                data.writeInt(10);
                data.write("abc".getBytes(StandardCharsets.UTF_8));
                return out.toByteArray();
            }
            writeEnvelope(out, "{\"event\":{\"start\":{\"pid\":7}}}");
            String stdout = Base64.getEncoder().encodeToString("ai4j-cubesandbox-ok\n".getBytes(StandardCharsets.UTF_8));
            writeEnvelope(out, "{\"event\":{\"data\":{\"stdout\":\"" + stdout + "\"}}}");
            writeEnvelope(out, "{\"event\":{\"end\":{\"exitCode\":0,\"exited\":true}}}");
            return out.toByteArray();
        }

        private static void writeEnvelope(ByteArrayOutputStream out, String json) throws IOException {
            writeEnvelope(out, json, (byte) 0);
        }

        private static void writeEnvelope(ByteArrayOutputStream out, String json, byte flags) throws IOException {
            byte[] payload = json.getBytes(StandardCharsets.UTF_8);
            DataOutputStream data = new DataOutputStream(out);
            data.writeByte(flags);
            data.writeInt(payload.length);
            data.write(payload);
        }

        private static String readConnectEnvelope(InputStream input) throws IOException {
            int flags = input.read();
            Assert.assertEquals(0, flags);
            byte[] sizeBytes = new byte[4];
            Assert.assertEquals(4, input.read(sizeBytes));
            int size = ((sizeBytes[0] & 0xff) << 24)
                    | ((sizeBytes[1] & 0xff) << 16)
                    | ((sizeBytes[2] & 0xff) << 8)
                    | (sizeBytes[3] & 0xff);
            byte[] payload = new byte[size];
            int offset = 0;
            while (offset < size) {
                int read = input.read(payload, offset, size - offset);
                if (read < 0) {
                    throw new IOException("unexpected EOF");
                }
                offset += read;
            }
            return new String(payload, StandardCharsets.UTF_8);
        }

        private static String read(HttpExchange exchange) throws IOException {
            return read(exchange.getRequestBody());
        }

        private static String read(InputStream input) throws IOException {
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            byte[] chunk = new byte[1024];
            int read;
            while ((read = input.read(chunk)) != -1) {
                buffer.write(chunk, 0, read);
            }
            return new String(buffer.toByteArray(), StandardCharsets.UTF_8);
        }

        private static void writeJson(HttpExchange exchange, int status, String body) throws IOException {
            byte[] raw = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, raw.length);
            OutputStream os = exchange.getResponseBody();
            os.write(raw);
            os.close();
        }
    }

    private static class JsonHandler implements HttpHandler {
        private final int status;
        private final String body;

        private JsonHandler(int status, String body) {
            this.status = status;
            this.body = body;
        }

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            TestCubeServer.writeJson(exchange, status, body);
        }
    }
}
