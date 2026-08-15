---
sidebar_position: 1
title: "MCP Overview"
description: "AI4J builds MCP as a capability-wiring subsystem spanning four planes — client, transport, gateway, and server — rather than a single tool integration option. This page gives the overall map and a recommended reading order."
tags: [concept]
---

# MCP Overview

In AI4J, MCP is not "an option for attaching tools" — it is a complete, standalone capability-wiring layer.

Viewed purely conceptually, it is of course the Model Context Protocol; but from the way this code is organized, it carries at least four responsibilities at once:

- Connect to a single remote or local MCP server
- Manage multiple MCP clients and tool mappings
- Publish Java-side capabilities as an MCP server
- Bridge MCP capabilities back into the Agent / Tool system

So this chapter is really about not "what MCP is" but rather "how AI4J ships MCP as a connectable, governable, publishable, reusable engineering layer."

## 1. Place MCP back into the overall architecture first

Where MCP sits in the repository is not an implementation detail of some tool; it belongs to the connection layer for AI capabilities.

Split by responsibility, you can read it this way:

- `ai-basics`
  covers model services, request protocols, service registry
- `mcp`
  covers standardized external capability wiring and publishing
- `agent`
  covers the reasoning loop, handoff, team, memory
- `coding-agent`
  covers the engineering host, workspace, regression and approval chains

In one sentence:

- `MCP` owns "how capabilities are connected in / published out"
- `Agent` owns "how the model uses these capabilities"

## 2. This subsystem actually splits into four planes

To understand MCP in AI4J, don't fixate on individual class names first — separate the four planes first.

### 2.1 Single-service client plane

Core objects:

- `McpClient`
- `McpTransport`

It connects to a concrete MCP server and provides high-level calls:

- `tools/list`
- `tools/call`
- `resources/list`
- `resources/read`
- `prompts/list`
- `prompts/get`

### 2.2 Transport plane

Core objects:

- `TransportConfig`
- `McpTransportFactory`
- `StdioTransport`
- `SseTransport`
- `StreamableHttpTransport`

It concretizes "connecting to an MCP server" into:

- local process
- SSE long-lived connection
- Streamable HTTP

### 2.3 Gateway governance plane

Core objects:

- `McpGateway`
- `McpGatewayToolRegistry`
- `McpGatewayConfigSourceBinding`
- `McpGatewayKeySupport`

It handles multi-service management, not just "spinning up a few more clients".

The key capabilities here include:

- global and user-level client isolation
- tool-name to client mapping
- dynamic config source loading
- unified tool-call entry point

### 2.4 Server publishing plane

Core entry points:

- `@McpService`
- `@McpTool`
- `@McpResource`
- `@McpPrompt`
- `McpServerFactory`

It solves the reverse-direction problem:

- not "connecting to someone else's MCP"
- but rather "publishing your own Java capabilities as MCP"

## 3. `McpClient` is not a thin HTTP wrapper

When reading the source, `McpClient` is the easiest to underestimate. It does more than just send messages out.

### 3.1 `connect()` depends on the transport profile

`connect()` starts the transport first, with a `30s` timeout; the subsequent protocol lifecycle depends on the transport:

- By default, `StreamableHttpTransport` uses `AUTO`. It first fires only the modern `server/discover`; a valid discovery result selects the stateless `2026-07-28`, in which case `connect()` does not send `initialize`.
- AUTO falls back to initialization-era Streamable HTTP only on unrecognized `400`, `404`, or `405`. It does not downgrade on auth failure or on recognizable modern errors, and it cannot auto-detect deprecated HTTP+SSE.
- STDIO, an explicit `sse` transport, and clients that explicitly select the legacy Streamable HTTP profile keep the session-era flow: `initialize`, capability negotiation, `notifications/initialized`.

Therefore, `isInitialized()` means the client is ready to call, and should not be read as "all transports have completed an initialization handshake". For protocol details and upgrade paths for modern HTTP, see [Streamable HTTP](/docs/capabilities/mcp/streamable-http).

### 3.2 It caches by default

`McpClient` caches:

- `availableTools`
- `availableResources`
- `availablePrompts`

