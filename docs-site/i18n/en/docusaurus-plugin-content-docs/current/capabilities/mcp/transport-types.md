---
sidebar_position: 3
title: "MCP Transport Types In Depth (STDIO / SSE / Streamable HTTP)"
description: "Compares the three MCP transports — STDIO, SSE, and Streamable HTTP — by fit, trade-offs, and default connection behavior, and clarifies the TransportConfig unified configuration surface and where responsibility for the choice lands."
tags: [concept]
---

# MCP Transport Types In Depth (STDIO / SSE / Streamable HTTP)

In MCP, transport is not a "wiring detail" — it is a first-class choice that directly changes the deployment shape, the stability model, and how the transport is governed.

In AI4J, the core of this abstraction is:

- `McpTransport`
- `TransportConfig`
- `McpTransportFactory`

## 1. First, what the `McpTransport` abstraction actually promises

Every transport must implement:

- `start()`
- `stop()`
- `sendMessage(...)`
- `setMessageHandler(...)`
- `isConnected()`
- `needsHeartbeat()`
- `getTransportType()`

This means a transport is not merely a connection object, but a complete message-layer adapter.

The single most important method here is:

- `needsHeartbeat()`

Because it directly determines whether `McpClient` starts an application-layer heartbeat check.

## 2. `TransportConfig` is the unified configuration surface

AI4J does not give each transport its own parameter bag. Everything is unified under `TransportConfig`.

### 2.1 Common fields

- `type`
- `connectTimeout`
- `readTimeout`
- `writeTimeout`
- `enableRetry`
- `maxRetries`
- `retryDelay`
- `enableHeartbeat`
- `heartbeatInterval`

### 2.2 HTTP / SSE fields

- `url`
- `headers`

### 2.3 STDIO fields

- `command`
- `args`
- `env`

### 2.4 Defaults

The current defaults are:

- `connectTimeout = 30`
- `readTimeout = 60`
- `writeTimeout = 60`
- `enableRetry = true`
- `maxRetries = 3`
- `retryDelay = 1000ms`
- `enableHeartbeat = false`
- `heartbeatInterval = 30000ms`

So transport selection decides not only the protocol, but also the default connection behavior.

## 3. STDIO: local process mode

### 3.1 What it actually fits

STDIO is best for:

- Local subprocess tools
- CLI / IDE hosts spawning an external MCP server
- Scenarios where you don't want to open an extra server port

### 3.2 Its strengths

- Simple deployment
- No remote network exposure needed
- Clear process boundary

### 3.3 Its cost

- The host owns the process lifecycle
- Issues in the subprocess's stdout / stderr / environment variables directly affect availability
- Scaling and remote governance are less natural than with HTTP-style transports

### 3.4 Typical usage in AI4J

```java
McpTransport transport = new StdioTransport(
        "npx",
        Arrays.asList("-y", "@modelcontextprotocol/server-filesystem", "D:/workspace"),
        null
);
```

### 3.5 When to prefer it

- Local development
- Single-machine toolchains
- Coding / IDE host environments

## 4. SSE: long-lived event stream mode

### 4.1 What it actually fits

SSE fits when:

- The peer MCP server already serves over SSE
- You accept the long-lived connection model
- Your environment is friendly to SSE proxies and timeout configuration

### 4.2 Its strengths

- The long-lived event model is intuitive
- Well suited to server-side proactive push

### 4.3 Its cost

- More dependent on network and proxy stability
- Reconnection strategy matters more
- Gateways, LBs, and reverse proxies can become sources of trouble

### 4.4 Typical usage in AI4J

```java
McpTransport transport = new SseTransport("https://example.com/sse");
McpClient client = new McpClient("my-client", "1.0.0", transport);
client.connect().join();
```

### 4.5 What it additionally means in AI4J

Network-style transports typically make:

- `needsHeartbeat() = true`

So `McpClient` will start an application-layer heartbeat check.

## 5. Streamable HTTP: service-first mode

### 5.1 What it actually fits

