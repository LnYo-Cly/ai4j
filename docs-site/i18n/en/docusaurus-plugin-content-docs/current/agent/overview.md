---
sidebar_position: 1
title: "Agent Overview"
description: "ai4j-agent overview: built on the Core SDK, it provides multi-step reasoning, tool loop, memory, workflow, trace, and multi-agent collaboration — when to use Agent, the minimal mental model, runtime selection, and module boundaries."
tags: [concept]
---

# Agent Overview

`ai4j-agent` is a Java Agent runtime built on top of the Core SDK. It does not replace the model call layer; rather, it solves the problem of "after a single model call, how does the system keep advancing the task".

If you only need to send a single message, start with [Core SDK / Model Access](/docs/core-sdk/model-access/overview). Enter this chapter only if you need multi-step reasoning, a tool loop, memory, workflow, trace, or multi-agent collaboration.

## In one sentence

Agent solves:

> Organizing the model, tools, memory, runtime strategy, and event observability into a multi-step execution unit that can be embedded in a Java project.

It is better suited as an intelligent runtime inside business systems, not as a local code repository assistant. For the latter, see [Coding Agent](/docs/coding-agent/overview).

## When you need Agent

| Problem you face | Is Agent suitable |
| --- | --- |
| Only need a single Chat or Responses request | No, start with the Core SDK |
| Need the model to keep calling tools based on intermediate results | Suitable |
| Need to persist session state across steps | Suitable |
| Need explicit workflow, StateGraph, conditional routing | Suitable |
| Need to record every model and tool event at each step | Suitable |
| Need code repository files, shell, patch, approval, session store | See Coding Agent |
| Need visual flowcharts and a task API | See FlowGram |

## Minimal mental model

A single Agent run can be understood as this chain:

```text
AgentRequest
  -> AgentRuntime
  -> AgentModelClient
  -> AgentToolRegistry + ToolExecutor
  -> AgentMemory
  -> AgentEventPublisher
  -> AgentResult
```

Key boundaries:

- `AgentModelClient` adapts the Agent prompt to Chat or Responses.
- `AgentToolRegistry` decides which tools the model can see.
- `ToolExecutor` decides what the system actually executes on a tool call.
- `AgentMemory` owns state and history.
- `AgentEventPublisher` owns trace and host-side observation.

## Runtime selection

| Runtime | What it fits |
| --- | --- |
| ReAct | General multi-step reasoning, tool calls, text tasks |
| CodeAct | Complex tasks that use code as an intermediate representation |
| DeepResearch | More research-oriented, planning, and multi-turn material consolidation |
| StateGraph | When you need explicit nodes, branches, loops, and state transitions |

Do not conflate runtime selection with model selection. The model decides "how to generate", while the runtime decides "how the task advances".

## Boundaries with adjacent modules

| Module | What it owns |
| --- | --- |
| `ai4j` | provider, Chat, Responses, Tool, MCP, RAG, base memory |
| `ai4j-agent` | runtime, tool loop, workflow, memory/state, trace, team |
| `ai4j-coding` | workspace-aware tools, approval, session, compact, code repository tasks |
| `ai4j-cli` | CLI, TUI, ACP host, and local session entry point |
| `ai4j-flowgram-*` | Explicit workflow graphs, task API, node execution, and front-end/back-end integration |

Agent is a general-purpose runtime layer. It can be reused by Coding Agent and FlowGram, but it does not itself own the host product shape.

If you care about how the upcoming Agent SDK will enhance Session, Memory, Blueprint, Sandbox, and Runner, see [AI4J Agent SDK Roadmap](/docs/agent/sdk-roadmap).

## What to note for production integration

- Set upper bounds on `maxSteps`, the tool loop, and stop conditions.
- Do not expose every Tool / MCP capability to the model by default.
- Do not store real secrets or unmasked sensitive data in memory.
- trace may contain prompts, tool parameters, and model output; mask them per scenario.
- In multi-user scenarios, session isolation is not the same as tool permission isolation.
- SubAgent or team orchestration needs explicit handoff and permission boundaries.

For more checklist items, see [Security Overview](/docs/security/overview) and [Production Checklist](/docs/operations/production-checklist).

## Recommended reading order

### If you want to decide whether you need Agent first

1. [Why Agent](/docs/agent/why-agent)
2. [Use Cases and Paths](/docs/agent/use-cases-and-paths)
3. [Architecture](/docs/agent/architecture)
4. [AI4J Agent SDK Roadmap](/docs/agent/sdk-roadmap)

### If you want to get it running first

1. [Quickstart](/docs/agent/quickstart)
2. [Model Client Selection](/docs/agent/model-client-selection)
3. [Minimal ReAct Agent](/docs/agent/minimal-react-agent)

### If you want to go deep on the runtime

1. [Runtime Implementations](/docs/agent/runtime-implementations)
2. [Tools and Registry](/docs/agent/tools-and-registry)
3. [Memory and State](/docs/agent/memory-and-state)
4. [Trace Observability](/docs/agent/trace-observability)

### If you want to do orchestration and collaboration

1. [Workflow StateGraph](/docs/agent/workflow-stategraph)
2. [SubAgent Handoff Policy](/docs/agent/subagent-handoff-policy)
3. [Agent Teams](/docs/agent/agent-teams)

If you want to work directly on local code repository tasks, do not force-extend from here; go straight to [Coding Agent Overview](/docs/coding-agent/overview).
