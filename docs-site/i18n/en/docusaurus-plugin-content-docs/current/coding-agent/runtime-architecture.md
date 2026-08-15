---
sidebar_position: 4
title: "Runtime Architecture"
description: "Breaks down the 5-layer assembly chain of the Coding Agent runtime (factory, builder, session, host, MCP runtime), explaining what state each layer holds, what behavior it decides, and where its boundaries with adjacent layers lie."
tags: [concept]
---

# Runtime Architecture

If you draw the `Coding Agent` runtime only as "CLI calls CodingAgent, which then calls the model," you will miss too many of the layers that actually shape behavior.
Looking at the current source, a complete run goes through at least 5 layers:

1. factory assembly layer
2. coding agent build layer
3. session execution layer
4. host/runtime interaction layer
5. MCP external tool runtime layer

The goal of this page is not to list module names, but to make clear: **what state each layer actually holds, what behavior it decides, and what its boundaries with adjacent layers are.**

## 1. Start with the outermost assembly entry point

The real preparation entry point for CLI / TUI / ACP today is:

- `ai4j-cli/.../DefaultCodingCliAgentFactory.java`

The main flow of `prepare(...)` is straightforward:

1. `resolveProtocol(options)`
2. `createModelClient(options, protocol)`
3. `prepareMcpRuntime(options, pausedServers, terminal)`
4. `buildAgent(options, terminal, interactionState, modelClient, mcpRuntimeManager)`
5. return `PreparedCodingAgent`

This means the runtime does not "grow automatically" inside `CodingAgent`; instead, the CLI factory first assembles:

- protocol
- provider
- workspace
- MCP
- approval
- stream options

into a runnable environment.

## 2. What the factory layer actually decides

`DefaultCodingCliAgentFactory` currently decides at least 6 things:

- whether to use `ChatModelClient` or `ResponsesModelClient`
- how the current provider / baseUrl / apiKey is written into `Configuration`
- the `CliWorkspaceConfig` corresponding to the current workspace
- whether to initialize `CliMcpRuntimeManager`
- how the approval decorator is attached
- whether to inject experimental subagent / delivery team

So the factory layer is not "a tiny object creator," but the first entry point for runtime policy.

Once you switch:

- provider
- protocol
- model
- the visible MCP set
- approval mode

it is rarely a single field change — most of the time you are reassembling the entire runtime.

## 3. `CodingAgentBuilder` is the real fork point that turns a generic Agent into a Coding Agent

Moving inward from the factory, the next critical layer is:

- `ai4j-coding/.../CodingAgentBuilder.java`

This builder currently also takes care of:

- `WorkspaceContext` resolution
- `CodingSkillDiscovery.enrich(...)`
- `CodingAgentOptions` / `AgentOptions` normalization
- built-in coding tool registry creation
- built-in tool executor creation
- `DefaultCodingRuntime` creation
- custom tool merging
- subagent / handoff merging
- workspace system prompt injection

Only then does it hand all of this off to:

- `AgentBuilder`

So the architecturally accurate description is:

**Coding Agent = generic Agent core + coding runtime assembly layer.**

## 4. What the Coding Agent core layer actually holds

If you only look at `CodingAgentBuilder.build()`, the objects this layer truly gathers are:

- `AgentModelClient`
- `WorkspaceContext`
- `CodingAgentOptions`
- `AgentToolRegistry`
- `ToolExecutor`
- `CodingRuntime`
- `SubAgentRegistry`
- `HandoffPolicy`

The key state this adds over an ordinary Agent is:

- workspace semantics
- coding-specific tools
- coding-specific runtime
- child-session / delegation capability

In other words, the Coding Agent itself is no longer a simple "single model + single tool table" executor.

## 5. Why `DefaultCodingRuntime` is a layer of its own

Many people on first read mistake `CodingRuntime` for "the host interface layer."
It is not.

- `ai4j-coding/.../runtime/DefaultCodingRuntime.java`

Today it is more of a coding work orchestration layer. It owns:

- `delegate(...)`
- background task scheduling
- child session creation
- `CodingTask` lifecycle
- `CodingSessionLink` persistence
- `CodingToolPolicyResolver` application
- runtime listeners

That is, what this layer cares about is:

- how tasks are split off
- how child sessions inherit the parent context
- which tools a child agent is allowed to use
- how foreground and background tasks are tracked

It is not a UI runtime, nor a model runtime — it is a **coding work runtime**.

## 6. The session execution layer and the host runtime are not the same layer

This is also the spot where the current docs are easiest to misread.

### Session execution layer

The core objects are:

- `CodingSession`
- `CodingAgentLoopController`

They are responsible for:

- how a single prompt becomes a multi-turn outer loop
- auto-continue and stop
- compact / checkpoint
- process snapshots
- state export / restore

### Host runtime

The core objects are:

- `CodingCliSessionRunner`
- `HeadlessCodingSessionRuntime`

They are responsible for:

- how user input enters the session
- how events are surfaced to CLI/TUI/ACP
- how to persist and replay
- how approval interactions communicate with the host

So:

- `CodingSession` is the execution session core
- `CodingCliSessionRunner` / `HeadlessCodingSessionRuntime` are the host drivers

The two are not one layer.

## 7. Where `DefaultCodingSessionManager` sits in the architecture

`DefaultCodingSessionManager` is also frequently miscategorized into the host UI layer.
In fact it is more of a session lifecycle service.

It handles:

- `create`
- `resume`
- `fork`
- `save`
- `load`
- `list`
- `appendEvent`
- `listEvents`

and also processes:

- workspace match validation
- `rootSessionId / parentSessionId`
- `SESSION_CREATED / RESUMED / FORKED / SAVED` events

So the correct positioning of this layer is:

- not responsible for reasoning
- not responsible for UI
- responsible for session lifecycle and ledger

## 8. Why the CLI/TUI path and the ACP/headless path are split

### CLI / TUI

Primarily driven by:

- `CodingCliSessionRunner`

It leans more toward an interactive controller, with responsibilities including:

- receiving user input and slash commands
- managing the current `ManagedCodingSession`
- handling session switching and runtime rebuilds
- driving terminal/TUI rendering

### ACP / headless

Primarily driven by:

- `HeadlessCodingSessionRuntime`
- `AcpJsonRpcServer`

`HeadlessCodingSessionRuntime` turns a single prompt into structured events such as:

- `USER_MESSAGE`
- `ASSISTANT_MESSAGE`
- `TOOL_CALL`
- `TOOL_RESULT`
- `AUTO_CONTINUE / AUTO_STOP / BLOCKED`
- `COMPACT`
- `ERROR`

`AcpJsonRpcServer` then sends these events to the host over the protocol.

So the ACP path is not about the terminal experience — it is about a protocolized event stream.

## 9. Why the MCP runtime is a live runtime layer of its own

The MCP layer today is not "read a config file and splice together a few tool definitions."

- `CliMcpRuntimeManager`

On startup it will:

- parse the resolved config
- build a `CliMcpConnectionHandle` per server
- connect
- `listTools()`
- validate tool-name conflicts
- convert to OpenAI-style `Tool`
- generate the `toolRegistry` and `toolExecutor`
- maintain `connected / disabled / paused / error / missing` state

In particular, it explicitly guards against conflicts with built-in tools:

- `bash`
- `read_file`
- `write_file`
- `apply_patch`

This shows that MCP in the Coding Agent is not a static extension point, but a **connectable, pausable, fallible, rebuildable** runtime layer.

## 10. Which layer `/experimental` affects

This is another spot that is easy to get wrong.

`/experimental` currently does not affect:

- the model client
- the session manager
- the TUI renderer

What it affects is what `DefaultCodingCliAgentFactory` attaches while building the agent:

- whether to attach the experimental subagent
- whether to attach the delivery team surface

In other words, it changes the callable agent surface that the current session exposes to the model — not the underlying runtime semantics as a whole.

## 11. Which layer approval interception belongs in

The current approval chain is:

- `DefaultCodingCliAgentFactory`
  -> `CodingAgentOptions.toolExecutorDecorator`
  -> `CodingAgentBuilder.createBuiltInToolExecutor(...)`
  -> `CliToolApprovalDecorator` / `AcpToolApprovalDecorator`

This shows that approval belongs in:

- the tool execution entry layer

and not in:

- the shell layer
- the session manager layer
- the UI renderer layer

Then the outer loop turns an approval rejection into `BLOCKED_BY_APPROVAL`.
This is a very clean layering:

- the decorator decides "can it execute"
- the loop decides "how the session stops after a rejection"

## 12. Why the runtime often needs to be "rebuilt"

In the Coding Agent, the following changes tend to trigger a runtime rebuild:

- provider/profile changes
- protocol changes
- model changes
- MCP server state changes
- workspace experimental setting changes

The reason is not UI caprice — these changes directly affect:

- `AgentModelClient`
- `toolRegistry`
- `toolExecutor`
- the approval decorator
- the available subagent surface

So many configuration changes are, in essence, execution-environment changes.

## 13. From an extension standpoint, which layer to change first

If your requirement is:

- change provider / protocol wiring
  start with `DefaultCodingCliAgentFactory`

- change delegation / child session / background task semantics
  start with `DefaultCodingRuntime`

- change session persistence and replay
  start with `DefaultCodingSessionManager` / `SessionEventStore`

- change host streaming event behavior
  start with `HeadlessCodingSessionRuntime` or `CodingCliSessionRunner`

- change MCP integration and conflict validation
  start with `CliMcpRuntimeManager`

- change approval policy
  start with `ToolExecutorDecorator` and `CliToolApprovalDecorator`

Editing by layer this way is far more stable than diving into the `CodingAgent` main class and hacking at random.

## 14. The conclusion worth remembering from this page

The AI4J Coding Agent runtime today is not a single-layer system, but a clear, layered assembly chain:

- the factory layer decides provider, protocol, workspace, MCP, approval, and the experimental surface
- the builder layer assembles these into a coding-specific agent core
- the runtime layer owns delegation and child work sessions
- the session layer owns the outer loop, compact, processes, and state
- the host layer owns CLI/TUI/ACP interaction and event surfacing
- the MCP runtime layer owns external tool connection and conflict governance

Once you see this chain clearly, the next time you change a feature you will know which layer to edit — rather than dumping everything into the single `CodingAgent` class.

## 15. Further reading

1. [Sessions, streaming, and processes](/docs/coding-agent/session-runtime)
2. [Tools and the approval mechanism](/docs/coding-agent/tools-and-approvals)
3. [Compact and checkpoint mechanism](/docs/coding-agent/compact-and-checkpoint)
4. [MCP and ACP](/docs/coding-agent/mcp-and-acp)

→ API Javadoc: [`CodingAgentBuilder`](https://javadoc.io/doc/io.github.lnyo-cly/ai4j-coding/2.4.2/io/github/lnyocly/ai4j/coding/CodingAgentBuilder.html) (the `ai4j-coding` module; the core class of the assembly fork point in §3 of this page)
