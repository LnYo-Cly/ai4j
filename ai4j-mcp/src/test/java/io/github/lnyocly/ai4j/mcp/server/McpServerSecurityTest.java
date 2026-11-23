package io.github.lnyocly.ai4j.mcp.server;

import com.sun.net.httpserver.HttpContext;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpPrincipal;
import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URL;
import java.util.Collections;

/**
 * Security-focused tests for the MCP server hardening: default bind address,
 * bearer-token auth, and the ServerConfig.withHost bug fix.
 */
public class McpServerSecurityTest {

    // ---- ServerConfig defaults ----

    @Test
    public void serverConfigShouldDefaultToLoopbackHost() {
        McpServerFactory.ServerConfig config = new McpServerFactory.ServerConfig("test", "1.0.0");
        Assert.assertEquals("default host must be 127.0.0.1, not wildcard or localhost",
                McpServerFactory.ServerConfig.DEFAULT_HOST, config.getHost());
    }

    @Test
    public void serverConfigShouldDefaultAuthEnabled() {
        McpServerFactory.ServerConfig config = new McpServerFactory.ServerConfig("test", "1.0.0");
        Assert.assertTrue("auth must be enabled by default", config.isAuthEnabled());
    }

    @Test
    public void serverConfigWithHostShouldBeHonoured() {
        McpServerFactory.ServerConfig config = new McpServerFactory.ServerConfig("test", "1.0.0")
                .withHost("127.0.0.1");
        Assert.assertEquals("127.0.0.1", config.getHost());
    }

    @Test
    public void serverConfigWithNoAuthShouldDisableAuth() {
        McpServerFactory.ServerConfig config = new McpServerFactory.ServerConfig("test", "1.0.0")
                .withNoAuth();
        Assert.assertFalse(config.isAuthEnabled());
        Assert.assertNull(config.resolveAuthProvider());
    }

    @Test
    public void serverConfigResolveAuthShouldCreateBearerProviderByDefault() {
        McpServerFactory.ServerConfig config = new McpServerFactory.ServerConfig("test", "1.0.0");
        McpAuthProvider provider = config.resolveAuthProvider();
        Assert.assertNotNull("default auth provider must not be null", provider);
        Assert.assertTrue("default provider must be BearerTokenAuthProvider",
                provider instanceof BearerTokenAuthProvider);
    }

    @Test
    public void serverConfigWithAuthShouldUseCustomProvider() {
        BearerTokenAuthProvider custom = new BearerTokenAuthProvider("custom-token");
        McpServerFactory.ServerConfig config = new McpServerFactory.ServerConfig("test", "1.0.0")
                .withAuth(custom);
        Assert.assertSame(custom, config.getAuthProvider());
    }

    // ---- BearerTokenAuthProvider (logic tests via fake exchange) ----

    @Test
    public void bearerAuthShouldAcceptCorrectToken() {
        BearerTokenAuthProvider provider = new BearerTokenAuthProvider("my-secret-token");
        Assert.assertTrue(provider.authenticate(new FakeExchange("Bearer my-secret-token")));
    }

    @Test
    public void bearerAuthShouldRejectWrongToken() {
        BearerTokenAuthProvider provider = new BearerTokenAuthProvider("my-secret-token");
        Assert.assertFalse(provider.authenticate(new FakeExchange("Bearer wrong-token")));
    }

    @Test
    public void bearerAuthShouldRejectMissingHeader() {
        BearerTokenAuthProvider provider = new BearerTokenAuthProvider("my-secret-token");
        Assert.assertFalse(provider.authenticate(new FakeExchange(null)));
    }

    @Test
    public void bearerAuthShouldRejectNonBearerScheme() {
        BearerTokenAuthProvider provider = new BearerTokenAuthProvider("my-secret-token");
        Assert.assertFalse(provider.authenticate(new FakeExchange("Basic dXNlcjpwYXNz")));
    }

    @Test
    public void bearerAuthShouldRejectBlankTokenInConstructor() {
        try {
            new BearerTokenAuthProvider("  ");
            Assert.fail("expected IllegalArgumentException for blank token");
        } catch (IllegalArgumentException expected) {
            // expected
        }
    }

    @Test
    public void bearerAuthGeneratedTokenShouldBeNonBlank() {
        BearerTokenAuthProvider provider = new BearerTokenAuthProvider();
        Assert.assertNotNull(provider.getToken());
        Assert.assertTrue("generated token must be at least 32 chars", provider.getToken().length() >= 32);
    }

    @Test
    public void bearerAuthDescribeShouldMaskToken() {
        BearerTokenAuthProvider provider = new BearerTokenAuthProvider("abcdef1234567890");
        String desc = provider.describe();
        Assert.assertTrue("describe should not contain full token", !desc.contains("abcdef1234567890"));
    }

