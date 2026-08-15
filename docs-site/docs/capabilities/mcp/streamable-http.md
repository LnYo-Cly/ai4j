---
sidebar_position: 6
title: Streamable HTTP 传输
description: "Streamable HTTP transport for AI4J MCP: AUTO profile discovery, modern vs initialization-era peers, protocol headers, publishing a server, and upgrade paths."
tags: [integration]
---

# Streamable HTTP 传输
Streamable HTTP is the HTTP transport for connecting AI4J to an MCP server or publishing an MCP server from Java. `TransportConfig.streamableHttp(...)` and `McpServerFactory.ServerConfig` both default to `McpProtocolProfile.AUTO`.

`AUTO` is a limited compatibility strategy, not universal protocol detection. A client starts with one modern `server/discover` request; an AUTO server accepts modern and initialization-era Streamable HTTP requests on the same `/mcp` endpoint. The deprecated HTTP+SSE transport remains separate and must be configured as `type: "sse"`.

## 0. 协议演进:为什么有新旧两套 Streamable HTTP

MCP 的 HTTP transport 经历了一次重大协议变更。AI4J **完整适配了新旧两套**,通过 `McpProtocolProfile` 让你按对端选。理解这条演进线,profile 配置才有意义。

### 旧 transport:HTTP+SSE(协议 `2024-11-05`,已废弃)

初版 MCP 用**两个端点**:

- 一个 SSE 端点(长连接,server → client 推消息)
- 一个 POST 端点(client → server 发请求)

问题:要维护长连接、不利于无状态部署、两端点协调复杂。官方在后续版本里废弃了它。

### 新 transport:Streamable HTTP(协议 `2025-03-26` 引入,`2025-06-18` 规范化)

