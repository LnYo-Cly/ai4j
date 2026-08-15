---
sidebar_position: 5
title: Documentation Map
description: "The canonical reading map of the AI4J docs: what question each of the eight top-level sections answers, the single entry point for every capability, and how old links redirect automatically."
tags: [reference]
---

# Documentation Map

This page defines the canonical reading map of the AI4J documentation. It does not replace the feature pages — it answers two questions:

- What question does each of the eight top-level sections answer, and which one should I enter?
- What is the single entry point for a given capability, so you don't bounce between pages that cover similar ground?

## The eight top-level sections

The docs are organized by **reader intent**, not by code module. The top-level order is the recommended reading order:

| Section | What it answers | Entry |
| --- | --- | --- |
| Getting Started | What AI4J is, whether it fits, and how to run your first snippet | [Intro](/docs/intro) |
| Capabilities | What works without the Agent module: models, media, tools, skills, chat memory, RAG, MCP | [Capabilities Overview](/docs/capabilities/overview) |
| Agent | How to build autonomous agents: runtimes, memory & compaction, orchestration, governance, observability & interop | [Agent Overview](/docs/agent/overview) |
| Extending AI4J | How to extend AI4J itself: the jar plugin system and code-level extension points | [Extending Overview](/docs/extending/overview) |
| Products | Products built on the SDK: Coding Agent (CLI) and FlowGram | [Coding Agent](/docs/products/coding-agent/overview) · [FlowGram](/docs/products/flowgram/overview) |
| Integrations | Wiring AI4J into your stack: Spring Boot and recipes | [Spring Boot](/docs/integrations/spring-boot/overview) · [Recipes](/docs/integrations/solutions/overview) |
| Production | Pre- and post-launch checks: security, checklist, troubleshooting | [Production Checklist](/docs/production/production-checklist) |
| Reference | Specs and background: API, versions & migration, maps, about | [API](/docs/reference/api) |

The dividing line between Capabilities and Agent is: **does this capability exist without importing the `ai4j-agent` module?** If yes, it belongs to Capabilities (e.g. chat memory); if no, it belongs to Agent (e.g. agent memory & compaction).

## Single entry point per capability

| Capability you are looking for | Start here |
| --- | --- |
| Chat / Responses / Messages / Streaming / Multimodal | [Models](/docs/capabilities/models/overview) |
| Image / Audio / Video / Music / Realtime | [Media Generation](/docs/capabilities/media/image-generation) |
| Function tools / allowlist / execution model | [Tools](/docs/capabilities/tools/overview) |
| Skills (SKILL.md, discovery, activation) | [Skills](/docs/capabilities/skills/overview) |
| MCP (client / transport / gateway / server) | [MCP](/docs/capabilities/mcp/overview) |
| Chat memory (session history) | [Chat Memory](/docs/capabilities/chat-memory/overview) |
| RAG / vector stores / ingestion / evaluation | [RAG](/docs/capabilities/rag/overview) |
| Agent runtimes / subagents / teams | [Agent](/docs/agent/overview) |
| Agent memory compaction / context management | [Memory & Compaction](/docs/agent/memory/memory-and-state) |
| Approval / interceptors / sandbox | [Governance](/docs/agent/governance/approval-permission-policy) |
| Trace / replay & recovery / A2A | [Observability & Interop](/docs/agent/observability/trace-observability) |
| Plugin packages (jar / SPI) | [Plugin System](/docs/extending/plugins/plugin-packages) |
| Provider / service / HTTP stack extension | [Code-Level Extension Points](/docs/extending/code-level/provider-extension) |
| Coding Agent CLI / TUI / ACP | [Coding Agent](/docs/products/coding-agent/overview) |
| FlowGram nodes / task API | [FlowGram](/docs/products/flowgram/overview) |
| Spring Boot auto-configuration | [Spring Boot](/docs/integrations/spring-boot/overview) |

## About old links

The historical directory layout (top-level `start-here/`, `core-sdk/`, `mcp/`, etc.) has been folded into the eight sections above. **Every old URL redirects automatically to its new location** — no manual mapping needed, and old addresses indexed by search engines keep working.

New documentation must land inside one of the eight sections' existing topics; do not open a new top-level directory for a single page.

If you don't know which track to read yet, go back to [Choose Your Path](/docs/getting-started/choose-your-path).