    @Test
    public void bearerAuthShouldRejectNullExchange() {
        BearerTokenAuthProvider provider = new BearerTokenAuthProvider("token");
        Assert.assertFalse(provider.authenticate(null));
    }

    // ---- Integration: server binds to loopback and enforces auth ----

    @Test
    public void streamableHttpServerShouldReturn401WithoutToken() throws Exception {
        int port = findFreePort();
        BearerTokenAuthProvider auth = new BearerTokenAuthProvider("integration-test-token");
        StreamableHttpMcpServer server = new StreamableHttpMcpServer(
                "test-sec", "1.0.0", "127.0.0.1", port, auth, null);

        try {
            server.start();
            waitForStartup(server);

            // Health endpoint: no auth required
            int healthStatus = httpStatus("http://127.0.0.1:" + port + "/health", null);
            Assert.assertEquals("health endpoint should be accessible without auth", 200, healthStatus);

            // MCP endpoint without token: must be 401
            int mcpStatus = httpStatus("http://127.0.0.1:" + port + "/mcp", null);
            Assert.assertEquals("MCP endpoint without token must return 401", 401, mcpStatus);

            // MCP endpoint with wrong token: must be 401
            int wrongStatus = httpStatus("http://127.0.0.1:" + port + "/mcp", "Bearer wrong-token");
            Assert.assertEquals("MCP endpoint with wrong token must return 401", 401, wrongStatus);
        } finally {
            server.stop();
            Thread.sleep(500);
        }
    }

    @Test
    public void streamableHttpServerWithNoAuthShouldNotReturn401() throws Exception {
        int port = findFreePort();
        StreamableHttpMcpServer server = new StreamableHttpMcpServer(
                "test-noauth", "1.0.0", "127.0.0.1", port, null, null);

        try {
            server.start();
            waitForStartup(server);

            int mcpStatus = httpStatus("http://127.0.0.1:" + port + "/mcp", null);
            Assert.assertNotEquals("MCP endpoint with no auth must not return 401", 401, mcpStatus);
        } finally {
            server.stop();
            Thread.sleep(500);
        }
    }

    private static void waitForStartup(StreamableHttpMcpServer server) throws InterruptedException {
        for (int i = 0; i < 50 && !server.isRunning(); i++) {
            Thread.sleep(100);
        }
        Assert.assertTrue("server did not start within 5s", server.isRunning());
    }

    // ---- helpers ----

    private static int findFreePort() throws IOException {
        java.net.ServerSocket socket = new java.net.ServerSocket(0);
        try {
            return socket.getLocalPort();
        } finally {
            socket.close();
        }
    }

    private static int httpStatus(String urlStr, String authHeader) throws IOException {
        HttpURLConnection conn = (HttpURLConnection) new URL(urlStr).openConnection();
        conn.setRequestMethod("GET");
        conn.setConnectTimeout(3000);
        conn.setReadTimeout(3000);
        if (authHeader != null) {
            conn.setRequestProperty("Authorization", authHeader);
        }
        try {
            return conn.getResponseCode();
        } finally {
            conn.disconnect();
        }
    }

    /**
     * Minimal HttpExchange stub for unit-testing auth header parsing without a running server.
     * Only getRequestHeaders() is meaningful; other methods return null/defaults.
     */
    private static final class FakeExchange extends HttpExchange {
        private final Headers headers;

        FakeExchange(String authHeader) {
            this.headers = new Headers();
            if (authHeader != null && !authHeader.isEmpty()) {
                headers.put("Authorization", Collections.singletonList(authHeader));
            }
        }

        @Override public Headers getRequestHeaders() { return headers; }
        @Override public void close() { }
        @Override public Object getAttribute(String name) { return null; }
        @Override public void setAttribute(String name, Object value) { }
        @Override public void setStreams(InputStream i, OutputStream o) { }
        @Override public InetSocketAddress getRemoteAddress() { return null; }
        @Override public int getResponseCode() { return 0; }
        @Override public InetSocketAddress getLocalAddress() { return null; }
        @Override public String getProtocol() { return "HTTP/1.1"; }
        @Override public String getRequestMethod() { return "GET"; }
        @Override public URI getRequestURI() { return URI.create("http://127.0.0.1:1/"); }
        @Override public InputStream getRequestBody() { return null; }
        @Override public OutputStream getResponseBody() { return null; }
        @Override public Headers getResponseHeaders() { return new Headers(); }
        @Override public void sendResponseHeaders(int rCode, long responseLength) { }
        @Override public HttpContext getHttpContext() { return null; }
        @Override public HttpPrincipal getPrincipal() { return null; }
    }
}
