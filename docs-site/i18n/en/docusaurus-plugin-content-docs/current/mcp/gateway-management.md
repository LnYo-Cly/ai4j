---
sidebar_position: 5
title: "MCP Gateway Management (Multi-Service Aggregation and Governance)"
description: "The real responsibilities of McpGateway as a multi-service MCP runtime: key-rule multi-tenant isolation, tool registry mapping, hot-reload of config sources, and the rebuild-the-catalog strategy for dynamic add/remove."
tags: [concept]
---

# MCP Gateway Management (Multi-Service Aggregation and Governance)

`McpGateway` is not "a wrapper that creates a few more `McpClient`s", but rather the real multi-service MCP runtime in AI4J.

It handles four things at once:

- Holding all connected `McpClient`s
- Maintaining the `tool -> client` catalog mapping
- Uniformly managing both global services and user-level services
- Collapsing config sources, dynamic add/remove, and upper-layer agent calls into a single entry point

If you only connect one MCP, `McpClient` is enough; if you need service governance, tenant isolation, and dynamic start/stop, the core entry point is `mcp/gateway/McpGateway.java`.

## 1. What it actually manages internally

`McpGateway` holds three pieces of key state internally:

- `mcpClients`
  Stores `clientKey -> McpClient`
- `toolRegistry`
  Maintained by `McpGatewayToolRegistry` as `toolKey -> clientKey`
- `configSource`
  Binds an `McpConfigSource`, responsible for translating config-system changes into gateway add/remove/update operations

This means what the gateway manages is not "a snapshot of the tool list", but rather:

- Connection state
- The tool catalog
- Config source bindings
- The lifecycle entry point

## 2. The key rules ARE its multi-tenant boundary

AI4J does not make user isolation an annotation convention; it is written directly into the key rules.

### Global services

- client key: `serviceId`
- tool key: `toolName`

### User-level services

- client key: `user_{userId}_service_{serviceId}`
- tool key: `user_{userId}_tool_{toolName}`

The corresponding implementation entry points:

- `McpGatewayKeySupport.buildUserClientKey(...)`
- `McpGatewayKeySupport.buildUserToolKey(...)`
- `McpGatewayKeySupport.extractUserIdFromClientKey(...)`

This has two direct consequences:

- `callUserTool(...)` can look up a user-specific tool first, then fall back to the global tool
- `getUserAvailableTools(...)` can merge user tools and global tools in a single result

## 3. Initialization is not "just read a JSON"

### Mode A: Initialize from a config file

```java
McpGateway gateway = new McpGateway();
gateway.initialize("mcp-servers-config.json").join();
```

When `configSource == null`, the execution chain of `initialize(...)` is:

1. `loadServerConfig(configFile)`
2. `startConfiguredServers()`
3. For each enabled service, call `clientFactory.create(...)`
4. `addMcpClient(...)`
5. `client.connect().join()`
6. `toolRegistry.refresh(mcpClients).join()`

There is an important real semantic here:

- Only services with `enabled == true` in the config file get started
- A single service failing to start is logged and swallowed
- The gateway as a whole may still be marked `initialized = true`

So a successful `initialize()` does not mean "all services are connected"; it only means the gateway initialization flow has completed.

### Mode B: Initialize from a config source

```java
McpConfigSource source = new FileMcpConfigSource("mcp-servers-config.json");
McpGateway gateway = new McpGateway();
gateway.setConfigSource(source);
gateway.initialize().join();
```

Once you set a `configSource`, `initialize()` no longer goes through local config file parsing; it goes through:

1. `configSourceBinding.loadAll(configSource)`
2. Fetch all configs back from the config source
3. Create a client for each config and wire it into the gateway

This path is better suited for:

- Database config centers
- Back-office admin consoles
- Multi-tenant dynamic onboarding platforms

## 4. Dynamic add/remove is "rebuild the catalog", not local patching

The real order inside `addMcpClientInternal(...)` is:

1. First put the new client into `mcpClients`
2. `client.connect().join()`
3. `toolRegistry.refresh(mcpClients).join()`
4. On success, disconnect the old client
5. On failure, roll back `mcpClients`, then disconnect the new client

The order inside `removeMcpClientInternal(...)` is:

1. Remove from `mcpClients`
2. `client.disconnect().join()`
3. `toolRegistry.refresh(mcpClients).join()`

This shows that the current catalog-update strategy is:

- After every add/remove/update, re-pull the tool list from all connected clients
- Then rebuild the `tool -> client` mapping cache wholesale

The upside is a simple implementation with strong state consistency. The downsides are:

- When there are many services, refresh cost scales linearly
- It is not incremental

## 5. What `McpGatewayToolRegistry` actually does

`McpGatewayToolRegistry.refresh(...)` will:

1. Iterate over all `mcpClients`
2. For each connected client, call `getAvailableTools()`
3. Convert each `McpToolDefinition` into an OpenAI-style `Tool.Function`
4. Synchronously generate the `toolKey -> clientKey` mapping
5. Refresh the `availableTools` cache

There is also an easily overlooked but critical fact here:

:::warning Global tools with the same name overwrite each other
If two global services expose a tool with the same name, the mappings overwrite each other:

- The current implementation does no namespace isolation for same-named global tools
- Which client a call ultimately hits depends on whichever entry was written last during refresh

So in a multi-service platform, the `toolName` naming convention is not a suggestion, it is a hard requirement.
:::

## 6. How the call path goes

### Global tools

```java
String result = gateway.callTool("query_weather", arguments).join();
```

The call chain is:

1. `toolRegistry.getClientId(toolName)`
2. Resolve the corresponding `clientKey`
3. `mcpClients.get(clientKey)`
4. `client.callTool(toolName, arguments)`

