---
sidebar_position: 5
title: "Runtime Implementations"
description: "Breaks down the three runtimes — ReActRuntime, CodeActRuntime, and DeepResearchRuntime: they share the BaseAgentRuntime main loop, and differ in the intermediate representation of model output, when to switch that representation, and when to write a custom runtime."
tags: [concept]
---

# Runtime Implementations

What `AgentRuntime` decides is not "which provider the model uses", but rather "how a single Agent run advances, when it stops, when it emits events, and how tool results are fed back in".

The same `AgentBuilder` can switch between `ReActRuntime`, `CodeActRuntime`, and `DeepResearchRuntime` because these three runtimes represent three distinct execution semantics, not wording variations of the same chain.

## 1. First grasp the 4 key design decisions

### 1.1 The runtime owns advancement semantics, not provider wiring

The `AgentRuntime` interface only takes:

- `AgentContext`
- `AgentRequest`

It does not care whether the underlying layer is OpenAI, Doubao, or any other provider, nor does it care how tools are concretely implemented.

So the runtime layer answers:

- Whether the current turn has reason to continue
- When tool results enter the next turn
- When the final output is triggered

### 1.2 `BaseAgentRuntime` is the real default main-loop core

ReAct, CodeAct, and DeepResearch are not three unrelated executors.

In the current implementation:

- `ReActRuntime` reuses `BaseAgentRuntime` almost entirely
- `DeepResearchRuntime` just inserts a planning phase, then returns to `BaseAgentRuntime`
- `CodeActRuntime` overrides the intermediate representation at the key points of "model output -> execution -> feedback"

This means that when you tune runtime behavior, you should usually look at `BaseAgentRuntime` first, then see where each runtime diverges from it.

### 1.3 The essence of the runtime difference is "intermediate representation difference"

The real distinction between the three runtimes is not in their titles, but in what intermediate representation they require the model to produce:

- ReAct: text + native tool calls
- CodeAct: JSON code/final message
- DeepResearch: planning text + subsequent ReAct loop

This determines:

- How the model expresses the next action
- How the runtime interprets model output
- What kinds of intermediate state settle into memory

### 1.4 runtime is not workflow, and not an outer loop

The runtime is responsible for advancing a single Agent run.

It is not directly responsible for:

- Explicit node graphs
- Long-range auto-continue
- checkpoint / compact
- Host-level approval and workspace lifecycle

These capabilities belong to higher-level abstractions such as workflows / the coding-agent outer loop.

## 2. What the `AgentRuntime` abstraction actually defines

The interface itself is very narrow:

```java
public interface AgentRuntime {
    AgentResult run(AgentContext context, AgentRequest request) throws Exception;
    void runStream(AgentContext context, AgentRequest request, AgentListener listener) throws Exception;
    default AgentResult runStreamResult(...)
}
```

It leaves the runtime three categories of freedom:

1. How to assemble the prompt
2. How to interpret model output
3. How to decide whether the loop continues

And precisely because the interface is narrow enough, `AgentBuilder` can reuse the same:

- model client
- tool registry
- tool executor
- memory

across different runtimes.

## 3. `BaseAgentRuntime` is the default semantic engine

To understand the three built-in runtimes, the first thing you should read is `BaseAgentRuntime.runInternal(...)`.

### 3.1 What its main loop does

The core chain is as follows:

1. Read `maxSteps`
2. Decide whether to enter the streaming path
3. Validate `memory`
4. Write the user input into memory
5. Emit `STEP_START`
6. `buildPrompt(...)`
7. `executeModel(...)`
8. Write the `memoryItems` returned by the model back into memory
9. `normalizeToolCalls(...)`
10. Validate the calls with `AgentToolCallSanitizer`
11. Execute the tools
12. Write the tool results back into memory
13. If there is no tool call this turn, emit the final result; otherwise continue to the next turn

This chain effectively defines the complete execution semantics of the default AI4J Agent.

