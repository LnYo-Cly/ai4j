---
title: "Core SDK Overview"
description: "Overview of the ai4j foundation capability lines: model access, Tool, Skill, MCP, Memory, RAG, and extensions — helps you choose your first main line and understand how it relates to the upper-layer modules."
tags: [concept]
---

# Core SDK Overview

`Core SDK` corresponds to the `ai4j/` module in the repository and is the foundational capability layer of AI4J. You can use only this layer to handle model calls, Tool, Skill, MCP, Memory, Search/RAG, and provider extensions, or wait until it is stable before wiring in Spring Boot, Agent, Coding Agent, or FlowGram.

This page first answers three questions:

- What Core SDK actually solves.
- Which capability line you should start from.
- Which capabilities are main lines, and which are advanced or provider-related.

## In One Sentence

Core SDK solves:

> Within a Java 8+ project, wire in model, tool, protocol capabilities, context, retrieval augmentation, and extension points using one continuous engineering model.

It is not a standalone `Chat` wrapper, nor a thin package layered over a few provider APIs. It is better understood as AI4J's capability foundation: upper-layer starters, Agent, Coding Agent, and FlowGram should all reuse this layer's capabilities rather than redefining model, tool, or RAG.

## Where You Should Start

| Goal | Entry point | You will learn first |
| --- | --- | --- |
| Just want to send the first message | [Model Access](/docs/capabilities/models/overview) | How to choose between Chat, Responses, streaming, and multimodal |
| Want the model to call local capabilities | [Tools](/docs/capabilities/tools/overview) | Function Tool, schema, execution model, and security boundaries |
| Want to give the model reusable instructions and flows | [Skills](/docs/capabilities/skills/overview) | Skill files, discovery, loading, and the boundary versus Tool/MCP |
| Want to connect external tools or publish Java capabilities | [MCP](/docs/capabilities/mcp/overview) | client, transport, gateway, server publish |
| Want to build conversational context | [Memory](/docs/capabilities/chat-memory/overview) | chat memory, session, and the boundary versus tools |
| Want to build a knowledge base or retrieval augmentation | [Search & RAG](/docs/capabilities/rag/overview) | ingestion, chunk, embedding, vector, rerank, citation |
| Want to extend providers or services | [Extension](/docs/extending/overview) | provider, model, service, HTTP stack extension patterns |

If this is your first time, start with [Quickstart for Java](/docs/getting-started/quickstart-java), then return to this page to choose a capability line.

## What Capabilities the Core SDK Contains

| Capability | Current positioning | Suitable scenarios |
| --- | --- | --- |
| Model Access | Main line | Call model capabilities such as Chat, Responses, streaming, and multimodal |
| Tools | Main line | Expose local Java functions or controlled capabilities to the model |
| Skills | Advanced main line | Let the model read instructions, templates, task flows, and experience assets on demand |
| MCP | Advanced main line | Connect external tools, services, resources, or prompts via protocol |
| Memory | Main line | Preserve session state, message history, and context boundaries |
| Search & RAG | Advanced main line | Document ingestion, retrieval augmentation, vector store, rerank, citation tracking |
| Extension | Advanced reference | New provider, new model, new service implementation, or network stack extension |
| Image / Audio / Realtime | Provider-related capability | Depends on the specific provider's capability coverage |

A unified entry point does not mean all provider capabilities are identical. Different platforms vary in their support for Chat, Responses, Embedding, Rerank, Image, Audio, and Realtime; before using them, consult the [Platform and Service Matrix](/docs/capabilities/models/platform-service-matrix).

## Three Conceptual Boundaries

### Tool

A Tool is a structured capability that can be called by the model. It usually has a name, description, parameter schema, and executor. The first main line is the local Function Tool.

### Skill

A Skill is an instruction asset the model can read, typically containing `SKILL.md`, templates, flows, and experience. It helps the model "know how to do it", but it is not itself an executable tool.

### MCP

MCP is the protocol-based capability connection layer. It can connect to third-party MCP servers, publish Java capabilities as MCP servers, and manage the tool surfaces of multiple services through a gateway.

These three can be combined, but they should not be conflated into one concept. Tool handles invocation, Skill handles instruction, and MCP handles protocol connection.

## Relationship to Upper-Layer Modules

| Upper-layer module | How it reuses the Core SDK |
| --- | --- |
| Spring Boot starter | Loads Core SDK configuration, services, and Beans into the Spring container |
| Agent | Adds runtime, workflow, trace, and team on top of model, tools, and memory |
| Coding Agent | Adds workspace, session, approval, and CLI/TUI/ACP on top of Agent and the Core SDK |
| FlowGram | Embeds Core/Agent capabilities into explicit workflow nodes and the task API |

Therefore, the Core SDK is not a "read-once-and-skip" foundational chapter; it is the shared prerequisite for all subsequent topics.

## What to Confirm Before Production Wiring

- Whether the source of provider, model, baseUrl, and key is clear.
- Whether the service surface you use is supported by the target provider.
- Whether Tool and MCP are minimally exposed by default.
- Whether RAG inherits business permissions and data-source metadata.
- Whether streaming, timeout, failure retry, and log masking have clear boundaries.
- Whether multi-module usage aligns versions through a BOM.

Recommended reading before going live:

- [Version Compatibility](/docs/reference/version-compatibility)
- [Security Overview](/docs/production/security)
- [Production Checklist](/docs/production/production-checklist)
- [Troubleshooting](/docs/production/troubleshooting)

## Recommended Reading Order

1. [Service Entry and Registry](/docs/capabilities/service-entry)
2. [Platform and Service Matrix](/docs/capabilities/models/platform-service-matrix)
3. [Model Access](/docs/capabilities/models/overview)
4. [Tools](/docs/capabilities/tools/overview)
5. [Skills](/docs/capabilities/skills/overview)
6. [MCP](/docs/capabilities/mcp/overview)
7. [Memory](/docs/capabilities/chat-memory/overview)
8. [Search & RAG](/docs/capabilities/rag/overview)
9. [Extension](/docs/extending/overview)

The legacy `ai-basics/`, `core-sdk/chat/`, `core-sdk/responses/`, and `core-sdk/mcp/` directories still hold historical details, but the current official reading path is governed by the sidebar and the [Documentation Map](/docs/reference/maps/documentation-map).
