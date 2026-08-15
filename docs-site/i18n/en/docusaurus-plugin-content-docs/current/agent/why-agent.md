---
title: "Why Agent"
description: "Answers why AI4J needs a separate Agent layer: multi-step execution sooner or later forces out a runtime, a state source, and tool governance. Agent unifies the main loop, state semantics, governance boundaries, and observability, rather than rebuilding a parallel framework."
tags: [concept]
---

# Why Agent

The `Agent` layer exists not to wrap another layer around the Core SDK, but because once a system moves into multi-step execution, business code sooner or later grows its own runtime.

This is not abstraction enthusiasm; it is an engineering fact.

As soon as you start handling any of the following:

- whether to continue after a turn ends
- how to execute a tool call returned by the model
- how tool results feed into the next turn
- how to preserve state for the same task across multiple steps
- when something goes wrong, how to tell which step is stuck

you are no longer merely "calling the model" — you are implementing an Agent runtime.

## 1. First, grasp six key design decisions

### 1.1 The Core SDK solves "capability access"; Agent solves "capability orchestration"

The Core SDK already handles many concerns:

- provider integration
- Chat / Responses requests
- tool schema
- MCP
- RAG / Search / Vector

But it does not decide for you:

- when to continue to the next turn
- when a tool call is executed
- whether a tool error terminates the loop or is fed back
- how session state is carried forward

All of these fall outside the scope of "calling an API".

### 1.2 Once the model drives tool calls, business code is no longer a simple orchestrator

If the tools are called by decision of the business code itself, you can still treat the model as a "pure function".

But when the model itself returns a tool call, the system must immediately answer:

- whether to call it
- whether it can be called
- whether the arguments are valid
- how the next turn's prompt changes after the call

At this point the application layer is no longer "the master that invokes tools"; it is hosting a loop system.

### 1.3 Multi-step tasks naturally force out a state source

A single call gives little reason to discuss state seriously.

But a multi-step task inevitably raises these questions:

- where to store user input
- where to store model output
- how tool results are fed back
- how the history is pruned
- how new sessions are kept separate from old ones

AI4J uses `AgentMemory` to explicitly absorb this layer of concern, rather than leaving business code to hand-write message lists everywhere.

### 1.4 "Hand-writing an if/else main loop" spirals out of control quickly

At first it looks simple:

1. call the model
2. if there is a tool call, execute it
3. call the model again

But more problems soon grow:

- `maxSteps`
- stream events
- tool call argument validation
- parallel tool calls
- error feedback
- trace
- session

At this point the code is no longer "auxiliary logic"; it is an expanding runtime.

### 1.5 Agent's value is not making the model smarter, but making the runtime more stable

`ai4j-agent` does not make model capabilities leap out of thin air.

The stable gains it actually delivers are:

- a unified main loop
- unified state semantics
- unified tool governance boundaries
- a unified event stream
- a unified entry point for switching runtimes

### 1.6 This layer must grow against the AI4J foundation, not rebuild a parallel framework

AI4J's Agent is not an independent technology stack; it is a continuation of:

- provider integration
- tool infrastructure
- MCP
- the Java 8 compatibility boundary

This means it is the natural upper layer of the SDK foundation, not an entirely separate Agent Framework bolted on from outside.

## 2. When the Core SDK is already enough

In the following scenarios, there is usually no need to adopt Agent first:

- single-shot Q&A or one-off structured generation
- tool calls are explicitly decided by business code
- the workflow is already explicitly orchestrated by the application layer, with the model as just one node
- you only need basic context, with no tool loop

These problems are essentially still about:

- how to construct one request
- how to parse one response

The Core SDK is lighter, more direct, and makes complexity easier to control.

## 3. When the problem has escalated into an Agent problem

When the following signals appear in the system, the nature of the problem has changed:

### 3.1 You start needing "what to do after this turn"

This corresponds to the runtime loop problem, not a single-request problem.

### 3.2 Tool results must return to the model, not directly to the business layer

As long as tool output still has to be fed back to the model, you have already entered the Agent loop.

### 3.3 You start maintaining persistent state

No matter what it is called:

- memory
- session
- history
- task context

in essence you are maintaining continuously runnable state.

### 3.4 You start caring about step budget, trace, retry, and the event stream

All of these indicate that you are no longer just "assembling an API request".

### 3.5 You need to switch between execution strategies

For example:

- plain ReAct
- CodeAct
- plan-then-execute

Here "calling the model" has already become "choosing a runtime".

## 4. What AI4J Agent concretely solves is not a category of concepts, but a category of code

### 4.1 Loop code

`BaseAgentRuntime.runInternal(...)` already unifies the default main loop:

- write user input
- assemble the prompt
- call the model
- write back to memory
- normalize tool calls
- validate
- execute tools
- write back results
- decide whether to continue

This spares you from re-growing a loop in every project.

### 4.2 State code

`AgentMemory` uniformly holds:

- user input
- model output
- tool output

This prevents the business layer from assembling state on its own as "a semi-structured message array plus a few temporary variables".

### 4.3 Governance code

The split between `AgentToolRegistry` and `ToolExecutor` fixes two boundary layers directly:

- tool visibility
- tool execution permission

So permission approval, interception, and audit no longer need to rely on fragile tricks like "hiding the tool".

