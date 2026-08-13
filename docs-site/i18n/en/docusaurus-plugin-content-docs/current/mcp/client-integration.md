---
sidebar_position: 4
title: "MCP Client Integration (Single-Server Mode)"
description: "Focused on single-server mode: the real lifecycle of McpClient, what connect() does and how AUTO differs from the legacy profile, caching and heartbeat/reconnect semantics, callTool failure semantics, and common troubleshooting paths."
tags: [how-to]
---

# MCP Client Integration (Single-Server Mode)

This page covers only "single-server mode".

In other words, the questions in front of you right now are:

- Connect to a single MCP server
- Determine whether it speaks modern stateless HTTP or the session-era protocol
- Get tool / resource / prompt, the three high-level APIs, working end to end

If you have already moved on to multiple services or per-user isolation, you should read the gateway docs instead of staying on this page.

## 1. The real lifecycle of `McpClient`

`McpClient` is not something you can `callTool()` on the moment it is constructed.

Its real lifecycle is:

1. Construct the transport
2. `new McpClient(...)`
3. `connect()`
4. Become ready per transport/profile: the default AUTO does limited probing first; modern HTTP does no handshake; legacy transports complete the initialization handshake
5. Read tools / resources / prompts
6. Invoke capabilities
7. `disconnect()`

Missing any step in between means it is not a stable client.

## 2. What `connect()` actually does

`connect()` always runs `transport.start()` first, with a `30s` timeout, and then takes different paths depending on transport/profile:

- The default `StreamableHttpTransport` uses `AUTO`. It only sends the modern `server/discover` first; a valid modern discovery result selects `2026-07-28` and does not send `initialize` or `notifications/initialized`.
- AUTO falls back to initialization-era Streamable HTTP only when that probe receives an unrecognized HTTP `400`, `404`, or `405`, and then runs `initialize -> notifications/initialized`. Authentication failures, recognizable modern JSON-RPC errors, and invalid discovery responses do not trigger the downgrade.
- STDIO, HTTP+SSE with explicit `type: "sse"`, and clients that explicitly select the legacy Streamable HTTP profile keep the initialization-era flow.
- If the transport needs a heartbeat, the client starts the heartbeat after it is ready.

Therefore `isConnected()` and `isInitialized()` are still pre-call checks, but for modern HTTP `isInitialized()` means "client ready", not "a protocol handshake happened".

## 3. `McpClient` capability boundary

AI4J's high-level APIs cover Tool, Resource, and Prompt. The capabilities declared per request on modern HTTP are conservative: it does not claim support for sampling, roots, elicitation, MRTR, or subscriptions until these multi-turn protocol capabilities are actually available.

Do not infer from old session-era `initialize` examples that a modern peer also supports these optional capabilities. Trust the target server's protocol profile and its actual capability catalog.

## 4. Minimal integration example: STDIO

If you are connecting to a local subprocess MCP server, the shortest path is usually stdio.

```java
McpTransport transport = new StdioTransport(
        "npx",
        Arrays.asList("-y", "@modelcontextprotocol/server-filesystem", "D:/workspace"),
        null
);

McpClient client = new McpClient("demo-client", "1.0.0", transport);
client.connect().join();

List<McpToolDefinition> tools = client.getAvailableTools().join();
String result = client.callTool("read_file", Collections.singletonMap("path", "README.md")).join();

client.disconnect().join();
```

This path validates:

- Whether the subprocess can start
- Whether the stdio transport can handshake
- Whether tools/list and tools/call work

## 5. Minimal integration example: Streamable HTTP / SSE

If you are integrating a service-style MCP, you will usually use HTTP or SSE.

### Streamable HTTP

```java
TransportConfig config = TransportConfig.streamableHttp("https://example.com/mcp");
config.setHeaders(Collections.singletonMap("Authorization", "Bearer your-token"));

McpTransport transport = McpTransportFactory.createTransport("streamable_http", config);
McpClient client = new McpClient("demo-client", "1.0.0", transport);
client.connect().join();
```

This example uses `AUTO` by default. For unknown or in-migration Streamable HTTP peers, it only does limited compatibility probing via `server/discover`; it does not auto-select HTTP+SSE. For a known session-handshake server, set it explicitly:

```java
config.withProtocolProfile(McpProtocolProfile.LEGACY_2025_03_26);
```

For full request headers, server profiles, and the upgrade path, see [Streamable HTTP](/docs/mcp/streamable-http).

### SSE

```java
McpTransport transport = new SseTransport("https://example.com/sse");
McpClient client = new McpClient("demo-client", "1.0.0", transport);
client.connect().join();
```

The difference between these two paths is not in `McpClient` but in the transport connection model. HTTP+SSE is the explicit `sse` transport; do not treat it as the fallback target of Streamable HTTP AUTO.

## 6. The high-level APIs on `McpClient` are more than just Tool

The current high-level APIs cover at least:

- `getAvailableTools()` -> `tools/list`
- `callTool(name, args)` -> `tools/call`
- `getAvailableResources()` -> `resources/list`
- `readResource(uri)` -> `resources/read`
- `getAvailablePrompts()` -> `prompts/list`
- `getPrompt(name, arguments)` -> `prompts/get`

