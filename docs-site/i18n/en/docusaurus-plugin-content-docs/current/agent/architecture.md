---
title: "Agent Architecture"
description: "Breaks down the three-layer boundaries of ai4j-agent (Builder, Runtime, ModelClient), the dual faces of tool declaration and execution, memory as the state source, and the architectural positioning of the event stream and trace."
tags: [concept]
---

# Agent Architecture

The architectural core of `ai4j-agent` is not "wrapping a model call", but rather converging multi-step reasoning, tool calls, state continuation, and observability into a reusable runtime.

If you treat it as "a slightly larger SDK helper", many design decisions will look overly complex; once you treat it as "a general-purpose Agent runtime", these layers become far more reasonable.

## 1. Start with 7 key design decisions

### 1.1 Builder, Runtime, and ModelClient are three distinct boundaries

The most important first split in the current architecture is:

- `AgentBuilder`
- `AgentRuntime`
- `AgentModelClient`

They answer, respectively:

- How this Agent is assembled
- How this run progresses
- How this prompt is sent to the model

Once these three layers are tangled inside business code, swapping runtimes, changing protocols, or applying governance later becomes very painful.

### 1.2 `AgentContext` is the runtime's single configuration snapshot

The Builder does not ultimately pass a scatter of fields to the runtime; instead it constructs one:

- `AgentContext`

The runtime recognizes only this one context object.

This means:

- The dependency boundary at runtime is stable
- `newSession()` can replace only memory
- The runtime does not need to know every detail of the Builder

### 1.3 Memory is the state source, but not the entire system state

`AgentMemory` currently carries:

- User input
- Model output
- Tool output
- summary / snapshot related state

But it does not equal the entire runtime state.

For example:

- Runtime type
- tool registry
- tool executor
- sampling parameters
- event publisher

These are not in memory; they live in `AgentContext`.

### 1.4 Tool governance is, by design, more than "hiding tools"

One of the most critical security boundaries in the architecture is:

- `AgentToolRegistry` only exposes the tool surface
- `ToolExecutor` is the actual execution surface

This shows the framework authors never conflated "exposing the schema" with "executing with permission" from the very start.

### 1.5 Runtime reuses the base main loop instead of each writing its own

In the current implementation:

- `ReActRuntime` is very thin
- `DeepResearchRuntime` is also built on top of the base loop
- Only `CodeActRuntime` swaps the intermediate representation at key points

This means the architecture is not three parallel runtime products, but one base runtime plus a few semantic branches.

### 1.6 The event stream is a first-class architectural object, not a debugging patch

`AgentEventPublisher`, `AgentEventType`, and `AgentTraceListener` are not peripheral tools.

They have been placed into the main chain of:

- Builder wiring
- Runtime execution
- Trace exporter

This determines that AI4J Agent's observability is not "grepping logs after the fact", but structured event projection.

### 1.7 Session's default isolation level is deliberately restrained

The boundary of the current `Agent.newSession()` is very clear:

- Only replaces memory

It does not replace:

- runtime
- modelClient
- toolRegistry
- toolExecutor
- prompt templates

So the default design of session leans toward "lightweight state forking", not a full runtime-environment copy.

## 2. Core object graph

The graph most worth remembering at this layer is not the package structure, but the object relationships:

```text
Agents
  -> AgentBuilder
  -> AgentContext
  -> Agent
  -> AgentRuntime
  -> AgentModelClient
  -> AgentToolRegistry + ToolExecutor
  -> AgentMemory
  -> AgentEventPublisher
```

The most critical responsibilities are:

| Object | Real responsibility |
| --- | --- |
| `Agents` | Provides the official wiring entry point |
| `AgentBuilder` | Resolves defaults, assembles dependencies |
| `AgentContext` | Runtime configuration snapshot |
| `Agent` | Execution entry point and session derivation |
| `AgentRuntime` | Progression strategy for a single run |
| `AgentModelClient` | Model protocol adaptation |
| `AgentToolRegistry` | Tool declaration surface |
| `ToolExecutor` | Tool execution surface |
| `AgentMemory` | State source |
| `AgentEventPublisher` | Runtime event bus |

## 3. What the build phase actually does

To understand the architecture, the first class worth reading directly is still `AgentBuilder`.

### 3.1 Default wiring chain

`AgentBuilder.build()` currently resolves these defaults:

