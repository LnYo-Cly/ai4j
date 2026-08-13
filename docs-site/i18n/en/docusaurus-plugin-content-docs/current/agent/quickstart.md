---
title: "Agent Quickstart"
description: "Walks you through a minimal but real Agent main path: AgentBuilder default wiring, the ReActRuntime step loop, AgentModelClient protocol adaptation, AgentMemory write-back, and AgentResult convergence."
tags: [how-to]
---

# Agent Quickstart

The goal of this page is not to show the "shortest demo", but to help you first get a minimal yet real Agent main path working.

By "real" we mean the path must go through at least:

- `AgentBuilder` default wiring
- the `AgentRuntime` step loop
- `AgentModelClient` protocol adaptation
- `AgentMemory` write and write-back
- `AgentResult` convergence

If this path is not yet working, you should not pile on:

- workflow
- subagent
- team
- custom approval
- trace platform integration

## 1. First grasp 4 key design decisions

### 1.1 Quickstart first validates that "the runtime path holds", not "every feature is loaded"

The minimal quickstart has only one job:

> Prove that a single Agent run can travel from input to final output.

So the first version of the example should not, out of the gate, depend on:

- multiple tools
- external approval
- complex memory
- workflow orchestration

Otherwise you cannot tell which layer a problem lives in.

### 1.2 `modelClient(...)` is a required dependency; the model name is not

What `AgentBuilder.build()` truly hard-requires is:

- `modelClient != null`

If you do not pass it, it throws directly:

```text
IllegalStateException: modelClient is required
```

And while `model(...)` will not stop you at the Builder stage, `BaseAgentRuntime.buildPrompt(...)` checks at runtime:

```text
IllegalStateException: model is required
```

In other words:

- `modelClient` decides how the request is sent
- `model` decides who the request is sent to

Missing either one will not work.

### 1.3 The default `maxSteps = 0` is not a safe default

Among the defaults of `AgentOptions.builder().build()`:

- `maxSteps = 0`

And the semantics of `BaseAgentRuntime.runInternal(...)` are:

- only `maxSteps > 0` is treated as a hard cap
- otherwise the loop sets no step limit

So in the quickstart example it is best to set it explicitly:

```java
.options(AgentOptions.builder().maxSteps(1).build())
```

or:

```java
.options(AgentOptions.builder().maxSteps(2).build())
```

Do not mistake "convenient for experimentation" for "a safe production default".

### 1.4 `toolRegistry(List<String>, List<String>)` is a convenience entry point, not the only low-level one

What you are most likely to see in a quickstart is:

```java
.toolRegistry(Arrays.asList("queryWeather"), null)
```

But this API is essentially a reflective wiring entry point that tries to create:

- `ToolUtilRegistry`
- `ToolUtilExecutor`

If the relevant integration module is not on the classpath, `build()` will fail.

So the more robust way to understand it is:

- this is a demo / quick-wiring entry point
- it is not the only official production wiring approach

## 2. First correct validation path: run a tool-less Agent first

For the minimal quickstart, "tool-less validation" is the most recommended first step.

The reason is simple:

- no dependency on ToolUtil
- no dependency on a tool allowlist
- no dependency on tool execution
- it only validates the model protocol + runtime loop + memory write-back

Example:

```java
import io.github.lnyocly.ai4j.agent.Agent;
import io.github.lnyocly.ai4j.agent.AgentOptions;
import io.github.lnyocly.ai4j.agent.AgentRequest;
import io.github.lnyocly.ai4j.agent.AgentResult;
import io.github.lnyocly.ai4j.agent.Agents;
import io.github.lnyocly.ai4j.agent.model.ResponsesModelClient;

Agent agent = Agents.react()
        .modelClient(new ResponsesModelClient(responsesService))
        .model("gpt-4.1")
        .systemPrompt("You are a concise assistant.")
        .options(AgentOptions.builder().maxSteps(1).build())
        .build();

AgentResult result = agent.run(AgentRequest.builder()
        .input("Introduce the AI4J Agent in one sentence.")
        .build());

System.out.println(result.getOutputText());
System.out.println(result.getSteps());
```

If this step fails, the problem usually lies only in:

- provider credentials / baseUrl
- the `modelClient` protocol wiring
- the model name
- the most basic runtime execution

## 3. Second validation path: add a minimal tool allowlist

Once the tool-less Agent runs, adding tools becomes meaningful.

Example:

```java
Agent agent = Agents.react()
        .modelClient(new ResponsesModelClient(responsesService))
        .model("gpt-4.1")
        .systemPrompt("You are a weather assistant.")
        .instructions("Use queryWeather when weather information is needed.")
        .toolRegistry(java.util.Arrays.asList("queryWeather"), null)
        .options(AgentOptions.builder().maxSteps(2).build())
        .build();

AgentResult result = agent.run(AgentRequest.builder()
        .input("Give me a weather summary for Beijing today.")
        .build());
```

What this validates is not "whether a weather tool exists in the system", but whether the following three things hold at the same time:

1. The tool schema can be exposed to the model
2. The model actually returns a tool call
3. The `ToolExecutor` can execute it and write the result back to memory

## 4. What actually happens behind this code

The most valuable thing about the quickstart is not the line count, but that it happens to cover the default wiring path.

### 4.1 `Agents.react()` is not a magic entry point

It essentially just enters `AgentBuilder` and defaults to `ReActRuntime`.

### 4.2 What default values `AgentBuilder.build()` fills in

