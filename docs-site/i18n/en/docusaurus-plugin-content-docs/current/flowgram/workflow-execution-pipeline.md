---
sidebar_position: 9
title: "How a front-end workflow executes on the back end"
description: "Breaks the full pipeline of a single workflow from edit state to execution state into six stages: JSON export, normalization, runtime plugin invocation orchestration, controller/facade/runtime layered execution, and read-side projection."
tags: [concept]
---

# How a front-end workflow executes on the back end

This page stops talking about front-end/back-end integration in general terms and instead tears apart the full pipeline of a single workflow from edit state to execution state.

If you want to know:

- what happens after `document.toJSON()`
- which canvas elements never reach the back end
- how `report` and `trace` get back to the front end

then this is the page to read.

## 1. Start with the full execution pipeline

The main path in the current reference implementation is:

```text
Flowgram.ai editor
  -> document.toJSON()
  -> normalizeWorkflowForBackend(...)
  -> serializeWorkflowForBackend(...)
  -> POST /tasks/validate
  -> POST /tasks/run
  -> FlowGramTaskController
  -> FlowGramRuntimeFacade
  -> FlowGramRuntimeService
  -> node execution
  -> report/result/trace projection
  -> front-end runtime snapshot
```

The real key is not any single interface, but rather *when* the edit-state object gets compressed into an execution-state object.

## 2. Stage 1: the edit-state workflow is exported to JSON from the canvas first

Before submitting a task, the front-end runtime exports the current workflow JSON from `WorkflowDocument`.

The object obtained at this stage still carries an editor-perspective structure.

That means it may contain:

- pure UI nodes
- block boundaries that only matter to the editor
- front-end internal type names

If this were sent straight to the back end, the back-end contract would be polluted by front-end internal structure.

## 3. Stage 2: `normalizeWorkflowForBackend(...)` performs execution-state normalization

The real bridge point lives in:

- `ai4j-flowgram-webapp-demo/src/utils/backend-workflow.ts`

### 3.1 It first filters out UI-only nodes

Today, these are explicitly removed:

- `Comment`
- `Group`
- `BlockStart`
- `BlockEnd`

This is a major design decision: canvas structure is not execution structure.

### 3.2 It then normalizes types

For example:

- `llm -> LLM`
- `tool -> TOOL`
- `knowledge -> KNOWLEDGE`

This step converts the type in the front-end node registry into the type the back-end runtime recognizes.

### 3.3 It also applies local data fixes

For example, a loop node gets its input schema and `inputsValues.loopFor` filled in. This shows that what the front-end hands the back end is not just "drop a few fields" — it also does protocol backfill.

### 3.4 It cleans up invalid edges

An edge only enters the back-end schema when both its source and target nodes exist and are valid.

This saves the back end from carrying the full cost of cleaning up canvas-state dirty data.

## 4. Stage 3: the runtime plugin organizes the call order

The front-end does not just POST `/run` the moment you hit run; instead `WorkflowRuntimeService` orchestrates a standard chain.

### 4.1 First, run local form validation

This walks every node form and makes sure the most basic editor constraints pass first.

### 4.2 Then call `/tasks/validate`

This escalates errors to the back-end schema perspective, for example:

- the back end does not support the node type
- a required binding is missing
- the root graph has no `End`

### 4.3 Once validation passes, call `/tasks/run`

At this point the back end returns a `taskId`, not the final output.

### 4.4 Then poll `report` at a fixed interval

Current front-end constant:

- `SYNC_TASK_REPORT_INTERVAL = 500`

This means the system default is a "polling observation" model, not a "server-side continuously pushes" model.

### 4.5 After the task ends, fetch `result`

The final result is not inferred from `report`; it is officially obtained through `result`.

## 5. Stage 4: back-end controller / facade / runtime enter execution in layers

### 5.1 Controller stage

`FlowGramTaskController` only catches the REST request and hands it to `FlowGramRuntimeFacade`.

### 5.2 Facade stage

`FlowGramRuntimeFacade` carries the platform-level semantics:

- caller resolution
- access check
- ownership creation
- task store update
- trace / node details assembly

### 5.3 Runtime stage

`FlowGramRuntimeService` is what actually handles:

- schema validation
- `TaskRecord` creation
- graph dispatch
- node status / workflow status update
- report / result aggregation

So the sentence "how a front-end workflow executes on the back end" really means: it first enters the platform control plane, then enters the execution engine.

## 6. Stage 5: node inputs are already resolved by the runtime before the executor runs

This is the most easily misunderstood part of custom nodes.

Before calling the executor, `executeCustomNode(...)` first:

- reads the node `inputsValues`
- resolves `REF` / `CONSTANT` / `TEMPLATE` / `EXPRESSION` using internal runtime logic
- applies input schema defaults

Only then does it put the result into:

- `FlowGramNodeExecutionContext.inputs`

So the `context.inputs` a custom executor receives is usually already an "execution-state input", not the raw front-end configuration object.

## 7. Stage 6: the back-end projects the execution state back into the front-end read side

After the back end finishes executing, it does not throw the internal object back to the front end as-is.

### 7.1 `FlowGramProtocolAdapter` owns the base response protocol

It turns the runtime output into:

- `FlowGramTaskReportResponse`
- `FlowGramTaskResultResponse`

### 7.2 `trace` does one more front-end projection

If `traceEnabled` is on, the facade also aggregates runtime events into a `FlowGramTraceView` and attaches it to the report / result.

This layer of data is better suited for direct front-end rendering:

- top-level status
- node highlighting
- timeline
- token / cost metrics

### 7.3 The front-end runtime then folds it into a snapshot

The final front-end runtime state does not stop at receiving the HTTP response; it further assembles:

- `validation`
- `report`
- `trace`
- `result`
- `errors`
- `status`

into a continuously evolving `WorkflowRuntimeSnapshot`.

## 8. The most common breakpoints in this pipeline

### 8.1 Broken at the normalization stage

Symptom:

- the front-end canvas looks normal
- the back-end receives the wrong node type

Usual cause:

- forgot to add to `BACKEND_TYPE_MAP`
- the node was misclassified as a UI-only type

### 8.2 Broken at the validate stage

Symptom:

- `/tasks/validate` returns a structured error

Usual cause:

- multiple `Start`s
- no `End`
- a required input was not bound
- the node type is not registered

### 8.3 Broken at the report polling stage after run

Symptom:

- there is a `taskId`
- but the front end never sees a terminal state

Usual cause:

- the executor is stuck
- an external HTTP / model call timed out
- the polling logic does not handle the terminated state correctly

### 8.4 Broken at the read-side mapping stage

Symptom:

- the back end actually ran
- but the front-end nodes are not highlighted or show no result

Usual cause:

- the front end did not consume `report.nodes` correctly
- only looked at `result`, ignored `report` / `trace`
- the output field path does not match what the front-end UI assumes

## 9. Why this execution pipeline is valuable

It lets the system fall into three natural segments:

- editor concerns
- execution concerns
- read-side rendering concerns

This is easier to debug than "cram everything into one request", and is more amenable to platformization.

If you keep building complex nodes or front-end panels, the next reads are:

1. [Front-end custom node development](/docs/flowgram/frontend-custom-node-development)
2. [FlowGram custom node extension](/docs/flowgram/custom-node-extension)
