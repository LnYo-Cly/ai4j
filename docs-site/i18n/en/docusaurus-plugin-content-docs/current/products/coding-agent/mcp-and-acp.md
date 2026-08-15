---
title: "MCP and ACP"
description: "Clarify the two completely different boundaries inside the Coding Agent: MCP (bring external capabilities into the model's tool surface) and ACP (expose the coding session as a protocol to hosts), and how both take effect in the same session."
tags: [concept]
---

# MCP and ACP

`MCP` and `ACP` show up together so often in the Coding Agent that they are easily miswritten as "two names for the same wiring mechanism".
From the source, they actually sit on two completely different boundaries:

- `MCP`: brings external capabilities into the tool surface the model can call
- `ACP`: exposes the Coding Session to an IDE / desktop client / other host

The most reliable way to remember it:

- MCP is about "what else the model can call"
- ACP is about "how the host views and controls this session"

## 1. Pull the two chains apart first

If we draw the shortest path through the current implementation, it looks like this:

```text
MCP server
  -> CliMcpRuntimeManager
  -> toolRegistry / toolExecutor
  -> CodingAgentBuilder
  -> CodingSession
  -> HeadlessCodingSessionRuntime
  -> AcpJsonRpcServer
  -> ACP host
```

Each segment here is not the same layer of semantics:

- The front half extends the agent's tool surface
- The back half exposes the session as a protocol

This is also why mixing MCP and ACP into one write-up distorts the documentation.

## 2. Where MCP actually sits in the Coding Agent

On the current CLI / Coding Agent path, the main entry points for the MCP runtime are:

- `CliMcpConfigManager`
- `CliMcpRuntimeManager`
- `CliResolvedMcpConfig`
- `CliResolvedMcpServer`

The most important class is:

- `ai4j-cli/.../mcp/CliMcpRuntimeManager.java`

It is not a static configuration object but a live connection layer. On startup it:

1. Reads the resolved MCP config
2. Builds a `CliMcpConnectionHandle` for each server
3. connect
4. `listTools()`
5. Validates tool names
6. Converts each `McpToolDefinition` into an OpenAI-style `Tool.Function`
7. Produces the `toolRegistry` and `toolExecutor`

So MCP's role in this chain is not "append an extra prompt", but rather:

**Formally project the remote server's tools into the current agent's structured tool surface.**

## 3. Why MCP config is split into global and workspace layers

Current MCP config resolution has two clear layers:

- Global
- Workspace

The design motivation is straightforward:

- The global layer answers "which MCP server definitions exist on this machine"
- The workspace layer answers "which servers are currently enabled for this repo"

This is also why MCP in the Coding Agent should not be understood as "just a few more startup URLs".
It is fundamentally a coupled runtime configuration of:

- Server definitions
- Workspace enablement state
- Session visibility state

## 4. Why `CliMcpRuntimeManager` keeps a state machine

In the current implementation, an MCP server is not just "usable / not usable" — it explicitly tracks:

- `connected`
- `disabled`
- `paused`
- `error`
- `missing`

This matters because in real use, an MCP problem can come from completely different places:

- The workspace explicitly disabled it
- The current session paused it
- The config exists but the server does not
- The server exists but the connection failed

If the docs only say "MCP is unavailable", both the host and the user have a hard time telling which kind of problem it is.

## 5. Why MCP tools go through strict name-conflict validation

`CliMcpRuntimeManager` currently reserves these built-in tool names explicitly:

- `bash`
- `read_file`
- `write_file`
- `apply_patch`

If an MCP server returns a tool with the same name, the runtime treats it directly as a conflict.

It also checks for:

- Duplicate tool names within a single server
- Duplicate tool names across different servers

This shows AI4J's current stance is unambiguous:

**MCP tools are not "just hooked in" — they must first keep the current tool space decidable.**

Otherwise the host has no way to tell:

- Whether this `read_file` is a local tool or a remote MCP tool

## 6. What transformation MCP tools go through before reaching the model

An MCP server returns:

- `McpToolDefinition`

But what the model side ultimately sees is:

- `Tool.Function`

`CliMcpRuntimeManager.convertTools(...)` converts:

- Name
- Description
- Input schema

into the tool schema the current agent/runtime can consume.

So wiring MCP in is not "passing the JSON through to the model as-is" — it does a layer of tool surface adaptation first.

The value of this adaptation:

- The model side still sees a unified tool shape
- Local tools and MCP tools can coexist in the same registry

## 7. Where ACP actually sits in the Coding Agent

If MCP answers "where capabilities come from", then ACP answers "how the session gets out".

The main entry points for ACP today are:

