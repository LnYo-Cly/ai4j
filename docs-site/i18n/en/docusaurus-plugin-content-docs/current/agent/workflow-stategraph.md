---
sidebar_position: 6
title: "Workflow and StateGraph"
description: "Walks through the Workflow and StateGraph of ai4j-agent: an orchestration layer rather than a runtime, nodes pass only outputText by default, WorkflowContext as a side channel, StateGraph edge-resolution priority, and maxSteps fuse semantics."
tags: [concept]
---

# Workflow and StateGraph

This layer is not about "building yet another runtime"; it is about chaining multiple Agent nodes into an explicit flow.

:::tip StateGraph assembly has a runnable unit test
[`StateGraphWorkflowTest`](https://github.com/LnYo-Cly/ai4j/blob/main/ai4j-agent/src/test/java/io/github/lnyocly/agent/StateGraphWorkflowTest.java) uses `StaticNode`/`CounterNode` (no keys required) to demonstrate conditional branching and loop routing, as an executable companion to §5/§6. For a live two-node weather workflow, see the [Cookbook](/docs/agent/weather-workflow-cookbook).
:::

If `ReActRuntime` owns the loop of a single Agent run, then the `workflow` package owns:

- How nodes hand off to one another
- How branches are selected
- When to loop back
- Which shared state does not belong inside a single Agent's memory

It is conceptually similar to LangGraph, but the current implementation is noticeably lighter and its boundary is narrower.

## 1. Five key design decisions to grasp first

### 1.1 Workflow is an orchestration layer above Agents, not a runtime variant

`AgentRuntime` still only owns a single execution of a single Agent.

`SequentialWorkflow` and `StateGraphWorkflow` do not plug into the main loop of `BaseAgentRuntime`; they are simply an external orchestration layer built around:

- `AgentNode`
- `AgentRequest`
- `AgentResult`
- `WorkflowContext`

So workflow addresses "how multiple nodes are organized", not "how a single node reasons internally".

### 1.2 Nodes pass only `outputText` by default

Whether it is `SequentialWorkflow` or `StateGraphWorkflow`, after a node finishes executing, the framework by default only takes:

- `lastResult.getOutputText()`

and repackages it as the next hop's `AgentRequest.input`.

This means the default chain does not automatically carry:

- `rawResponse`
- `toolCalls`
- `memoryItems`
- Business structured fields

If you need richer cross-node state, you must write it explicitly to `WorkflowContext.state`.

### 1.3 `WorkflowContext` is a side channel, not the main node input path

The primary input of a node is still `AgentRequest`.

`WorkflowContext` only adds a shared state region, used to hold:

- Routing decisions
- Counters
- The previous node ID
- Temporary business fields

It is not a strongly typed global state object like LangGraph's; today it is just a `Map<String, Object>`.

### 1.4 StateGraph edge resolution has a fixed priority

The order inside `StateGraphWorkflow.resolveNext(...)` is not random, but rather:

1. First iterate the `conditionalEdges` on the current node
2. The first router that returns a non-null route wins
3. If a `routeMap` is configured, map the route key to the actual node ID
4. If no router hits, iterate `transitions` in registration order
5. The first transition whose condition is null or whose `matches(...) == true` wins
6. If nothing hits, return `null` and the graph ends immediately

In other words:

- `conditionalEdges` take priority over ordinary `transition`s
- When no edge hits, no exception is thrown; the graph "ends normally"

### 1.5 `maxSteps` is only an infinite-loop fuse, not a success condition

`StateGraphWorkflow` defaults to `maxSteps = 32`.

The while condition is:

```java
while (currentNodeId != null && steps < maxSteps)
```

Once the limit is reached, execution simply stops and returns the current `lastResult`; it does not throw a "step limit exceeded" exception.

So its semantics are:

- Prevent infinite loops
- But it does not judge "whether the business flow has truly completed" for you

## 2. Get the object relationships straight

The core objects in the current `io.github.lnyocly.ai4j.agent.workflow` are few:

```text
AgentWorkflow
  -> SequentialWorkflow
  -> StateGraphWorkflow

WorkflowAgent
  -> holds AgentWorkflow + AgentSession

AgentNode
  -> RuntimeAgentNode
  -> your own custom nodes

WorkflowContext
  -> session
  -> state(Map<String, Object>)
```

The most important division of responsibility is:

| Object | Actual responsibility |
| --- | --- |
| `AgentWorkflow` | Defines the unified run interface of a workflow |
| `SequentialWorkflow` | Sequential node handoff |
| `StateGraphWorkflow` | Branching, conditions, loops |
| `WorkflowAgent` | Wraps a workflow into a more Agent-like entry point |
| `AgentNode` | The execution abstraction of a single node |
| `RuntimeAgentNode` | Wraps an `AgentSession` directly as a node |
| `WorkflowContext` | Shared state between nodes |

## 3. The real boundary between `WorkflowAgent` and `AgentSession`

Many people, on first reading this package, mistakenly assume:

- The `session` held by `WorkflowAgent`
- Is automatically the session every node will use

The source code says otherwise.

`WorkflowAgent` is thin:

```java
public AgentResult run(AgentRequest request) throws Exception {
    return workflow.run(session, request);
}
```

It only passes a root `AgentSession` to the workflow.

But whether each node actually uses this session depends on the node implementation.

For example, the built-in `RuntimeAgentNode`:

```java
public class RuntimeAgentNode implements AgentNode, WorkflowResultAware {
    private final AgentSession session;
}
```

It uses the session passed in at its own construction time, not `WorkflowContext.session`.

This means two session sources currently coexist in the workflow layer:

1. The workflow root session
2. The session each node holds itself

If you do not deliberately unify them, it is easy to end up with:

- The workflow top level passing one session
- Each node in fact running its own session

This is not a bug, but you must keep it in mind.

## 4. The real execution chain of `SequentialWorkflow`

The implementation of `SequentialWorkflow` is very direct, but precisely because it is so direct, its boundary is also obvious.

### 4.1 What it does

The execution chain is basically:

1. Create a new `WorkflowContext`
2. `current = request`
3. Execute each node in order
4. If `lastResult.outputText != null`
5. Repackage it as the new `AgentRequest.input`
6. Hand it to the next node

That is:

```text
nodeA.outputText -> nodeB.input
nodeB.outputText -> nodeC.input
```

### 4.2 What it does not do

It does not automatically:

- Merge multiple node outputs
- Carry structured objects
- Do conditional branching
- Check whether a node executes repeatedly
- Keep the full chain history

If you want to preserve intermediate structures, the move is not to expect the framework to carry them for you, but to write them into `WorkflowContext` yourself.

### 4.3 The easiest detail to overlook

As long as the previous node returned an `outputText`, the original request is overwritten.

So if the next node needs both:

- The original user input
- The previous node's output

Then you cannot rely on the default handoff alone; you must write the original input into `WorkflowContext.state` yourself.

## 5. How `StateGraphWorkflow` actually advances

The main loop of `StateGraphWorkflow` is also short, but its logic has one more layer than `SequentialWorkflow`: "next-hop resolution".

### 5.1 The main loop skeleton

The core flow is:

1. Validate `startNodeId`
2. `currentNodeId = startNodeId`
3. `currentRequest = request`
4. while `currentNodeId != null && steps < maxSteps`
5. Find the current node
6. Write `currentNodeId` and `currentRequest` into `WorkflowContext`
7. Execute the node
8. Write `lastResult` and `lastNodeId` into `WorkflowContext`
9. If `lastResult.outputText != null`, rebuild the next-hop request
10. `resolveNext(...)` computes the next node
11. `steps += 1`

The actual graph semantics all live in step 10.

### 5.2 The relationship between `addEdge` and `addTransition`

These two APIs do not have two separate underlying implementations.

`addEdge(from, to)` is simply:

```java
return addTransition(from, to, null);
```

In other words:

- `addEdge` represents an unconditional edge
- `addTransition` represents an edge that can carry a condition

Both land in the same `transitions` list and match in registration order.

### 5.3 `addConditionalEdges` is not "syntactic sugar for multiple conditional edges"

`addConditionalEdges` goes through a different mechanism.

It does not split a route into multiple `StateTransition`s; it stores a single entry:

- `from`
- `StateRouter`
- `routeMap`

At runtime, `router.route(context, request, result)` is called first.

Then:

- If no `routeMap` is configured, the router's return value is used directly as the node ID
- If a `routeMap` is configured, the mapping is looked up first, then the actual node ID is obtained

So the purpose of `routeMap` is not "to describe the graph", but "to translate a business routing key into a node ID".

## 6. Edge resolution order drives a lot of behavior

This is the part most worth reading the source code for directly.

### 6.1 Conditional edges match first

As long as the current node has `conditionalEdges` configured, `resolveNext(...)` iterates them first.

Once a router returns a non-null route and the routeMap also resolves to a node, it returns immediately.

The transitions after that are never even looked at.

### 6.2 Ordinary transitions match after

Only when no conditional route hits does it fall through to `transitions`.

And `transitions` itself also matches the first hit in insertion order.

So when you write:

```java
.addTransition("route", "incident", incidentCondition)
.addTransition("route", "general", null)
```

the second `null` condition is essentially a fallback.

### 6.3 When there is no next hop, it ends silently

If:

- No conditional route hits
- And no transition hits either

`resolveNext(...)` eventually returns `null`, and the while loop ends.

This means a graph ends in two ways:

1. You explicitly route to `null`
2. You never configured a next hop at all

The framework does not distinguish the two for you.

## 7. What WorkflowContext is actually good for

`WorkflowContext` currently holds only three things:

- `session`
- `state`
- `eventPublisher`

But note:

- `eventPublisher` is barely used along the workflow main chain today
- The one actually used is `state`

More precisely, `WorkflowContext.state` is suited to carry:

- Routing decisions
- Counters
- Business metadata shared between nodes
- Objects that need to survive across nodes but should not be stuffed into a prompt

For example:

- `route`
- `count`
- `riskLevel`
- `city`
- `draftJson`

It is not meant to become a "dump everything here" bag, otherwise it becomes very hard to tell which layer a piece of state came from.

## 8. Two most common patterns

### 8.1 Sequential processing chain

Fits:

- Draft -> review -> format
- Extract -> classify -> output

Example:

```java
SequentialWorkflow workflow = new SequentialWorkflow()
        .addNode(new RuntimeAgentNode(draftAgent.newSession()))
        .addNode(new RuntimeAgentNode(formatAgent.newSession()));
```

The real semantics here are not "two Agents collaborating", but rather:

- The first Agent's `outputText`
- Is fed directly as the second Agent's `input`

### 8.2 Route then converge

Fits:

- Identify the type first
- Branch to handle it
- Format uniformly at the end

Example:

```java
StateGraphWorkflow workflow = new StateGraphWorkflow()
        .addNode("route", new RoutingNode(router.newSession()))
        .addNode("weather", new RuntimeAgentNode(weather.newSession()))
        .addNode("generic", new RuntimeAgentNode(generic.newSession()))
        .addNode("final", new RuntimeAgentNode(formatter.newSession()))
        .start("route")
        .addConditionalEdges("route", (ctx, req, res) -> String.valueOf(ctx.get("route")))
        .addEdge("weather", "final")
        .addEdge("generic", "final");
```

The key here is not the branching itself, but:

- The route decision lives in `WorkflowContext`
- The business output still flows along the default `outputText -> input` handoff line

## 9. The real boundary of Stream mode today

This part is easy to misjudge without reading the source.

### 9.1 The default implementation of `AgentNode.executeStream(...)` is very thin

The default implementation only:

1. Calls the synchronous `execute(...)` directly
2. Then `listener.onEvent(context.createResultEvent(result))`

That is, the default stream node has no independent node-level event model of its own; it just wraps the final result as a single `FINAL_OUTPUT` event.

### 9.2 The stream path of `RuntimeAgentNode` does not backfill `lastResult` automatically

`RuntimeAgentNode.executeStream(...)` only does:

```java
session.runStream(request, listener);
```

It does not assign `lastResult` the way the synchronous `execute(...)` does.

And `SequentialWorkflow` / `StateGraphWorkflow` in stream mode can only keep passing the result to the next hop when the node also implements `WorkflowResultAware` and can return the latest `lastResult`.

This means the stream semantics of the built-in `RuntimeAgentNode` today carry an obvious limitation:

- You can get streaming events
- But by default it is not guaranteed that the node output can flow back into the next workflow hop as reliably as in synchronous mode

If you intend to do streaming multi-node orchestration seriously, this layer still needs extra wrapping.

### 9.3 Workflow itself has no node-level trace system

The current workflow package does not publish structured runtime events the way `BaseAgentRuntime` does, such as:

- `STEP_START`
- `MODEL_REQUEST`
- `TOOL_CALL`

So the workflow's stream is more accurately:

- Transparently forwarding the inner node's listener
- Not building a workflow trace model of its own

## 10. Defaults, failure paths, and boundaries

### 10.1 Missing `start(...)` throws directly

If `StateGraphWorkflow` does not set a start point, `run(...)` throws:

```text
IllegalStateException: start node is required
```

### 10.2 A non-existent node ID throws directly

At runtime, if `nodes.get(currentNodeId) == null`, it also throws:

```text
IllegalStateException: node not found: <id>
```

### 10.3 Reaching `maxSteps` does not error, it only ends early

This is one of the failure semantics of the current StateGraph most worth keeping in mind.

:::warning maxSteps is not a success guarantee
If you treat `maxSteps` as a success guarantee, you will misjudge "the flow completed" when in fact it was just cut off by the fuse.
:::

### 10.4 When a node output is empty, the next-hop request is not rewritten

A new `AgentRequest` is generated only when `lastResult != null && lastResult.getOutputText() != null`.

Otherwise the next node keeps receiving the previous round's `currentRequest`.

This is common in "only updates state, not text" nodes, and is easy to overlook.

### 10.5 Workflow does not isolate node memory for you by default

If multiple `RuntimeAgentNode`s reuse the same `AgentSession`, they share the same Agent memory.

If each node calls `newSession()`, they are isolated by default.

The framework does not enforce a unified policy for you today.

## 11. When to use Workflow, and when not to

### Suitable for Workflow

- The node structure needs to be expressed explicitly
- You want to split "routing decision" and "execution node" apart
- Some shared state does not belong in a single Agent prompt
- You want to chain multiple Agents into a stable pipeline

### Not the right place to reach for Workflow first

- Just a single Agent's tool loop
- Just want stronger tool orchestration — usually see [CodeAct Runtime](/docs/agent/codeact-runtime) first
- Just delegating one capability to another Agent — usually see [SubAgent and Handoff Policy](/docs/agent/subagent-handoff-policy) first
- Need a task board, roles, and a message bus — usually see [Agent Teams](/docs/agent/agent-teams)

## 12. Recommended source-reading order

- `ai4j-agent/src/main/java/io/github/lnyocly/ai4j/agent/workflow/AgentWorkflow.java`
- `ai4j-agent/src/main/java/io/github/lnyocly/ai4j/agent/workflow/WorkflowAgent.java`
- `ai4j-agent/src/main/java/io/github/lnyocly/ai4j/agent/workflow/AgentNode.java`
- `ai4j-agent/src/main/java/io/github/lnyocly/ai4j/agent/workflow/SequentialWorkflow.java`
- `ai4j-agent/src/main/java/io/github/lnyocly/ai4j/agent/workflow/StateGraphWorkflow.java`
- `ai4j-agent/src/main/java/io/github/lnyocly/ai4j/agent/workflow/WorkflowContext.java`
- `ai4j-agent/src/test/java/io/github/lnyocly/agent/AgentWorkflowTest.java`
- `ai4j-agent/src/test/java/io/github/lnyocly/agent/AgentWorkflowUsageTest.java`
- `ai4j-agent/src/test/java/io/github/lnyocly/agent/StateGraphWorkflowTest.java`

## 13. Further reading

1. [Agent Architecture](/docs/agent/architecture)
2. [Minimal ReAct Agent](/docs/agent/minimal-react-agent)
3. [SubAgent and Handoff Policy](/docs/agent/subagent-handoff-policy)
4. [Agent Teams](/docs/agent/agent-teams)
