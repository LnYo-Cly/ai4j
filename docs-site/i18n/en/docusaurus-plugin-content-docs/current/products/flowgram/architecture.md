---
title: "Flowgram Architecture"
description: "Breaks down the four-layer architecture of the Flowgram frontend canvas plus the AI4J backend execution layer: canvas, adaptation, Spring Boot platform wiring, and the execution engine — clarifying the difference between edit-time and run-time schema and the default security posture."
tags: [concept]
---

# Flowgram Architecture

The architectural focus of `Flowgram` is not the `Flowgram.ai` frontend library in isolation, but rather how the "frontend canvas + AI4J backend execution layer" combine into a genuinely runnable workflow platform.

If you only look at the demo, you will think this is a set of REST APIs; if you follow the source code, you will find it is actually split into four very clear layers.

## 1. Start with the layering

```text
Flowgram.ai canvas / editor
  -> webapp runtime adapter
  -> Spring Boot task API + facade
  -> FlowGramRuntimeService
  -> built-in graph logic + node executors + LLM node runner
  -> task store / trace projection / result snapshot
```

Expand this chain and you can see that each layer has a distinct responsibility.

### 1.1 Canvas layer

- `Flowgram.ai`
- `ai4j-flowgram-webapp-demo/`

Responsibilities:

- Provide the editor and runtime UI
- Manage node forms
- Assemble the workflow JSON
- Initiate validate / run / report / result / cancel

### 1.2 Adaptation layer

Key files:

- `ai4j-flowgram-webapp-demo/src/utils/backend-workflow.ts`
- `ai4j-flowgram-webapp-demo/src/plugins/runtime-plugin/runtime-service/index.ts`

Responsibilities:

- Compress the edit-time workflow into the backend run-time workflow
- Filter out UI-only nodes
- Handle task polling and frontend state synchronization

### 1.3 Platform wiring layer

- `ai4j-flowgram-spring-boot-starter/`

Key classes:

- `FlowGramAutoConfiguration`
- `FlowGramTaskController`
- `FlowGramRuntimeFacade`

Responsibilities:

- Auto-configure the runtime
- Register node executors
- Expose the REST API
- Wire caller, permission, ownership, task store, and trace output

### 1.4 Execution engine layer

- `ai4j-agent/.../flowgram/FlowGramRuntimeService`
- `Ai4jFlowGramLlmNodeRunner`

Responsibilities:

- Parse the schema
- Validate the graph structure
- Create and maintain the task record
- Schedule node execution
- Assemble the report / result

## 2. The edit-time schema and the run-time schema are not the same thing

This point is critical, and it is where the Flowgram system is more mature than "the frontend just sends JSON to the backend".

### 2.1 The frontend first strips out UI-only nodes

`backend-workflow.ts` explicitly filters out:

- `Comment`
- `Group`
- `BlockStart`
- `BlockEnd`

These elements have meaning on the canvas, but no semantics in the backend execution layer.

### 2.2 Types are mapped to the backend protocol

The current mapping includes at least:

- `start -> START`
- `end -> END`
- `llm -> LLM`
- `http -> HTTP`
- `code -> CODE`
- `condition -> CONDITION`
- `loop -> LOOP`
- `variable -> VARIABLE`
- `tool -> TOOL`
- `knowledge -> KNOWLEDGE`

This shows that the backend does not depend on the frontend's internal display names at all; it depends on a separate set of execution types.

### 2.3 What this implies

The implication is direct:

- You can keep evolving the canvas presentation layer
- But the backend contract should not drift along with UI copy

For the same reason, the real boundary of a custom node is never "whether the frontend rendered it", but "whether the frontend and backend are aligned on the execution type and the input/output protocol".

## 3. The real responsibilities of the runtime core

`FlowGramRuntimeService` is the heart of the entire execution chain.

### 3.1 `runTask(...)` is not a simple forward

It will:

1. Parse and validate the schema
2. Create the `taskId`
3. Construct the `TaskRecord`
4. Put it into the in-process `ConcurrentMap<String, TaskRecord>`
5. Submit the execution logic to an internal `ExecutorService`
6. Return the `taskId` immediately

This shows it is an asynchronous task model by design.

### 3.2 Validation is not just "can the JSON be parsed"

