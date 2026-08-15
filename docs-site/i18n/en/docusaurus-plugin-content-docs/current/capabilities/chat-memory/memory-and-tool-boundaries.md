---
title: "Memory and Tool Boundaries"
description: "Clarifies the responsibility boundaries across three layers — ChatMemory, Tool, and MCP. Memory only stores session facts; tool approval and side-effect governance belong to the runtime. Explains why execution control should not be coupled into the memory abstraction."
tags: [concept]
---

# Memory and Tool Boundaries

This page is only about boundaries. Keeping memory, tool, and MCP separate is what lets you tell where the Core SDK's responsibilities actually end.

## 1. What `ChatMemory` is responsible for

`ChatMemory` is responsible for the factual content of the session:

- system / user / assistant messages
- multimodal user input
- tool calls that the assistant has issued
- tool output written back as results
- reusable context projections for `Chat` / `Responses`
- context trimming, summarization, snapshot, and recovery

Its responsibility is, at its core: **storing "the facts the model needs to see again on the next turn"**.

## 2. Why `addToolOutput(...)` does not make it a tool system

`ChatMemory` exposes:

- `addAssistantToolCalls(...)`
- `addToolOutput(toolCallId, output)`

This only means that memory can write the facts of a tool interaction back into the session history, so that subsequent model turns continue to see that context.

It does not imply that `ChatMemory` is responsible for:

- whether a tool is allowed to be called
- whether a tool requires approval
- whether a tool's side effects may execute
- how to compensate after a tool fails
- how a multi-step tool chain advances

It records "what happened", not "what should be allowed to happen".

## 3. What the Tool layer is actually responsible for

Within the Core SDK, the core boundaries for local tools live in:

- `ToolUtil`
- `@FunctionCall`
- `@FunctionRequest`
- `@FunctionParameter`
- the request-side `functions`
- the request-side `mcpServices`

This part is responsible for:

- which tool capabilities are exposed
- what the parameter schema looks like
- which tools the caller attaches to the model
- how local tools and remote MCP tools are aggregated

This is adjacent to memory, not contained by it.

## 4. The role of MCP here

MCP is also one source of capability, but its primary responsibilities are:

- establishing connections to external services
- managing transport and capability
- exposing available tools / resources / prompts

When MCP ultimately exposes capability to the model, it behaves like a tool source; conceptually, though, it still belongs to the protocol and integration layer, not the memory layer.

## 5. A layering that is useful for the architecture

- `ChatMemory`: stores the facts of the conversation
- `Tool`: provides local execution capability
- `MCP`: brings in external service capability

If any one layer starts to simultaneously carry "memory, authorization, execution, approval, recovery", that is usually a sign the boundaries have blurred.

## 6. Why this boundary matters

If tool governance responsibility is forced into memory, two problems show up immediately:

- session storage and execution control become coupled, turning memory into an implicit runtime
- business code starts depending on memory to express permissions, approvals, or side-effect state, leading to a distorted abstraction

AI4J currently keeps these layers separate, with the goal of letting:

- session history stay stable
- tool selection stay explicit
- external capability wiring stay replaceable

## 7. When you should upgrade to an upper-layer runtime

If what you actually care about is:

- tool approval
- multi-step state advancement
- checkpoint / resume
- workspace-level side-effect governance
- multi-agent handoff

then you should not keep pressing that responsibility onto `ChatMemory`. Instead, move into:

- `ai4j-agent`
- `ai4j-coding`

## 8. The takeaway of this page

> In AI4J, memory is responsible for storing context facts, tool is responsible for exposing and executing capability, and MCP is responsible for bringing in external service capability. `ChatMemory` can record tool interactions, but it is not the center of tool governance; once a problem starts to involve approval, execution control, or multi-step runtime semantics, it should be escalated to a higher-layer runtime.
