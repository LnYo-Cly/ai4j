---
sidebar_position: 8
title: "MCP Integration"
description: "How MCP becomes a live tool surface in the Coding Agent: two-layer config (global definition + workspace enablement), five server runtime states, tool name collision checks, and the independent per-session MCP injection path under ACP."
tags: [integration]
---

# MCP Integration

This page is not about the MCP protocol itself; it is about how MCP turns into a "live tool surface" inside the `Coding Agent`.

In the current implementation, MCP is neither a static piece of configuration nor a simple JSON forwarder.

It has to go through:

- global definition
- workspace enablement selection
- session-level pause state
- connection establishment
- tool list fetching
- naming collision checks
- registry / executor rebuild

Only then does it actually enter the agent's available tool set.

---

## 1. Start with the real assembly chain

Compress the CLI path into a single execution chain:

```text
/mcp add|enable|pause...
  -> CliMcpConfigManager
  -> CliResolvedMcpConfig
  -> CliMcpRuntimeManager.initialize(...).start()
  -> connect to each MCP server
  -> listTools()
  -> validateToolNames(...)
  -> convertTools(...)
  -> StaticToolRegistry + ToolExecutor
  -> DefaultCodingCliAgentFactory.attachMcpRuntime(...)
  -> CodingAgentBuilder.toolRegistry/toolExecutor(...)
```

The key judgment here is:

Inside the Coding Agent, `MCP` is not a "protocol noun" — it is a real external tool runtime.

It has a connection state, an error state, a paused state, and it can also fail as a whole because of naming conflicts.

---

## 2. Why configuration is split into two layers

The current CLI path splits MCP configuration into two parts:

### 2.1 Global definition

Path:

```text
~/.ai4j/mcp.json
```

It stores the server definition — i.e. "who this MCP server is and how to connect to it".

The core fields currently supported by `CliMcpServerDefinition` include:

- `type`
- `url`
- `command`
- `args`
- `env`
- `cwd`
- `headers`

### 2.2 Workspace enablement state

Path:

```text
<workspace>/.ai4j/workspace.json
```

What it actually cares about is:

- `enabledMcpServers`

It answers a different question:

- Which of the globally-defined MCP servers should be enabled for the current repo

This two-layer split is highly valuable:

- You can maintain a stable set of server definitions at the machine level
- While each repo only turns on the few it actually needs
- Without conflating "exists on this machine" with "this repo actually wants it enabled"

---

## 3. What `CliMcpConfigManager` is actually responsible for

`CliMcpConfigManager` does more than read and write JSON.

It is also responsible for:

- Normalizing server names and fields
- Mapping `http` uniformly to `streamable_http`
- Auto-filling `stdio` when `type` is missing but `command` is present
- Validating that `stdio` has a `command`
- Validating that `sse` / `streamable_http` has a `url`
- Resolving global definitions together with workspace enablement state into a `CliResolvedMcpConfig`

So its output is not "the raw configuration", but "the resolved result that the current session can use to start a runtime".

That is why `CliResolvedMcpConfig` and `CliResolvedMcpServer` exist.

---

## 4. What actually happens when the runtime starts

`CliMcpRuntimeManager.start()` is where the whole chain truly enters a running state.

For each resolved server, it makes these decisions in order:

1. Not enabled by the workspace — mark as `disabled`
2. Paused for the current session — mark as `paused`
3. Configuration invalid — mark as `error`
4. Otherwise, try to create a client session and connect
5. Fetch `listTools()`
6. Validate tool names
7. Convert to the OpenAI function tool shape
8. Set the server state to `connected`

If the workspace references a name that does not exist in the global definitions at all, an additional state is produced:

- `missing`

So there are at least five visible states:

- `connected`
- `disabled`
- `paused`
- `error`
- `missing`

This is also why `/mcp` output does not simply list configuration — it lists the "current runtime view".

---

## 5. Why MCP tools are much stricter than skills

`CliMcpRuntimeManager.validateToolNames(...)` performs three layers of collision checks:

### 5.1 Cannot collide with built-in tools

These names are reserved:

