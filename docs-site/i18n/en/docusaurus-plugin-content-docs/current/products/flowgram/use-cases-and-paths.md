---
sidebar_position: 2
title: "Flowgram Usage Paths and Scenario Selection"
description: "Maps five entry paths into Flowgram by task goal, and compares when to choose Flowgram, Agent, Coding Agent, or MCP, with a recommended evolution order, common misjudgments, and the shortest reading path by role."
tags: [concept]
---

# Flowgram Usage Paths and Scenario Selection

This page does not re-explain concepts. It directly answers two questions:

- Which path you should enter from right now
- Whether this problem should land on `Flowgram`, `Agent`, `Coding Agent`, or `MCP`

If you read Flowgram as "just another Agent chapter," you will usually head the wrong direction. It is closer to an "explicit workflow platform backend."

## 1. Pick a path by goal first

The most time-efficient way is not to read linearly from start to finish, but to pick an entry point by the task in front of you.

### Path A: Verify the backend can actually run

Fits what you are doing:

- Get the Spring Boot demo running first
- Want to see `/tasks/run -> /report -> /result` directly
- Do not have a frontend canvas yet

Read first:

1. [Quickstart](/docs/products/flowgram/quickstart)
2. [Runtime](/docs/products/flowgram/runtime)
3. [Architecture](/docs/products/flowgram/architecture)

The goal of this path is not "understand all the source code," but to confirm the task lifecycle and the node execution chain are wired up.

### Path B: You already have a Flowgram.ai canvas and need to wire an AI4J backend

Fits what you are doing:

- The frontend can already draw flows
- Now you need a Java backend execution layer
- You care about schema, polling, report, result, cancel

Read first:

1. [Frontend / Backend Integration](/docs/products/flowgram/frontend-backend-integration)
2. [Architecture](/docs/products/flowgram/architecture)
3. [Runtime](/docs/products/flowgram/runtime)

Then come back to these real code entry points:

- `ai4j-flowgram-webapp-demo/src/utils/backend-workflow.ts`
- `ai4j-flowgram-webapp-demo/src/plugins/runtime-plugin/runtime-service/index.ts`
- `ai4j-flowgram-webapp-demo/src/plugins/runtime-plugin/client/server-client/index.ts`

The key point on this path is to first see how "frontend edit state" gets compressed into "backend execution state."

### Path C: You want to extend enterprise-specific nodes

Fits what you are doing:

- Want to wire internal enterprise HTTP services
- Want to turn a stable piece of business logic into an official node
- Need stronger input/output constraints than a prompt

Read first:

1. [Built-in Nodes](/docs/products/flowgram/built-in-nodes)
2. [Custom Nodes](/docs/products/flowgram/custom-nodes)
3. [Frontend / Backend Integration](/docs/products/flowgram/frontend-backend-integration)
4. [Runtime](/docs/products/flowgram/runtime)

The most important principle on this path is: confirm the built-in nodes are not enough before you extend.

### Path D: You need to wire knowledge bases, tools, and services into the flow

Fits what you are doing:

- Want to turn a RAG capability into a workflow node
- Want a node to go through a Tool or model capability
- Want the workflow to wire into other AI4J foundation modules

Read first:

1. [Built-in Nodes](/docs/products/flowgram/built-in-nodes)
2. [Agent / Tool / Knowledge Integration](/docs/products/flowgram/agent-tool-knowledge-integration)
3. [Runtime](/docs/products/flowgram/runtime)

Note here: `Flowgram` does not re-implement a separate set of AI capabilities. It reorganizes AI4J foundation capabilities by node contract.

### Path E: You want to make it a real platform backend

Fits what you are doing:

- Want to wire in task permissions, tenants, task center
- Want to switch to a JDBC task store
- Want to connect trace and the task detail page

Read first:

1. [Architecture](/docs/products/flowgram/architecture)
2. [Runtime](/docs/products/flowgram/runtime)
3. [Frontend / Backend Integration](/docs/products/flowgram/frontend-backend-integration)

Then focus on these classes:

- `FlowGramRuntimeFacade`
- `FlowGramTaskStore`
- `DefaultFlowGramTaskOwnershipStrategy`
- `DefaultFlowGramCallerResolver`
- `DefaultFlowGramAccessChecker`

