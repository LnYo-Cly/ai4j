---
sidebar_position: 4
title: "Feature Map"
description: "AI4J feature map: lists each capability's status, owning module, suitable scenarios, and further-reading entry point by maturity marker (stable/advanced/preview/experimental), helping you adopt the minimal module on demand."
tags: [reference]
---

# Feature Map

This page is the AI4J feature map. It does not replace each topic page; it only tells you:

- What capabilities exist today.
- What problems each capability is suitable for solving.
- Which page you should start reading from.
- Which capabilities are the stable mainline and which are better suited for advanced exploration.

## Maturity markers

| Marker | Meaning |
| --- | --- |
| `stable` | Recommended as the everyday integration mainline; docs and API semantics are relatively stable |
| `advanced` | The capability already forms a coherent system, but is better used once you have a clear engineering goal |
| `preview` | Has an implementation and doc entry point, but the interface, behavior, or best practices may still change |
| `experimental` | More exploratory or tied to a specific integration; confirm the source code, examples, and limits before use |

## Getting-started paths

| Capability | Status | Module | When to use it | Start here |
| --- | --- | --- | --- | --- |
| First Java call | `stable` | `ai4j` | First integration; you want the shortest path to get one model request through | [Quickstart for Java](/docs/getting-started/quickstart-java) |
| Plain Java quickstart | `stable` | `ai4j` | You want to verify dependencies, configuration, and one model call first | [Quickstart for Java](/docs/getting-started/quickstart-java) |
| Spring Boot quickstart | `stable` | `ai4j-spring-boot-starter` | You already have a Spring Boot project and want to wire in via configuration and beans | [Quickstart for Spring Boot](/docs/getting-started/quickstart-spring-boot) |
| Chat call semantics | `stable` | `ai4j` | The first call works; you want to understand the message model and call details | [Model Access / Chat](/docs/capabilities/models/chat) |
| First tool call | `stable` | `ai4j` | You want the model to call local functions or tools | [First Tool Call](/docs/getting-started/first-tool-call) |
| Path selection | `stable` | docs | You're not sure whether to go SDK, Spring, Agent, or FlowGram | [Choose Your Path](/docs/getting-started/choose-your-path) |
| Documentation map | `stable` | docs | You want to confirm the canonical mainline and where the old paths lead | [Documentation Map](/docs/reference/maps/documentation-map) |

## Adopt by module

AI4J's module relationships are stacked upward from a foundation, not a platform you must adopt wholesale. You can pick the minimal module for your current goal.

| Current goal | Minimal module to adopt | Dependencies | Suitable scenarios |
| --- | --- | --- | --- |
| Only do model calls, tools, RAG, MCP | `ai4j` | No internal AI4J dependencies | Get AI capabilities running in a plain Java project first |
| Wire a Spring Boot app into AI | `ai4j-spring-boot-starter` | Depends on `ai4j` | You need configuration properties, auto-configuration, and bean extensions |
| Embed an Agent runtime | `ai4j-agent` | Depends on `ai4j` | You need memory, state, workflow, tracing, or team orchestration |
| Build a local Coding Agent runtime | `ai4j-coding` | Depends on `ai4j`, `ai4j-agent` | You need workspace tools, sessions, the outer loop, and compaction |
| Provide a CLI / TUI / ACP entry point | `ai4j-cli` | Depends on `ai4j`, `ai4j-coding` | You need a terminal product shell and a local session entry point |
| Connect a FlowGram backend | `ai4j-flowgram-spring-boot-starter` | Depends on `ai4j-agent`, `ai4j-spring-boot-starter` | You need visual workflows, task API, and a trace bridge |
| Run the FlowGram demo | `ai4j-flowgram-demo` | Depends on the FlowGram starter | You need a sample backend to validate the integration |
| Unify versions | `ai4j-bom` | Manages versions across multiple artifacts | Reduce version drift when pulling in multiple AI4J modules |

The rule is simple: pull in the minimal module that solves your current problem first; only stack on the next layer when the need naturally rises.

## Core SDK

| Capability | Status | Module | What it solves | Further reading |
| --- | --- | --- | --- | --- |
| Model Access | `stable` | `ai4j` | Unified model integration mainline | [Overview](/docs/capabilities/models/overview) |
| Chat | `stable` | `ai4j` | Conversational model calls | [Chat](/docs/capabilities/models/chat) |
| Responses | `stable` | `ai4j` | Unified call for the Responses style | [Responses](/docs/capabilities/models/responses) |
| Streaming | `stable` | `ai4j` | Streamed output, incremental results, and frontend display | [Streaming](/docs/capabilities/models/streaming) |
| Multimodal | `advanced` | `ai4j` | Multimodal input/output such as text and images | [Multimodal](/docs/capabilities/models/multimodal) |
| Tools / Function Call | `stable` | `ai4j` | Local function declaration, execution, and safety boundaries | [Tools](/docs/capabilities/tools/overview) |
| Skills | `advanced` | `ai4j` | Let the model read instructions, templates, and workflow assets on demand | [Skills](/docs/capabilities/skills/overview) |

## RAG, retrieval, and MCP