If no mapping is found, it throws directly:

- `IllegalArgumentException("工具不存在: ...")`

If the client does not exist or is not connected, it throws:

- `IllegalStateException("MCP客户端不可用: ...")`

### User tools

```java
String result = gateway.callUserTool("u1001", "query_weather", arguments).join();
```

The call chain is:

1. Construct `user_{userId}_tool_{toolName}`
2. Look up the user-level mapping first
3. On a hit, route to the corresponding user client
4. On a miss, fall back to the global `callTool(...)`

This fallback strategy is practical, but be aware of what it implies:

- A user-level miss does not mean the tool is unavailable
- It may simply have landed on the global shared service

:::warning Default fallback is not strong isolation
If your business requires "strong tenant isolation, no fallback allowed", you cannot just copy the default strategy as-is.
:::

## 7. The boundaries of `getAvailableTools(...)` and `getUserAvailableTools(...)`

### `getAvailableTools()`

- Returns the full global tool cache
- If the cache is empty, it refreshes once first

### `getAvailableTools(serviceIds)`

- Filters only global `clientKey`s
- Essentially returns the tool set of the specified clients by `serviceId`

### `getUserAvailableTools(serviceIds, userId)`

- First enumerates user-level clients
- Then merges in global clients
- `serviceIds` only filters against global clients

So it is not "return only user-level tools", but rather "user-level tools + visible global tools".

## 8. How config source hot-reload actually takes effect

`McpGatewayConfigSourceBinding` is the bridge between the config source and the gateway.

It translates the three kinds of events from `McpConfigSource` into concrete actions:

- `onConfigAdded` -> create client -> `gateway.addMcpClient(...)`
- `onConfigUpdated` -> recreate client -> `gateway.addMcpClient(...)`
- `onConfigRemoved` -> `gateway.removeMcpClient(...)`

This means AI4J now has the foundational skeleton for "config changes driving runtime service switching".

But note the boundaries too:

- `FileMcpConfigSource` only provides `reloadConfigs()`; it does not auto-watch the file system
- MySQL / Redis / config centers require you to implement `McpConfigSource` yourself

## 9. What `getGatewayStatus()` can and cannot show

`getGatewayStatus()` currently provides:

- `totalClients`
- `globalClients`
- `userClients`
- `connectedClients`
- `totalTools`
- Per-client `connected / initialized / type`

It fits:

- Admin console status pages
- Post-startup self-checks
- Regression test assertions

But it is not a complete observability surface, because it does not include:

- Call volume per tool
- Latency distribution
- Failure rate
- The most recent exception cause

These kinds of metrics still need to be filled in with monitoring and logging around the gateway.

### Tool mapping snapshot: `getToolToClientMap()`

To see "which client each tool name currently lands on", use `getToolToClientMap()`:

```java
Map<String, String> mapping = gateway.getToolToClientMap();
// e.g.:
//   "search_repositories"                  -> "github"
//   "user_123_tool_query_weather"          -> "user_123_service_weather"
```

It returns a snapshot from `McpGatewayToolRegistry.snapshotMappings()` (`toolKey -> clientKey`) that covers both global and user-level tool keys. It corresponds to the same catalog as the `totalTools` in `getGatewayStatus()`, and is suitable for debugging, admin console display, or regression assertions. Note that it is a one-time snapshot, not a live view.

### User-level bulk cleanup: `clearUserMcpClients(userId)`

As the lifecycle counterpart to `addUserMcpClient(...)` / `removeUserMcpClient(...)`, `clearUserMcpClients(userId)` disconnects **all** MCP clients for a given user in one shot:

```java
gateway.clearUserMcpClients("u1001").join();
// Equivalent to: find all clientKeys starting with "user_u1001_service_",
// then removeMcpClientInternal(...) on each (disconnect + refresh the catalog)
```

Its real behavior is:

1. Filter out all of that user's clientKeys by the `user_{userId}_service_` prefix
2. Walk `removeMcpClientInternal(...)` on each one: remove from `mcpClients` -> `client.disconnect()` -> `toolRegistry.refresh(...)`
3. Return a log line with the cleanup count

Applicable scenarios: user logout, tenant cancellation, session-expiry reclamation. A single client failing to remove only logs and does not abort the whole batch. Like a single `removeUserMcpClient(...)`, it only cleans user-**specific** clients; global shared services are unaffected.

## 10. The real layering when integrating with an Agent

The recommended structure is:

1. The host initializes `McpGateway` at startup
2. The Agent explicitly declares `mcpServices` at build time
3. At runtime, `ToolUtil` completes remote calls through the gateway

```java
Agent agent = Agents.react()
        .toolRegistry(Collections.<String>emptyList(), Arrays.asList("github", "filesystem"))
        .build();
```

The key point in one sentence:

- The gateway solves "how services are managed"
- `toolRegistry(..., mcpServices)` solves "which services to show the model this time"

The two are not the same thing.

## 11. When to use it, and when not to

Scenarios suited to bringing in `McpGateway`:

- More than one service
- Need to uniformly manage multiple transports
- Need user-level isolation
- Need dynamic start/stop and config-source governance

Scenarios where you don't need to start with the gateway:

- Only connecting a single MCP
- Just verifying whether a transport can connect
- Haven't yet got the most basic `connect -> tools/list -> tools/call` working

## 12. The conclusion you should take away from this page

`McpGateway` is not a "helper class" in AI4J; it is the MCP multi-service governance layer itself.

It collapses:

- Connection lifecycle
- The tool catalog
- User isolation
- Config source hot-reload

into a proper runtime; but it does not solve every problem. Things like naming-conflict governance, failure circuit-breaking, and audit metrics still need to be filled in at the platform layer.