- `bash`
- `read_file`
- `write_file`
- `apply_patch`

If an MCP server returns a tool with the same name, that server goes straight into the error state.

### 5.2 No duplicates within the same server

If a server returns duplicate tool names, it also errors directly.

### 5.3 No name sharing across servers

Once the first server claims a tool name, a later server returning the same name is also treated as a conflict.

This is completely different from how skills are handled.

- Skill conflicts are shadowing at the prompt layer
- MCP conflicts are failures at the execution layer

Because once the execution-layer name is not unique, tool routing loses determinism.

---

## 6. The real semantics of the three transports in the current implementation

The core transports are still three categories:

- `stdio`
- `sse`
- `streamable_http`

Their applicability can be understood roughly as follows:

### `stdio`

Local-process-style MCP server.

Best fit:

- Local binaries
- Node/Python launch scripts
- Servers that need to be spawned directly by the CLI

### `sse`

Remote event-stream-style MCP endpoint.

Best fit:

- Services that are already live in production
- Integration styles that need to keep a message stream over SSE

### `streamable_http`

Standard HTTP-style MCP endpoint.

Best fit:

- Existing HTTP-ized MCP services
- Deployment styles that prefer a more standard, proxy-friendly path

One easily confusing point:

- The CLI command layer still lets you write `--transport http`
- But `CliMcpConfigManager.normalizeTransportType(...)` normalizes it to `streamable_http`
- `McpTransportFactory` also treats the legacy `http` as a compatibility alias

So:

- CLI input can continue to use `http`
- But in config files and ACP injection, writing `streamable_http` directly is preferred

---

## 7. Which layer each `/mcp` subcommand modifies

The `/mcp` commands in the CLI essentially operate on three distinct layers of state.

### 7.1 `add` / `remove`

Operate on the global definition layer.

Affected file:

- `~/.ai4j/mcp.json`

`/mcp add --transport <...> <name> <target>` only creates a server definition in the global store.

It does not automatically mean the current workspace has it enabled.

### 7.2 `enable` / `disable`

Operate on the workspace enablement layer.

Affected file:

- `<workspace>/.ai4j/workspace.json`

That is, they modify `enabledMcpServers`.

Once enabled or disabled, the current session runtime is rebuilt.

### 7.3 `pause` / `resume` / `retry`

Operate on the session runtime layer.

This is the most easily misunderstood part:

- `pause` does not modify the global store
- `pause` does not modify `workspace.json` either
- It only modifies `pausedMcpServers` in the current session's memory

Then it rebuilds the MCP runtime used by the current session via `switchSessionRuntime(...)`.

`retry` works the same way — it is not fundamentally "a hot patch on a single connection", but rather a way to make the current session walk through the runtime assembly again.

---

## 8. How MCP tools are wired into the Coding Agent

`DefaultCodingCliAgentFactory.prepareMcpRuntime(...)` first creates a `CliMcpRuntimeManager`.

If the runtime actually produces:

- `toolRegistry`
- `toolExecutor`

then `attachMcpRuntime(...)` hands them to `CodingAgentBuilder`.

In other words, the current MCP wiring is not "a hidden branch inside the built-ins", but rather:

- First prepare an independent runtime at the CLI host layer
- Then inject it into `CodingAgentBuilder` as an external tool surface

Then `CodingAgentBuilder.mergeToolRegistry(...)` / `mergeToolExecutor(...)` merges it together with built-ins and subagent tools.

This is also why:

- MCP fundamentally belongs to the host assembly layer
- It is not hard-coded as part of the `ai4j-agent` core

---

## 9. How to read error and failure paths

The most common failure paths in the current implementation fall into five categories.

### 9.1 Workspace references an undefined server

Symptom:

- `/mcp` shows `state=missing`

Meaning:

- A name exists in `workspace.json`'s `enabledMcpServers`
- But no matching definition can be found in `~/.ai4j/mcp.json`

### 9.2 Configuration fields are incomplete

Symptom:

- `state=error`
- The error usually says `stdio transport requires command` or `<type> transport requires url`

### 9.3 Connection or `listTools()` failed

Symptom:

