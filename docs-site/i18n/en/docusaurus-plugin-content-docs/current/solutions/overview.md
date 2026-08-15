---
title: "Solutions Overview"
description: "Entry point for AI4J solution compositions: solution paths, module combinations, and guidance on when to return to the main line, organized by common business problems."
tags: [concept]
---

# Solutions Overview

`Solutions` is the scenario composition entry point for AI4J. It does not redefine SDK capabilities; rather, it composes capabilities such as Core SDK, Spring Boot, RAG, MCP, Agent, and FlowGram into reproducible paths organized around common problems.

If you have not yet grasped the foundational concepts, start with [Start Here](/docs/intro) and [Feature Map](/docs/start-here/feature-map). Once you know which business problem you need to solve, pick a solution from this section.

## In one sentence

Solutions addresses:

> When a real scenario requires composing multiple AI4J modules, it tells you which solution path to start from, which modules you need, and where the boundaries lie.

It is not the source of truth. Every solution page routes you back to its corresponding main line, such as Spring Boot, Search & RAG, Agent, or FlowGram.

## Quick path picker

| Goal | Solution | Composed capabilities |
| --- | --- | --- |
| Multi-turn chat with persistent sessions | [Spring Boot + MySQL Chat Memory](/docs/solutions/springboot-mysql-chat-memory) | Spring Boot, Chat Memory, MySQL |
| Persistent Agent sessions | [Spring Boot + JDBC Agent Memory](/docs/solutions/springboot-jdbc-agent-memory) | Spring Boot, Agent, JDBC Memory |
| Build a RAG ingestion and retrieval pipeline | [RAG Ingestion Vector Store](/docs/solutions/rag-ingestion-vector-store) | Ingestion, Embedding, Vector Store, Retrieval |
| Use Pinecone | [Pinecone Vector Workflow](/docs/solutions/pinecone-vector-workflow) | Vector Store, Pinecone, RAG |
| Integrate web search | [SearXNG Web Search](/docs/solutions/searxng-web-search) | Online Search, SearXNG |
| Combine streaming output, search, and RAG | [DeepSeek Stream Search RAG](/docs/solutions/deepseek-stream-search-rag) | Streaming, Search, RAG |
| Build high-evidence Q&A | [Legal Assistant](/docs/solutions/legal-assistant) | RAG, Citation, Trace |
| Persist FlowGram tasks | [FlowGram MySQL Task Store](/docs/solutions/flowgram-mysql-taskstore) | FlowGram, Task Store, MySQL |
| Tune HTTP concurrency and connection pool | [SPI Dispatcher ConnectionPool](/docs/solutions/spi-dispatcher-connectionpool) | HTTP Stack, SPI, OkHttp |

## How to read a solution page

Read in this order:

1. First check whether the solution solves your problem.
2. Then see which modules need to be composed.
3. Confirm the scenarios and limits where it does not apply.
4. Jump back to the corresponding main line to fill in the full concepts.
5. Before going live, review the production, security, and troubleshooting pages.

Do not reverse-engineer the entire project architecture from a solution page. Solution pages are composition paths, not module boundary definitions.

## What a solution page should make clear

Every solution should reliably answer:

- What problem it solves.
- What problem it does not solve.
- Which modules and configuration it requires.
- What the minimum runnable path is.
- Which points need a pre-production check.
- Which main line to return to for going deeper.

This structure matters more than piling up code. Code can be plentiful, but it must serve the path and the boundaries.

## Back to the main line

| If you find yourself missing | Go here |
| --- | --- |
| Model call, Tool, MCP, foundational RAG capabilities | [Core SDK](/docs/core-sdk/overview) |
| Spring configuration, Beans, and auto-configuration | [Spring Boot](/docs/spring-boot/overview) |
| Multi-step reasoning, workflow, trace | [Agent](/docs/agent/overview) |
| Local codebase tasks, CLI, ACP | [Coding Agent](/docs/coding-agent/overview) |
| Visual workflow backend | [FlowGram](/docs/flowgram/overview) |
| Versioning, security, production checks, and troubleshooting | [Production Checklist](/docs/operations/production-checklist) |

If a solution page does not make these paths clear, it has not yet met the quality this section expects.