## 2. When to choose Flowgram instead of other subsystems

This is the easiest place to get confused.

### 2.1 Choose Flowgram

When your main problem is one of the following, prefer `Flowgram`:

- You need to draw the flow out and hand it to the backend for stable execution
- You need a formal control plane like `validate/run/report/result/cancel`
- Node inputs and outputs must be stable
- The frontend needs node-level state and trace

### 2.2 Choose Agent

When your main problem is one of the following, prefer `Agent`:

- The task is open-ended multi-step reasoning
- The tool-call path is decided by the model itself
- You need memory, handoff, team orchestration

In one sentence, `Agent` gives the goal first; `Flowgram` gives the graph first.

### 2.3 Choose Coding Agent

When your main problem is one of the following, prefer `Coding Agent`:

- The task target is a local code repository
- You need shell, file system, editor, regression loop
- You need to complete coding tasks inside a workspace

`Coding Agent` is a repository-interaction runtime, not a backend execution layer for the frontend canvas.

### 2.4 Choose MCP

When your main problem is one of the following, prefer `MCP`:

- You are solving "where capabilities come from"
- You want to wrap external systems into a standard tool protocol
- You want to supply a class of services for multiple runtimes to reuse

`MCP` solves capability supply; `Flowgram` solves how those capabilities are organized and executed inside a workflow.

## 3. Recommended evolution order

If you want to go from demo to a real platform, follow this order rather than building every feature at once.

1. Get the backend demo running and confirm the `/validate`, `/run`, `/result` main path
2. Wire the frontend canvas and confirm schema normalization and the report polling path
3. Build one minimal business flow using only built-in nodes
4. Then add one custom node you actually need
5. Then switch to a JDBC task store and wire in permissions
6. Finally, optimize trace, task center, and deployment topology

The core purpose of this order is to separate troubleshooting into "canvas problem," "execution problem," and "platform governance problem."

## 4. Common misjudgments

### 4.1 Writing custom nodes from the start

Many teams' first reaction is "I need to build out all the business nodes first." This is usually premature.

The more common real blockers are actually:

- Frontend/backend schema is not aligned yet
- The task polling path is not wired up
- The report / result read side is not confirmed

Before these problems are resolved, custom nodes only add noise.

### 4.2 Treating the task store as a complete persistence engine

The current `FlowGramTaskStore` implementation leans more toward metadata / snapshot persistence; the runtime truth still lives in-process inside `FlowGramRuntimeService`.

If you expect "after a process restart, the original task can be fully recovered and continue executing," that is not a capability this implementation provides by default.

### 4.3 Treating Flowgram as "Agent visualization"

The two share the model and tool foundation, but the control logic differs. Mistaking Flowgram for an Agent UI will eventually skew your contract design.

## 5. Shortest reading path by role

### Frontend engineer

Read first:

1. [Frontend / Backend Integration](/docs/products/flowgram/frontend-backend-integration)
2. [Architecture](/docs/products/flowgram/architecture)

Then read:

- `backend-workflow.ts`
- `WorkflowRuntimeService`

Because the first thing you need to understand is "which nodes get sent to the backend, and which do not."

### Backend platform engineer

Read first:

1. [Architecture](/docs/products/flowgram/architecture)
2. [Runtime](/docs/products/flowgram/runtime)

Then read:

- `FlowGramRuntimeFacade`
- `FlowGramRuntimeService`
- `FlowGramTaskStore`

Because the first thing you need to understand is "where the execution truth lives, where the governance entry point is, and what the default boundary is."

### Node extension developer

Read first:

1. [Built-in Nodes](/docs/products/flowgram/built-in-nodes)
2. [Custom Nodes](/docs/products/flowgram/custom-nodes)

Then read:

- Your frontend node schema
- The corresponding `FlowGramNodeExecutor`

Because node extension is fundamentally frontend/backend contract design, not just writing one backend class.

## 6. If you only want to pick one starting line

The most stable starting point for most teams is actually:

1. Get the backend runtime running first
2. Then bring the canvas in
3. Then extend nodes last

This is far more stable than "changing the frontend, backend, and node protocol all at once."
