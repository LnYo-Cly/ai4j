---
sidebar_position: 8
title: "SubAgent and Handoff Policy"
description: "A breakdown of how SubAgent wraps another Agent into a governed tool — StaticSubAgentRegistry exposes the schema, SubAgentToolExecutor intercepts the handoff, and HandoffPolicy defines depth, timeout, deny/fallback, and session-mode semantics."
tags: [concept]
---

# SubAgent and Handoff Policy

The essence of SubAgent is not "the main agent calls another agent," but rather wrapping another `Agent` into a governed tool surface.

From the source, this chain splits into two layers:

- `StaticSubAgentRegistry` turns a subagent into a function tool
- `SubAgentToolExecutor` intercepts, governs, executes, and provides fallback

So SubAgent is fundamentally "tool delegation with a handoff policy," not "multi-agent chat."

## 0. Communication and concurrency model (establish the right mental model first)

The most critical point in understanding SubAgent: **communication is a tool call — a synchronous request-response, not a message stream.**

```text
parent agent model ──tool_call(delegate_xxx, {task})──► SubAgentToolExecutor
                                                       │ starts the sub-agent, runs the entire ReAct loop
                                                       ▼
parent agent  ◄──── tool_result (the sub-agent's outputText) ────┘
```

Once the sub-agent receives the task input it **runs to completion independently**, and cannot interact with the parent during execution. The parent only sees `outputText` after the sub-agent returns — **it cannot see the sub-agent's reasoning or intermediate tool calls** (this matches Claude Code subagents and is a deliberate context-economy design).

### Parallel subagents: the parent model emits multiple tool_calls in one turn

The SDK does not proactively fan out. The truth behind "the parent agent runs several sub-agents at once" is:

1. The parent model emits multiple tool_calls **in a single turn** (e.g. calling `delegate_research` and `delegate_write` at the same time)
2. If `parallelToolCalls=true`, the runtime uses a thread pool to **execute every tool_call of that turn in parallel**
3. But this is a **fork-join barrier**: the parent must wait for all tool_calls of the current turn to finish before moving to the next turn

So the trigger for parallelism is the **model's behavior** (how many calls it emits in one turn) plus the `parallelToolCalls` flag — the SDK does not schedule it for you.

:::note Gap versus Claude Code
Claude Code's background agent is **async** — the parent can keep doing other work after dispatching a child, and the child flows back asynchronously when done. AI4J's SubAgent is **synchronous and blocking**: the parent waits at the fork-join barrier for all children to return. This is imperceptible for short tasks (reading a file, grep); for long-running research sub-agents the parent blocks. This is a capability on the roadmap that is not yet shipped.
:::

### Nested handoff and depth

A sub-agent can also mount its own subagents (nested handoff). `HandoffContext` uses a ThreadLocal to record the recursion depth **on the thread the sub-agent runs on** — not by inheriting from the parent thread, but by setting it on that thread when the sub-agent starts (`HandoffContext.runWithDepth`). So **depth counting is correct under parallel nested handoff**, and `maxDepth` does not break down under parallelism.

## 1. Grasp the three key design decisions first

### 1.1 SubAgent is a tool surface first, a collaboration capability second

`AgentBuilder.build()` does two critical things during assembly:

1. Merges the subagent tools into the final `toolRegistry`
2. Wraps the original `ToolExecutor` with `SubAgentToolExecutor`

In other words, SubAgent is not side-logic bolted onto the runtime; it enters the standard "model-visible tool surface + tool execution surface" chain.

### 1.2 The governance point sits in the executor, not the registry

`StaticSubAgentRegistry` is only responsible for:

- Defining the tool schema
- Invoking the real subagent

The real policy control — for example:

- Whether handoff is allowed
- Maximum depth
- timeout
- retry
- deny / fallback

— all lives in `SubAgentToolExecutor`.

This shows AI4J has a clear stance on SubAgent:

- The registry exposes capability
- The executor governs capability

### 1.3 Session semantics are part of the subagent design

