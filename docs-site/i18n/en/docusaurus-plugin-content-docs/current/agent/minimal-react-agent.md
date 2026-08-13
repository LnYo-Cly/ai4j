---
sidebar_position: 2
title: "Minimal ReAct Agent"
description: "Dissects the minimal but complete ReAct Agent run loop: AgentBuilder default wiring, the relationship between ReActRuntime and BaseAgentRuntime, the boundary between tool declaration and execution, and when an empty-tool Agent is valid."
tags: [concept]
---

# Minimal ReAct Agent

This page is not about the "shortest demo"; it is about the minimal but complete run loop of the AI4J Agent layer.

"Minimal" here does not mean "only a few lines of code" — it means keeping only the components that genuinely make up an Agent loop:

- One `AgentModelClient`
- One `model`
- One `AgentRuntime`
- One `AgentMemory`
- An optional tool declaration surface and execution surface

If this layer is not yet working, you should not jump ahead to CodeAct, SubAgent, StateGraph, or Agent Teams.

## 1. First, grasp the three key design decisions

### 1.1 ReAct is not "a mode" — it is the default runtime

`Agents.react()` is essentially not a completely separate product entry point; it is a convenience wrapper over `AgentBuilder`.

Looking at the default wiring in `AgentBuilder.build()`:

- When `runtime == null`, `ReActRuntime` is used by default
- When `memorySupplier == null`, `InMemoryAgentMemory::new` is used by default
- When `toolRegistry == null`, `StaticToolRegistry.empty()` is used by default

In other words, ReAct is not an extra plugin; it is the default main line of the Agent layer.

### 1.2 The minimal Agent is still valid even without tools

Many people, when first approaching agents, assume that "without tool calls, it doesn't count as an Agent."

That is not true in AI4J.

Even when `toolRegistry` is empty, the minimal ReAct Agent still has:

- Unified `AgentPrompt` assembly
- `AgentMemory` state accumulation
- The step loop
- Streaming / event / trace integration points

Tools only give the loop more external action capability; they are not a precondition for an Agent to exist.

### 1.3 Session isolation only swaps memory — it does not swap the entire runtime environment

The implementation of `Agent.newSession()` is very important:

```java
AgentMemory memory = memorySupplier == null ? baseContext.getMemory() : memorySupplier.get();
AgentContext sessionContext = baseContext.toBuilder().memory(memory).build();
```

This means a new session reuses:

- The same runtime
- The same model client
- The same tool registry
- The same tool executor

Only `memory` is replaced.

So the session boundary is "state isolation", not "runtime environment isolation".

## 2. What this page actually solves

The value of the minimal ReAct Agent is not to teach you to assemble a demo, but to first separate out the following sets of concepts:

- A single model call vs a multi-step Agent loop
- The tool exposure surface vs the tool execution surface
- A single run vs a persistent session
- Runtime strategy vs model protocol

Sort out these boundaries first, and the later CodeAct, SubAgent, and Teams will not get more confusing as you learn them.

## 3. The minimal object graph

The core objects involved in the minimal ReAct Agent are not many, but their relationships must be seen clearly first:

```text
Agents.react()
  -> AgentBuilder
  -> AgentContext
  -> Agent
  -> ReActRuntime
  -> AgentModelClient
  -> AgentMemory
  -> AgentToolRegistry + ToolExecutor
```

The most critical division of responsibility is:

| Object | True responsibility |
| --- | --- |
| `AgentBuilder` | Wires default dependencies and the execution chain |
| `Agent` | Exposes `run(...)`, `runStream(...)`, `newSession()` |
| `ReActRuntime` | Decides how the loop advances |
| `AgentModelClient` | Adapts the underlying model protocol |
| `AgentMemory` | Stores inputs, outputs, and tool results |
| `AgentToolRegistry` | Tells the model "which tools exist" |
| `ToolExecutor` | Decides "how a tool is actually executed" |

The two easiest to conflate are the last two. By design, AI4J separates "capability declaration" from "capability execution".

## 4. What the `AgentBuilder` default wiring chain actually does

To understand the minimal ReAct Agent, the class most worth reading directly is `AgentBuilder`.

