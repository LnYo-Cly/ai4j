---
title: "Coding Agent Architecture"
description: "Breaks down the layered architecture of the Coding Agent from the main execution chain: the CodingAgentBuilder assembly point, the WorkspaceContext boundary, the CodingSession container, the delegation runtime, the approval decorator, and the host runtime division of responsibilities."
tags: [concept]
---

# Coding Agent Architecture

The point of the `Coding Agent` architecture is not "wrapping one more API layer outside `AgentBuilder`", but rather assembling the general agent runtime, workspace semantics, long-running sessions, host interaction, and external tool integration into one stable local delivery chain.

If you do not look at it from the "execution chain" angle, you will underestimate why many of the classes need to exist.

---

## 1. Start with the entire main chain

The main chain closest to real execution can be compressed into the following:

```text
CodeCommand / AcpJsonRpcServer
  -> DefaultCodingCliAgentFactory
  -> CodingAgentBuilder
  -> CodingSession
  -> CodingAgentLoopController
  -> built-in tools / approval decorators / MCP tools / delegate tools
  -> Agent / AgentSession / model client
```

If you add session management and persistence back in, the more complete picture is:

```text
host runtime
  -> session manager + session runtime
  -> coding runtime
  -> tool surface + approval + MCP
  -> base agent runtime
```

These layers are not abstract divisions on paper, but rather real responsibility boundaries separated in the current code.

---

## 2. The bottom layer is still `Agent`, but it is not exposed directly to users

The bottom layer is still the general `Agent` capability:

- `Agent`
- `AgentBuilder`
- `AgentSession`
- `AgentRuntime`
- `AgentModelClient`
- `AgentToolRegistry`
- `ToolExecutor`

What this layer is responsible for remains:

- model call
- tool loop inside a step
- basic memory
- tool call abstraction

But what Coding Agent cares most about is not here yet:

- workspace path boundary
- session persistence
- approvals
- process management
- auto-continue
- compact / checkpoint
- CLI / TUI / ACP integration

So the architecture of `Coding Agent` does not replace it, but rather stacks multiple layers of runtime semantics on top of it.

---

## 3. `CodingAgentBuilder` is the real fork point

The first key fork when switching from a "general agent" to a "coding agent" is `CodingAgentBuilder.build()`.

What it does is not simple assignment, but rather a complete assembly:

1. Validate `modelClient` and `model`
2. Normalize `WorkspaceContext`
3. Call `CodingSkillDiscovery.enrich(...)`
4. Prepare `CodingAgentOptions`, `AgentOptions`
5. Prepare `CodingAgentDefinitionRegistry`
6. Prepare `CodingRuntime`
7. Create the built-in tool registry
8. Create the built-in tool executor
9. Merge external tools, e.g. MCP
10. Merge subagent tools
11. Prepend the workspace prompt to the system prompt according to the configuration
12. Finally call the general `AgentBuilder` to build the underlying `Agent`

So the real responsibility of `CodingAgentBuilder` is:

- Compress the "peripheral semantics needed for repository delivery" into an ordinary `Agent`

If you want to determine whether a capability should land in `Agent` or `Coding Agent`, a practical question is:

- Does this capability depend on workspace / session / host / coding tool policy?

If it does, usually it should not be stuffed directly into the general `Agent` layer.

---

## 4. Why the workspace layer must be independent

`WorkspaceContext` is the boundary baseline of the entire coding architecture.

It currently defines at least the following:

- root path
- excluded paths
- whether explicit out-of-workspace access is allowed
- skill directories
- allowed read roots
- available skills

More critically, it has two sets of path resolution semantics:

- `resolveWorkspacePath(...)`
- `resolveReadablePath(...)`

This shows that the current architecture explicitly distinguishes:

- where it can write
- where it can read

The reason skill roots can be placed outside the workspace is precisely because `allowedReadRoots` only expands the read surface, not the write surface.

If this layer were not independent, many coding-specific rules would have to be scattered across various tool executors, eventually becoming very hard to maintain.

---

## 5. The tool layer is not a single registry, but a multi-source merge

The currently visible tool surface comes from at least four sources:

1. built-in coding tools
2. externally injected tools, e.g. MCP
3. delegate / subagent tools
4. tool abstractions already supported by the lower `Agent` layer

`CodingAgentBuilder` first generates the built-ins, then merges the external registry / executor.

This means the core of the tool architecture is not "listing which tools exist", but rather:

- how tools from different sources are merged
- where conflicts are handled
- how the host mounts its own tool surface during assembly

This is also why MCP integration is designed so the host layer first prepares `CliMcpRuntimeManager`, then plugs its `toolRegistry` / `toolExecutor` into the builder, instead of being hardcoded in the built-ins.

---

## 6. `CodingSession` is the real runtime container for long-running tasks

If you only look at the API name, it is easy to treat `CodingSession` as a "chat session wrapper".

That would misjudge its responsibility.

What it currently takes on:

- bind workspace context
- bind process registry
- bind coding runtime
- support state export / restore
- record loop decisions
- aggregate compact results

Its position is closer to:

- "a persistent, resumable, forkable local work session container"

rather than the "conversation id" in an ordinary chat product.

This is why the session architecture is lifted separately in Coding Agent, rather than being hung off the host layer as a small utility class.

---

## 7. Why the outer loop forms its own layer

The general `Agent` is closer to a "tool loop within a single call".

`Coding Agent` also needs to handle a higher level of task continuity:

- whether the current turn should auto-continue
- for what reason to continue or stop
- when to compact
- how to record when blocked

This responsibility should not be stuffed into the CLI, nor into the general `Agent`.

The more appropriate location right now is the coding session / loop layer, with core classes including:

- `CodingAgentLoopController`
- `CodingLoopDecision`
- `CodingStopReason`

The point of this layer existing is:

- to separate the semantics of "one user task may span multiple model calls" into its own layer

Otherwise the host layer would be forced to guess on its own when to continue and when to stop.

---

## 8. `DefaultCodingRuntime` is not a UI runtime, but a delegation runtime

`DefaultCodingRuntime` is easily misread as "the overall runtime of the entire coding agent".

More accurately, its most important responsibility right now is:

- manage delegated tasks
- create child session
- track `CodingTask`
- maintain `CodingSessionLink`
- apply per-definition tool policy

From `createChildSession(...)` you can see the real position of this layer:

1. Inherit the runtime and context skeleton of the parent session
2. Recreate the child's built-in tools and process registry
3. Merge custom tools, subagent tools
4. Apply tool policy per definition
5. Create a brand-new child `CodingSession`
6. Restore seed state on demand

This shows that what `DefaultCodingRuntime` solves is:

- "how a subtask runs as an independent unit of work"

rather than:

- "how the terminal displays"

So it is not on the same layer as CLI/TUI/ACP.

---

## 9. `DefaultCodingSessionManager` solves persistence and lineage, not execution

`DefaultCodingSessionManager` is responsible for:

- `create`
- `resume`
- `fork`
- `save`
- `load`
- `list`
- event append / list

Note that it is not responsible for:

- running the model
- continuing the outer loop
- executing tools

It solves a different class of problem:

- how a running work session gets named, persisted, resumed, forked, and leaves behind an event ledger

So architecturally it should be seen as:

- session lifecycle management

rather than:

- runtime core

This is also why `HeadlessCodingSessionRuntime` and `CodingCliSessionRunner` still need it, but cannot have it replace the execution layer.

---

## 10. Why approval sits in the executor decorator layer

Current approval is not an OS hook, nor JVM instrumentation.

It is a decorator in the `ToolExecutor` assembly process.

The CLI/TUI path uses:

- `CliToolApprovalDecorator`

The ACP path uses:

- `AcpToolApprovalDecorator`

The architectural judgment behind this is very clear:

- approval belongs to "host decisions before tool execution"
- not to the model layer
- nor to the underlying OS interception layer

Placing it this way has three benefits:

1. The underlying tool executor can keep pure execution logic
2. CLI/TUI and ACP can share approval semantics but use different host channels
3. The approval policy can vary with the host without polluting the general coding runtime

---

## 11. Why MCP forms its own live runtime layer

MCP is not a set of static tool definitions in the current architecture.

`CliMcpRuntimeManager` is responsible for:

- read resolved config
- establish connections
- pull tool definitions
- perform built-in / cross-server conflict checks
- maintain state snapshots
- generate tool registry / executor

In other words, it is a runtime with connection state, not a builder that helps you parse JSON on the side.

This is why it is reasonable to single out MCP in the architecture diagram.

Otherwise you would mistakenly think:

- "MCP is just a configuration form of tools"

In fact, the current implementation is clearly heavier:

- it is an external capability integration layer
- it has its own error model and runtime state

---

## 12. Why the host runtime is not "pure UI"

The layer of `CodeCommand`, `CodingCliSessionRunner`, `JlineCodeCommandRunner`, `AcpJsonRpcServer` is often underestimated.

But what they actually take on:

- parse runtime options
- choose CLI / TUI backend
- create session manager
- create and switch session runtime
- emit MCP startup warnings
- respond to slash commands
- relay permission requests and structured events in ACP

These behaviors already go far beyond "displaying text".

So the accurate understanding of host runtime should be:

- product entry point and interaction orchestration layer

rather than:

- the thinnest shell that renders UI

---

## 13. From an extension perspective, which layer to change first

This question is very important in actual development.

### Want to change model call or basic tool loop

Look at `ai4j-agent` first

### Want to change workspace semantics, coding tools, session state, delegation

Look at `ai4j-coding` first

### Want to change CLI/TUI/ACP interaction, approval channels, MCP config, session storage

Look at `ai4j-cli` first

### Want to add team rules or task workflow descriptions

Prefer the skill system, do not change the runtime first

:::warning Common architecture mistakes
The most common architectural mistake is not "cannot write it out", but rather "writing host logic into runtime" or "hardcoding general logic in CLI".
:::

---

## 14. How this page divides work with adjacent pages

- `overview`: establish the global mental model first
- `why-coding-agent`: explains why an independent product line is needed
- `architecture`: explains layers, responsibilities, the main execution chain
- `runtime-architecture`: breaks down runtime components in finer detail
- `session-runtime`: explains long-running tasks, events, save and resume
- `tools-and-approvals`: explains the execution surface and approval interception
- `mcp-integration`: explains the MCP integration chain in coding scenarios

If you now want to keep chasing "why long-running tasks can auto-continue and how they get saved", the next page you should look at is `session-runtime`.

---

## 15. The conclusions most worth remembering on this page

- `CodingAgentBuilder` is the key assembly point where the general `Agent` forks into `Coding Agent`
- `WorkspaceContext`, tool merge, skills, MCP, approval are all architecture-level components, not scattered features
- `CodingSession` owns long-running work container semantics, `DefaultCodingSessionManager` owns persistence and lineage management
- `DefaultCodingRuntime` focuses on delegation and child session, not UI
- host runtime owns real product interaction orchestration, not the pure display layer

---

## 16. Further reading

1. [Runtime Architecture](/docs/products/coding-agent/runtime-architecture)
2. [Session Runtime](/docs/products/coding-agent/session-runtime)
3. [Tools and Approvals](/docs/products/coding-agent/tools-and-approvals)
4. [MCP Integration](/docs/products/coding-agent/mcp-integration)