### 3.2 What it already has built in

`BaseAgentRuntime` already handles for upper layers:

- `maxSteps`
- stream / non-stream branching
- tool call id completion
- empty tool name fallback
- tool argument structure validation
- routing validation failures to `TOOL_ERROR`
- parallel tool execution
- standard event emission

This means that when you customize a runtime, many needs do not require reimplementing the entire loop from scratch — you only have to replace the part that is genuinely different.

### 3.3 What the default failure semantics are

A very key fact:

- A tool exception does not abort the Agent run immediately by default

`executeTool(...)` flattens the exception into:

```text
TOOL_ERROR: {"errorType":"...","error":"...","tool":"...","callId":"..."}
```

It then writes this error text back into memory so the model sees it on the next turn.

So the default runtime treats failure as "recoverable context" rather than "a non-resumable termination condition".

## 4. `ReActRuntime` is in fact the thinnest layer

`ReActRuntime` currently has almost no full complex implementation of its own.

It explicitly defines only two things:

- `runtimeName() = "react"`
- `runtimeInstructions() = "Use tools when necessary. Return concise final answers."`

In other words, the actual behavior of the ReAct runtime is almost entirely determined by `BaseAgentRuntime`.

This has two consequences:

1. It is the best entry point for understanding the entire Agent layer
2. Much of the ReAct behavior you encounter is actually Base runtime behavior, not ReAct-specific behavior

## 5. `CodeActRuntime` is the one that genuinely swaps the intermediate representation

The difference between CodeAct and ReAct is not "whether there are tools", but rather "a code-message protocol is layered in front of the tools".

### 5.1 Model output is first interpreted as a JSON protocol

`CodeActRuntime` requires the model to output:

- `{"type":"code","language":"...","code":"..."}`
- `{"type":"final","output":"..."}`

This means the model does not directly emit native tool calls; it first decides:

- Whether I should produce code
- What language that code uses
- Whether I should now enter the final answer phase

### 5.2 Tools no longer enter the model protocol directly

Unlike `BaseAgentRuntime.buildPrompt(...)`, `CodeActRuntime.buildPrompt(...)` does not put `tools` into the `AgentPrompt`.

It takes another path:

- Turn the tool description into a text guide
- Let the code executor bridge tool calls at runtime

So CodeAct is:

- prompt protocol driven
- code executor bridged

rather than driven by the provider's native schema.

### 5.3 `reAct` decides whether to return to the model after execution

The difference made by `CodeActOptions.reAct` is not "toggling some minor detail"; it decides which layer the final answer belongs to:

- `false`: close out directly with the execution result whenever possible
- `true`: after successful execution, return to the model for a summary

This directly affects:

- token cost
- latency
- whether the output is more natural-language-like

## 6. `DeepResearchRuntime` is not as heavy as many people imagine

From the source, the current DeepResearch implementation is quite restrained.

### 6.1 It just inserts a plan first, then runs the default loop

`DeepResearchRuntime.run(...)` does not implement a completely independent new main loop.

What it actually does:

1. `preparePlan(...)`
2. If the request input is a string, call `Planner.plan(goal)`
3. Write the plan as:

```text
Plan:
1. ...
2. ...
```

4. Write it into memory as a `systemMessage`
5. Then call `super.run(...)`

So the essence of DeepResearch is closer to:

- planning-enhanced ReAct

rather than a full research platform.

### 6.2 The default planner is very light

The implementation of `Planner.simple()` is just:

- If goal is not empty, return a single-element list containing `goal` itself

So default DeepResearch does not automatically produce complex task decomposition.

If this page does not make this point clear, readers can easily mistake it for having complex research orchestration by default.

## 7. How to understand the real differences between the three runtimes

### 7.1 ReAct

Closest to the framework's default semantics.

Fits:

- General Q&A
- Light or moderate-complexity tool calls
- Entering the Agent loop with as few constraints as possible

### 7.2 CodeAct