`SubAgentSessionMode` is not just an optional toggle; it directly decides whether the sub-agent:

- Runs in a fresh session each time
- Or accumulates memory across multiple handoffs in the same session

So SubAgent is not simply "another Agent instance" — it is a "delegated runtime with an explicit session policy."

## 2. Boundaries versus plain tools and Agent Teams

| Capability | Core abstraction | Suited for | Not suited for |
| --- | --- | --- | --- |
| Plain tool | `ToolExecutor` | Problems one synchronous function call can solve | Subtasks that need their own prompt and memory |
| SubAgent | `SubAgentDefinition` + `HandoffPolicy` | Encapsulating a complex specialized capability as a delegable tool | Shared task boards, message buses, proactive collaboration across members |
| Agent Teams | `AgentTeam` | Explicit team collaboration and task scheduling | One-shot master-slave delegation |

SubAgent fits best when:

- You still want to preserve "the main Agent makes the decisions"
- But some capability has grown too complex to keep writing as a plain tool function

## 3. How `AgentBuilder` wires SubAgent in

SubAgent actually enters the system inside `AgentBuilder.build()`.

:::tip All code on this page runs
The assembly and HandoffPolicy examples below come from
[`SubAgentHandoffDocExamplesTest`](https://github.com/LnYo-Cly/ai4j/blob/main/ai4j-agent/src/test/java/io/github/lnyocly/agent/SubAgentHandoffDocExamplesTest.java),
using an inline scripted model client — zero network, runnable in ordinary CI.
:::

Minimal assembly — mounting a reviewer agent as a subagent of the parent:

```java
Agent reviewer = Agents.react()
        .modelClient(reviewerClient)
        .model("reviewer-model")
        .build();

SubAgentDefinition reviewerSubAgent = SubAgentDefinition.builder()
        .name("code-reviewer")
        .description("Review code quality and risks")
        .toolName("delegate_code_review")   // tool name exposed to the parent model
        .agent(reviewer)
        .build();

Agent parent = Agents.react()
        .modelClient(parentClient)
        .model("manager-model")
        .subAgent(reviewerSubAgent)         // mount in one line
        .build();

AgentResult result = parent.run(AgentRequest.builder().input("analyze").build());
// parent model calls delegate_code_review → reviewer runs → output flows back to the parent
```

The core assembly order is:

1. First obtain the `baseToolRegistry`
2. `resolveSubAgentRegistry()`
   - If an explicit `subAgentRegistry` was passed, use it
   - Otherwise, if there are `subAgentDefinitions`, create a `StaticSubAgentRegistry`
3. `resolveToolRegistry(...)`
   - Uses `CompositeToolRegistry(baseToolRegistry, new StaticToolRegistry(subRegistry.getTools()))`
   - Merges the subagent tools into the model-visible tool surface
4. Resolve the original `ToolExecutor`
5. If a subagent registry exists, wrap it again with `new SubAgentToolExecutor(...)`

This chain has one important side effect:

- The original `ToolExecutor` does not need to know about subagent tools
- Because the subagent tool names are intercepted by the wrapper first
- Only non-subagent tools continue to be delegated to the original executor

So SubAgent's integration point is very clean: it does not pollute the implementation of the plain tool executor.

## 4. The semantics of `SubAgentDefinition` matter more than the field list

There are only 5 fields:

- `name`
- `description`
- `toolName`
- `agent`
- `sessionMode`

But what really matters is the default behavior.

### 4.1 `sessionMode` defaults to `NEW_SESSION`

This means if you configure nothing, SubAgent's default semantics are:

- Each handoff creates a fresh `AgentSession`
- The sub-agent does not inherit memory from the previous handoff

It favors isolation and predictability over long-term context continuity.

### 4.2 `name` is not purely a display field

If `toolName` is not explicitly given, `StaticSubAgentRegistry` generates a default tool name from `name`.

So `name` is not just a documentation label; it also affects the tool name ultimately exposed to the model.

## 5. What `StaticSubAgentRegistry` actually does

This layer's responsibility can be summarized in three lines:

- Turn definitions into tools
- Turn tool calls into subagent input
- Flatten subagent output into a tool result string

### 5.1 Tool name generation rule

When `toolName` is not explicitly specified, the default name is:

```text
subagent_<normalized name>
```

The normalization rules include:

- Lowercase
- Replace characters not in `[a-z0-9_]` with `_`
- Collapse consecutive `_`
- Trim leading and trailing `_`
- If the first character is a digit, prepend `agent_`

Additionally, when constructing the registry, a duplicate tool name throws directly:

```text
duplicate subagent tool name
```

So tool name collision is caught at initialization.

### 5.2 The schema exposed to the model is actually very narrow

The auto-generated function tool exposes only two fields:

- `task`, required
- `context`, optional

This shows SubAgent's design is not "expose the full Agent API to the model"; it deliberately narrows the handoff input surface into a minimal delegation protocol.

### 5.3 The return value is not `AgentResult`

`execute(...)` ultimately returns a JSON string containing:

- `subagent`
- `toolName`
- `output`
- `steps`

This is critical, because what the main Agent ultimately sees is still an ordinary tool result, not a nested Agent object graph.

SubAgent's position in the system is kept inside the "tool result channel" from end to end.

## 6. How input actually reaches the subagent

The logic in `StaticSubAgentRegistry.resolveInput(...)` is very specific:

1. If arguments is empty, return an empty string
2. Try to parse arguments as JSON
3. Prefer `task`
4. If there is no `task`, try `input`
5. If both `task` and `context` exist, assemble them as:

```text
<task>

Context:
<context>
```

6. If it is not valid JSON, use the raw arguments as input directly

This shows SubAgent's input protocol has two traits:

- It targets delegation tasks, not structured complex parameters
- It is tolerant of argument formats, but eventually folds everything into a single string input

## 7. `SubAgentToolExecutor` is the control hub of handoff

All the handoff semantics that really matter live here.

### 7.1 Plain tools are not polluted

The entry logic is straightforward:

- Subagent tool hit -> `executeSubAgent(...)`
- Otherwise -> `delegate.execute(call)`

So this is not a global tool proxy, but a "selective interceptor that only proxies subagent tools."

### 7.2 handoff emits events from the very start

Whether or not policy applies, it always emits first:

- `HANDOFF_START`

And on completion:

- `HANDOFF_END`

The payload also carries:

- `handoffId`
- `callId`
- `tool`
- `subagent`
- `status`
- `depth`
- `sessionMode`
- `attempts`
- `durationMillis`
- `output`
- `error`

So handoff is a first-class event at the observability layer, not a side log of an ordinary tool call.

## 8. The real execution chain of the handoff lifecycle

`executeSubAgent(...)` breaks down into 7 steps.

### 8.1 First check `enabled`

If `policy.isEnabled() == false`, it goes straight to `executeWithoutPolicy(...)`.

But note an easily overlooked detail here:

- `enabled=false` does not fully bypass the wrapper
- It still emits handoff events
- It still calls `executeOnce(...)`

And `executeOnce(...)` still reads `policy.getTimeoutMillis()`.

So the more accurate semantics of "disabling policy" are:

- Turn off governance branches like deny / filter / retry / onDenied / onError
- Not fully remove the entire handoff wrapper

### 8.2 Compute the next depth

Depth is computed via:

- `HandoffContext.currentDepth() + 1`

When actually executing, it uses:

- `HandoffContext.runWithDepth(depth, ...)`

to write the depth into a `ThreadLocal`.

So handoff depth is not an explicit parameter threaded through each level; it is a runtime context.

### 8.3 Admission control first

`denyReason(...)` checks in order:

- `allowedTools`
- `deniedTools`
- `maxDepth`

Note that what is checked here is:

- `toolName`

not the subagent `name`.

So the allow/deny lists are configured against the tool name exposed to the model, not the internal role name.

### 8.4 Then input filtering

If `inputFilter` is configured, it rewrites the `AgentToolCall` once.

This layer is suitable for:

- masking
- long-context trimming
- injecting policy fields

And if the filter returns `null`, the executor falls back to the original call — it does not swallow the handoff.

### 8.5 The attempt count is `maxRetries + 1`

The real attempts count is:

```java
int attempts = Math.max(1, policy.getMaxRetries() + 1);
```

So:

- `maxRetries = 0` -> 1 actual attempt
- `maxRetries = 2` -> 3 actual attempts

This is the classic "retry count does not include the first attempt" semantics.

### 8.6 timeout is per-attempt

Inside `executeOnce(...)`, if `timeoutMillis > 0`, it:

- Submits to an internal cached thread pool
- `future.get(timeoutMillis, TimeUnit.MILLISECONDS)`

So the timeout is for a single handoff attempt, not a ceiling on the total elapsed time across multiple retries.

### 8.7 Success, failure, and fallback all emit `HANDOFF_END`

No matter whether the final outcome is:

- completed
- failed
- fallback

an end event is explicitly emitted with the corresponding status.

This keeps handoff closed-loop in traces.

## 9. Session mode is not a performance switch, it is a state semantic

### 9.1 `NEW_SESSION`

Each execution:

- `agent.newSession()`
- `session.run(...)`

Characteristics:

- Strong isolation
- No history leakage
- More concurrency-friendly

### 9.2 `REUSE_SESSION`

`StaticSubAgentRegistry` caches `AgentSession` by `toolName`, and:

- `computeIfAbsent(...)`
- `synchronized(session)`

This implies two very specific semantics:

1. Memory accumulates across multiple handoffs
2. Concurrent calls to the same subagent tool are serialized

So `REUSE_SESSION` is not "higher throughput"; it is "stronger continuity, but at the cost of concurrency independence."

## 10. What the `HandoffPolicy` defaults really mean

The defaults are:

| Field | Default | Real meaning |
| --- | --- | --- |
| `enabled` | `true` | handoff is governed by the policy layer by default |
| `maxDepth` | `1` | Only lead -> subagent, one level, is allowed by default |
| `maxRetries` | `0` | No retry by default |
| `timeoutMillis` | `0L` | No timeout truncation by default |
| `allowedTools` | `null` | No allow-list by default |
| `deniedTools` | `null` | No deny-list by default |
| `onDenied` | `FAIL` | Denied = fail by default |
| `onError` | `FAIL` | Exception = fail by default |
| `inputFilter` | `null` | No input rewriting by default |

The policy lean behind this set of defaults is clear:

- SubAgent is usable by default
- But recursive handoff is not open by default
- And errors are not silently swallowed or auto-downgraded by default

A typical configuration that tightens the tool surface and prevents recursion:

```java
Agent parent = Agents.react()
        .modelClient(parentClient)
        .model("manager")
        .subAgent(workerDef)
        .handoffPolicy(HandoffPolicy.builder()
                .allowedTools(Collections.singleton("delegate_worker"))  // allow only this one handoff tool
                .maxDepth(1)          // only one level; block the subagent from handing off further
                .maxRetries(1)        // retry once on failure
                .build())
        .build();
```

Handoff tools not in `allowedTools` are rejected; with the default `onDenied=FAIL` an exception is thrown directly, and the exception message states which policy blocked it (`tool is not in allowedTools` / `handoff depth N exceeds maxDepth`).

## 11. The semantics of `FALLBACK_TO_PRIMARY` must be spelled out

When `onDenied` or `onError` is set to `FALLBACK_TO_PRIMARY`, the executor does:

```java
delegate.execute(call)
```

This means the real semantics of fallback are:

- Hand the same `AgentToolCall` back to the original tool execution chain

It is not:

- Letting the main Agent rethink on its own
- Or letting the subagent degrade into a plain text reply

This requires a precondition:

- Your original `delegate` must actually be able to handle this same-named tool call

Otherwise configuring fallback is meaningless.

:::note Under default wiring the primary executor usually does not recognize subagent tool names
Under the default `AgentBuilder` wiring, the primary executor (`ToolUtilExecutor`) captures its allowlist before the subagent is merged into the registry, so it does not contain subagent tool names. So with `FALLBACK_TO_PRIMARY` under the default configuration, the primary executor **cannot** serve the call — instead it returns a clear "handoff unavailable" message (rather than forcing the call through as a plain tool). To make fallback actually execute something else, you need a custom `toolExecutor` to serve that tool name. See [issue #238](https://github.com/LnYo-Cly/ai4j/issues/238).
:::

```java
// Instead of throwing when denied, hand the tool call back to the primary execution chain
Agent parent = Agents.react()
        .modelClient(parentClient)
        .model("manager")
        .subAgent(workerDef)
        .handoffPolicy(HandoffPolicy.builder()
                .allowedTools(Collections.singleton("delegate_other"))  // does not include delegate_worker
                .onDenied(HandoffFailureAction.FALLBACK_TO_PRIMARY)
                .build())
        .build();
// parent calls delegate_worker → rejected by allowedTools → falls back to the primary agent to handle itself
```

## 12. An easily overlooked design detail

When `AgentBuilder` creates the default `ToolUtilExecutor`, the allowed tool names it passes in come from:

- `baseToolRegistry`

not the merged subagent registry.

This is exactly why the default tool executor does not need to know about subagent tools, because:

- The subagent tool is intercepted first by `SubAgentToolExecutor`
- Only the plain tools that miss go to the delegate

This is a very clean layered design.

## 13. What scenarios it fits, and what it does not

Fits:

- You want to keep a main Agent for global decisions
- Some specialized capability has grown complex enough to deserve being its own Agent
- You want those specialized capabilities to keep their own prompt, memory, and tooling

Does not fit:

- Needs explicit team collaboration
- Needs task claiming, message broadcast, state recovery
- Needs multiple members collaborating as peers rather than master-slave delegation

Those scenarios belong in Agent Teams.

## 14. A few real limitations of the current implementation

### 14.1 SubAgent still uses a string input/output protocol

Even though the parameters look like JSON on the surface, what is actually fed to the sub-agent is still a single string input.

So it is more like "structured delegation entry + textual task body," not a strongly-typed RPC.

### 14.2 `REUSE_SESSION` only reuses by `toolName`

Not by user, not by task, not by tenant.

So if your system needs finer-grained session isolation, you cannot treat the default `REUSE_SESSION` as a complete solution.

### 14.3 policy allow/deny is by tool name, not by role name

This requires you to keep tool names stable and governance-meaningful when designing them, otherwise later policy configuration becomes messy.

## 15. Recommended source entry points

- `ai4j-agent/src/main/java/io/github/lnyocly/ai4j/agent/AgentBuilder.java`
- `ai4j-agent/src/main/java/io/github/lnyocly/ai4j/agent/subagent/SubAgentDefinition.java`
- `ai4j-agent/src/main/java/io/github/lnyocly/ai4j/agent/subagent/SubAgentSessionMode.java`
- `ai4j-agent/src/main/java/io/github/lnyocly/ai4j/agent/subagent/StaticSubAgentRegistry.java`
- `ai4j-agent/src/main/java/io/github/lnyocly/ai4j/agent/subagent/SubAgentToolExecutor.java`
- `ai4j-agent/src/main/java/io/github/lnyocly/ai4j/agent/subagent/HandoffPolicy.java`
- `ai4j-agent/src/main/java/io/github/lnyocly/ai4j/agent/subagent/HandoffContext.java`

## 16. Recommended verification tests

- `ai4j-agent/src/test/java/io/github/lnyocly/agent/SubAgentRuntimeTest.java`
- `ai4j-agent/src/test/java/io/github/lnyocly/agent/SubAgentParallelFallbackTest.java`
- `ai4j-agent/src/test/java/io/github/lnyocly/agent/HandoffPolicyTest.java`

## 17. Further reading

1. [Tools and Registry](/docs/agent/tools-and-registry)
2. [Agent Teams](/docs/agent/agent-teams)
3. [Trace and observability](/docs/agent/trace-observability)
