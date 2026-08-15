---
title: "Flowgram Runtime"
description: "The Flowgram backend execution truth: FlowGramRuntimeService turns a workflow schema into async tasks, the node graph into a stateful execution chain, and node outputs into report/result/trace read-side structures."
tags: [concept]
---

# Flowgram Runtime

The `Runtime` layer is the backend execution truth of Flowgram — it is not as simple as "slap a service behind a controller".

If you had to summarize it in one sentence:

> `FlowGramRuntimeService` turns a workflow schema into async tasks, the node graph into a stateful execution chain, and node outputs into report / result / trace consumable read-side structures.

## 1. Start with the responsibility boundary of the Runtime

The Runtime mainly answers five questions:

- How is the schema validated
- How is a task created and finished
- How are nodes scheduled for execution
- How do node state and workflow state transition
- Where do report / result ultimately come from

This is not about how the frontend draws the graph, nor how the starter exposes HTTP — it is "how the backend actually runs".

## 2. The real lifecycle of a task

From the source, the lifecycle of a single task is well-defined.

### 2.1 `validateTask(...)`: pre-check only, no task created

`validateTask(...)` runs the same structural validation logic, but it does not create a `taskId`, and it does not trigger execution.

This is critical for frontend products, because it lets you discover — before actually submitting the task:

- missing schema
- unsupported node types
- edges referencing non-existent nodes
- required inputs left unbound
- invalid output references

### 2.2 `runTask(...)`: creates the task and returns asynchronously right away

The core flow of `runTask(...)` is:

1. `parseAndValidate(input)`
2. generate `taskId`
3. create `TaskRecord`
4. put it into the in-process `ConcurrentMap<String, TaskRecord>`
5. `executorService.submit(...)` to actually execute
6. return `FlowGramTaskRunOutput` immediately

This means the semantics of `runTask(...)` is "submit the task", not "complete the task synchronously".

### 2.3 `getTaskReport(...)` / `getTaskResult(...)`: read-side APIs

After the task is submitted, the frontend or platform side reads the execution status and outputs through:

- `getTaskReport(...)`
- `getTaskResult(...)`

Currently report leans toward the execution view, and result leans toward the final-output view; both depend on the runtime state inside `TaskRecord`.

### 2.4 `cancelTask(...)`: best-effort cancel

The cancel implementation will:

- set `cancelRequested`
- call `cancel(true)` on the corresponding `Future`

So it is a best-effort cancel, not a transactional rollback.

## 3. What does validation actually validate

Flowgram validation is not "as long as the JSON parses" — this matters.

### 3.1 Graph structure constraints

`validateGraph(...)` at minimum checks:

- the schema exists and contains at least one node
- the root graph must have exactly one `Start`
- the root graph must have at least one `End`
- the source / target nodes of every edge must exist
- the block subgraph of a `LOOP` node is also validated recursively

### 3.2 Node definition constraints

`validateNodeDefinitions(...)` continues to check:

- whether the node ID is empty
- whether node IDs are duplicated
- whether the node type is supported
- whether required input bindings are missing
- whether nodes referenced by `REF`-type outputs actually exist

### 3.3 Why this design matters

This means many errors surface "before submission", rather than appearing as ambiguous exceptions during execution. This is also why `validate` is a first-class API.

## 4. What semantics the Runtime kernel actually hardcodes

Not all node capabilities sit on the same layer.

### 4.1 Core types natively built into the Runtime

The only types `FlowGramRuntimeService` understands directly are:

- `START`
- `END`
- `LLM`
- `CONDITION`
- `LOOP`

The key point here: the kernel only hardcodes control structures and the LLM node — it does not shove every business node into the kernel.

### 4.2 Other nodes go through `FlowGramNodeExecutor`

Capabilities like:

- `HTTP`
- `VARIABLE`
- `CODE`
- `TOOL`
- `KNOWLEDGE`

are not native logic of the runtime kernel; they are extended in through `FlowGramNodeExecutor`.

This is a healthy layering:

- graph semantics stay in the runtime
- business capability stays in the executor

### 4.3 The state machine is also well-defined

Currently the workflow and node states transition primarily around these five values:

- `pending`
- `processing`
- `success`
- `failed`
- `canceled`

This set of states matters for report, frontend highlighting, and trace events.

