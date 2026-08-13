---
sidebar_position: 14
title: "Agent Core Class Reference"
description: "Source-map navigation for the ai4j-agent core classes: layered by wiring, runtime, model adaptation, tools, memory, workflow, subagent, team, and trace — tells you which line to read first and which entry point fits your question."
tags: [reference]
---

# Agent Core Class Reference

This page is not a class catalog, nor an API listing — it is a source-code navigation map.

If you are entering `ai4j-agent` for the first time, the most common trap is bouncing between:

- `agent`
- `runtime`
- `tool`
- `memory`
- `subagent`
- `team`
- `trace`

...and ending up with a scatter of class names but no real picture of how the objects relate.

The goal of this page is to re-order the core classes along "wiring -> execution -> extension -> observation", so you know which line to read first.

## 1. Build the big picture first

The overall shape of the AI4J Agent layer can be compressed into one diagram:

```text
Agents / AgentBuilder
  -> Agent / AgentSession
  -> AgentContext
  -> AgentRuntime
  -> AgentModelClient
  -> AgentToolRegistry + ToolExecutor
  -> AgentMemory
  -> trace / event
  -> subagent / workflow / team
```

If you treat every object in this diagram as a standalone feature, it will be hard to learn; the right approach is to read along the dependency direction.

## 2. Layer one: wiring entry points

This layer decides "how an Agent gets assembled".

### `Agents`

This is the factory entry point.

From here you should get to know the official main lines:

- `builder()`
- `react()`
- `codeAct()`
- `deepResearch()`
- `team()`

It answers the question "which wiring entry points does the framework expose publicly".

### `AgentBuilder`

This is the single most critical assembler in the Agent layer.

If you only read one class to understand the default behavior, read this one.

It defines the most important defaults in the current system:

- runtime defaults to `ReActRuntime`
- memory supplier defaults to `InMemoryAgentMemory::new`
- tool registry defaults to `StaticToolRegistry.empty()`
- when no `ToolExecutor` is explicitly provided, it attempts to initialize a default executor
- when subagents are configured, it merges subagent tools and wraps them in a `SubAgentToolExecutor`
- when no `CodeExecutor` is explicitly provided, it picks Nashorn or GraalVM based on the Java version

So a lot of "why does the system behave this way" questions ultimately lead back to `AgentBuilder`.

### `AgentContext`

This is the configuration snapshot that the runtime actually consumes.

It packs every execution-related dependency into one object:

- `modelClient`
- `toolRegistry`
- `toolExecutor`
- `codeExecutor`
- `memory`
- `options`
- `codeActOptions`
- `eventPublisher`
- `model`
- prompt / sampling / reasoning / extraBody and other configuration

Its significance is not "lots of fields", but rather:

- the runtime can depend on a single context object
- a session can swap out only `memory` while keeping most of the context intact

## 3. Layer two: runtime entry points and session semantics

This layer decides "how an already-assembled Agent actually gets invoked".

### `Agent`

`Agent` is the execution entry point, not the execution logic itself.

It mainly exposes:

- `run(AgentRequest)`
- `runStream(...)`
- `runStreamResult(...)`
- `newSession()`

The key points:

- it does almost no complex logic on its own
- it only routes the call to the runtime, and holds the `baseContext` and `memorySupplier`

### `AgentSession`

`AgentSession` is "under the same runtime and mostly the same context, swap in a fresh memory".

The question it answers is not "what is a new Agent", but:

- how does the same Agent start a new state space

So once you understand the implementation of `Agent.newSession()`, you realize:

- a Session is not a full clone of the Agent runtime environment
- it only switches memory

## 4. Layer three: the runtime line

This layer decides the advancement semantics of a single Agent run.

### `AgentRuntime`

This is the runtime abstraction interface.

What it really defines is:

- `run(...)`
- `runStream(...)`
- `runStreamResult(...)`

In other words, the runtime is the "advancer for a single invocation".

### `BaseAgentRuntime`

This is the default main-loop skeleton.

If you are investigating:

- why the model goes on to the next turn
- why a tool result lands in memory
- why a tool error doesn't immediately terminate
- why multiple tools are executed in parallel

your first instinct should be to come back here.

The core flow it owns covers:

- writing the user input to memory
- `AgentPrompt` assembly
- the model call
- tool call normalization
- argument validation
- tool execution
- result feedback
- event emission

### `ReActRuntime`

This is the runtime that stays closest to the framework's default semantics.

The current implementation is very thin — it mainly adds a layer on top of `BaseAgentRuntime`:

- runtime name
- runtime instructions

Its value is not "lots of features", but "the closest thing to the truth of the default behavior".

### `CodeActRuntime`

