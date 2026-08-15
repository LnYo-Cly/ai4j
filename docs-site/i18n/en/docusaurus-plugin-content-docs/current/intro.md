---
sidebar_position: 1
title: "AI4J Docs"
description: "AI4J is a Java 8+ AI SDK entry point — an overview, recommended starting points, and a repository module map for its on-demand building blocks: model calls, tools, RAG, MCP, Spring Boot, Agent, Coding Agent, and FlowGram."
tags: [concept]
---

# AI4J Docs

AI4J is an AI SDK for `Java 8+`. It is not an all-or-nothing AI platform, but a
set of Java AI building blocks you can take on demand: start with `ai4j` to get
model calls working, then bring in Spring Boot, RAG, MCP, Agent, Coding Agent, or
FlowGram as your project needs them.

If you just want to fire off a first model request, start from plain Java or
Spring Boot. If you already know you need tool calling, RAG, MCP, an Agent, or the
Coding Agent, jump straight in from the feature map.

## Start here

| Goal | Recommended entry | What you get |
| --- | --- | --- |
| Send your first AI request in plain Java | [Quickstart for Java](/docs/start-here/quickstart-java) | Minimal deps, key config, validation, and your first model call |
| Get a plain Java project working | [Quickstart for Java](/docs/start-here/quickstart-java) | Minimal deps, config, and your first snippet |
| Integrate a Spring Boot app | [Quickstart for Spring Boot](/docs/start-here/quickstart-spring-boot) | The starter, config properties, and bean injection |
| See the full capability surface | [Feature Map](/docs/start-here/feature-map) | Current AI4J capabilities, maturity, and reading paths |
| Find the canonical doc routes | [Documentation Map](/docs/start-here/documentation-map) | Canonical main lines, legacy sources, and migration directions |
| Pre-launch checks | [Production Checklist](/docs/operations/production-checklist) | Checks for versions, keys, tools, MCP, RAG, Agent, FlowGram |
| Understand why AI4J exists | [Why AI4J](/docs/start-here/why-ai4j) | Positioning, fit, and differences from adjacent options |

## Take what you need

You don't need to understand all of AI4J up front, nor pull every module into
your project. The saner path is to take one module for the problem in front of
you, then upgrade to the next layer when things get more complex.

| What you want to do now | Take | When to upgrade |
| --- | --- | --- |
| Model calls, tools, RAG, MCP | `ai4j` | Add the Spring Boot starter when you need containerized config |
| Add AI to a Spring Boot app | `ai4j-spring-boot-starter` | Add an Agent when you need richer state or orchestration |
| Build an embeddable Agent runtime | `ai4j-agent` | Add the Coding Agent when you need codebase tasks |
| A local entry point for codebase tasks | `ai4j-coding` + `ai4j-cli` | Extend the command layer when you need a productized CLI / TUI |
| Hook up FlowGram visual workflows | `ai4j-flowgram-spring-boot-starter` | Look at the FlowGram demo when you need a full demo |
| Align versions across modules | `ai4j-bom` | Use when a project pulls multiple AI4J artifacts |

## What AI4J covers

AI4J's capabilities sit in layers. We recommend getting the Core SDK working
first, then upgrading upward as your project needs.

| Layer | Capabilities | Entry |
| --- | --- | --- |
| Core SDK | `Chat`, `Responses`, streaming, multimodal, image, audio, embedding, rerank | [Core SDK / Model Access](/docs/core-sdk/model-access/overview) |
| Capability wiring | Function call, local tools, Skills, MCP | [Tools](/docs/core-sdk/tools/overview), [Skills](/docs/core-sdk/skills/overview), [MCP](/docs/mcp/overview) |
| Data augmentation | Memory, search, RAG, VectorStore, ingestion, hybrid retrieval | [Search & RAG](/docs/core-sdk/search-and-rag/overview) |
| App integration | Spring Boot starter, config governance, auto-configuration | [Spring Boot](/docs/spring-boot/overview) |
| Upper runtimes | Agent, Coding Agent, FlowGram workflows | [Agent](/docs/agent/overview), [Coding Agent](/docs/coding-agent/overview), [FlowGram](/docs/flowgram/overview) |
| Scenario solutions | Common combinations and repeatable integration paths | [Solutions](/docs/solutions/overview) |
| Production readiness | Version compatibility, release artifacts, security, migration, troubleshooting | [Version Compatibility](/docs/reference/version-compatibility), [Security](/docs/security/overview), [Troubleshooting](/docs/troubleshooting/overview) |

## Three concepts, kept distinct

AI4J docs deliberately separates three kinds of capabilities:

- `Function Call / Tool`: lets the model invoke local functions or controlled tools.
- `Skill`: on-demand assets the model reads — instructions, templates, flows, know-how.
- `MCP`: brings in external tools, services, or capability gateways over a protocol.

These compose, but their responsibilities differ. Keeping the boundaries clear is
what keeps the downstream cost of using them low.

## Repository module map

| Module | Role |
| --- | --- |
| `ai4j/` | Core SDK: provider access, Chat, Responses, RAG, MCP, vector, image, audio, realtime |
| `ai4j-spring-boot-starter/` | Spring Boot auto-configuration and app-side wiring |
| `ai4j-agent/` | Agent runtime, workflow, trace, memory, team orchestration |
| `ai4j-coding/` | Coding Agent runtime, workspace tools, outer loop, compaction |
| `ai4j-cli/` | CLI, TUI, ACP host, and local session entry |
| `ai4j-flowgram-spring-boot-starter/` | FlowGram integration, task APIs, trace bridge |
| `ai4j-flowgram-demo/` and `ai4j-flowgram-webapp-demo/` | FlowGram backend and frontend demos |
| `ai4j-bom/` | Maven version alignment |

These modules exist so you can pull in only what you need right now, while leaving
room to upgrade upward.

Suggested next step: if you haven't run any code yet, start with the
[Quickstart for Java](/docs/start-here/quickstart-java); if you'd rather first
judge whether AI4J fits your project, read [Why AI4J](/docs/start-here/why-ai4j).