- `runtime` -> `ReActRuntime`
- `memorySupplier` -> `InMemoryAgentMemory::new`
- `toolRegistry` -> `StaticToolRegistry.empty()`
- `codeExecution` -> on Java 8 uses `NashornCodeExecutor`, on higher versions uses `GraalVmCodeExecutor`
- `options` -> `AgentOptions.builder().build()`
- `codeActOptions` -> `CodeActOptions.builder().build()`
- `eventPublisher` -> a new `AgentEventPublisher`

Then, depending on whether you configured:

- `subAgentRegistry` / `subAgent(...)`
- `toolExecutor`
- `traceExporter`

it decides whether to further wrap:

- `CompositeToolRegistry`
- `SubAgentToolExecutor`
- `AgentTraceListener`

### 3.2 Several key implications of the Builder phase

#### The default minimal Agent can have no tools at all

Because the default tool surface is:

- `StaticToolRegistry.empty()`

This shows that tools are not a precondition for an Agent to exist.

#### Trace is not on by a global switch

Only if you configure:

- `traceExporter(...)`

will the Builder automatically attach `AgentTraceListener`.

#### `toolRegistry(List<String>, List<String>)` is not the low-level abstraction

It is just a convenience API that ultimately relies on reflection-loaded:

- `ToolUtilRegistry`
- `ToolUtilExecutor`

So it is more of a quick-wiring entry point, not a core architectural primitive.

## 4. How the run phase actually progresses

### 4.1 `Agent` itself is very thin

`Agent` mainly exposes:

- `run(...)`
- `runStream(...)`
- `runStreamResult(...)`
- `newSession()`

It is not the complex main loop body itself; the genuinely complex logic lives in the runtime.

### 4.2 `BaseAgentRuntime` is the default main loop skeleton

The current main chain of `BaseAgentRuntime.runInternal(...)` is roughly:

1. Read `AgentOptions`
2. Validate memory
3. Write user input into memory
4. Publish `STEP_START`
5. `buildPrompt(...)`
6. `executeModel(...)`
7. Write the `memoryItems` returned by the model back into memory
8. Normalize tool calls
9. Parameter validation
10. Execute tools
11. Write tool output back into memory
12. Publish `STEP_END`
13. If there is no tool call, publish `FINAL_OUTPUT` and close out

This chain defines the default execution semantics of the current ReAct-family runtimes.

### 4.3 The prompt is not hand-assembled each time, but rebuilt from memory at each step

`buildPrompt(...)` recombines:

- `memory.getItems()`
- `systemPrompt`
- `instructions`
- `tools`
- sampling parameters

into a new `AgentPrompt`.

This shows the core of the Agent's main loop is not "concatenating an ever-growing large string", but rather:

- Treating memory as the state source
- Rebuilding a prompt snapshot at each step

### 4.4 The termination condition is not "the model has finished talking", but "no tool call in this turn"

In the default ReAct main loop, if the model produces no tool call this turn:

- The runtime considers it can close out

If there is a tool call:

- The runtime continues to the next turn

This is also one of the essential differences between an Agent and a plain single model call.

## 5. How the prompt layer is layered

This layer is easy to get wrong if you don't read the source.

### 5.1 `systemPrompt` is additionally concatenated by the runtime

`BaseAgentRuntime.buildPrompt(...)` does:

```java
String systemPrompt = mergeText(context.getSystemPrompt(), runtimeInstructions());
```

So the system-layer text sent to the model is not only the string you configured, but also includes the runtime's own strategy.

### 5.2 `instructions` stays a separate field

It does not participate in this merge step, but is kept as:

- `AgentPrompt.instructions`

### 5.3 Different model clients map these two fields into different protocol shapes

For example:

- Chat path: both are lowered into system messages
- Responses path: `systemPrompt` goes into request-level instructions, `instructions` goes into a leading input item

This shows prompt layering is already part of the architecture, not just a documentary convention.

## 6. Why the tool layer must be split into two faces

### 6.1 Declaration face

`AgentToolRegistry` only answers:

> Which tools can the model see?

### 6.2 Execution face

`ToolExecutor` only answers:

> When a call actually happens, how does the system execute it?

### 6.3 The architectural benefits this brings

- The tool allowlist stays stable
- Execution governance is pluggable
- Approval / audit / sandbox can hang on the execution side
- The subagent tool surface can be uniformly exposed as an ordinary tool

Without this split, you would later have almost no elegant way to explain:

- Why the model "seeing" a tool does not equal "being able to execute" it
- Why approval should sit in the executor
- Why a subagent is a tool-like handoff

