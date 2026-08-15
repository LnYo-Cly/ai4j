---
sidebar_position: 7
title: "Flowgram API and Runtime"
description: "The task control-plane contract that external callers actually face: the five HTTP entry points validate/run/report/result/cancel, how DTOs are reshaped by the adapter and facade, and the configuration items that genuinely change external behavior."
tags: [reference]
---

# Flowgram API and Runtime

This page does not re-explain the runtime core itself. It focuses specifically on the control-plane contract that external callers actually face.

If `runtime.md` describes the execution truth, this page describes:

- What the HTTP API looks like
- How DTOs are reshaped by the adapter and facade
- Which configuration items genuinely change external behavior
- How task store, auth, and trace surface in the external protocol

## 1. Look at the control plane first, not at internal class names

What external callers actually face is these five entry points:

- `POST /flowgram/tasks/validate`
- `POST /flowgram/tasks/run`
- `GET /flowgram/tasks/{taskId}/report`
- `GET /flowgram/tasks/{taskId}/result`
- `POST /flowgram/tasks/{taskId}/cancel`

The default base path comes from:

- `ai4j.flowgram.api.base-path`

Default value:

- `/flowgram`

Together these endpoints form the Flowgram task control plane.

## 2. The layers behind the API

An HTTP request does not go straight into the runtime. It passes through at least these three layers:

1. `FlowGramTaskController`
2. `FlowGramRuntimeFacade`
3. `FlowGramProtocolAdapter`

Plus the final one:

4. `FlowGramRuntimeService`

Each has a different responsibility.

### 2.1 Controller: only exposes HTTP

The Controller's only job is to accept REST requests and forward them to the facade.

### 2.2 Facade: collects platform semantics

The Facade is responsible for:

- resolve caller
- access check
- ownership generation
- task store sync
- whether trace / node detail is returned

### 2.3 ProtocolAdapter: pins down the protocol

`FlowGramProtocolAdapter` is responsible for:

- Turning the request DTO into the runtime `FlowGramTaskRunInput`
- Turning the runtime output into `run/validate/report/result/cancel` response DTOs
- Copying map/list values to avoid leaking internal objects directly to the outside

So the real shape of the external protocol is not determined directly by the internal runtime classes, but fixed by the adapter.

## 3. The `validate` contract

### Input

`validate` accepts:

- `schema`
- `inputs`

Here `schema` can be either an object or a JSON string, because `FlowGramProtocolAdapter` uniformly runs `schemaToJson(...)` on it.

### Output

The core fields `validate` returns are:

- `valid`
- `errors`

### When to call it

Suitable for:

- Frontend pre-submit validation
- CI validation of the workflow schema
- Node editor integration debugging

Not suitable for:

- Treating it as the actual execution result

## 4. The `run` contract

### Input

Same as `validate`:

- `schema`
- `inputs`

### Output

`run` does not return the final result, but:

- `taskId`

This must be emphasized, because it determines that the whole system follows a task-based model, not synchronous RPC.

### What happens in run

`FlowGramRuntimeFacade.run(...)` will:

1. resolve caller
2. `ensureAllowed(RUN, ...)`
3. call `runtimeService.runTask(...)`
4. create ownership
5. write the initial state to `FlowGramTaskStore`

So `run` is not just "start the runtime". It is also the point where platform metadata is created.

## 5. The contract difference between `report` and `result`

These two endpoints are the easiest to misuse.

### 5.1 `report`

`report` leans more toward an execution-process view.

By default it returns:

- `taskId`
- `inputs`
- `outputs`
- `workflow`
- optional `nodes`
- optional `trace`

Whether `nodes` is returned is controlled by:

- `ai4j.flowgram.report-node-details`

and defaults to `true`.

### 5.2 `result`

`result` leans more toward the final closed-out view.

The core fields are:

- `taskId`
- `status`
- `terminated`
- `error`
- `result`
- optional `trace`

### 5.3 Why split it into two responses

Because the two kinds of callers care about different things:

- Task dashboards look at `report`
- Final-result consumers care more about `result`

If the two were merged into one endpoint, the external protocol would grow increasingly bloated.

## 6. The `cancel` contract

The `cancel` response is simple:

- `success`

But its semantics should not be read as "transactional rollback". The current implementation only:

- Marks cancel requested
- Interrupts the task `Future`

So it is a best-effort stop.

## 7. Which error semantics are visible at the HTTP layer

### 7.1 Validation errors

Usually returned through `validate` with `valid=false` and `errors[]`.

### 7.2 Non-existent tasks

Confirmed from integration tests: an unknown task returns 404 with:

- `code = FLOWGRAM_TASK_NOT_FOUND`

This means task-not-found is already an explicit error semantic at the HTTP layer, not a simple empty object.

### 7.3 Access denied

If a custom `FlowGramAccessChecker` denies access, the facade throws an access-denied exception. This is the real interception point for platform-side permission governance.

## 8. Which configuration items change external behavior

Not all configuration is equally important. The ones below directly affect the external calling experience.

### 8.1 `enabled`

- Whether the entire Flowgram starter is enabled

### 8.2 `default-service-id`

- Decides which service an LLM node uses by default when no explicit `serviceId` / `aiServiceId` is passed

### 8.3 `stream-progress`

- Currently defaults to `false`
- Do not misread it as "there is already a complete real-time push control plane"

### 8.4 `task-retention`

- Mainly affects ownership / retention metadata
- Not equivalent to durable execution retention

### 8.5 `report-node-details`

- Decides whether `report` returns node-level inputs / outputs

### 8.6 `trace-enabled`

- Decides whether `report` / `result` carry `trace`

### 8.7 `task-store.type`

Supports:

- `memory`
- `jdbc`

### 8.8 `auth.enabled` / `auth.header-name`

Decides whether `DefaultFlowGramCallerResolver` parses the caller from the request header.

Default is:

- `auth.enabled = false`

This is also why by default the caller is usually treated as an anonymous caller.

## 9. What the JDBC store actually means at the API layer

This is very easy to get wrong.

Switching to:

```yaml
ai4j:
  flowgram:
    task-store:
      type: jdbc
```

Plus a `DataSource`, the starter will wire:

- `JdbcFlowGramTaskStore`

This gives you:

- Task metadata persisted to the database
- State snapshots persisted to the database
- result snapshots persisted to the database

:::warning JDBC store is not a durable execution engine
But do not misread it as "the external API has already become a durable workflow engine that can recover execution across processes".

Currently:

- The first source of truth for `report` / `result` still comes from the in-process runtime `TaskRecord`
- The task store is more like a platform record layer
:::

## 10. The relationship between `report` / `result` and trace

Trace is not an additional standalone endpoint. It is a frontend-consumable projection embedded in `report` / `result`.

This layer of data comes from:

- runtime event
- trace collector
- trace response enricher

Its targets are:

- Providing runtime state for the frontend canvas
- The task detail page
- Node-level debugging panels

It is not meant to replace OTel, nor to expose internal events verbatim to the client.

## 11. When to read this page instead of the runtime page

Scenarios where you should read this page first:

- You are writing a frontend or test script
- You are wiring an HTTP client
- You are doing task API governance
- You are integrating caller / access / ownership / task store

Scenarios where you should read `runtime.md` first:

- You are tracing an execution chain
- You are writing an executor
- You are looking at graph validation and node dispatch

If you remember only one sentence:

`runtime.md` explains "how the system runs", and this page explains "how the outside should talk to it".