When you wonder:

- why the model outputs JSON
- why code has to run first
- why `CODE_RESULT` ends up back in memory

...it is time to switch to this one.

Its value lies in swapping out the intermediate representation.

### `DeepResearchRuntime`

The current implementation is not a heavy research framework — it inserts planning ahead of the default loop.

What is worth looking at here:

- how planning enters memory
- exactly how light `Planner.simple()` is

## 5. Layer four: the model adaptation line

This layer answers "how the prompt assembled by the runtime is finally sent down to the underlying model protocol".

### `AgentModelClient`

The unified model adaptation interface.

When you need to wire up a new provider, look here first.

### `AgentPrompt`

This is the standard input that the runtime submits to the model layer.

When you are investigating prompt assembly, you should look here, not guess what some provider's request looks like.

### `AgentModelResult`

This is the standard output that the model layer returns to the runtime.

Key fields typically include:

- `outputText`
- `toolCalls`
- `memoryItems`
- `rawResponse`

### `ChatModelClient` / `ResponsesModelClient`

They answer protocol differences, not runtime strategy.

Keep these two layers distinct:

- the runtime decides the loop
- the model client decides the protocol

## 6. Layer five: the tool line

This layer is one of the most important partitions in the entire Agent architecture.

### `AgentToolRegistry`

It answers only one thing:

> Which tools can the model see?

### `ToolExecutor`

It answers only one thing:

> How are tools actually executed?

If you conflate the responsibilities of these two objects, every downstream concern — permission governance, approval interception, argument rewriting — will get messier and messier.

### `StaticToolRegistry`

The most direct way to expose tools.

### `CompositeToolRegistry`

When you start merging:

- ordinary tools
- subagent tools
- team tools

...you will run into it.

### `AgentToolCall` / `AgentToolResult`

These two objects are the basic carriers for exchanging information between the runtime and the tool execution layer.

### `AgentToolCallSanitizer`

If you are investigating:

- why a tool call was rejected
- why the arguments structure is invalid

this is required reading.

## 7. Layer six: the memory line

Agent continuity does not live in private fields inside the runtime — it lives in `AgentMemory`.

### `AgentMemory`

The abstraction interface, answering:

- where the inputs and outputs accumulated during a run are stored
- where the next turn's prompt is rebuilt from

### `InMemoryAgentMemory`

The default implementation.

### `MemorySnapshot`

You need it when you care about copy, persistence, or recovery semantics.

### `MemoryCompressor` / `WindowedMemoryCompressor`

You only need to go down to this layer when the question becomes:

- how to drive down the cost of long conversations
- how to trim history

## 8. Layer seven: the workflow line

When a single Agent loop is no longer enough to express your execution structure, you move into the workflow layer.

### `SequentialWorkflow`

The simplest node chaining.

### `StateGraphWorkflow`

Worth looking at for:

- conditional routing
- explicit state advancement
- multi-node orchestration

### `AgentNode`

How an Agent gets embedded into a workflow as a node.

### `WorkflowContext`

Workflow-level context, not single-Agent memory.

### `WorkflowAgent`

Wraps a workflow back into an Agent shape.

## 9. Layer eight: the SubAgent line

This layer solves "bringing in another Agent as a governed tool".

### `SubAgentDefinition`

Look at:

- `name`
- `toolName`
- `sessionMode`

It defines "under what identity this subagent is exposed to the model".

### `StaticSubAgentRegistry`

Look at:

- default tool name generation
- schema generation
- how input folds into a subagent task
- how output is flattened into an ordinary tool result

### `SubAgentToolExecutor`

This is where handoff actually happens and gets governed.

If the question involves:

- timeout
- retry
- deny / fallback
- depth

...look here first.

### `HandoffPolicy` / `HandoffContext`

This layer defines:

- whether handoff is allowed
- what the maximum nesting depth is
- what the handoff depth in the current thread is

## 10. Layer nine: the Team line

This layer solves "multiple members collaborating around a goal".

### `AgentTeamBuilder`

Look at it first, because it decides:

- the fallback rules for lead / planner / synthesizer
- how options / storage / hooks are wired

### `AgentTeam`

This is the team runtime body.

It is also the `AgentTeamControl`.

If you want to understand:

- run lifecycle
- dispatch loop
- member task execution
- persistence / restore

...read it first.

### `AgentTeamTaskBoard`

The real state machine core.

How a task advances between:

- `PENDING`
- `READY`
- `IN_PROGRESS`
- `COMPLETED`
- `FAILED`
- `BLOCKED`

...is all in here.

### `AgentTeamToolRegistry` / `AgentTeamToolExecutor`

If you are investigating:

- why a member can send messages on its own initiative
- why a member can claim / release / heartbeat a task

...look at these two.

### `AgentTeamMessageBus` / `AgentTeamStateStore`

Team continuity and persistence is not built into member memory — it is externalized to:

- the message bus
- the state store

This is also the key difference between Team and SubAgent.

## 11. Layer ten: the trace and event line

This layer decides "how the system exposes the run to the outside".

### `AgentEventPublisher`

The unified event bus of the Agent runtime.

### `AgentTraceListener`

Maps events into trace spans.

### `TraceSpan`

The unified span data model.

### `TraceConfig`

Decides whether to record:

- model input and output
- tool arguments and output
- metrics

### `TraceExporter`

The export abstraction.

### Common implementations

- `ConsoleTraceExporter`
- `JsonlTraceExporter`
- `OpenTelemetryTraceExporter`
- `LangfuseTraceExporter`

If your question is "it ran, but I can't see what happened inside", this is usually the layer to read along.

## 12. Which defaults are most worth remembering

Not all defaults are equally important. The ones most worth remembering are these:

| Location | Default | Why it matters |
| --- | --- | --- |
| `AgentBuilder.runtime` | `ReActRuntime` | The default runtime is ReAct |
| `AgentBuilder.memorySupplier` | `InMemoryAgentMemory::new` | A session only swaps memory by default |
| `AgentBuilder.toolRegistry` | `StaticToolRegistry.empty()` | A minimal Agent can have no tools |
| `AgentBuilder.codeExecutor` | Java 8 -> Nashorn; higher versions -> GraalVM | Directly affects CodeAct language semantics |
| `CodeActOptions.reAct` | `false` | CodeAct no longer goes back to the model for a summary by default |
| `SubAgentDefinition.sessionMode` | `NEW_SESSION` | A subagent executes in isolation by default |
| `HandoffPolicy.maxDepth` | `1` | Only one level of handoff is allowed by default |
| `AgentTeamOptions.parallelDispatch` | `true` | Team is a concurrent dispatch model by default |
| `AgentTeamOptions.enableMemberTeamTools` | `true` | Members can collaborate proactively by default |

## 13. Pick a source-code entry point by question type

### I just want to get a minimal Agent running

Look at:

1. `Agents`
2. `AgentBuilder`
3. `Agent`
4. `BaseAgentRuntime`
5. `ReActRuntime`

### I am investigating tool permissions or execution interception

Look at:

1. `AgentToolRegistry`
2. `ToolExecutor`
3. `AgentToolCallSanitizer`
4. your own custom executor

### I am investigating why CodeAct didn't end as expected

Look at:

1. `CodeActRuntime`
2. `CodeActOptions`
3. `CodeExecutionResult`
4. the currently injected `CodeExecutor`

### I am investigating why a SubAgent handoff failed

Look at:

1. `SubAgentDefinition`
2. `StaticSubAgentRegistry`
3. `SubAgentToolExecutor`
4. `HandoffPolicy`

### I am investigating why a Team isn't collaborating

Look at:

1. `AgentTeamBuilder`
2. `AgentTeam`
3. `AgentTeamTaskBoard`
4. `AgentTeamToolRegistry`
5. `AgentTeamToolExecutor`

## 14. Recommended test index

If you want to use tests to reverse-engineer the source, prioritize:

- `ai4j-agent/src/test/java/io/github/lnyocly/agent/CodeActRuntimeTest.java`
- `ai4j-agent/src/test/java/io/github/lnyocly/agent/CodeActRuntimeWithTraceTest.java`
- `ai4j-agent/src/test/java/io/github/lnyocly/agent/SubAgentRuntimeTest.java`
- `ai4j-agent/src/test/java/io/github/lnyocly/agent/SubAgentParallelFallbackTest.java`
- `ai4j-agent/src/test/java/io/github/lnyocly/agent/HandoffPolicyTest.java`
- `ai4j-agent/src/test/java/io/github/lnyocly/agent/AgentTeamTest.java`
- `ai4j-agent/src/test/java/io/github/lnyocly/agent/AgentTeamTaskBoardTest.java`
- `ai4j-agent/src/test/java/io/github/lnyocly/agent/AgentTeamPersistenceTest.java`

## 15. Further reading

1. [Agent Architecture](/docs/agent/architecture)
2. [Tools and Registry](/docs/agent/tools-and-registry)
3. [Runtime Implementations](/docs/agent/runtime-implementations)
4. [CodeAct Runtime](/docs/agent/codeact-runtime)
5. [SubAgent and Handoff Policy](/docs/agent/subagent-handoff-policy)
6. [Agent Teams](/docs/agent/agent-teams)