What it does in `build()` is not a simple `new Agent(...)`; it is a full set of default wiring:

1. Resolve the runtime
   - Default `ReActRuntime`
2. Resolve the memory supplier
   - Default `InMemoryAgentMemory::new`
3. Resolve the base tool registry
   - Default `StaticToolRegistry.empty()`
4. If SubAgents are configured, merge the subagent tools
5. If no `ToolExecutor` is explicitly provided, try to create a default executor based on tool names
6. Resolve the default `CodeExecutor`
   - Java 8 -> `NashornCodeExecutor`
   - Higher versions -> `GraalVmCodeExecutor`
7. Construct the `AgentContext`

This wiring chain tells you something important:

- The minimal runnable state of a ReAct Agent does not require you to manually assemble a pile of objects
- But once you want to govern tools, swap memory, insert a subagent, or switch the runtime, you must come back to `AgentBuilder` to understand the default behavior

## 5. How "thin" `ReActRuntime` really is in the current implementation

Many people assume ReActRuntime has a lot of complex logic of its own, but the current implementation is actually very thin:

- `runtimeName() -> "react"`
- `runtimeInstructions() -> "Use tools when necessary. Return concise final answers."`

The actual main loop lives almost entirely in `BaseAgentRuntime`.

This has two direct implications:

1. ReAct is the runtime "closest to the framework's default capabilities"
2. When you tune ReAct behavior, many issues actually require going back to look at `BaseAgentRuntime`

## 6. How the minimal execution chain runs

A single `run(...)` of the minimal ReAct Agent follows this key flow:

1. `Agent.run(request)`
2. `ReActRuntime.run(...)`
3. `BaseAgentRuntime.runInternal(...)`
4. Write the user input into `AgentMemory`
5. `buildPrompt(...)` assembles the `AgentPrompt`
6. `AgentModelClient.create(...)` or `createStream(...)`
7. If the model returns `memoryItems`, write them back to memory
8. Normalize `toolCalls`
9. Validate the tool calls
10. Invoke `ToolExecutor.execute(...)`
11. Write the tool results back to memory
12. If there are no tool calls, end; otherwise continue to the next turn

The most important point of this chain is:

- Tool results are not returned directly to the business layer
- They are first written into memory, which then decides what the next turn's prompt looks like

So the essence of an Agent is not "one question, one answer" — it is "the output in turn shapes the next turn's input".

## 7. What the minimal working example should actually validate

From an engineering perspective, a minimal working example must at least explicitly set:

- `modelClient(...)`
- `model(...)`

```java
ResponsesService responsesService = aiService.getResponsesService(PlatformType.OPENAI);

Agent agent = Agents.react()
        .modelClient(new ResponsesModelClient(responsesService))
        .model("gpt-4.1")
        .instructions("You are a concise assistant.")
        .build();

AgentResult result = agent.run(AgentRequest.builder()
        .input("Introduce the AI4J Agent in one sentence")
        .build());
```

What this example actually validates is:

- Whether the model path is wired up
- Whether the runtime can run
- Whether `AgentMemory` accumulates inputs and outputs normally
- Whether `AgentResult` closes out correctly

It is not validating tool calls, because tools have not been introduced at this step.

## 8. The boundary between an "empty-tool Agent" and a "tool-bearing Agent"

### 8.1 Without tools

What you get is:

- A model runtime with memory
- A loop that can advance over multiple steps
- An execution entry point where trace / stream / event can be plugged in

### 8.2 With tools

Only then do you truly enter:

- The tool exposure surface
- Parameter validation
- Tool execution
- Tool result feedback

So the learning order should be:

1. First get the empty-tool Agent running
2. Then connect a minimal tool allowlist
3. Then discuss governance and extension

Otherwise it is easy to conflate the issues.

## 9. Why tool integration requires understanding `Registry` and `Executor` first

This is arguably one of the most important boundaries in the entire Agent layer.

### 9.1 `AgentToolRegistry`

It answers only one question:

> Which tools can the model see?

### 9.2 `ToolExecutor`

It also answers only one question:

> When the model actually initiates a call, how does the system execute it?

This separation brings large engineering benefits:

- The tool allowlist can exist stably
- Permission approval does not have to be written into the schema
- Audit, rate limiting, sandbox, and proxy forwarding can be attached to the execution surface

So if your goal is permission governance, the focus is always on `ToolExecutor`, not on "hiding tools".

## 10. The real boundary of the convenience `toolRegistry(List<String>, List<String>)`

`AgentBuilder` provides a very handy entry point:

```java
.toolRegistry(Arrays.asList("queryWeather"), Collections.<String>emptyList())
```

But it is essentially a reflective convenience API, which tries to initialize:

- `ToolUtilRegistry`
- `ToolUtilExecutor`

If the corresponding module is not on the classpath, `build()` will throw `IllegalStateException` directly.

So this API is more suitable for:

- Quick demos
- Scenarios where the known tool modules are fully available

If you are doing stable engineering integration, you should explicitly provide:

- `AgentToolRegistry`
- `ToolExecutor`

## 11. The most common pitfalls in default values and failure semantics

### 11.1 `maxSteps = 0` is not a safe default

In `BaseAgentRuntime.runInternal(...)`:

:::warning maxSteps has no upper limit by default
- Only `maxSteps > 0` counts as having a step limit
- Otherwise the loop has no hard limit

This is convenient for experiments but usually unsuitable for production.
:::

### 11.2 Tool errors are written back to memory by default, not thrown directly

`executeTool(...)` catches the exception and constructs:

```text
TOOL_ERROR: {"errorType":"...","error":"...","tool":"...","callId":"..."}
```

Then continues the main loop.

This means the default semantics are:

- Tool failure is treated first as "recoverable context"
- Not as "immediately terminate the entire run"

### 11.3 Parallel tool calls depend on executor thread safety

Parallel execution only happens when both of the following conditions hold:

- `parallelToolCalls == true`
- The number of legal tool calls in the same turn is greater than 1

Parallelism uses a thread pool opened by the runtime itself, so your custom `ToolExecutor` must be thread-safe on its own.

## 12. The true boundary between `Agent` and `AgentSession`

### `Agent`

More like "a run entry point that shares configuration and default dependencies".

### `AgentSession`

More like "swapping in a fresh piece of memory under the same runtime environment".

Therefore:

- To switch user context or session state, use `newSession()`
- To switch the runtime, tool surface, execution permissions, or model configuration, it is clearer to `build()` a new Agent

## 13. When to leave "minimal ReAct"

Continuing to stay on minimal ReAct is no longer enough, usually because you have run into one of the following:

- Need to execute model-generated code: go to [CodeAct Runtime](/docs/agent/codeact-runtime)
- Need explicit nodes and state advancement: go to [Workflow StateGraph](/docs/agent/workflow-stategraph)
- Need master-delegate delegation: go to [SubAgent and Handoff Policy](/docs/agent/subagent-handoff-policy)
- Need team collaboration: go to [Agent Teams](/docs/agent/agent-teams)

The criterion is not "how many features there are", but whether the current problem still belongs to a single tool loop.

## 14. Recommended source reading order

- `ai4j-agent/src/main/java/io/github/lnyocly/ai4j/agent/AgentBuilder.java`
- `ai4j-agent/src/main/java/io/github/lnyocly/ai4j/agent/Agent.java`
- `ai4j-agent/src/main/java/io/github/lnyocly/ai4j/agent/AgentSession.java`
- `ai4j-agent/src/main/java/io/github/lnyocly/ai4j/agent/runtime/BaseAgentRuntime.java`
- `ai4j-agent/src/main/java/io/github/lnyocly/ai4j/agent/runtime/ReActRuntime.java`
- `ai4j-agent/src/main/java/io/github/lnyocly/ai4j/agent/tool/AgentToolRegistry.java`
- `ai4j-agent/src/main/java/io/github/lnyocly/ai4j/agent/tool/ToolExecutor.java`

## 15. Further reading

1. [Agent Architecture](/docs/agent/architecture)
2. [Tools and Registry](/docs/agent/tools-and-registry)
3. [Memory and State](/docs/agent/memory-and-state)
4. [CodeAct Runtime](/docs/agent/codeact-runtime)
