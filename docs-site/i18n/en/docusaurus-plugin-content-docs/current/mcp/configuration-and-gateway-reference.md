---
sidebar_position: 3
title: "MCP Configuration and Gateway Reference"
description: "Distinguish which fields in mcp-servers-config.json actually flow into the transport/client/gateway runtime, and which are merely governance metadata — to avoid mistaking a field's presence for a capability being in effect."
tags: [reference]
---

# MCP Configuration and Gateway Reference

This page does not repeat "how to write the configuration file". Instead it clarifies a point that is frequently gotten wrong:

- Which fields are only configuration metadata
- Which fields actually flow into the transport / client / gateway runtime

If this distinction is not drawn clearly, the docs will misstate "the field exists" as "the feature is implemented".

## 1. First, tell the two `McpServerInfo` types apart

There are two very similarly named types in the repository:

- `io.github.lnyocly.ai4j.mcp.config.McpServerConfig.McpServerInfo`
  This is the runtime configuration object, deserialized from `mcp-servers-config.json`.
- `io.github.lnyocly.ai4j.mcp.entity.McpServerInfo`
  This is the protocol/presentation-layer service metadata object, containing only `name/version/description/...`.

This page discusses the former — the runtime configuration object that the configuration file actually deserializes into.

## 2. Top-level file structure

The default configuration file entry point is:

- `mcp-servers-config.json`

The structure is:

```json
{
  "mcpServers": {
    "github": {
      "type": "stdio",
      "command": "npx",
      "args": ["-y", "@modelcontextprotocol/server-github"],
      "enabled": true
    }
  }
}
```

A few key points:

- The top-level key must be `mcpServers`.
- Each child key is the `serverId`.
- The `serverId` flows directly into gateway registration, routing, and the Agent allowlist.

So `serverId` is not a comment field — it is the actual runtime primary key.

## 3. Which fields actually affect the runtime

The current creation chain in `McpGatewayClientFactory` is:

1. `McpTypeSupport.resolveType(serverInfo)`
2. `TransportConfig.fromServerInfo(serverInfo)`
3. `McpTransportFactory.validateConfig(...)`
4. `McpTransportFactory.createTransport(...)`
5. `new McpClient(serverId, clientVersion, transport)`

Tracing this chain, the core fields that actually flow into the transport/client are only:

- `type`
- `transport`
  Legacy field, kept only for compatibility.
- `command`
- `args`
- `env`
- `url`
- `headers`
- `protocolProfile` (only for `streamable_http` / `http`)

Plus, effective at the configuration-source layer:

- `enabled`

This is the single most important fact.

## 4. Field layering table

| Field | Actually participates in the runtime today | Notes |
| --- | --- | --- |
| `type` | Yes | Decides `stdio / sse / streamable_http` |
| `transport` | Yes, but compatibility only | Read by `McpTypeSupport.resolveType(...)` as a legacy field |
| `command` | Yes | Required for `stdio` |
| `args` | Yes | `stdio` arguments |
| `env` | Yes | `stdio` subprocess environment variables |
| `url` | Yes | Required for `sse / streamable_http` |
| `headers` | Yes | Remote HTTP/SSE request headers |
| `protocolProfile` | Yes, limited to `streamable_http` / `http` | Deserialized by `McpServerConfig.McpServerInfo`, then passed into the transport by `TransportConfig.fromServerInfo(...)`; defaults to `AUTO` when omitted |
| `enabled` | Yes | Used when the config source extracts the valid service set |
| `cwd` | No | Present in config, but `TransportConfig.fromServerInfo(...)` does not pass it down |
| `autoReconnect` | No | Not passed into the constructor when the gateway creates the `McpClient` |
| `reconnectInterval` | No | The current `McpClient` reconnect logic is hard-coded to 5 seconds and does not read this field |
| `maxReconnectAttempts` | No | No reconnect-attempt limit is wired in currently |
| `connectTimeout` | No | Present on the config object, but not mapped onto `TransportConfig` |
| `tags` | No | Does not participate in routing or filtering today |
| `priority` | No | Does not participate in conflict arbitration or ordering today |
| `version` | Indirect | Participates in the JSON comparison inside `FileMcpConfigSource`, thereby triggering updates |
| `createdTime` / `lastUpdatedTime` | Indirect | Same as above; leans toward governance metadata |
| `requiresAuth` | No | Metadata; does not auto-inject authentication |
| `authTypes` | No | Metadata; does not automatically change client behavior |

If you remember only one thing, remember this table.

## 5. The real relationship between `type` and `transport`

The recommended form is always:

- `type: "stdio"`
- `type: "sse"`
- `type: "streamable_http"`