- `state=error`
- At startup you may also see `Warning: MCP unavailable: ...`

### 9.4 Tool name conflict

Symptom:

- A server goes directly into `error`
- Common causes include built-in conflicts, duplicates within the same server, and duplicates across servers

### 9.5 The current session paused a server

Symptom:

- `state=paused`
- The workspace is still enabled
- It is just that this round of session runtime did not wire it into the available tool set

---

## 10. Why MCP under ACP is a different chain

ACP does not necessarily depend on the local `~/.ai4j/mcp.json`.

When handling `session/new` / `session/load`, `AcpJsonRpcServer.createSession(...)` can receive `mcpServers` directly from the request parameters.

It then calls its own `resolveMcpConfig(...)`:

- Converting each incoming server directly into a `CliResolvedMcpServer`
- Treating them all as workspace enabled by default
- Not going through the local global store
- Not going through `workspace.json`'s `enabledMcpServers`

This chain fits:

- IDE plugins dynamically injecting MCP
- Desktop hosts temporarily allocating MCP per project
- Multi-tenant hosts that do not want to depend on the user's local global config

So MCP under ACP is more like:

- host-managed session-scoped MCP config

While MCP under the CLI is more like:

- machine-scoped definition + workspace-scoped enablement

---

## 11. How to read `/mcp` output

`CodingCliSessionRunner.renderMcpOutput()` currently prints each server as:

- `name`
- `type`
- `state`
- `workspace=enabled|disabled`
- `paused=yes|no`
- `tools=<count>`
- `error=<summary>` (when there is an error)

At the end it also appends:

- `store=<globalMcpPath>`
- `workspaceConfig=<workspaceConfigPath>`

These two paths are important, because they tell you directly whether the problem lands on:

- the global definition layer
- the workspace enablement layer
- or the runtime connection layer

---

## 12. Recommended way to organize

If you want to use MCP stably over the long term, this organization is the safest:

1. Put stable server definitions in `~/.ai4j/mcp.json`
2. Only enable the corresponding names in repos that actually need them
3. For temporary per-session disabling, use `/mcp pause`
4. For configuration or connection changes, use `retry` or re-switch the session runtime
5. For host-side temporary injection, go through ACP `mcpServers`

Practices that are not recommended:

- Keeping all servers permanently enabled in the workspace
- "Trying your luck" with same-named tools across multiple servers
- Treating `pause` as a persistent configuration switch

---

## 13. Where to look first when extending or troubleshooting

The entry classes most worth reading directly:

- `ai4j-cli/.../mcp/CliMcpConfigManager`
- `ai4j-cli/.../mcp/CliMcpRuntimeManager`
- `ai4j-cli/.../factory/DefaultCodingCliAgentFactory`
- `ai4j-cli/.../runtime/CodingCliSessionRunner`
- `ai4j-cli/.../acp/AcpJsonRpcServer`
- `ai4j/.../mcp/transport/McpTransportFactory`

Recommended troubleshooting order:

1. Run `/mcp` to check whether the state is `missing / error / paused`
2. Check exactly which files `store` and `workspaceConfig` point to
3. Check whether the transport was normalized to the expected type
4. Check whether the tool name conflicts with built-ins or another server
5. Under ACP, confirm that `mcpServers` was correctly passed in by the host for the session

---

## 14. The takeaways from this page

- MCP in the Coding Agent is a live runtime, not a static configuration fragment
- Global `mcp.json` is responsible for "defining servers"; `workspace.json` is responsible for "which ones this repo enables"
- `pause`/`resume` is session-level state and is not persisted to configuration files
- A tool name conflict puts an MCP server straight into the error state, rather than silently shadowing like a skill would
- The CLI's `http` is only a compatibility input; it is eventually normalized to `streamable_http`
- ACP can bypass the local global store and inject MCP servers directly per session

---

## 15. Further reading

1. [MCP and ACP](/docs/coding-agent/mcp-and-acp)
2. [Tools and the approval mechanism](/docs/coding-agent/tools-and-approvals)
3. [ACP integration](/docs/coding-agent/acp-integration)
4. [MCP overview](/docs/mcp/overview)
