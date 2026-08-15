---
title: "Protocol Capabilities"
description: "Explains the AI4J MCP protocol surface: the server supports three capability types (tools/resources/prompts) plus list_changed notifications, the legacy profile requires an initialize handshake while modern Streamable HTTP is stateless, and the transport affects the capability boundary."
tags: [concept]
---

# Protocol Capabilities

If you read MCP as nothing more than `tools/call`, you are only seeing the thinnest layer.

The MCP protocol surface in the current AI4J implementation covers at least:

- Modern stateless Streamable HTTP versus legacy initialization negotiation
- Tool capability
- Resource capability
- Prompt capability
- list_changed notifications
- Session differences bound to transport/profile

The real protocol entry points are:

- `mcp/server/McpServerEngine.java`
- `mcp/client/McpClient.java`

## 1. Which protocol methods the server actually supports

`McpServerEngine.processMessage(...)` currently handles directly:

- `server/discover`
- `tools/list`
- `tools/call`
- `resources/list`
- `resources/read`
- `prompts/list`
- `prompts/get`
- `ping`

For the legacy transport it also handles:

- `initialize`
- `notifications/initialized`

This is enough to make one point clear:

- MCP in AI4J is not "remote Tool RPC"
- Tool is just one capability surface

## 2. Initialization belongs only to the legacy profile

Under STDIO, SSE, or an explicit `LEGACY_2025_03_26` Streamable HTTP, `McpClient.initialize()` sends:

- `protocolVersion = 2025-03-26`
- `clientInfo`
- A set of client capabilities

It then also sends:

- `notifications/initialized`

The server-side `McpServerEngine.handleInitialize(...)` will:

1. Parse the protocol version requested by the client
2. Negotiate within the supported versions
3. Return server capabilities
4. Set `session.setInitialized(true)`

If the server for the given transport has `initializationRequired=true`, every subsequent call to:

- `tools/list`
- `tools/call`
- `resources/list`
- `resources/read`
- `prompts/list`
- `prompts/get`

first goes through `requireInitialization(...)`, otherwise it returns `-32002 Server not initialized`.

So initialization is not ceremonial — it is the gate for every later capability.

When Streamable HTTP is explicitly set to `MODERN_2026_07_28`, or after a default `AUTO` `server/discover` probe succeeds, this chain is not taken: there is no `initialize`, `notifications/initialized`, `Mcp-Session-Id`, or protocol session. Every `POST /mcp` carries `_meta`, `MCP-Protocol-Version`, and `Mcp-Method`, and when needed it also carries `Mcp-Name` and the schema-defined `Mcp-Param-*`. In this profile `isInitialized()` means "client ready", not "already handshaked".

`AUTO` sends only the modern `server/discover` probe; only an unrecognized HTTP `400`, `404`, or `405` selects the initialization-era Streamable HTTP and falls back to the initialization chain in this section. Authentication failures, recognized modern JSON-RPC errors, or a malformed discovery response do not trigger a downgrade, so AUTO is not a universal MCP server probe.

:::warning
Do not migrate legacy initialization state, session affinity, or capability declarations to the modern endpoint. See [Streamable HTTP](/docs/capabilities/mcp/streamable-http).
:::

## 3. What the server-returned capability looks like

`McpServerEngine.buildCapabilities()` builds the capability metadata for the Tool, Resource, and Prompt catalogs. The exact fields are interpreted per profile:

- `tools`
  - optional `listChanged`
- `resources`
  - `listChanged = true`
- `prompts`
  - `listChanged = true`

Whether `tools.listChanged` is `true` depends on the `toolsListChanged` flag passed in when constructing `McpServerEngine`.

:::warning
Old capability metadata may carry a `resources.subscribe` marker; it is not a promise that modern subscriptions are implemented. AI4J does not currently implement modern subscriptions or the MRTR flow, and modern clients do not claim to support them. Do not design interop flows around that marker.
:::

This shows that capabilities are not all fixed — they are shaped by the concrete server transport form.

## 4. How Tool capability lands in AI4J

The server-side paths for Tool capability are:

- `tools/list`
- `tools/call`

The `tools/list` chain ultimately:

1. Calls `ToolUtil.getLocalMcpTools()`
2. Converts the local `@McpService + @McpTool` scan results into `Tool`
3. Then converts them into `McpToolDefinition`