`transport` is merely a legacy field for compatibility with old configurations.

`McpTypeSupport.normalizeType(...)` also accepts a few aliases:

- `process`
- `local`
- `server-sent-events`
- `event-stream`
- `http`
- `mcp`
- `streamable-http`
- `http-streamable`

But note:

- Unknown values are normalized back to `stdio`.

:::warning Unknown types silently fall back to stdio
This means that if you write the type incorrectly, it will not necessarily fail with an explicit error — it may simply be treated as `stdio` and keep going. Do not rely on this fault tolerance in production.
:::

## 6. The correct form for the three common configurations

### `stdio`

```json
{
  "mcpServers": {
    "github": {
      "type": "stdio",
      "command": "npx",
      "args": ["-y", "@modelcontextprotocol/server-github"],
      "env": {
        "GITHUB_TOKEN": "${GITHUB_TOKEN}"
      },
      "enabled": true
    }
  }
}
```

What actually takes effect today:

- `command`
- `args`
- `env`

`cwd` has a field, but it currently does not flow into `StdioTransport`.

### `sse`

```json
{
  "mcpServers": {
    "remote-sse": {
      "type": "sse",
      "url": "http://127.0.0.1:8080/sse",
      "headers": {
        "Authorization": "Bearer ${TOKEN}"
      },
      "enabled": true
    }
  }
}
```

What actually takes effect today:

- `url`
- `headers`

Here `type: "sse"` is the explicit selection of the deprecated HTTP+SSE transport. Do not write it as `streamable_http` and expect AUTO to switch to SSE.

### `streamable_http`

```json
{
  "mcpServers": {
    "weather-http": {
      "type": "streamable_http",
      "url": "http://127.0.0.1:8000/mcp",
      "protocolProfile": "AUTO",
      "headers": {
        "Authorization": "Bearer ${TOKEN}"
      },
      "enabled": true
    }
  }
}
```

`http` is still accepted, but semantically it has been normalized to `streamable_http`.

### Streamable HTTP protocol profile

`protocolProfile` is now supported by `McpServerConfig.McpServerInfo`. The configuration-driven Gateway passes it to the Streamable HTTP transport; when omitted it defaults to `AUTO`, and the `http` alias uses the same semantics.

- `AUTO` probes Streamable HTTP only via the modern `server/discover`: only an unrecognized `400`, `404`, or `405` triggers the initialization-era fallback.
- `MODERN_2026_07_28` disables legacy fallback, suitable for peers already known to be modern.
- `LEGACY_2025_11_25`, `LEGACY_2025_06_18`, and `LEGACY_2025_03_26` pin the corresponding initialization-era profile.

This is not infallible auto-detection: authentication failures, recognizable modern JSON-RPC errors, or unexpected successful responses will not be downgraded. The deprecated HTTP+SSE transport must still be configured with `type: "sse"`. For the full behavior, see [Streamable HTTP](/docs/mcp/streamable-http).

## 7. `enabled` is the real on/off field

`FileMcpConfigSource.loadConfigs()` calls `McpConfigIO.extractEnabledConfigs(serverConfig)`, that is:

- Only enabled configurations enter the valid configuration set.
- After `reloadConfigs()`, added/removed/updated events are all computed against the "enabled configuration set".

This means setting a service to `enabled: false` does not "keep the configuration but mark it deactivated". Rather, it:

- Removes the service from the current valid service set.
- Causes the gateway to take the corresponding client offline upon receiving the removal event.

## 8. Why fields like `autoReconnect` must not be oversold right now

These are the fields most easily overstated in the docs:

- `autoReconnect`
- `reconnectInterval`
- `maxReconnectAttempts`
- `connectTimeout`

They do exist on the configuration class, but when the gateway creates the client today:

- `autoReconnect` is not passed to `McpClient`.
- `reconnectInterval` / `maxReconnectAttempts` are not applied to reconnect scheduling.
- `connectTimeout` is not mapped from config onto `TransportConfig`.

The actual runtime behavior is:

- `McpClient` defaults to `autoReconnect = true`.
- After a disconnect, it attempts a reconnect after a fixed 5 seconds.
- Inside `connect()`, the transport startup timeout is fixed at 30 seconds; the initialization handshake for the legacy profile also uses this timeout.

So if you write these fields in the configuration file, you should currently read them as:

- Platform governance metadata
- Reserved for future extension

Rather than "runtime parameters that are already fully wired".

## 9. Do not conflate `headers` with authentication metadata

### `headers`

This is the injection point that actually participates in authentication for remote requests today.

Use cases include:

- `Authorization`
- API key
- Tenant identifier