This is a client-side capability-directory cache; it does not mean modern Streamable HTTP created a session at the protocol level. Modern HTTP requests themselves remain stateless.

### 3.3 It does heartbeat and reconnect by default

In the current implementation:

- Networked transports usually have `needsHeartbeat() = true`
- the client starts a low-frequency heartbeat check every 10 minutes
- `autoReconnect = true` by default
- after a disconnect, it attempts reconnect after 5 seconds

This shows the MCP client in AI4J already carries baseline connection governance, rather than leaving stability entirely to the upper-layer caller.

## 4. The gateway is the real center of "multi-service MCP"

Many systems claim to support multiple MCPs but in practice only maintain a client list. AI4J's implementation does not.

The point of `McpGateway` is that it elevates multi-service management into an independent runtime.

### 4.1 It manages not just clients but key rules

In the current source, there are at least two classes of key:

- global client: `serviceId`
- user client: `user_{userId}_service_{serviceId}`

Tool mapping also has two classes of key:

- global tool: `toolName`
- user tool: `user_{userId}_tool_{toolName}`

This means user isolation is not bolted on with extra annotations — it is baked directly into the key rules.

### 4.2 It has a standalone tool registry

`McpGatewayToolRegistry`:

- pulls the tool list from every connected client
- builds the tool -> client mapping
- caches the available tool list

This makes "which MCP service a tool came from" no longer something you have to keep in your head.

### 4.3 It supports config sources, not just config files

`McpGateway` can initialize from the default `mcp-servers-config.json`, and also supports:

- `McpConfigSource`

This means it can evolve into dynamic config management instead of being stuck with local static JSON.

## 5. The tool exposure layer is not "wide open" — it converges explicitly

This is one of the most important security boundaries in this chapter.

`ToolUtil` currently bridges:

- built-in tool
- legacy `Function` tools
- local MCP tools
- remote MCP services

But the exposure semantics are not "hand everything to the model at once".

### 5.1 The real semantics of `getAllTools(functionList, mcpServerIds)`

It only merges:

- the function list you pass in explicitly
- the MCP server list you pass in explicitly

In other words, in a normal Agent scenario it does not expose all local MCP tools by default.

### 5.2 `getLocalMcpTools()` carries different semantics

This method returns the locally scanned `@McpService` / `@McpTool` capabilities, which is better suited to service-publishing or local-exposure scenarios.

So:

- `getAllTools(...)` leans toward a consumer-side allowlist
- `getLocalMcpTools()` leans toward local MCP capability enumeration

### 5.3 The invocation priority is a separate concern

The current priority order in `ToolUtil.invoke(...)` is roughly:

1. built-in tool
2. user-level MCP tools
3. local MCP tools
4. legacy Function tools
5. global MCP gateway tools

This shows "exposure-surface selection" and "priority at actual invocation time" are two separate logics — they cannot be conflated into one sentence.

## 6. Who this chapter fits best

### 6.1 You just want to connect a third-party MCP

Focus on:

- `McpClient`
- transport

### 6.2 You need to connect multiple MCPs and govern them

Focus on:

- `McpGateway`
- config source
- tool registry

### 6.3 You want to publish your own Java capabilities

Focus on:

- MCP annotations
- server factory

### 6.4 You are wiring up an Agent / Coding Agent

Focus on:

- Tool exposure boundary
- the bridging semantics between Gateway and ToolUtil

## 7. Recommended reading order

1. [MCP usage paths and scenario selection](/docs/capabilities/mcp/use-cases-and-paths)
2. [MCP transport types in depth](/docs/capabilities/mcp/transport-types)
3. [MCP Client integration (single-service mode)](/docs/capabilities/mcp/client-integration)
4. [Tool exposure semantics and security boundary](/docs/capabilities/mcp/tool-exposure-semantics)
5. [MCP Gateway management](/docs/capabilities/mcp/gateway-management)
6. [Build and publish an MCP Server](/docs/capabilities/mcp/build-your-mcp-server)

If you remember just one sentence:

MCP in AI4J is not "a tool-list protocol" — it is a capability-wiring subsystem spanning four planes: client, transport, gateway, and server.