Swaps the intermediate representation, turning "the model calls tools directly" into "the model first produces code, then the code calls tools".

Fits:

- Multi-tool batch processing
- Aggregation, transformation, sorting
- Tasks that need an explicit, executable process

### 7.3 DeepResearch

Not a more complex loop, but planning inserted in front of the default loop.

Fits:

- When you want an explicit task breakdown before execution
- When you want the model to enter the tool loop with a structured plan

## 8. The right selection question is not "which is more powerful"

What you should really ask is:

### 8.1 What next action does the model need to express right now

- Call tools directly -> ReAct
- Write code first, then call tools -> CodeAct
- Decompose a plan first, then advance -> DeepResearch

### 8.2 What intermediate state should settle into memory

- Tool results -> ReAct
- `CODE_RESULT / CODE_ERROR` -> CodeAct
- plan text + tool results -> DeepResearch

### 8.3 Which class of stability are you optimizing for

- Plain tool loop stability -> ReAct
- Tool orchestration and intermediate computation stability -> CodeAct
- Explainability of the plan-then-execute path -> DeepResearch

## 9. Where to cut in when customizing a runtime

If you want to build your own runtime, there are usually two routes.

### 9.1 Only change a small piece of semantics

Prefer to extend `BaseAgentRuntime` and override:

- `runtimeName()`
- `runtimeInstructions()`
- `buildPrompt(...)`
- a local part of `runInternal(...)`

Minimal skeleton (model on `ReActRuntime`, which itself only overrides `runtimeName()` and reuses the base main loop):

```java
public class MyRuntime extends BaseAgentRuntime {
    @Override
    protected String runtimeName() {
        return "my-runtime";
    }

    // Only override when you need to change the main loop; otherwise fully reuse base runInternal
    // @Override
    // protected AgentResult runInternal(AgentContext context, AgentRequest request,
    //                                   AgentListener listener) throws Exception { ... }
}
```

Attach it to an Agent: `Agents.builder().runtime(new MyRuntime())...build()` (or via `AgentBuilder.runtime(...)`).

### 9.2 Genuinely replace the main loop

Only when you need to change:

- the loop termination condition
- the model output interpretation protocol
- the memory feedback mechanism

— these core-chain behaviors — is it worth rewriting a larger chunk of `runInternal(...)`.

Otherwise, reusing the base runtime is more stable.

## 10. Keep the boundaries between runtime, workflow, and outer loop distinct

### What the runtime answers

- How a single Agent run advances
- How model output is interpreted
- When tool results enter the next turn

### What the workflow answers

- How to transition between multiple nodes
- How to organize state-graph conditions

### What the coding-agent outer loop answers

- Auto-continue
- checkpoint / compact
- Host-level blocked / approval
- Workspace lifecycle

Once these three layers are conflated, it is easy to make the runtime do too much, or to force upper-layer needs into the runtime where they don't belong.

## 11. Recommended source reading order

- `ai4j-agent/src/main/java/io/github/lnyocly/ai4j/agent/runtime/BaseAgentRuntime.java`
- `ai4j-agent/src/main/java/io/github/lnyocly/ai4j/agent/runtime/ReActRuntime.java`
- `ai4j-agent/src/main/java/io/github/lnyocly/ai4j/agent/runtime/CodeActRuntime.java`
- `ai4j-agent/src/main/java/io/github/lnyocly/ai4j/agent/runtime/DeepResearchRuntime.java`
- `ai4j-agent/src/main/java/io/github/lnyocly/ai4j/agent/runtime/Planner.java`

## 12. Further reading

1. [Agent Architecture](/docs/agent/architecture)
2. [Tools and Registry](/docs/agent/tools-and-registry)
3. [Memory and State](/docs/agent/memory/memory-and-state)
4. [CodeAct Runtime](/docs/agent/runtimes/codeact-runtime)
5. [CodeAct Custom Sandbox](/docs/agent/runtimes/codeact-custom-sandbox)