- `AcpJsonRpcServer`
- `AcpCodingCliAgentFactory`
- `AcpToolApprovalDecorator`
- `HeadlessCodingSessionRuntime`

The core one is:

- `ai4j-cli/.../acp/AcpJsonRpcServer.java`

It is not a tool runtime; it is a session protocol gateway.

## 8. Which methods ACP currently exposes

From the constants in `AcpJsonRpcServer`, we can see directly that ACP currently handles at least:

- `initialize`
- `session/new`
- `session/load`
- `session/list`
- `session/prompt`
- `session/set_mode`
- `session/set_config_option`
- `session/cancel`
- `session/request_permission`

This set of methods itself spells out ACP's positioning:

- It is not a thin RPC of "send a prompt, get an answer"
- It exposes a sustainable coding session at the protocol layer

## 9. Why `session/prompt` goes through `HeadlessCodingSessionRuntime`

Under ACP, a prompt is not handed straight to `CodingSession.run()` to return a string.
`AcpJsonRpcServer` actually routes it to:

- `HeadlessCodingSessionRuntime`

The latter breaks a prompt run into structured events:

- `USER_MESSAGE`
- `ASSISTANT_MESSAGE`
- `TOOL_CALL`
- `TOOL_RESULT`
- `AUTO_CONTINUE`
- `AUTO_STOP`
- `BLOCKED`
- `COMPACT`
- `ERROR`

This shows ACP's focus is not "print the terminal content remotely", but to turn the session's internal process into an event stream the host can understand.

## 10. Why ACP approval is a protocol round-trip, not a local terminal interaction

Under CLI/TUI, approval is handled by:

- `CliToolApprovalDecorator`

through the terminal.

Under ACP it is different.
`AcpToolApprovalDecorator` routes through:

- `PermissionGateway`

to hand the approval request back to `AcpJsonRpcServer`, which then emits:

- `session/request_permission`

So ACP approval is fundamentally:

- The runtime sends a permission request
- The host returns an allow / reject decision
- Then it is decided whether the tool continues

This shows ACP is not "a remote CLI mirror" but a host protocol with a permission round-trip.

## 11. Why ACP and MCP take effect in the same session at once

This is a very easy place to get the Coding Agent story wrong.

Inside the same session, two things can happen at once:

- The MCP runtime injects remote tools into the agent
- The ACP runtime exposes the session state to the host

So a tool call an ACP host sees might well be:

- Local `bash`
- Local `apply_patch`
- A remote MCP tool

And the host does not need to care about the underlying differences in tool source — it just consumes the unified event stream.

This is the value of the layering:

- MCP handles capability wiring
- ACP handles session protocol

## 12. The truest boundaries in the current implementation

### MCP boundary

In the current CLI/Coding Agent, MCP solves:

- Server connect
- Tool discovery
- Conflict validation
- `toolRegistry`/`toolExecutor` injection

It does not directly solve:

- How the host UI renders
- How a session recovers
- How a permission popup is shown

### ACP boundary

In the current implementation, ACP solves:

- Session lifecycle protocol
- Prompt execution and cancellation
- Permission request
- Structured event delivery

It does not directly solve:

- Where external tools come from
- How tool schemas are defined
- How MCP servers connect

## 13. The five easiest places to trip up

### 13.1 Writing MCP as a "local tool" of the Coding Agent

It is not a single tool; it is the wiring layer for the external tool runtime surface.

### 13.2 Writing ACP as "another CLI"

It is a session protocol layer, not a terminal shell layer.

### 13.3 Ignoring MCP tool name conflicts

The current runtime strictly blocks built-in and cross-server conflicts — this is not a minor detail.

### 13.4 Reading `session/request_permission` as a UI prompt string

It is fundamentally part of a permission protocol round-trip.

### 13.5 Assuming an ACP host only sees text output

ACP currently sees structured session events, not just the final assistant text.

## 14. The conclusion this page should leave you with

Inside AI4J's current Coding Agent:

- MCP is responsible for wiring external server tools into the tool surface the current agent can call
- ACP is responsible for exposing the current coding session as a protocol to the host

The two show up on the same run chain, but they handle completely different boundaries.
Separating these two layers is the only way to truly understand which layer "where tools come from" and "how the session gets out" each land on.

## 15. Further reading

1. [Runtime architecture](/docs/products/coding-agent/runtime-architecture)
2. [Session, streaming, and process](/docs/products/coding-agent/session-runtime)
3. [Tools and the approval mechanism](/docs/products/coding-agent/tools-and-approvals)
4. [Core SDK / MCP](/docs/capabilities/mcp/overview)