## 7. The real boundary between the state layer and the session layer

### 7.1 `AgentMemory` carries the runtime context

What it records is not an abstract "memory" concept, but:

- User input
- Model output
- Tool output
- summary / snapshot

### 7.2 The architectural meaning of `newSession()`

The implementation of `Agent.newSession()` only:

- Based on the same `baseContext`
- Replaces it with a new memory

So:

- A new session is not a new Agent world
- Just a new state space

### 7.3 When you should rebuild an Agent

If what you want to change is:

- runtime
- tool allowlist
- execution permissions
- model protocol
- prompt templates

then you should not just open a new session; you should re-assemble the Agent.

## 8. How events and trace fit into the architecture

### 8.1 Events come from the runtime main chain

Current key events include:

- `STEP_START`
- `STEP_END`
- `MODEL_REQUEST`
- `MODEL_REASONING`
- `MODEL_RESPONSE`
- `MODEL_RETRY`
- `TOOL_CALL`
- `TOOL_RESULT`
- `FINAL_OUTPUT`
- `ERROR`

These are not guessed from the outside; they are actively published by the runtime at key points.

### 8.2 Trace is an event projection, not a runtime-embedded span

If the Builder finds you configured:

- `traceExporter(...)`

it attaches, on the `eventPublisher`:

- `AgentTraceListener`

The latter then folds events into spans such as:

- `RUN`
- `STEP`
- `MODEL`
- `TOOL`
- `HANDOFF`
- `TEAM_TASK`

This shows trace is a formal side-channel in the architecture, not an extra log plugin.

## 9. Defaults, limits, and failure semantics

### 9.1 `modelClient` is the only hard-required dependency

The Builder phase validates directly:

- `modelClient`

A missing one throws `IllegalStateException`.

### 9.2 `maxSteps = 0` means no upper limit

:::warning
This is an experiment-friendly default, but not a production-safe default.
:::

### 9.3 Tool exceptions do not abort the whole run by default

`BaseAgentRuntime.executeTool(...)` catches exceptions and converts the result into:

```text
TOOL_ERROR: {...}
```

then writes it back into memory, letting the model see this failure result.

This means the default failure semantics lean toward:

- Recoverable context

rather than:

- Immediate termination

### 9.4 Illegal tool calls are validated first, then error-fed back

`AgentToolCallSanitizer` does structural validation first; when invalid, it does not throw directly either, but converts the result into an error and feeds it back.

### 9.5 Parallel tool calls require the executor to be thread-safe

Only when:

- `parallelToolCalls == true`
- the number of valid calls is greater than 1

does the runtime spin up a thread pool to run tools in parallel.

:::note
So a custom executor must guarantee thread safety itself.
:::

## 10. What this architecture does not solve

It does not automatically solve:

- Code-repo host approval
- Terminal UI / TUI / ACP host
- checkpoint / compact outer loop
- Visual node-based platforms

These all need to be picked up by higher-level modules.

## 11. Recommended source-reading order

- `ai4j-agent/src/main/java/io/github/lnyocly/ai4j/agent/Agents.java`
- `ai4j-agent/src/main/java/io/github/lnyocly/ai4j/agent/AgentBuilder.java`
- `ai4j-agent/src/main/java/io/github/lnyocly/ai4j/agent/AgentContext.java`
- `ai4j-agent/src/main/java/io/github/lnyocly/ai4j/agent/Agent.java`
- `ai4j-agent/src/main/java/io/github/lnyocly/ai4j/agent/runtime/BaseAgentRuntime.java`
- `ai4j-agent/src/main/java/io/github/lnyocly/ai4j/agent/model/AgentModelClient.java`
- `ai4j-agent/src/main/java/io/github/lnyocly/ai4j/agent/tool/AgentToolRegistry.java`
- `ai4j-agent/src/main/java/io/github/lnyocly/ai4j/agent/tool/ToolExecutor.java`
- `ai4j-agent/src/main/java/io/github/lnyocly/ai4j/agent/memory/AgentMemory.java`
- `ai4j-agent/src/main/java/io/github/lnyocly/ai4j/agent/event/AgentEventPublisher.java`

## 12. Further reading

1. [Quickstart](/docs/agent/quickstart)
2. [Tools and Registry](/docs/agent/tools-and-registry)
3. [Memory and State](/docs/agent/memory/memory-and-state)
4. [Runtime Implementations](/docs/agent/runtimes/runtime-implementations)
5. [Trace and observability](/docs/agent/observability/trace-observability)