## 5. How node execution advances

Look at `executeTask(...)` and `executeFromNode(...)` and you understand the current execution model.

### 5.1 Recursive advance starting from `Start`

Once a task enters execution, the runtime will:

1. change the workflow state to `processing`
2. emit `TASK_STARTED`
3. enter `executeFromNode(...)` from the resolved `Start` node
4. after a node finishes, select the successor along the edges and continue recursively

### 5.2 The runtime does cycle detection

`executeFromNode(...)` maintains an `activePath`. If the same node is encountered again on the current path, it immediately throws:

- `Cycle detected in FlowGram graph at node ...`

This shows that the current execution model does not accept arbitrary cyclic graphs by default.

### 5.3 Never reaching `End` is treated as failure

If the execution chain ends without ever producing a final result, the runtime throws:

- `FlowGram workflow finished without reaching an End node`

This is not a minor detail — it makes `End` part of the formal termination semantics.

## 6. The real execution semantics of the LLM node

`Ai4jFlowGramLlmNodeRunner` determines how Flowgram reuses the Agent foundation.

### 6.1 It constructs an Agent on the fly

Each time an LLM node executes, the runner will:

- resolve the model client
- resolve the model name
- resolve the prompt
- construct an Agent with `AgentBuilder`
- run `agent.run(...)` once

### 6.2 By default it is not "free multi-step reasoning"

The default options are:

- runtime: `ReActRuntime`
- `maxSteps(1)`
- `stream(false)`

So this is more like a "single-node intelligent step" than an unbounded mini Agent.

### 6.3 Input fields have compatibility aliases

The model field accepts:

- `modelName`
- `model`
- `modelId`

The prompt field accepts:

- `prompt`
- `message`
- `input`

This compatibility keeps the coupling between the frontend node form and the backend executor lower.

### 6.4 The output is more than just text

The LLM node ultimately returns:

- `result`
- `outputText`
- `rawResponse`
- `metrics`

If a `TracePricingResolver` is configured, `metrics` also carries token and cost information.

## 7. The relationship between report / result / task store

This is the most easily misunderstood part.

### 7.1 The runtime truth lives in `TaskRecord`

Internally `FlowGramRuntimeService` uses `ConcurrentMap<String, TaskRecord>` to hold the task runtime state. Both report and result read from here first today.

### 7.2 The starter's `FlowGramTaskStore` is a supplementary layer

`FlowGramRuntimeFacade` writes status and result snapshots to `FlowGramTaskStore`, but this does not mean the runtime has become a recoverable execution engine.

The more accurate reading is:

- the runtime holds live execution state
- the task store holds the metadata and snapshots the platform needs

### 7.3 What this means for the platform

This structure is already enough to support:

- a task center
- a detail page
- a trace panel
- basic persistence

But if what you want is "cross-process recovery of the original task execution state", the current implementation has not gone that far yet.

## 8. The default thread model and what it implies

The Runtime uses an internal `ExecutorService` to execute tasks asynchronously by default.

### 8.1 By default it is a cached thread pool

Internal thread names look like:

- `ai4j-flowgram-1`
- `ai4j-flowgram-2`

This is well-suited for demos and lightweight platforms, but it also means thread governance, rate limiting, and resource isolation still need to be built out at your platform layer.

### 8.2 Cancellation relies on thread interruption

:::note Cancel responsiveness depends on the executor implementation
Because cancellation is done through `future.cancel(true)` and interrupt propagation, whether a long-blocking custom executor can respond in time depends on the quality of the executor's own implementation.
:::

## 9. The most important entry points for extending the Runtime

If you want to extend this layer, do not touch the controller first — look at these extension points:

- `FlowGramNodeExecutor`
- `FlowGramLlmNodeRunner`
- `FlowGramRuntimeListener`

They correspond respectively to:

- new node capabilities
- LLM node strategy
- runtime event observation

This is also why the Runtime is the true kernel of the whole subsystem.

## 10. Current boundaries

The Runtime is very clear today, but it also has explicit boundaries:

- no durable distributed scheduler by default
- no real-time push-style progress channel by default
- no strong permission model by default
- shoving complex business logic back into the LLM node is discouraged

If you understand these boundaries, you can judge more accurately when to keep building the platform layer, and when to go write a node executor.