From `validateGraph(...)` and `validateNodeDefinitions(...)` you can directly see that it checks:

- Whether the schema exists
- Whether the workflow has at least one node
- Whether the root graph has exactly one `Start`
- Whether the root graph has at least one `End`
- Whether the source / target nodes referenced by edges exist
- Whether node IDs are duplicated
- Whether the node type is supported
- Whether required input bindings are missing
- Whether the nodes referenced by outputs exist

This is why having the frontend call `/validate` first is sound design, not a redundant step.

### 3.3 The runtime natively builds in only "control structures"

The types that the `FlowGramRuntimeService` core understands directly are only:

- `START`
- `END`
- `LLM`
- `CONDITION`
- `LOOP`

This is a very clear design trade-off:

- Control-flow semantics stay in the runtime core
- Business capabilities are extended through executors

This is more maintainable than stuffing every node into one giant switch.

### 3.4 Other business nodes rely on registered executors

The starter registers by default:

- `FlowGramHttpNodeExecutor`
- `FlowGramVariableNodeExecutor`
- `FlowGramCodeNodeExecutor`
- `FlowGramToolNodeExecutor`

And only registers the following when an `AiServiceRegistry` and a single `VectorStore` are present:

- `FlowGramKnowledgeRetrieveNodeExecutor`

This means the "built-in nodes" themselves are conditional capabilities, not identical across every environment.

### 3.5 Graph execution advances recursively

`executeFromNode(...)` will:

- Execute the current node
- Select the next batch of edges
- Recurse into successor nodes

At the same time it uses `activePath` to detect repeated nodes on the current path, and throws immediately on a repeat:

- `Cycle detected in FlowGram graph at node ...`

This shows the current implementation is closer to an explicit DAG / block graph execution, not a general-purpose flow engine that tolerates arbitrary cyclic graphs.

## 4. The LLM node is not a "call the model directly" special case

`Ai4jFlowGramLlmNodeRunner` is worth a separate look, because it reflects the real relationship between Flowgram and Agent.

### 4.1 It constructs an Agent on every run

The core behavior is:

- Parse the model name from the node input
- Parse the prompt
- Dynamically create an Agent with `AgentBuilder`
- Execute `agent.run(...)`
- Write the result and metrics back to the node output

### 4.2 The default execution strategy is conservative

The default configuration is:

- runtime: `ReActRuntime`
- `maxSteps(1)`
- `stream(false)`

This shows that an LLM node in Flowgram is by default not "a small Agent that can think indefinitely", but "an intelligent step that completes in a single node".

### 4.3 The input protocol is also made compatible

It reads values from the following fields:

- model name: `modelName` / `model` / `modelId`
- prompt: `prompt` / `message` / `input`

This reduces the coupling between the frontend node definition and the backend executor.

### 4.4 metrics are not just elapsed time

If a `TracePricingResolver` is wired, the LLM node can also output:

- token usage
- input / output / total cost
- currency

This is very valuable for the task detail page and the cost panel.

## 5. What the Spring Boot platform layer actually does

Many people think of the starter as "just newing up a few objects for you". It is not.

### 5.1 `FlowGramAutoConfiguration` decides the system's default shape

It is responsible for:

- Selecting the task store type
- Creating the caller resolver
- Creating the access checker
- Creating the ownership strategy
- Creating the protocol adapter
- Creating the LLM node runner
- Creating the runtime service
- Registering the default node executors and runtime listener

In other words, the starter decides "how this system runs by default".

### 5.2 `FlowGramTaskController` only exposes the control plane

The controller is mounted by default at:

- `${ai4j.flowgram.api.base-path:/flowgram}`

And provides 5 standard endpoints:

- `POST /tasks/run`
- `POST /tasks/validate`
- `GET /tasks/{taskId}/report`
- `GET /tasks/{taskId}/result`
- `POST /tasks/{taskId}/cancel`

It is itself very thin; the real platform logic has been pushed down into the facade.

### 5.3 `FlowGramRuntimeFacade` is the platform governance convergence point

Within `run / validate / report / result / cancel`, the facade is responsible for:

- Resolving the caller
- Performing the permission check
- Creating ownership
- Reading and writing the task store
- Deciding whether trace is returned
- Projecting the runtime output into a response friendlier to the frontend