### 6.1 Resource example

```java
List<McpResource> resources = client.getAvailableResources().join();
McpResourceContent resource = client.readResource("file://docs/README.md").join();
```

### 6.2 Prompt example

```java
List<McpPrompt> prompts = client.getAvailablePrompts().join();
McpPromptResult prompt = client.getPrompt(
        "code_review_prompt",
        Collections.<String, Object>singletonMap("language", "java")
).join();
```

This shows that MCP inside AI4J is not simply a "remote function call protocol" — it covers all three capability types: tool / resource / prompt.

## 7. Understand the caching semantics first

`McpClient` caches:

- `availableTools`
- `availableResources`
- `availablePrompts`

This has two direct consequences:

### 7.1 Benefits

- No need to repeat the list call every time
- Lower per-session call overhead

### 7.2 Boundary

After a disconnect or reconnect, the cache may be invalid, so the client clears these caches on disconnect.

This is also why `disconnect()` is not just "close the connection" — it also resets the state.

## 8. Heartbeat and auto-reconnect are not optional details

### 8.1 Heartbeat

If the transport's `needsHeartbeat()` returns `true`, the client starts a low-frequency heartbeat check.

The current implementation is:

- Run a `getAvailableTools()` check once every 10 minutes

This is more of a last-resort liveness check than a high-frequency keepalive.

### 8.2 Auto-reconnect

`McpClient` defaults to:

- `autoReconnect = true`

On disconnect it will:

- Clear the cache
- Stop the heartbeat
- Stop the transport
- Cancel pending requests
- Attempt to reconnect after 5 seconds

This means it already has basic session-recovery capability, but it is not a sophisticated connection pool.

### 8.3 Disabling auto-reconnect

The default 3-argument constructor pins `autoReconnect` to `true`:

```java
new McpClient("demo-client", "1.0.0", transport)
// equivalent to new McpClient("demo-client", "1.0.0", transport, true)
```

If you want to own the connection lifecycle yourself (for example, letting an upper-layer gateway or orchestrator schedule reconnects uniformly, or doing one-shot short-lived calls), disable it explicitly with the 4-argument constructor:

```java
McpClient client = new McpClient("demo-client", "1.0.0", transport, false);
```

Behavioral differences once disabled:

- On disconnect it still clears the cache, stops the heartbeat, stops the transport, and cancels pending requests
- But it **no longer schedules a reconnect** (the log will print `自动重连已禁用，跳过MCP重连`)
- Whether to reconnect afterwards is entirely up to the caller; the usual approach is to `new McpClient(...)` again and then `connect()`

:::note Gateway-created clients have reconnect on by default
`McpGateway` uses the default 3-argument constructor when creating a client via `McpGatewayClientFactory`, i.e. `autoReconnect = true`. There is currently no config option to inject `autoReconnect` from the configuration file into the gateway creation path (see [Configuration & Gateway Reference - autoReconnect field](/docs/mcp/configuration-and-gateway-reference)). To disable it, construct the client outside the gateway yourself and register it via `addMcpClient(...)`.
:::

## 9. Look at the failure semantics of `callTool()` in two layers

This is an easy place to get wrong.

### 9.1 Connection-state failure

If the client is not connected or not initialized, `callTool()` returns an exceptional future directly.

### 9.2 Protocol-layer failure

If the server returns an MCP error response, the current implementation usually flattens the error into a string instead of necessarily throwing an exception.

:::warning Protocol-layer failure does not always throw
Therefore the caller must not only catch exceptions — it must also check whether the returned content is failure text.
:::

## 10. Recommended integration posture

The most stable way to use single-server mode is:

1. Construct the transport
2. `connect().join()`
3. Call `getAvailableTools()` first to see the real exposed names
4. Then `callTool(...)`
5. `disconnect().join()` in a `finally`

This significantly lowers troubleshooting cost, because tool names, permissions, and connection issues surface at earlier steps.

## 11. Common troubleshooting paths

### 11.1 `not connected or not initialized`

Check first:

- Did you call `connect()` first
- Whether the transport/profile matches the target peer; whether AUTO's `server/discover` received a response that allows fallback
- Only the legacy profile checks whether the initialization handshake truly completed; for modern HTTP, check whether request metadata and HTTP headers are preserved by the proxy

### 11.2 `tool not found`

Check first:

- Whether `getAvailableTools()` can see that name
- Whether you are calling the MCP-exposed name, not your own alias

### 11.3 `resource not found` / `prompt not found`

Check first:

- `getAvailableResources()`
- `getAvailablePrompts()`

### 11.4 HTTP 401 / 403

Check first:

- `TransportConfig.headers`
- Whether the token is actually being sent in the request

## 12. When to leave this page

Once you have:

- Connected more than one MCP
- A need for per-user isolation
- A need for tool-source governance

you should no longer stay in single-server mode — switch to:

- [MCP Gateway Management](/docs/mcp/gateway-management)
- [Tool Exposure Semantics and Security Boundaries](/docs/mcp/tool-exposure-semantics)
