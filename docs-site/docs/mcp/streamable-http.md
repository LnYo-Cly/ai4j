---
sidebar_position: 6
---

# Streamable HTTP

Streamable HTTP is the HTTP transport option for connecting AI4J to an MCP server or publishing an MCP server from Java. It is the right starting point when the MCP endpoint is a service rather than a locally spawned process.

This page describes the behavior present in the current AI4J source. MCP transport specifications evolve independently, so interoperability must be verified against the exact peer and protocol version you deploy.

## Use it when

Choose Streamable HTTP when you need normal HTTP infrastructure around MCP:

- TLS termination, authentication headers, and reverse proxies;
- service-to-service deployment rather than a local child process;
- JSON responses with an optional SSE response stream;
- a route that is easier to observe and govern than a long-lived client-managed SSE connection.

For transport selection across STDIO, SSE, and HTTP, see [Transport types](/docs/mcp/transport-types).

## Connect an AI4J client

`TransportConfig.streamableHttp(...)` creates the HTTP configuration. Keep credentials outside source code and attach them through the normal header map.

```java
import io.github.lnyocly.ai4j.mcp.client.McpClient;
import io.github.lnyocly.ai4j.mcp.transport.McpTransport;
import io.github.lnyocly.ai4j.mcp.transport.StreamableHttpTransport;
import io.github.lnyocly.ai4j.mcp.transport.TransportConfig;

import java.util.Collections;

TransportConfig config = TransportConfig.streamableHttp(
        "https://mcp.example.internal/mcp"
);
config.setHeaders(Collections.singletonMap(
        "Authorization",
        "Bearer " + System.getenv("MCP_TOKEN")
));

McpTransport transport = new StreamableHttpTransport(config);
McpClient client = new McpClient("orders-client", "1.0.0", transport);
client.connect().join();
```

Use `McpTransportFactory` instead when the transport type comes from configuration. The deprecated `http` alias is normalized to `streamable_http` by the factory.

## Current HTTP behavior

The published implementation exposes its MCP endpoint at `/mcp` and supports these requests:

| Request | Current behavior |
| --- | --- |
| `POST /mcp` | Sends a JSON-RPC MCP message and returns JSON or an SSE response according to `Accept`. |
| `GET /mcp` with `Accept: text/event-stream` | Opens an SSE stream. |
| `GET /mcp` without that accept value | Returns server information. |
| `DELETE /mcp` | Terminates the named session. |
| `OPTIONS /mcp` | Responds to the configured CORS preflight path. |

The client posts `Content-Type: application/json` and accepts `application/json, text/event-stream`. It also remembers `mcp-session-id` and `last-event-id` values returned by the peer.

## Session model and stateless deployments

Current AI4J Streamable HTTP is **session-oriented**:

- the server stores `SessionContext` instances in memory;
- it returns `mcp-session-id` and the client sends that header on later requests;
- `DELETE` removes the in-memory session;
- an SSE connection is associated with that session.

Do not document or deploy this implementation as a stateless MCP server. In a multi-instance deployment, keep requests for a session on the same instance or move the session state behind a deliberately designed shared store before removing affinity.

The current `McpClient` initializes with MCP protocol version `2025-03-26`. Treat that as a compatibility input, not a claim that every newer transport mode is implemented. Test initialize, `tools/list`, `tools/call`, JSON responses, SSE responses, reconnects, and session termination against the actual peer.

## Server and proxy checklist

- Bind a development server to loopback unless remote access is intentional.
- Put TLS and authentication in front of any reachable endpoint.
- Restrict CORS to trusted origins; do not use a broad origin policy as a shortcut.
- Preserve `Content-Type`, `Accept`, `mcp-session-id`, and `last-event-id` through proxies.
- Configure proxy timeouts for an SSE response if the peer requests one.
- Do not place bearer tokens in the URL or commit them in `TransportConfig` examples.

For tool exposure rules after a connection succeeds, read [Tool exposure semantics](/docs/mcp/tool-exposure-semantics). For server publication, read [Build an MCP server](/docs/mcp/build-your-mcp-server).

## Verify locally

The focused regression for the client transport is:

```bash
mvn -pl ai4j -Dtest=StreamableHttpTransportTest -DskipTests=false test
```

That test is a local transport check. It does not replace an interoperability test against the MCP server, proxy, and authentication setup used in production.