If you want to treat MCP as a governable, deployable, gateway-friendly service interface, Streamable HTTP is usually the most natural choice.

Fits:

- Intranet / public network service publishing
- Unified platform-side integration
- Cloud-native deployment

### 5.2 Its strengths

- Mature HTTP infrastructure
- Easier to wire authentication, gateways, observability, and traffic governance
- Deployment habits closer to an ordinary service

### 5.3 Its cost

- You need to handle auth headers, timeouts, and network issues more carefully
- For "just running a local script", it feels heavier than necessary

### 5.4 Typical usage in AI4J

```java
TransportConfig config = TransportConfig.streamableHttp("https://example.com/mcp");
config.setHeaders(Collections.singletonMap("Authorization", "Bearer xxx"));

McpTransport transport = new StreamableHttpTransport(config);
McpClient client = new McpClient("my-client", "1.0.0", transport);
client.connect().join();
```

This configuration defaults to `McpProtocolProfile.AUTO`. It first probes the Streamable HTTP peer only with the modern `server/discover`: a successful discovery result selects the stateless `2026-07-28` profile; an unrecognized `400`, `404`, or `405` is what triggers fallback to the initialization-era profile.

If the target is a session-era Streamable HTTP server that has not been upgraded, select the legacy profile explicitly:

```java
config.withProtocolProfile(McpProtocolProfile.LEGACY_2025_03_26);
```

:::note AUTO is not omniscient detection
AUTO is not omniscient detection: authentication failures, recognizable modern JSON-RPC errors, or invalid discovery responses will not trigger downgrade. The `http` type alias normalizes to `streamable_http` and keeps this profile semantics; the deprecated HTTP+SSE combo must use `sse` explicitly. For the full set of differences and the upgrade path, see [Streamable HTTP](/docs/capabilities/mcp/streamable-http).
:::

### 5.5 Why it is usually the production-first option

Because it aligns most easily with:

- Authentication
- Reverse proxies
- Gateway governance
- Service discovery

these platform capabilities.

## 6. Why `McpTransportFactory` matters

If every upstream module news its own concrete transport, transport selection gets scattered across business code.

AI4J uses:

- `McpTransportFactory.createTransport(type, config)`

to unify the creation logic.

Supported type aliases include at least:

- `stdio`
- `sse`
- `streamable_http`
- `http`

Of which:

- `http` normalizes to `streamable_http`

This lets the configuration layer be more flexible, while the runtime still has unified normalization semantics.

## 7. The point of `TransportConfig.fromServerInfo(...)`

When you take a configuration-driven or gateway approach, you typically don't hand-write transport creation code.

`TransportConfig.fromServerInfo(...)` auto-normalizes from `McpServerConfig.McpServerInfo` into:

- a stdio configuration
- an sse configuration
- a streamable http configuration

This means transport selection can be lifted to the configuration layer, rather than being hardcoded in Java.

## 8. When choosing, the question is not "which is more advanced" but "who owns what"

### When the host owns the process lifecycle

Prefer:

- STDIO

### When the platform owns service governance

Prefer:

- Streamable HTTP

### When the peer already provides SSE capability

Prefer:

- SSE

Transport selection is fundamentally about choosing "which layer the connection-governance responsibility lands on".

## 9. Common problems

### 9.1 `connect()` hangs

Common causes:

- URL unreachable
- Subprocess did not start
- The legacy profile `initialize` did not complete
- On the modern HTTP profile, the request metadata or `MCP-Protocol-Version` was stripped by a proxy

### 9.2 SSE keeps disconnecting

Check first:

- Proxy-layer timeouts
- Long-connection support
- Whether auto-reconnect is enabled

### 9.3 STDIO fails to start

Check first:

- `command`
- `args`
- `env`
- Whether the subprocess even runs successfully locally

## 10. The final selection principle

If you just want to get connected, pick the transport closest to your target deployment shape. Don't reach for a complex mode prematurely "in case it scales later".

Typically:

- Local toolchain: STDIO
- Existing SSE service: SSE
- Platform service integration: Streamable HTTP