| Capability | Status | Module | What it solves | Further reading |
| --- | --- | --- | --- | --- |
| Search & RAG | `advanced` | `ai4j` | Retrieve from external knowledge, augment answers, and preserve citation leads | [Overview](/docs/capabilities/rag/overview) |
| Ingestion Pipeline | `advanced` | `ai4j` | Document ingestion, splitting, and pre-indexing | [Ingestion Pipeline](/docs/capabilities/rag/ingestion-pipeline) |
| Hybrid Retrieval | `advanced` | `ai4j` | Combine keyword, vector, and other recall strategies | [Hybrid Retrieval](/docs/capabilities/rag/hybrid-retrieval) |
| Rerank | `advanced` | `ai4j` | Rerank candidate results to improve retrieval quality | [Rerank](/docs/capabilities/rag/rerank) |
| MCP | `advanced` | `ai4j` | Integrate external tools, services, and capability gateways via the protocol | [MCP Overview](/docs/capabilities/mcp/overview) |
| MCP Client Integration | `advanced` | `ai4j` | Connect to and consume MCP capabilities on the client side | [Client Integration](/docs/capabilities/mcp/client-integration) |

## App integration and upper runtimes

| Capability | Status | Module | Suitable scenarios | Start here |
| --- | --- | --- | --- | --- |
| Spring Boot Starter | `stable` | `ai4j-spring-boot-starter` | Configuration-driven Spring integration, auto-configuration, and bean extensions | [Spring Boot Overview](/docs/integrations/spring-boot/overview) |
| Agent Runtime | `preview` | `ai4j-agent` | You need memory, state, tool registry, workflow, or team orchestration | [Agent Overview](/docs/agent/overview) |
| Agent Quickstart | `preview` | `ai4j-agent` | You want to run a minimal Agent first | [Agent Quickstart](/docs/agent/quickstart) |
| Agent Teams | `preview` | `ai4j-agent` | Multi-agent collaboration and division-of-labor orchestration | [Agent Teams](/docs/agent/orchestration/agent-teams) |
| Coding Agent | `preview` | `ai4j-coding`, `ai4j-cli` | Repository-scoped task execution, workspace tools, and CLI/TUI | [Coding Agent Overview](/docs/products/coding-agent/overview) |
| Coding Agent Quickstart | `preview` | `ai4j-coding`, `ai4j-cli` | You want to try the local Coding Agent product entry point | [Coding Agent Quickstart](/docs/products/coding-agent/quickstart) |
| FlowGram | `preview` | `ai4j-flowgram-spring-boot-starter` | Visual-workflow platform backend, node execution, and trace bridge | [FlowGram Overview](/docs/products/flowgram/overview) |
| FlowGram Quickstart | `preview` | `ai4j-flowgram-demo` | You want to run the FlowGram demo or starter integration | [FlowGram Quickstart](/docs/products/flowgram/quickstart) |
| Solutions | `advanced` | multiple | Reuse combined solutions by business scenario | [Solutions Overview](/docs/integrations/solutions/overview) |

## Production readiness and maintenance

| Capability | Status | Module | What it solves | Further reading |
| --- | --- | --- | --- | --- |
| Version Compatibility | `stable` | docs | Capability boundaries for Java, Maven, modules, and providers | [Version Compatibility](/docs/reference/version-compatibility) |
| Release and Artifacts | `stable` | docs | Maven artifacts, BOM, and module pull order | [Release and Artifacts](/docs/reference/release-and-artifacts) |
| Security | `stable` | docs | Security boundaries for secrets, Tools, MCP, RAG, Agent, and FlowGram | [Security Overview](/docs/production/security) |
| Production Checklist | `stable` | docs | Pre-launch checks for configuration, permissions, observability, and regression | [Production Checklist](/docs/production/production-checklist) |
| Migration | `stable` | docs | Mental-model migration for old paths, old examples, and old APIs | [Migration Guide](/docs/reference/migration) |
| Troubleshooting | `stable` | docs | Troubleshooting entry points for providers, Tools, MCP, RAG, Agent, and FlowGram | [Troubleshooting](/docs/production/troubleshooting) |
| Comparison | `stable` | docs | Selection boundaries vs. Spring AI, LangChain4j, AgentScope Java, and Pi Agent | [Comparison](/docs/reference/about/comparison) |

## Integrations without a dedicated page yet

Some ecosystem integrations or platform connections may not yet have a stable topic page. If the docs mention Dify, Coze, n8n,
AgentFlow, or other external platforms, read by capability category first:

| What you want to connect | Which mainline to read first |
| --- | --- |
| External tool or service gateway | [MCP](/docs/capabilities/mcp/overview) |
| Local Java function or business service | [Tools](/docs/capabilities/tools/overview) |
| Structured prompts, process instructions, and reusable task assets | [Skills](/docs/capabilities/skills/overview) |
| Knowledge base, retrieval augmentation, or document Q&A | [Search & RAG](/docs/capabilities/rag/overview) |
| Visual-workflow backend | [FlowGram](/docs/products/flowgram/overview) |

:::note Integration maturity
These integrations should not be packaged as fully stable capabilities on the entry page. Once the corresponding topic pages are filled in, add deep links from here.
:::

## Recommended reading order

For your first integration:

1. [Why AI4J](/docs/getting-started/why-ai4j)
2. [Quickstart for Java](/docs/getting-started/quickstart-java)
3. [Quickstart for Java](/docs/getting-started/quickstart-java) or [Quickstart for Spring Boot](/docs/getting-started/quickstart-spring-boot)
4. [Core SDK / Model Access / Chat](/docs/capabilities/models/chat)
5. [First Tool Call](/docs/getting-started/first-tool-call)
6. Enter [Core SDK](/docs/capabilities/overview), [Spring Boot](/docs/integrations/spring-boot/overview), [Agent](/docs/agent/overview), or [FlowGram](/docs/products/flowgram/overview) as needed
7. Before launch, check the [Production Checklist](/docs/production/production-checklist) and [Security](/docs/production/security)
