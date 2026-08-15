---
sidebar_position: 4
title: "Integrating Third-Party MCP (All Approaches)"
description: "Breaks down the 5 integration depths for third-party MCP in AI4J: single-client direct connection, config-driven gateway, runtime dynamic add/remove, user-level isolation, and the Agent exposure allowlist, with common pitfalls."
tags: [integration]
---

# Integrating Third-Party MCP (All Approaches)

This page does more than list "a few ways to connect" — it unpacks the integration tiers for third-party MCP in AI4J.

What you are really choosing is not a single API but one of 5 integration depths:

1. Single-client direct connection
2. Config-driven multi-service gateway
3. Runtime dynamic add/remove
4. User-level isolation
5. Wiring onto the Agent request chain

Different depths solve different problems. Don't conflate them as "just plugging in an MCP."

:::note Streamable HTTP peer profile

`streamable_http` defaults to `AUTO`, and the Gateway JSON supports `protocolProfile`. AUTO uses only the modern `server/discover` probe for Streamable HTTP: a successful discovery result takes the stateless `2026-07-28` profile, and only an unrecognized `400`, `404`, or `405` falls back to the initialization-era profile. It is not omniscient detection, and it will not automatically select the deprecated HTTP+SSE; the latter must be configured explicitly with `type: "sse"`. The STDIO examples on this page still follow the session-era flow, so they use `initialize`. See [Streamable HTTP](/docs/capabilities/mcp/streamable-http).

:::

## 1. Pick the Integration Tier First, Don't Write Code First

| Integration approach | Problem it solves | Suitable scenarios |
| --- | --- | --- |
| `McpClient` direct connection | Get one service working | Validation, prototyping, small tools |
| `McpGateway.initialize(...)` | Unified onboarding of multiple services | Service count starts growing |
| `addMcpClient/removeMcpClient` | Runtime hot-plug | Platform-style governance |
| `addUserMcpClient(...)` | Per-user or per-tenant isolation | SaaS, multi-account |
| `toolRegistry(..., mcpServices)` | Let an Agent consume specific services | Wiring into the reasoning path |

The recommended order is always:

1. Validate connectivity with a single client first
2. Then decide whether to introduce a gateway
3. Only then wire it into the Agent

## 2. Approach 1: Single `McpClient` Direct Connection

This is the minimal closed loop and the starting point for all troubleshooting.

```java
McpTransport transport = McpTransportFactory.createTransport(
        "stdio",
        TransportConfig.stdio("npx", Arrays.asList("-y", "@modelcontextprotocol/server-github"))
);

McpClient client = new McpClient("github-client", "1.0.0", transport);
client.connect().join();

List<McpToolDefinition> tools = client.getAvailableTools().join();
String result = client.callTool("search_repositories", Collections.singletonMap("q", "ai4j")).join();

client.disconnect().join();
```

What this **STDIO** example actually runs:

1. transport starts
2. `initialize`
3. `notifications/initialized`
4. `tools/list`
5. `tools/call`

As long as this chain isn't stable yet, don't rush to `McpGateway`.

## 3. Approach 2: Config-Driven Integration of Multiple Third-Party Services

Once the service count exceeds one, you shouldn't hand-write multiple `McpClient` instances in business code.

Config example:

```json
{
  "mcpServers": {
    "github": {
      "type": "stdio",
      "command": "npx",
      "args": ["-y", "@modelcontextprotocol/server-github"],
      "enabled": true
    },
    "weather-http": {
      "type": "streamable_http",
      "url": "http://127.0.0.1:8000/mcp",
      "protocolProfile": "AUTO",
      "enabled": true
    }
  }
}
```

Initialization:

```java
McpGateway gateway = new McpGateway();
gateway.initialize("mcp-servers-config.json").join();

List<Tool.Function> tools = gateway.getAvailableTools().join();
```

Two real boundaries to understand here:

- The gateway aggregates multiple third-party services into a single catalog
- It does not automatically resolve same-name tool conflicts for you

If two third-party servers both export names like `search`, the current mapping overwrites.

## 4. Approach 3: Runtime Dynamic Add/Remove

This is the integration depth only "platform mode" needs.

```java
McpClient githubClient = new McpClient("github", "1.0.0", githubTransport);
gateway.addMcpClient("github", githubClient).join();

gateway.removeMcpClient("github").join();
```

Suitable for:

- Console-driven service toggles
- Dynamically trying out a new MCP
- Fault isolation / traffic draining

The real semantics are not "a put into a map":