The current default wiring includes:

- `runtime` -> `ReActRuntime`
- `memorySupplier` -> `InMemoryAgentMemory::new`
- `toolRegistry` -> `StaticToolRegistry.empty()`
- `codeExecutor` -> `NashornCodeExecutor` on Java 8, `GraalVmCodeExecutor` on higher versions
- `options` -> `AgentOptions.builder().build()`
- `codeActOptions` -> `CodeActOptions.builder().build()`
- `eventPublisher` -> a new `AgentEventPublisher`

If you also configure:

- `traceExporter(...)`

the Builder attaches an `AgentTraceListener` along the way.

### 4.3 Which path `run(...)` actually enters

The execution path is roughly:

```text
Agent.run(...)
  -> ReActRuntime.run(...)
  -> BaseAgentRuntime.runInternal(...)
  -> memory.addUserInput(...)
  -> buildPrompt(...)
  -> modelClient.create(...)
  -> memory.addOutputItems(...)
  -> normalizeToolCalls(...)
  -> execute tools if needed
  -> final AgentResult
```

If you do not yet understand this path, it is best not to keep adding new capabilities to the quickstart.

## 5. What the quickstart should validate first

### 5.1 First check whether it can complete one turn

Minimum requirements:

- no exception thrown
- `AgentResult.outputText` has a value
- `AgentResult.steps` is a reasonable value

### 5.2 Then check whether it unexpectedly enters multiple turns

If you only wanted a single round of pure Q&A, yet find:

- `steps > 1`

it usually means:

- the prompt induced tool behavior
- or the model output was interpreted as a condition to continue the loop

### 5.3 For the tool version, also check whether the loop fully closes

For the tool version, do not look only at the final answer; also check:

- `toolCalls`
- `toolResults`
- `steps`

Because sometimes the final answer looks "plausibly correct", but in fact no tool was ever triggered.

## 6. The fields in `AgentResult` most worth looking at

`AgentResult` currently has very few fields:

- `outputText`
- `rawResponse`
- `toolCalls`
- `toolResults`
- `steps`

Their troubleshooting value is:

| Field | Primary use |
| --- | --- |
| `outputText` | See the final answer |
| `rawResponse` | See the provider's raw response structure |
| `toolCalls` | See which tools the model actually wanted to call |
| `toolResults` | See whether the tools actually executed and what they produced |
| `steps` | See how many turns the loop ran |

## 7. The most common quickstart errors, and which layer each actually points to

### 7.1 Passing only a model name, without `modelClient`

This is not a model problem; it means the protocol adaptation layer was never wired in.

### 7.2 Calling a Core SDK service directly and assuming you have entered the Agent

Calling `IChatService` / `IResponsesService` directly is not yet an Agent.

Only when you enter the `AgentRuntime` main loop have you truly entered the Agent layer.

### 7.3 The tool exists, but the model cannot see it

This is usually not because the tool implementation was not written, but because you did not put the tool into:

- `toolRegistry`

### 7.4 `toolRegistry(List<String>, List<String>)` throws as soon as you call it

This is usually not a runtime problem, but rather:

- `ToolUtilRegistry`
- `ToolUtilExecutor`

the corresponding modules are not on the classpath.

### 7.5 Stacking workflow / subagent / team all at once from the start

This expands the troubleshooting surface from:

- a single Agent

to instantly:

- runtime
- tool surface
- handoff
- task board
- message bus

This is not faster; it is harder to locate.

## 8. When the quickstart is no longer enough

If you have run into any of the following, it is time to leave the quickstart behind:

- You want to decide whether `ChatModelClient` or `ResponsesModelClient` fits better
- You want to understand how `systemPrompt` and `instructions` actually map
- You want tool approval, interception, audit
- You want longer sessions or memory compaction
- You want to orchestrate Agent nodes into a workflow
- You want to switch to CodeAct / SubAgent / Team

The quickstart's mission is to help you get the entry point working first, not to carry all the complexity.

## 9. Recommended minimum validation order

1. First run a tool-less Agent
2. Then add a minimal tool allowlist
3. Then turn on tracing to inspect step / model / tool
4. Then consider memory / workflow / subagent / team

This order looks slow, but in practice yields the fastest troubleshooting.

## 10. Recommended source reading order

- `ai4j-agent/src/main/java/io/github/lnyocly/ai4j/agent/Agents.java`
- `ai4j-agent/src/main/java/io/github/lnyocly/ai4j/agent/AgentBuilder.java`
- `ai4j-agent/src/main/java/io/github/lnyocly/ai4j/agent/Agent.java`
- `ai4j-agent/src/main/java/io/github/lnyocly/ai4j/agent/AgentOptions.java`
- `ai4j-agent/src/main/java/io/github/lnyocly/ai4j/agent/runtime/BaseAgentRuntime.java`
- `ai4j-agent/src/main/java/io/github/lnyocly/ai4j/agent/runtime/ReActRuntime.java`
- `ai4j-agent/src/main/java/io/github/lnyocly/ai4j/agent/AgentResult.java`

## 11. What to read next

1. [Minimal ReAct Agent](/docs/agent/minimal-react-agent)
2. [Model Client Selection](/docs/agent/model-client-selection)
3. [Tools and Registry](/docs/agent/tools-and-registry)
4. [Memory and State](/docs/agent/memory-and-state)
5. [Runtime Implementations](/docs/agent/runtime-implementations)
