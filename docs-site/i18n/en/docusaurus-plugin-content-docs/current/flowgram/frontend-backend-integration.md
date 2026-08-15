---
sidebar_position: 4
title: "Frontend Canvas and Backend Runtime Integration"
description: "The three contracts that align the Flowgram.ai canvas with the AI4J Java backend execution layer: workflow schema, task lifecycle, and the report/result/trace read side — covering schema normalization, polling, and permission integration points."
tags: [concept]
---

# Frontend Canvas and Backend Runtime Integration

This page is not about "building yet another frontend editor", but rather how the `Flowgram.ai` canvas aligns with AI4J's Java backend execution layer.

Saying "the frontend just calls a few endpoints" misses the most critical fact: what actually aligns the frontend and backend is not the buttons, but 3 contracts.

- workflow schema contract
- task lifecycle contract
- report / result / trace read-side contract

## 1. Start with the full chain

The main chain of the current reference implementation is:

```text
Flowgram.ai canvas
  -> runtime plugin
  -> WorkflowRuntimeServerClient
  -> /flowgram/tasks/*
  -> FlowGramTaskController
  -> FlowGramRuntimeFacade
  -> FlowGramRuntimeService
  -> node executors / LLM node runner
  -> report / result / trace
```

This chain tells you one thing: AI4J is not responsible for the frontend canvas itself, but rather the formal execution backend behind the canvas.

## 2. The frontend does not "directly call five endpoints"

In `ai4j-flowgram-webapp-demo`, the frontend runtime is wired in through the runtime plugin, not by hand-writing `fetch` inside components.

Key files:

- `src/plugins/runtime-plugin/create-runtime-plugin.ts`
- `src/plugins/runtime-plugin/runtime-service/index.ts`
- `src/plugins/runtime-plugin/client/server-client/index.ts`

### 2.1 Two modes are currently supported

- `browser`
- `server`

If you want to integrate with the AI4J Java backend, use `server` mode.

### 2.2 Who sends requests in `server` mode

The frontend binds `WorkflowRuntimeServerClient`, which centrally calls:

- `POST /flowgram/tasks/validate`
- `POST /flowgram/tasks/run`
- `GET /flowgram/tasks/{taskId}/report`
- `GET /flowgram/tasks/{taskId}/result`
- `POST /flowgram/tasks/{taskId}/cancel`

This means the frontend should treat the backend as a task API server, not "some node firing off an ad-hoc request".

## 3. Before the frontend sends to the backend, the schema is rewritten first

This layer is the most easily overlooked yet actually most critical part of the whole integration.

Key file:

- `ai4j-flowgram-webapp-demo/src/utils/backend-workflow.ts`

### 3.1 UI-only nodes never reach the backend

Currently, at least the following are filtered out:

- `Comment`
- `Group`
- `BlockStart`
- `BlockEnd`

These objects are meaningful to the editor, but meaningless to the executor.

### 3.2 Frontend types map to backend execution types

Currently confirmed mappings include:

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

This shows:

- frontend display types are not necessarily the same as backend protocol types
- there is an explicit adaptation layer between the frontend schema and the backend runtime

### 3.3 Why this step matters

Without this adaptation layer, the backend would be forced to understand canvas internals, ultimately causing:

- UI structure leaking into the execution layer
- frontend iteration dragging the backend contract along
- custom nodes becoming hard to maintain

So the most robust approach is to treat "pre-execution normalization" as a formal protocol step, not a demo trick.

## 4. The backend side is more than just a controller

At least 3 layers participate in the integration on the backend.

### 4.1 `FlowGramTaskController`: HTTP exposure layer

The controller is mounted by default at:

- `${ai4j.flowgram.api.base-path:/flowgram}`

It only exposes the REST entry points; it does not execute business logic.

### 4.2 `FlowGramRuntimeFacade`: platform governance layer

The facade does far more than the controller:

- converts the request into runtime input
- resolves the caller
- performs access checks
- creates task ownership
- writes state to `FlowGramTaskStore`
- decides whether to include node details / trace based on configuration

If you later need to integrate permissions, a task center, or audit, this layer is the real backend boundary.

### 4.3 `FlowGramRuntimeService`: the actual execution layer

The runtime is responsible for:

- schema validation
- task record creation
- node graph execution
- report / result generation

This shows the frontend-backend integration is not "frontend JSON -> controller -> done"; there are explicit platform and execution layers in between.

## 5. How the frontend runtime actually drives a task

The current main flow of `WorkflowRuntimeService` is very clear.

### 5.1 Local form validation first, before submission

The frontend first walks all node forms to ensure basic form constraints pass.

### 5.2 Then call `/tasks/validate`

This step catches errors from the backend's perspective, such as:

- invalid graph structure
- unregistered node types
- required inputs not bound
- invalid reference paths

### 5.3 Once it passes, call `/tasks/run`

The backend does not return the final result; it returns a `taskId`. This means the execution model is an async task.

### 5.4 The frontend polls `report` at a fixed interval

Current frontend constant:

- `SYNC_TASK_REPORT_INTERVAL = 500`

That is, by default it polls the task report once every `500ms`.

### 5.5 After it finishes, fetch the `result`

The final output is not inferred directly from the report, but formally fetched via `/tasks/{taskId}/result`.

## 6. Do not conflate the `validate`, `report`, and `result` responses

This is the most common mistake when wiring up the frontend.

### 6.1 `validate`

Suitable for:

- pre-submission blocking
- form error messaging
- schema validity confirmation

Not suitable for:

- runtime UI display

### 6.2 `report`

Suitable for:

- in-progress status panel
- node highlighting
- error localization
- progress timeline

If `reportNodeDetails = true`, it also includes node-level inputs / outputs.

### 6.3 `result`

Suitable for:

- final output display
- task completion page
- final success / failure settlement

It leans toward "final result view", while report leans toward "execution process view".

## 7. `trace` is a projection for the frontend to consume directly, not raw instrumentation

When `ai4j.flowgram.trace-enabled = true`:

- `/report`
- `/result`

both include a `trace` field.

This data originates from:

- `FlowGramRuntimeEvent`
- `FlowGramRuntimeTraceCollector`
- `FlowGramTraceView`

### 7.1 What problem it solves

The frontend needs:

- top-level task status
- node execution timeline
- which node failed
- per-node duration and metrics

This kind of UI should not consume raw backend instrumentation objects directly; it should consume the `FlowGramTraceView` projected for the frontend.

### 7.2 Why not just let the frontend read OTel directly

Because the two layers have different goals:

- OTel targets backend observability platforms
- `FlowGramTraceView` targets the frontend canvas runtime

Keeping them separate makes the frontend-backend responsibility boundary clearer.

## 8. The role of the Protocol Adapter here

`FlowGramProtocolAdapter` is one of the protocol convergence points between frontend and backend.

It wires together:

- HTTP request DTOs
- runtime input/output models
- response DTOs consumed by the frontend

### Why it matters

Because it guarantees a few key facts:

- the request `schema` can be an object or a JSON string
- the response is copied into a safer map structure
- the external field shape of report / result is fixed by the adapter, not leaked from runtime internal objects

This makes protocol evolution more controllable than directly exposing internal models.

## 9. The 4 most common integration issues

### 9.1 The node renders on the frontend, but the backend does not recognize it

The usual causes are:

- type mapping missing
- backend has not registered the corresponding executor
- the node was mistakenly filtered out as a UI-only type

### 9.2 The form looks filled in, but the backend still reports missing required fields

The usual causes are:

- frontend form field names don't match the field names the backend reads
- the binding is written in `data` but never enters `inputsValues`
- fields lost during schema normalization

### 9.3 The task runs, but the report shows no node detail

Usually check:

- whether `reportNodeDetails` is enabled
- whether the frontend is reading `workflow.nodes`
- whether the node outputs are written out properly by the executor

### 9.4 Cannot get task details after a restart

This is an architectural boundary, not necessarily a bug.

Currently the source of truth for running state lives mainly in the in-process `TaskRecord` of `FlowGramRuntimeService`. `FlowGramTaskStore` persists metadata and snapshots, but it is not a fully recoverable execution engine.

## 10. Where the permission and multi-tenancy integration points are

:::warning Default is a light-security posture
The current default is a light-security posture:

- `auth.enabled = false`
- caller is anonymous by default
- access checker allows all by default
:::

If you want to integrate with an enterprise platform, the key integration points are:

- `FlowGramCallerResolver`
- `FlowGramAccessChecker`
- `FlowGramTaskOwnershipStrategy`

In other words, permission control lives not in the frontend runtime plugin, but in the backend facade layer.

Additionally, the frontend canvas and the backend task API are usually deployed on different origins, so cross-origin browser calls to `/flowgram/**` require CORS to be allowed by the backend: add the editor's origin to `ai4j.flowgram.cors.allowed-origins` (empty by default, bound to `CorsProperties`). For the full field list, see [Spring Boot Configuration Reference](/docs/spring-boot/configuration-reference) §5 (`ai4j.flowgram.*` / CORS).

## 11. The most important integration principle

The most robust integration approach is not to let the frontend know more backend details, but to hold these 3 boundaries:

- the canvas layer owns editing and display
- the adaptation layer owns compressing edit state into execution state
- the backend owns the task lifecycle and node execution

Once these 3 boundaries are stable, frontend and backend can each iterate without dragging each other down.