If you later want to wire in permissions, audit, tenants, or a task center, this is the first entry point.

## 6. The real boundary of the storage model

This is the part most easily misjudged.

### 6.1 The run-time truth lives in runtime memory

`FlowGramRuntimeService` internally holds:

```java
private final ConcurrentMap<String, TaskRecord> tasks = new ConcurrentHashMap<String, TaskRecord>();
```

`report(...)` and `result(...)` read this in-process state first.

### 6.2 `FlowGramTaskStore` is not a complete durable execution store

The current `FlowGramTaskStore` only defines:

- `save`
- `find`
- `updateState`

Default implementations include:

- `InMemoryFlowGramTaskStore`
- `JdbcFlowGramTaskStore`

They are responsible for:

- ownership metadata
- task state snapshots
- result snapshot

But the first source of truth for `report` / `result` still comes from the runtime. In other words, the JDBC store is more of a "platform recording layer", not a "fully recoverable execution core".

### 6.3 The architectural implication

This design is very well suited to:

- Monolithic backends
- demo / staging
- platform prototypes
- Scenarios that need to query task state but do not require distributed recovery

But if your goal is "seamless execution-state recovery after restart", further evolution is needed.

## 7. Defaults for security and tenant boundaries

The default security posture must be stated clearly, otherwise it is easy to overestimate the maturity of this starter.

### 7.1 The caller can be anonymous by default

`DefaultFlowGramCallerResolver` returns an anonymous caller directly when `auth.enabled = false`.

Once auth is enabled, it reads from:

- `Authorization`
- `X-Tenant-Id`

by default to obtain caller and tenant information.

### 7.2 The access checker allows everything by default

:::warning The default security posture is open
`DefaultFlowGramAccessChecker.isAllowed(...)` returns `true` directly.

This shows the current default positioning of the starter is:

- Wire the platform up first
- Harden the security policy yourself by replacing beans
:::

### 7.3 Ownership does have an abstraction layer

`FlowGramTaskOwnershipStrategy` is responsible for creating ownership. The default implementation writes:

- `creatorId`
- `tenantId`
- `createdAt`
- `expiresAt`

So although permission governance is very light by default, the extension point is already in place.

## 8. Failure paths and debugging implications

If this layer is not documented clearly, the documentation will be reduced to nothing but flowcharts.

### 8.1 Pre-submission failures

Common cases:

- schema missing
- JSON parsing failure
- Multiple `Start`
- No `End`
- Node references do not exist
- Required fields not bound
- Node type not registered

These issues should primarily be surfaced through `/validate`.

### 8.2 During-execution failures

Common cases:

- LLM node missing model name or prompt
- Custom executor throwing an exception
- Graph execution never reaching `End`
- Cycle detection triggered at run time

These issues will show up in the node status, workflow status, error field, and trace events.

### 8.3 Cancellation is best-effort

The `cancelTask(...)` implementation will:

- Mark `cancelRequested`
- Call `future.cancel(true)`

This means the cancellation semantics are best-effort, not a transactional rollback.

## 9. Key extension points

If you want to keep building this architecture into a platform, the most important extension points are:

- `FlowGramNodeExecutor`
- `FlowGramLlmNodeRunner`
- `FlowGramRuntimeListener`
- `FlowGramTaskStore`
- `FlowGramCallerResolver`
- `FlowGramAccessChecker`
- `FlowGramTaskOwnershipStrategy`

They correspond respectively to:

- Node capability
- LLM node strategy
- Event listening
- State persistence
- Identity resolution
- Permission decision
- Task ownership and retention policy

## 10. Where to go after this chapter

Recommended order to continue:

1. [Runtime](/docs/products/flowgram/runtime)
2. [Frontend / Backend Integration](/docs/products/flowgram/frontend-backend-integration)
3. [Built-in Nodes](/docs/products/flowgram/built-in-nodes)
4. [Custom Nodes](/docs/products/flowgram/custom-nodes)

If you only take away one architectural conclusion:

The core of Flowgram is not "the frontend canvas", but "turning the graph produced by the canvas into a backend execution system with a formal task lifecycle, a node execution contract, and the ability to wire in platform governance".
