---
title: "Why Flowgram"
description: "The reason Flowgram exists — it supplies the platform-grade backend structure an Agent alone cannot offer (workflow contracts, task lifecycle, node-level execution boundaries, and platform-facing read-side outputs), and clarifies its current boundaries and applicable scenarios."
tags: [concept]
---

# Why Flowgram

`Flowgram` deserves to exist on its own, not because the phrase "visual workflow" sounds more complete, but because what a frontend canvas product demands from the backend is a different problem from what an ordinary `Agent` runtime has to solve.

With only an Agent you can ship a working agent; to ship a backend that a frontend canvas, a platform control plane, and a task center can all consume, you are still missing a lot of structured capability.

## 1. The platform problems an Agent cannot solve

A free-reasoning Agent is good at:

- Deciding the next step on its own based on the goal
- Making strategic choices between tools
- Completing open-ended tasks through memory, handoff, and tool use

But when the frontend is a workflow canvas like `Flowgram.ai`, what the platform actually needs is usually the following:

- A unified workflow schema shared between frontend and backend
- A formal `validate` entry point, rather than "run it and see what errors come back"
- A task ID that can be queried via report / result / cancel
- Node-level state, not just the final text output
- A control plane that can wire into the task center, permissions, audit, and the trace panel

If these capabilities keep piling up on the prompt or the session, the product boundaries get blurry.

## 2. What Flowgram actually adds

Reading the source, AI4J Flowgram does not add a "graphical UI" — it adds four kinds of backend structure.

### 2.1 An explicit workflow contract

The frontend normalizes the canvas data first, then sends it to the backend for execution.

`backend-workflow.ts` strips out the nodes that only belong to the UI and maps frontend types to backend-executable types. The result is:

- The canvas can still hold annotations, groupings, and block boundaries
- The backend only receives an executable graph

This stops the "editing model" and the "execution model" from being conflated.

### 2.2 A formal task lifecycle

What `FlowGramTaskController` exposes is not a single `invoke` — it is a whole family of lifecycle APIs:

- `run`
- `validate`
- `report`
- `result`
- `cancel`

This means the frontend, test scripts, and platform hosts can all work against the same task protocol.

### 2.3 Node-level execution boundaries

In Flowgram, the flow graph is not decoration — it is the structure that actually drives execution paths.

`FlowGramRuntimeService` natively supports:

- `START`
- `END`
- `LLM`
- `CONDITION`
- `LOOP`

Capabilities such as `HTTP`, `VARIABLE`, `CODE`, `TOOL`, and `KNOWLEDGE` are injected through `FlowGramNodeExecutor`. This keeps "flow control" and "business capability" on separate layers by design.

### 2.4 Platform-facing read-side output

What the frontend wants is not only "the final result" — it also wants to know:

- Whether the current task is still running
- Which node failed
- How long each node took
- Which events occurred in the trace

`report` / `result` / `trace` are designed for this platform read side, not for the prompt.

## 3. Why the frontend canvas scenario needs it in particular

When a flow canvas sits at the front of the system — rather than a backend engineer who only writes code — the way the backend interface is abstracted changes.

### 3.1 The user sees the graph first, not the call stack

In a workflow platform, what the user grasps first is:

- Nodes
- Edges
- Inputs and outputs
- Where the current highlight is

That forces the backend to speak in terms of nodes and tasks too, instead of just returning "what the model answered".

### 3.2 The platform needs preflight checks, not trial-and-error at runtime

`WorkflowRuntimeService` calls `/tasks/validate` first, and only calls `/tasks/run` after it passes.

This is not a redundant step — it is a normal requirement for a canvas product:

- The form has to surface missing fields first
- Invalid node references have to be reported first
- An unsupported type has to be blocked before submission

Otherwise the frontend can only treat a run failure as a validation failure, which makes troubleshooting painful.

### 3.3 The platform needs cancellation, result polling, and a detail page

An ordinary Agent demo easily ignores "how the UI reflects state during a long task". Flowgram is designed for these scenarios by default:

- `run` returns a `taskId` immediately
- The frontend polls `report`
- Once the task finishes, it fetches `result`
- When needed, it can `cancel`

This model fits canvases, task centers, and consoles well.

## 4. The difference from an Agent is not "whether it can use a model"

Both can use a model, but they organize the model differently.

| Dimension | Agent | Flowgram |
| --- | --- | --- |
| Main control logic | A model loop decides the next step | The graph structure decides the next step |
| Core contract | request / session / tools | workflow schema / task API / node contract |
| Read-side focus | output / trace / messages | task status / node status / report / result / trace |
| How it extends | tools, memory, handoff, runtime | node executor, task store, access checker, runtime listener |
| Best-fit tasks | open-ended reasoning | explicit flows, canvas platforms, stable schemas |

The key point: Flowgram does not abandon the Agent — it confines the Agent to a single LLM node.

`Ai4jFlowGramLlmNodeRunner` constructs an Agent with `maxSteps(1)` by default, which means it reuses the reasoning foundation without handing the whole graph back over to free reasoning.

## 5. What it buys you

### 5.1 A more stable frontend/backend contract

As long as the frontend canvas aligns around the task API and schema, it can collaborate with the backend long-term without hard-coding some prompt detail into the frontend.

### 5.2 Easier problem localization

In the current implementation, failures usually fall into a few very explicit boundaries:

- Schema parsing failed
- Node type not supported
- Required input missing
- Node output reference does not exist
- LLM node missing a model name or prompt
- The graph has a cycle or never reaches `End`

These errors are more diagnosable than "the agent answered wrong".

### 5.3 Easier to wire into platform governance

`FlowGramRuntimeFacade` has already factored these governance points out:

- caller resolution
- access check
- task ownership
- task store
- trace response enrichment

This shows it is built, by nature, as a backend meant "to be taken over by a platform", not just to serve a demo.

## 6. What it does not solve

This matters just as much. The current implementation is not a complete distributed workflow engine.

### 6.1 The runtime truth still lives in-process

`FlowGramRuntimeService` keeps the `TaskRecord` in an in-process `ConcurrentMap`. `FlowGramTaskStore` records ownership and result snapshots, but the first source of truth that `report` / `result` read still comes from runtime memory.

The direct consequences are:

- It fits as a platform backend prototype and a monolithic service
- But it is not a durable scheduler that "seamlessly resumes execution state after a restart"

### 6.2 The default security model is open

:::warning The default security model is open
By default:

- `auth.enabled = false`
- `DefaultFlowGramCallerResolver` returns an anonymous caller
- `DefaultFlowGramAccessChecker` always allows

This is a fine starting point for demos and intranet integration, but it should not be mistaken for completed multi-tenant permission governance.
:::

### 6.3 The default is a polling model, not a real-time push stream

The frontend runtime currently polls `report` on a fixed interval. The `streamProgress` config also defaults to `false`. If you want to build a highly real-time, multi-session, large-scale console, you will need to evolve the push pipeline yourself.

## 7. When you should choose it

Scenarios that favor Flowgram:

- The task is naturally a flow graph
- The frontend will have a real visual canvas
- Node inputs and outputs need to stay stable long-term
- You want the backend to expose a formal task API, not just SDK calls

Scenarios that should not prioritize Flowgram:

- The requirement is fundamentally open-ended multi-step reasoning
- There is no canvas and no need for a task control plane
- You just want to write a quick model-call sample

## 8. Suggested further reading

1. [Architecture](/docs/flowgram/architecture)
2. [Runtime](/docs/flowgram/runtime)
3. [Frontend / Backend Integration](/docs/flowgram/frontend-backend-integration)
4. [Built-in Nodes](/docs/flowgram/built-in-nodes)
5. [Custom Nodes](/docs/flowgram/custom-nodes)

If you only take away one conclusion:

The value of `Flowgram` is not in drawing the flow — it is in turning "the drawn flow" into a backend execution system with a formal contract, a control plane, governability, and extensibility.