### 4.4 Observation code

`AgentEventPublisher` and the trace system turn:

- `STEP_START`
- `MODEL_REQUEST`
- `MODEL_RESPONSE`
- `TOOL_CALL`
- `TOOL_RESULT`
- `FINAL_OUTPUT`

into formal events at these key stages, rather than external guesswork.

## 5. Why not just hand-write a set in business code

Of course you can write your own.

But writing your own soon runs into five problems:

### 5.1 Protocol logic and runtime logic become tangled

You will write:

- Chat / Responses protocol details
- tool loop
- state management

in the same layer of code.

### 5.2 Every project re-grows a similar but incompatible main loop

At first the differences are small; later reuse gets harder and harder.

### 5.3 Switching runtimes means starting over

For example, from:

- ReAct

to:

- CodeAct

Without a unified runtime boundary, this usually amounts to rewriting a large stretch of the call chain.

### 5.4 trace, session, and subagent become patches

Without a unified runtime, these capabilities can only be patched in from the outside, getting more fragmented over time.

### 5.5 Error semantics become inconsistent

For example:

- whether a tool failure should terminate
- how validation errors are handled
- whether exceeding the step count counts as a failure

If every project defines these itself, the system's behavior eventually becomes hard to predict.

## 6. Why Agent inside AI4J, rather than another standalone framework

This point matters especially for Java engineers.

AI4J's Agent does not expect you to:

1. first bring in an independent Agent Framework
2. then bridge it back to the Java SDK
3. then bridge it back to MCP / Tool / RAG / the host system

It takes a different route:

- Agent is built directly on top of the AI4J foundation

This brings very practical gains:

### 6.1 You can keep reusing the existing capability surface

- provider
- tools
- MCP
- RAG
- memory

### 6.2 You stay within the existing Java compatibility boundary

This matters especially for keeping Java 8 compatibility inside a monorepo.

### 6.3 You can naturally reach higher-level modules

- `ai4j-agent`
- `ai4j-coding`
- `ai4j-cli`
- `ai4j-flowgram-*`

You can rise layer by layer along the same set of foundational abstractions.

## 7. The main benefits Agent actually delivers

### 7.1 A unified main loop

You no longer hand-write a "model -> tool -> model again" loop in every project.

### 7.2 Swappable execution strategies

Through the `AgentRuntime` abstraction, you can switch between:

- ReAct
- CodeAct
- DeepResearch

### 7.3 Unified state semantics

Through `AgentMemory`, state is no longer just a byproduct of prompt assembly, but a formal object.

### 7.4 Unified tool governance boundaries

Through the registry / executor separation, permission governance finally has a stable anchor.

### 7.5 Unified observability

Through the standard event stream and trace projection, the run is no longer a black box.

## 8. What Agent does not solve

This layer also has clear boundaries.

### 8.1 It does not replace the Core SDK

For a single model call, the Core SDK is still more suitable.

### 8.2 It does not automatically become a product-grade coding assistant

Agent does not automatically provide:

- workspace-aware permissions
- process management
- checkpoint / compact outer loop
- CLI / TUI / ACP host

These belong to `ai4j-coding` and `ai4j-cli`.

### 8.3 It does not replace explicit workflow design

If the problem is inherently a graph-style node transition, `workflow` / `StateGraph` / `Flowgram` is more appropriate.

### 8.4 It does not automatically define your security policy

The framework provides governance boundaries, not the final approval policy for your business scenario.

## 9. The cost and constraints of using Agent

After moving to Agent, complexity does rise.

You have to start taking seriously:

- step budget
- session / state
- the tool exposure surface and execution surface
- regression validation
- observation and troubleshooting

So "entering Agent" is both a capability upgrade and an engineering-responsibility upgrade.

## 10. A more practical decision table

| Problem type | More suitable choice |
| --- | --- |
| Single-shot answer, one-off structured output | Core SDK |
| Model calls tools on demand, needs a multi-turn closed loop | Agent |
| Local repo interaction, approval, session recovery | Coding Agent |
| Explicit node graph, task API, platform-grade execution | Flowgram |

## 11. Recommended source reading order

- `ai4j-agent/src/main/java/io/github/lnyocly/ai4j/agent/AgentBuilder.java`
- `ai4j-agent/src/main/java/io/github/lnyocly/ai4j/agent/Agent.java`
- `ai4j-agent/src/main/java/io/github/lnyocly/ai4j/agent/runtime/BaseAgentRuntime.java`
- `ai4j-agent/src/main/java/io/github/lnyocly/ai4j/agent/memory/AgentMemory.java`
- `ai4j-agent/src/main/java/io/github/lnyocly/ai4j/agent/tool/AgentToolRegistry.java`
- `ai4j-agent/src/main/java/io/github/lnyocly/ai4j/agent/tool/ToolExecutor.java`
- `ai4j-agent/src/main/java/io/github/lnyocly/ai4j/agent/event/AgentEventPublisher.java`

## 12. Further reading

1. [Agent Overview](/docs/agent/overview)
2. [Architecture](/docs/agent/architecture)
3. [Quickstart](/docs/agent/quickstart)
4. [Tools and Registry](/docs/agent/tools-and-registry)
5. [Memory and State](/docs/agent/memory/memory-and-state)