- add connects the client first, then refreshes the entire tool catalog
- remove disconnects first, then refreshes the entire catalog

So this kind of integration requires you to plan ahead for catalog-refresh cost and conflict governance.

## 5. Approach 4: User-Level Isolation Integration

If the third-party MCP isn't globally shared but rather "each user has their own set of credentials or services," you need user-level integration.

```java
McpClient userClient = new McpClient("user-github", "1.0.0", transport);
gateway.addUserMcpClient("u123", "github", userClient).join();

String result = gateway.callUserTool(
        "u123",
        "search_repositories",
        Collections.singletonMap("q", "ai4j")
).join();
```

The internal key rules are:

- User client: `user_{userId}_service_{serviceId}`
- User tool: `user_{userId}_tool_{toolName}`

The current default behavior is:

- Look up user-level tools first
- Fall back to global tools if not found

:::warning User-level falls back to global tools by default
If your permission model requires "a user with no configuration must never invoke a globally shared service," you must disable the fallback yourself at the business layer.
:::

## 6. Approach 5: Exposing Third-Party MCP to an Agent

The key here is not "being able to reach the gateway" but "exposing only the services the current task actually needs to the model."

```java
McpGateway gateway = McpGateway.getInstance();
gateway.initialize("mcp-servers-config.json").join();

Agent agent = Agents.react()
        .modelClient(modelClient)
        .model("doubao-seed-1-8-251228")
        .toolRegistry(Collections.<String>emptyList(), Arrays.asList("github"))
        .build();
```

The core semantics of this chain are:

- `McpGateway` is only the service catalog
- `toolRegistry(..., mcpServices)` is what decides the services visible to the current request

Currently `ToolUtil.getAllTools(functionList, mcpServerIds)` merges only:

- The explicitly passed local function list
- The explicitly passed MCP service list

It does not automatically expose all remote MCPs to the model.

## 7. The Complete Runtime Chain of Third-Party MCP in AI4J

If you draw the whole chain as a single line, it is:

1. `McpServerConfig` or a custom config source defines services
2. `McpGatewayClientFactory` creates the transport + client based on type
3. `McpClient.connect()` completes the lifecycle matching the profile: AUTO discovers a modern peer or enters the legacy initialization handshake
4. `McpGatewayToolRegistry` collects the tool inventory
5. The Agent selects the exposure surface for this call via `mcpServices`
6. The model triggers a tool call
7. `ToolUtil.invoke(...)` routes the call to the gateway
8. The gateway locates the corresponding client
9. The client issues `tools/call`
10. The result returns to the model

As long as you know which step the problem is stuck on, troubleshooting won't get tangled.

## 8. The 4 Most Common Pitfalls When Onboarding Third-Party Services

### 8.1 Same-Name Tool Conflicts Across Services

The current gateway mapping is:

- Global tool: `toolName -> clientKey`

Not:

- `serviceId + toolName -> clientKey`

So when different services export tools with the same name, the conflict surfaces directly at runtime.

### 8.2 Treating the Gateway as a Permission System

The gateway handles connection and routing. It does not handle:

- Whether a given session is allowed to use a given service
- Who may connect to a given tenant's third-party credentials

Permission control should still be done at the business layer or session layer.

### 8.3 Assuming Every Config Field Takes Effect

The core fields that actually reach the transport/client today are mainly:

- `type`
- `command`
- `args`
- `env`
- `url`
- `headers`
- `protocolProfile` (Streamable HTTP only)

Fields like `priority`, `tags`, and `requiresAuth` are closer to governance metadata and do not automatically change invocation behavior.

### 8.4 Wiring Into the Agent Before Validating Connectivity

The correct order is:

1. Get `McpClient` working standalone
2. Then enter the gateway
3. Only then enter the Agent

## 9. Recommended Adoption Strategy

### Small projects

- Start with direct `McpClient`
- Don't introduce heavy governance too early when services are few

### Medium projects

- Route everything through `McpGateway`
- Lock down serviceId and toolName conventions

### Platform projects

- `McpGateway + McpConfigSource`
- Dynamic add/remove
- Audit logs
- User-level isolation
- Explicit exposure allowlist

## 10. The Conclusion Most Worth Remembering From This Page

Third-party MCP integration in AI4J is not a single API — it is a layered chain running from connection, to governance, to isolation, to Agent exposure.

First separate "connectivity problems" from "governance problems," then separate "governance problems" from "model-visibility problems," and your integration plan will become much clearer.