The `tools/call` path ultimately goes through:

`ToolUtil.invoke(toolName, JSON.toJSONString(arguments))`

There is a key execution fact here:

- The server-side MCP tool call chain still reuses AI4J's unified tool execution entry point
- So local MCP tools, classic function tools, and remote gateway tools share part of the execution dispatch logic

## 5. Resource capability is not "read-only Tool"

The protocol paths for Resource are:

- `resources/list`
- `resources/read`

The implementation depends on:

- `McpResourceAdapter.getAllMcpResources()`
- `McpResourceAdapter.readMcpResource(uri)`

Its core characteristics are:

- Uses URI templates rather than function names
- Matches `{param}` placeholders
- Extracts parameters from the URI, then invokes the resource method
- Ultimately returns `McpResourceContent`

This is better suited to expressing:

- Documents
- Configuration
- Read-only structured content

rather than action-style calls.

## 6. Prompt capability is not a plain string constant

The protocol paths for Prompt are:

- `prompts/list`
- `prompts/get`

The implementation depends on:

- `McpPromptAdapter.getAllMcpPrompts()`
- `McpPromptAdapter.getMcpPrompt(name, arguments)`

Its core characteristics are:

- Prompt names use `serviceName.promptName`
- Can declare an argument schema
- Support `required`
- Support `defaultValue`
- At execution time, arguments are injected into method parameters

This means Prompt is better suited to:

- Templated interaction fragments
- System prompts that need argument rendering
- Prompt content the server wants to standardize and reuse

## 7. Why the list_changed notifications matter

`McpClient` currently caches:

- The tool catalog
- The resource catalog
- The prompt catalog

And invalidates those caches when it receives these notifications:

- `notifications/tools/list_changed`
- `notifications/resources/list_changed`
- `notifications/prompts/list_changed`

This shows AI4J does not treat remote capabilities as a fully static catalog — it explicitly supports "re-fetch after a capability surface changes".

This matters a great deal if you are building a long-lived host.

## 8. Transport affects the capability boundary

The three server transports differ in the `McpServerEngine` constructor parameters.

### `StdioMcpServer`

- Supports only `2024-11-05`
- `initializationRequired = false`
- `pingEnabled = false`
- `toolsListChanged = false`

### `SseMcpServer`

- Supports only `2024-11-05`
- `initializationRequired = true`
- `pingEnabled = true`
- `toolsListChanged = false`

### `StreamableHttpMcpServer`

- Default `AUTO`
- AUTO accepts both modern stateless requests and initialization-era Streamable HTTP on the same `/mcp` endpoint; modern requests are identified by headers or `_meta`
- Modern requests use stateless `POST /mcp`, with no initialization handshake or protocol session, and validate modern metadata and required HTTP headers
- Modern requests support `server/discover` and response cache hints
- Does not support MRTR or subscriptions
- `MODERN_2026_07_28` can explicitly pin it to modern-only; a specific `LEGACY_*` profile can pin it to legacy-only
- Deprecated HTTP+SSE keeps an explicit `sse` transport, not an AUTO fallback

This means capability is not purely a business declaration — it is also bound to the transport session model.

## 9. Which part is most mature in the current implementation

By implementation completeness:

- Tool capability enters the request and execution main chain most directly
- Resource capability is structurally complete, suitable for publishing read-only content
- Prompt capability is also in shape, especially suited to templated prompts

But if you want to ship the most stable production path first, the usual move is to get Tool capability working end to end, then extend to Resource / Prompt.

## 10. Recommendations when designing capabilities

### Actions go in Tool

For example:

- Queries
- Writes
- External system operations

### Content goes in Resource

For example:

- Documents
- Configuration
- Version inventories
- Fixed-structure data

### Templates go in Prompt

For example:

- Parameterized prompts
- Standardized task instructions
- Interaction starter templates

Do not cram everything into Tool, or the protocol semantics and downstream governance will suffer.

## 11. Conclusion of this page

> MCP capability in AI4J is a combination of "profile-aware lifecycle + Tool / Resource / Prompt + list metadata", not a single remote tool call interface. Tool is just the most visible layer of the model execution chain — it is not the whole of MCP.
