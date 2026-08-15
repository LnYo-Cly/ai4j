---
sidebar_position: 5
title: "MySQL Dynamic MCP Service Management"
description: "AI4J does not ship a built-in MySQL configuration center, but it reserves the McpConfigSource and McpGatewayConfigSourceBinding extension points; this page covers how to implement a database configuration source and round out naming, secret, and audit governance."
tags: [integration]
---

# MySQL Dynamic MCP Service Management

This page has to state a boundary first:

> AI4J currently does not ship a ready-made "MySQL MCP configuration center", but it has already reserved this extension point.

The actual extension points are:

- `McpConfigSource`
- `McpGatewayConfigSourceBinding`

In other words, MySQL dynamic management is not an out-of-the-box switch, but rather "a database implementation built on top of the existing configuration source SPI".

## 1. Why this capability deserves its own design

A static `mcp-servers-config.json` is well suited to:

- Local development
- Single-service validation
- A small number of fixed services

Once you enter these scenarios, file configuration starts to struggle:

- Adding a service requires a release
- Disabling a faulty service is slow to take effect
- No audit trail or operator record
- Hard to do tenant isolation

What you really need then is not "put the JSON in a database", but rather:

- A hot-reloadable configuration source
- Auditable changes
- Rollbackable service switches

## 2. The code-level skeleton that is actually reusable

The current repository already provides 3 key pieces:

### `McpConfigSource`

This is the configuration source SPI, which requires you to implement:

- `getAllConfigs()`
- `getConfig(serverId)`
- `addConfigChangeListener(...)`
- `removeConfigChangeListener(...)`

And to notify via listeners on:

- `onConfigAdded`
- `onConfigRemoved`
- `onConfigUpdated`

### `FileMcpConfigSource`

This is the default file-based implementation. Its value is not "being a file", but that it provides a complete reference:

- Load all configurations
- Diff against the old snapshot and the new snapshot
- Emit add/remove/update events

### `McpGatewayConfigSourceBinding`

This is the most critical bridge layer. It translates configuration source events into actual gateway actions:

- Added -> create client -> `gateway.addMcpClient(...)`
- Updated -> rebuild client -> `gateway.addMcpClient(...)`
- Removed -> `gateway.removeMcpClient(...)`

So when you build MySQL dynamic configuration, what you really need to fill in is "the database configuration source", not rewrite the entire gateway.

## 3. Recommended data modeling

A minimal table structure must at least be able to express the following kinds of information:

```sql
CREATE TABLE mcp_service_config (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  service_id VARCHAR(128) NOT NULL,
  transport_type VARCHAR(32) NOT NULL,
  command_text VARCHAR(255) NULL,
  args_json TEXT NULL,
  url VARCHAR(512) NULL,
  headers_json TEXT NULL,
  env_json TEXT NULL,
  enabled TINYINT NOT NULL DEFAULT 1,
  version BIGINT NOT NULL,
  tenant_id VARCHAR(128) NULL,
  operator VARCHAR(128) NULL,
  remark VARCHAR(512) NULL,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);
```

Why we do not recommend just storing a single `config_json`:

- Querying and auditing are inconvenient
- Field-level validation is hard
- The admin console cannot easily do structured editing

A more robust approach is:

- Store core runtime fields in a structured way
- Put supplementary extension fields into JSON

## 4. Not every field is worth putting in the database

Based on the current AI4J runtime, the fields most worth persisting and that actually take effect are:

- `service_id`
- `type`
- `command`
- `args`
- `env`
- `url`
- `headers`
- `enabled`

Whereas these fields are currently more like governance metadata:

- `priority`
- `tags`
- `requiresAuth`
- `authTypes`
- `autoReconnect`
- `reconnectInterval`
- `maxReconnectAttempts`
- `connectTimeout`

This does not mean they have no value, only that the current runtime does not wire all of them up. If the MySQL backend exposes them as switches that "instantly change underlying behavior", it will mislead users.

## 5. Recommended implementation: a custom `McpConfigSource`

A typical MySQL configuration source can look like this:

```java
public class MysqlMcpConfigSource implements McpConfigSource {

    private final List<ConfigChangeListener> listeners = new CopyOnWriteArrayList<>();
    private volatile Map<String, McpServerConfig.McpServerInfo> cache = new HashMap<>();

    @Override
    public Map<String, McpServerConfig.McpServerInfo> getAllConfigs() {
        return new HashMap<>(cache);
    }

    @Override
    public McpServerConfig.McpServerInfo getConfig(String serverId) {
        return cache.get(serverId);
    }

    @Override
    public void addConfigChangeListener(ConfigChangeListener listener) {
        listeners.add(listener);
    }

    @Override
    public void removeConfigChangeListener(ConfigChangeListener listener) {
        listeners.remove(listener);
    }

    public void reloadFromDatabase() {
        Map<String, McpServerConfig.McpServerInfo> oldSnapshot = new HashMap<>(cache);
        Map<String, McpServerConfig.McpServerInfo> newSnapshot = loadEnabledRows();
        cache = newSnapshot;
        diffAndNotify(oldSnapshot, newSnapshot);
    }
}
```

The point of this path is not the amount of code, but two things:

- Always treat the "enabled configuration set" as the set of effective services
- After every reload, do a diff and then emit events

## 6. How changes actually enter the gateway

Just bind the MySQL configuration source to the gateway:

```java
MysqlMcpConfigSource source = new MysqlMcpConfigSource(...);
McpGateway gateway = new McpGateway();
gateway.setConfigSource(source);
gateway.initialize().join();
```

After that, as long as `source.reloadFromDatabase()` emits the correct add/remove/update events, `McpGatewayConfigSourceBinding` will automatically:

- Build the client
- Wire it into the gateway
- Refresh the tool catalog
- Take the old client offline

This is also why we say MySQL dynamic configuration is "extending the configuration source", not "extending the gateway".

## 7. Two common ways to listen for database changes

### Polling

- Query `updated_at` or a version number on a schedule
- Compare snapshots
- Trigger a diff

Pros:

- Simple
- Easy to implement

Cons:

- Has latency
- High-frequency polling puts load on the database

### Event-driven

- Pair with binlog / CDC / MQ / admin console events
- Notify the configuration source to refresh in a targeted way

Pros:

- Low latency
- A clearer change chain

Cons:

- Higher implementation complexity

If you are just getting the platform capability off the ground, polling is good enough.

## 8. Five governance points you must round out in the design

### Naming governance

`serviceId` and tool names must not collide, otherwise the gateway's catalog mapping will overwrite each other.

### Secret governance

:::danger Do not store real tokens in plaintext
Do not persist real tokens long-term in plaintext inside `headers_json`.
:::

A more robust approach is:

- Store key references in the database
- Decrypt at runtime, or inject them from a secret service

### Audit governance

At minimum, record:

- Who changed which service
- Which fields were changed
- When it took effect
- Whether it was rolled back

### Rollback governance

We recommend keeping historical configuration versions, not just the latest one.

### Effectiveness governance

When updating a third-party MCP, it is best to first:

1. Validate the configuration
2. Probe connectivity
3. Then cut it into the production gateway

Do not let invalid configurations directly contaminate the runtime.

## 9. Relationship with the Agent

Even if the service source switches to MySQL, the Agent's exposure semantics do not change.

The Agent still only selects services visible for this run via:

```java
.toolRegistry(Collections.<String>emptyList(), Arrays.asList("weather-http"))
```

That is to say:

- MySQL governs "how the service catalog changes"
- The Agent allowlist governs "which services this task sees"

## 10. Recommended migration strategy

When migrating from static JSON to MySQL, a two-phase approach is recommended:

### Phase 1: Dual-source comparison

- File configuration keeps working
- MySQL configuration is used only for comparison and admin console validation

### Phase 2: Switch to the primary database

- The gateway rebinds to the MySQL `McpConfigSource`
- File configuration is kept only as a fallback
- After an observation period, drop the file source entirely

The benefit of this approach is:

- You do not stack connectivity issues, configuration issues, and platform issues all on top of each other at once

## 11. The conclusion to remember from this page

AI4J currently has no ready-made MySQL MCP configuration center, but it has already prepared the key slots for implementing this capability:

- `McpConfigSource`
- `McpGatewayConfigSourceBinding`

So the correct approach is not to hack the gateway, but to add a database configuration source, and design change governance, secret management, audit, and rollback into it together.
