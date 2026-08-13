---
sidebar_position: 999
title: "Glossary"
description: "Unified table of core AI4J terms: defines Agent, AiService, Chat, Coding Agent, Function Call, MCP, Memory, Skill, Responses, Tool Registry, and other key concepts to avoid cross-topic confusion."
tags: [reference]
---

# Glossary

This page unifies the core terms used across the AI4J documentation, so the same concept is not confused across different topics.

---

## A

### AI Foundation

In this documentation, "AI foundation" is not a marketing term but a structured positioning.

It indicates that AI4J does more than offer single-point model calls — it places these layers into one unified system:

- Model calls
- `Tool / Function Call`
- `Skill`
- `MCP`
- Upper layers: `Spring Boot / Agent / Coding Agent / FlowGram`

If you want to see where this layering starts, read these first:

- [Why AI4J](/docs/start-here/why-ai4j)
- [Architecture at a Glance](/docs/start-here/architecture-at-a-glance)

### ACP

`ACP` is the host integration protocol for the `Coding Agent`, used by IDEs, desktop apps, or custom front-ends to communicate with `ai4j-cli acp` via structured JSON-RPC.

It is not a model protocol, and it is not MCP.

Related docs:

- [MCP and ACP](/docs/coding-agent/mcp-and-acp)

### Agent

In AI4J, `Agent` refers to an agent framework built around model, runtime, tools, memory, and orchestration.

Related docs:

- [Agent Architecture Overview](/docs/agent/overview)

### AiService

AI4J's unified service factory, used to obtain service interfaces such as `Chat`, `Responses`, `Embedding`, `Audio`, `Image`, and `Realtime` by `PlatformType`.

Related docs:

- [Service Entry and Registry](/docs/core-sdk/service-entry-and-registry)

---

## C

### Chat

Refers to the `Chat Completions`-style, message-based model interface.

In AI4J it typically corresponds to:

- `IChatService`
- `ChatCompletion`
- `ChatCompletionResponse`

### CodeAct

A code-driven runtime in the AI4J Agent.

Suitable for:

- Generating code first
- Then calling tools multiple times through code
- Handling complex structured tasks

Related docs:

- [CodeAct Runtime](/docs/agent/codeact-runtime)

### Coding Agent

An engineering entry point in AI4J aimed at local repository delivery, comprising:

- CLI
- TUI
- ACP
- Sessions, commands, tools, Skills, and MCP integration

It is not a synonym for a general-purpose Agent framework, but rather a product layer biased toward "local coding interaction".

Related docs:

- [Coding Agent Overview](/docs/coding-agent/overview)

---

## F

### Function Call

`Function Call` refers to the call semantics by which a model can select and invoke a local capability according to a tool schema.

In the AI4J context, it is usually the entry point through which most users first understand the Tool track.

Related docs:

- [First Tool Call](/docs/start-here/first-tool-call)
- [Core SDK / Tools / Function Calling](/docs/core-sdk/tools/function-calling)

### FlowGram

A low-code workflow orchestration integration direction provided by AI4J, aimed at flowchart-style node execution, backend task execution, and node extension.

Related docs:

- [FlowGram Overview](/docs/flowgram/overview)

### Function Tool

A local Java function tool, exposed to the model via annotations or registration.

It differs from an MCP Tool in that:

- A Function Tool usually lives directly inside the local application
- An MCP Tool comes from an MCP Server

---

## G

### Gateway

In the MCP context it usually refers to `McpGateway`, used to manage multiple MCP Clients uniformly, aggregate tools, and apply routing governance.

Related docs:

- [Gateway Management](/docs/mcp/gateway-management)

---

## M

### MCP

`Model Context Protocol`, the standard protocol layer for a model to reach external capabilities.

In AI4J it covers:

- MCP Client
- MCP Gateway
- MCP Server

Related docs:

- [MCP Overview](/docs/mcp/overview)

### Memory

The context memory mechanism an Agent or Coding Agent uses within a persistent session.

It usually includes:

- History messages
- Tool call records
- Compaction summaries
- checkpoint

### Model Client

The model adaptation interface at the Agent layer, used to turn the `AgentPrompt` built by the runtime into a concrete model request.

Common implementations:

- `ChatModelClient`
- `ResponsesModelClient`

---

## P

### PlatformType

The platform enum in AI4J, used to declare which model platform you are calling.

For example:

- `OPENAI`
- `DOUBAO`
- `DASHSCOPE`
- `OLLAMA`

### Profile

In the `Coding Agent`, a `provider profile` is a reusable combination of model configuration, for example:

- provider
- protocol
- model
- baseUrl
- apiKey source

Related docs:

- [Configuration](/docs/coding-agent/configuration)

### Prompt Assembly

In the `Coding Agent` context, this refers to how the final context sent to the model is composed from:

- `systemPrompt`
- workspace instructions
- `instructions`
- session memory
- current input
- tool schemas

Related docs:

- [Coding Agent Architecture](/docs/coding-agent/architecture)

---

## R

### ReAct

The default general-purpose runtime of the AI4J Agent, suitable for:

- Text tasks
- Multi-turn reasoning
- Calling tools on demand

Related docs:

- [Minimal ReAct Agent](/docs/agent/minimal-react-agent)

### Responses

Refers to the event-based response model interface.

In AI4J it typically corresponds to:

- `IResponsesService`
- `ResponseRequest`
- `Response`
- `ResponseSseListener`

The difference from `Chat` is not only that the interface name differs — the event model is more powerful.

---

## S

### Session

A persistent session instance.

In the `Coding Agent`, a session usually contains:

- Current context
- History events
- Branch relationships
- In-memory compaction info
- Process state

### Skill

A `Skill` is first and foremost an instructional-asset capability in the AI4J foundation, further productized inside the `Coding Agent`.

It usually takes the form of a `SKILL.md`.

It is not a tool protocol, but rather a task instruction, template, or workflow guidance that the model can read and reuse on demand.

Related docs:

- [Core SDK / Skills Overview](/docs/core-sdk/skills/overview)

### StateGraph

The state-graph orchestration capability within an Agent Workflow, suitable for branching, loops, and conditional routing.

Related docs:

- [Workflow StateGraph](/docs/agent/workflow-stategraph)

### Stream

Means the model response arrives incrementally, rather than being returned all at once as a single payload.

:::note
Keep in mind:

- A streaming event is not the same as a token
- Different platforms have different chunk granularity
:::

---

## T

### Tool Registry

The registration layer that decides "which tools are exposed to the model".

It differs from `ToolExecutor`:

- `ToolRegistry` decides visibility
- `ToolExecutor` decides how a tool is executed

### Trace

Refers to the process-observation capability of an Agent or Coding Agent.

Usually used to record:

- Model calls
- Tool calls
- Per-step latency
- Errors and fallbacks

Related docs:

- [Trace and Observability](/docs/agent/trace-observability)