### `requiresAuth` / `authTypes`

These two fields are currently better suited for:

- Backend UI display
- Configuration validation
- Audit tagging

They do not auto-generate request headers, nor do they auto-trigger a login flow.

## 10. Three common ways to initialize the gateway

### Read the configuration file directly

```java
McpGateway gateway = new McpGateway();
gateway.initialize("mcp-servers-config.json").join();
```

### Bind a configuration source first

```java
McpConfigSource source = new FileMcpConfigSource("mcp-servers-config.json");
McpGateway gateway = new McpGateway();
gateway.setConfigSource(source);
gateway.initialize().join();
```

### Dynamic injection at runtime

```java
gateway.addMcpClient("github", client).join();
gateway.removeMcpClient("github").join();
```

These three approaches are not redundant features — they represent three tiers of governance:

- Static configuration
- Abstract configuration source
- Full platform-grade hot-plugging

## 11. Configuration source SPI and built-in implementations

The abstract configuration source is defined by the `McpConfigSource` interface, and is a prerequisite for the gateway to take "mode B / mode C". Its contract has only four parts:

- `getAllConfigs()` / `getConfig(serverId)`
- `addConfigChangeListener(...)` / `removeConfigChangeListener(...)`
- `ConfigChangeListener` callbacks: `onConfigAdded` / `onConfigRemoved` / `onConfigUpdated`

Once `McpGateway` binds a configuration source via `setConfigSource(...)`, `McpGatewayConfigSourceBinding` translates the three event types above into runtime actions (see [Gateway Management - Hot configuration updates](/docs/mcp/gateway-management)): add/update → create the client and wire it in; remove → disconnect and take offline.

### Built-in implementation 1: `FileMcpConfigSource`

A file-based configuration source that loads from `mcp-servers-config.json`. It only offers a manual `reloadConfigs()`; it does not watch the filesystem for changes. To react to external modifications, you must call reload on a timer yourself, or swap in a different implementation.

### Built-in implementation 2: `McpConfigManager` (in-memory)

`McpConfigManager` is an out-of-the-box **purely in-memory** `McpConfigSource` implementation, suitable for:

- Programmatic dynamic registration of MCP services (admin consoles, multi-tenant platforms)
- Constructing a configuration source inside unit tests
- Serving as the building block for custom storage (database / Redis / configuration center)

Its characteristics:

- Internally uses a `ConcurrentHashMap` for configurations and a `CopyOnWriteArrayList` for listeners; thread-safe.
- `addConfig(serverId, info)` adds; for an existing key it fires `onConfigUpdated`, otherwise `onConfigAdded`.
- `removeConfig(serverId)` deletes and fires `onConfigRemoved`.
- `updateConfig(...)` is equivalent to `addConfig(...)`.
- `hasConfig(serverId)` / `getConfig(serverId)` / `getAllConfigs()` for queries.
- `validateConfig(info)` validates: `name` is non-empty, the transport type is recognizable, and `TransportConfig` is valid; an invalid configuration returns `false` instead of throwing.

Minimal usage:

```java
McpConfigManager source = new McpConfigManager();

McpServerConfig.McpServerInfo github = new McpServerConfig.McpServerInfo();
github.setName("github");
github.setType("stdio");
github.setCommand("npx");
github.setArgs(Arrays.asList("-y", "@modelcontextprotocol/server-github"));
source.addConfig("github", github);          // fires onConfigAdded

source.updateConfig("github", updatedInfo);   // fires onConfigUpdated
source.removeConfig("github");                // fires onConfigRemoved

// Bind to the gateway: events automatically drive client add/remove
McpGateway gateway = new McpGateway();
gateway.setConfigSource(source);
gateway.initialize().join();
```

:::note It is only an in-memory implementation
`McpConfigManager` loses its state on process restart and performs no persistence on its own. When you need to persist to a store, implement `McpConfigSource` yourself (you can borrow its listener-notification pattern), or wrap a persistence layer around it.
:::

## 12. How the Agent side references these configurations

The `serverId` registered in the configuration file must ultimately be listed in the Agent's `mcpServices` allowlist:

```java
.toolRegistry(Collections.<String>emptyList(), Arrays.asList("github", "filesystem"))
```

There is only one key point:

- "The service is registered with the gateway" does not equal "it is visible by default for this request".

## 13. The conclusion most worth remembering from this page

In the current AI4J implementation, configuration fields fall into two layers:

- One layer of runtime fields that actually flow into the transport/client/gateway.
- One layer of metadata fields reserved for governance, audit, and future extension.

Documentation must stay faithful to this boundary — otherwise "an object field exists" gets miswritten as "the runtime capability is in effect".
