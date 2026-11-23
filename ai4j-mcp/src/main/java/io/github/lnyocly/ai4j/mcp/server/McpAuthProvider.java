package io.github.lnyocly.ai4j.mcp.server;

import com.sun.net.httpserver.HttpExchange;

/**
 * Pluggable authentication for HTTP/SSE MCP servers. Implementations verify the incoming
 * request (typically via a bearer token in the {@code Authorization} header) and return
 * {@code true} to allow the request or {@code false} to deny it.
 *
 * <p>The MCP server calls {@link #authenticate(HttpExchange)} for every inbound HTTP request.
 * When authentication fails the server responds with HTTP 401.</p>
 *
 * @see BearerTokenAuthProvider
 */
public interface McpAuthProvider {

    /**
     * Inspect the exchange and decide whether the request is authenticated.
     *
     * @param exchange the incoming HTTP exchange (headers available via {@code exchange.getRequestHeaders()})
     * @return {@code true} if the request passes authentication, {@code false} otherwise
     */
    boolean authenticate(HttpExchange exchange);

    /**
     * Human-readable description of the auth scheme (for logging at startup).
     *
     * @return a short label such as {@code "Bearer token: abc..."}
     */
    String describe();
}