[2025-03-26 修订](https://modelcontextprotocol.io/specification/2025-06-18/basic/transports)用**单端点** `/mcp` 取代了双端点:

- client POST 请求到 `/mcp`
- server 可以直接返回 JSON(短任务),也可以升级成 SSE 流(长任务/流式)
- 不再要求长连接,支持无状态 server,可恢复(用 `Last-Event-ID`)

`2025-06-18` 把它规范化,并加了结构化 tool 输出、OAuth 2.1 + PKCE、JSON-RPC 批处理语义。

### AI4J 的 modern 演进:`server/discover`(协议 `2025-11-25` / `2026-07-28`)

更新的协议版本进一步简化了握手:

- **去掉 `initialize` 握手**和 `notifications/initialized`
- 用一次 `server/discover` 探测能力
- **stateless `POST /mcp`**,无 `Mcp-Session-Id` 会话头

AI4J 把这称为 **modern** profile(`MODERN_2026_07_28`),与仍保留握手的 **legacy** Streamable HTTP profile(`LEGACY_2025_03_26` 等)区分。

### AI4J 的适配:5 个版本 + AUTO 自动协商

AI4J 的 `McpProtocolProfile` 覆盖全部 5 个 MCP 协议版本:

| Profile | 协议版本 | 性质 |
| --- | --- | --- |
| `MODERN_2026_07_28` | `2026-07-28` | modern:stateless、无握手、`server/discover` |
| `LEGACY_2025_11_25` | `2025-11-25` | legacy Streamable HTTP(带握手) |
| `LEGACY_2025_06_18` | `2025-06-18` | legacy Streamable HTTP |
| `LEGACY_2025_03_26` | `2025-03-26` | legacy Streamable HTTP(最早引入) |
| `LEGACY_2024_11_05` | `2024-11-05` | 旧的 HTTP+SSE(用 `type: "sse"`,不走 Streamable HTTP) |

默认 `AUTO` 是协商策略:先发一次 modern `server/discover`,根据响应(或 400/404/405 回退)自动落到 modern 或 legacy。这样**调用方通常不用知道对端是哪个版本**——除非你要固定 pin 一个 profile。

:::note 已废弃的 HTTP+SSE 仍可用,但单独配置
旧的 HTTP+SSE(`2024-11-05`)没有消失——它在 AI4J 里作为 `SseTransport` / `type: "sse"` 保留,是和 Streamable HTTP 并列的独立 transport,不是 profile 的一种。`AUTO` 不会把 Streamable HTTP 端点重解释成 HTTP+SSE。
:::

## Choose the profile for the peer you have

| Peer | AI4J configuration | Wire behavior |
| --- | --- | --- |
| An unknown or transition-era Streamable HTTP peer | Default `AUTO` | Probes only `server/discover`; uses modern only after a valid modern discovery result, or falls back to an initialization-era Streamable HTTP profile only for an unrecognized `400`, `404`, or `405`. |
| A server known to support MCP `2026-07-28` Streamable HTTP | Explicit `MODERN_2026_07_28` | Pins stateless `POST /mcp`; no `initialize`, `notifications/initialized`, or `Mcp-Session-Id`. |
| An existing session-era Streamable HTTP server | Explicit `LEGACY_2025_03_26` | Keeps the handshake, session headers, optional SSE/GET path, and session termination behavior expected by that peer. |
| A deprecated HTTP+SSE server or a local STDIO server | Use `SseTransport` / `type: "sse"`, or `StdioTransport` | These are distinct transports. AUTO does not reinterpret a Streamable HTTP endpoint as HTTP+SSE or STDIO. |

For transport selection across STDIO, SSE, and HTTP, see [Transport types](/docs/capabilities/mcp/transport-types).

## Connect with the default AUTO profile

`TransportConfig.streamableHttp(...)` defaults to `McpProtocolProfile.AUTO`.

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

`connect()` starts the transport and resolves AUTO before application traffic is sent:

1. It sends only `server/discover`, using the `2026-07-28` metadata and headers.
2. A valid modern discovery result selects `MODERN_2026_07_28`; `connect()` marks the client ready without `initialize`.
3. It falls back to `LEGACY_2025_11_25` only when that probe receives an unrecognized HTTP `400`, `404`, or `405`; the normal legacy `initialize -> notifications/initialized` lifecycle then runs.

It does not downgrade for authentication or connection failures, recognized modern JSON-RPC errors, or successful/malformed responses that are not a usable discovery result. This protects a modern endpoint from being retried as legacy after a request error, but also means AUTO cannot identify every third-party MCP server. Pin a concrete profile when the peer contract is known or a compatibility probe is not acceptable.

## What AI4J sends after modern resolution

After AUTO resolves to modern, or when `MODERN_2026_07_28` is pinned, each `POST /mcp` uses this request envelope:

- `MCP-Protocol-Version: 2026-07-28`
- `Mcp-Method` matching the JSON-RPC method
- `_meta` with protocol version, client info, and the capabilities the client can actually satisfy
- `Mcp-Name` for named reads or invocations such as `tools/call`, `resources/read`, and `prompts/get`
- `Mcp-Param-*` headers when a tool input schema uses the MCP header extension

The server cross-checks the headers against the JSON-RPC request instead of treating headers as an alternate source of truth. Application code should call the normal `McpClient` APIs; it should not manufacture protocol headers itself.

The modern path accepts a request-scoped JSON response and can return an SSE response to the same `POST` when the peer requests it. A server pinned to `MODERN_2026_07_28` does not expose the legacy `GET /mcp` session stream or `DELETE /mcp` session termination endpoints.

## Modern discovery and cache hints

The modern server supports `server/discover` and includes cache hints in its results:

- `server/discover`: `ttlMs = 3600000`, `cacheScope = public`
- capability lists and reads: `ttlMs = 30000`, `cacheScope = private`

Treat these as protocol hints, not permission to share tenant-specific data across callers. A host or proxy remains responsible for cache keys, authentication, and invalidation policy.

:::note MRTR and subscriptions are not supported
AI4J does not currently claim support for modern multi-round-trip requests (MRTR) or subscriptions. Do not advertise or depend on those capabilities when integrating a modern peer.
:::

## Pin an existing session-era server

Use the legacy profile only when the peer requires the older `initialize` and session model:

```java
import io.github.lnyocly.ai4j.mcp.transport.McpProtocolProfile;
import io.github.lnyocly.ai4j.mcp.transport.TransportConfig;

TransportConfig config = TransportConfig.streamableHttp(
        "https://legacy-mcp.example.internal/mcp"
).withProtocolProfile(McpProtocolProfile.LEGACY_2025_03_26);

config.setHeaders(java.util.Collections.singletonMap(
        "Authorization",
        "Bearer " + System.getenv("MCP_TOKEN")
));
```

With that profile, `McpClient.connect()` retains the legacy `initialize` then `notifications/initialized` sequence. The transport can retain `mcp-session-id` and `last-event-id`, and a legacy server can expose session-specific `GET` and `DELETE` behavior.

Pinning a legacy profile skips AUTO discovery and is appropriate when the peer contract is known. Keep the exact legacy profile required by the peer until it has been verified against the modern contract.

### Gateway configuration boundary

`McpServerConfig.McpServerInfo` supports `protocolProfile` in JSON. Where a configuration source is loaded into that model, `McpGatewayClientFactory` copies the value into `TransportConfig`:

```json
{
  "mcpServers": {
    "orders": {
      "type": "streamable_http",
      "url": "https://mcp.example.internal/mcp",
      "protocolProfile": "AUTO"
    }
  }
}
```

Use `"MODERN_2026_07_28"` to prohibit legacy fallback, or a concrete `"LEGACY_2025_11_25"`, `"LEGACY_2025_06_18"`, or `"LEGACY_2025_03_26"` value to pin an initialization-era peer. The field affects `streamable_http` (and its `http` alias); a deprecated HTTP+SSE peer still uses `"type": "sse"`, not an AUTO fallback.

## Publish a server

`McpServerFactory.ServerConfig` defaults to `AUTO`. An AUTO server accepts modern requests and initialization-era Streamable HTTP requests on the same `/mcp` endpoint. It identifies a modern request from the modern headers or request metadata; it is not a detector for the separate HTTP+SSE transport.

```java
import io.github.lnyocly.ai4j.mcp.server.McpServer;
import io.github.lnyocly.ai4j.mcp.server.McpServerFactory;
import io.github.lnyocly.ai4j.mcp.transport.McpProtocolProfile;

McpServerFactory.ServerConfig config = new McpServerFactory.ServerConfig(
        "orders-server", "1.0.0"
).withPort(8081).withProtocolProfile(
        McpProtocolProfile.AUTO
);

McpServer server = McpServerFactory.createServer("streamable_http", config);
server.start().join();
```

Pin `McpProtocolProfile.MODERN_2026_07_28` only when the endpoint must reject initialization-era requests. Pin a concrete legacy profile when the endpoint must retain only its session-era behavior. HTTP+SSE remains a separately published `sse` transport.

## Upgrade an existing deployment

1. Identify whether the peer is Streamable HTTP or the deprecated HTTP+SSE transport. Configure the latter explicitly as `type: "sse"`.
2. Use the default `AUTO` profile for an unknown or transition-era Streamable HTTP peer, or pin its documented profile when it is known.
3. Run `server/discover`, `tools/list`, and a representative `tools/call` through the deployed authentication and proxy path.
4. During a server rollout, leave the Streamable HTTP server on `AUTO` so modern and initialization-era clients can share `/mcp`.
5. After every client is verified modern, pin `MODERN_2026_07_28` only if rejecting legacy traffic is part of the deployment policy.

This route preserves compatibility without claiming that AUTO can detect arbitrary endpoints or transports.

## Server and proxy checklist

- Bind a development server to loopback unless remote access is intentional.
- Put TLS and authentication in front of any reachable endpoint.
- Restrict CORS to trusted origins; do not use a broad origin policy as a shortcut.
- Preserve `Content-Type`, `Accept`, `MCP-Protocol-Version`, `Mcp-Method`, and `Mcp-Name` through proxies.
- If a browser or proxy must send schema-driven `Mcp-Param-*` headers, verify its CORS preflight and header allow-list against the deployed endpoint.
- Do not place bearer tokens in the URL or commit them in `TransportConfig` examples.

For tool exposure rules after a connection succeeds, read [Tool exposure semantics](/docs/capabilities/mcp/tool-exposure-semantics). For server publication, read [Build an MCP server](/docs/capabilities/mcp/build-your-mcp-server).

## Verify locally

Run the focused modern and transport regressions from the repository root:

```bash
mvn -pl ai4j -Dtest=StreamableHttpTransportTest,StreamableHttpModernProtocolTest,McpClientModernProtocolTest -DskipTests=false test
```

That is a local implementation check. It does not replace an interoperability test against the exact MCP server, proxy, authentication setup, and profile used in production.
