---
sidebar_position: 5
title: "Documentation Map"
description: "The official reading path for the AI4J docs site: lists the canonical main-line entry points, the role of legacy source directories, and the single path each capability should be entered from, so you don't bounce between old and new pages."
tags: [reference]
---

# Documentation Map

This page defines the official reading path for the AI4J docs site. It does not replace the feature pages; instead, it tells readers:

- Which directories are the current canonical main line.
- Which directories are historical sediment or migration sources.
- Which path to enter for a given capability, so you don't bounce between old and new pages.

## Canonical main line

| Topic | Official entry point | Suited for |
| --- | --- | --- |
| Project positioning, module selection, first run | [Start Here](/docs/intro) | Users new to AI4J |
| Core SDK, models, Tool, Skill, RAG, foundational capabilities | [Core SDK](/docs/capabilities/overview) | Regular Java or SDK integrators |
| MCP client, gateway, server, tool exposure | [MCP](/docs/capabilities/mcp/overview) | Teams wiring up external tools or publishing Java capabilities |
| Spring Boot auto-configuration and Bean extensions | [Spring Boot](/docs/integrations/spring-boot/overview) | Spring application developers |
| General-purpose Agent runtime, workflow, trace, team | [Agent](/docs/agent/overview) | Teams embedding an Agent in a business system |
| Local repo tasks, CLI, TUI, ACP | [Coding Agent](/docs/products/coding-agent/overview) | Teams building a coding agent or local dev host |
| FlowGram.ai canvas backend execution layer | [FlowGram](/docs/products/flowgram/overview) | Teams building a visual workflow platform |
| Scenario cookbooks | [Solutions](/docs/integrations/solutions/overview) | Readers with a concrete business scenario |
| Versioning, release, security, migration, production checks | [Operations](/docs/production/production-checklist) | Selection, rollout, and maintenance staff |

## Legacy source directories

These directories are kept for now because they contain a number of long-form articles and migration content that still hold value. They are no longer the first entry point for new users.

| Directory | Current role | Handling principle |
| --- | --- | --- |
| `getting-started/` | Old intro pages, historical quickstarts, and version pages | Migrate the essentials to `start-here/`, `reference/`, and `spring-boot/`, then keep redirects |
| `ai-basics/` | Low-level Core SDK details and the legacy capability tree | Migrate the strong content to `core-sdk/`, `mcp/`, and `solutions/`, then demote to legacy reference |
| `guides/` | Historical blog posts, solutions, and migration guides | Migrate to `solutions/` or `migration/`, keep the blog migration index |
| `core-sdk/chat/`, `core-sdk/responses/` | Old split pages for model access | Content is being progressively merged into `core-sdk/model-access/` |
| `core-sdk/mcp/` | Deep MCP reference from the Core SDK perspective | The top-level `mcp/` is the canonical main line; unique technical details are moved in or kept as advanced reference |
| `agent/orchestration/`, `agent/runtimes/`, `agent/observability/` | Alias directories during migration | Prefer the flat canonical pages under `agent/` |

## Single entry point per capability

| Capability you're looking for | Start here | Not recommended to start here |
| --- | --- | --- |
| Chat / Responses / Streaming / Multimodal | [Model Access](/docs/capabilities/models/overview) | `ai-basics/chat/*`, `core-sdk/chat/*` |
| Function Tool / Tool whitelist | [Tools](/docs/capabilities/tools/overview) | `ai-basics/chat/tool-calling` |
| Skill | [Skills](/docs/capabilities/skills/overview) | `ai-basics/skills` |
| MCP | [MCP Overview](/docs/capabilities/mcp/overview) | `core-sdk/mcp/overview` |
| Memory | [Memory](/docs/capabilities/chat-memory/overview) | `ai-basics/chat/chat-memory*` |
| RAG / Vector / Ingestion | [Search & RAG](/docs/capabilities/rag/overview) | `ai-basics/rag/*` |
| Provider extension | [Extension](/docs/extending/overview) | `ai-basics/provider-and-model-extension` |
| Spring Boot | [Spring Boot Overview](/docs/integrations/spring-boot/overview) | `getting-started/quickstart-springboot` |
| Coding Agent MCP / ACP | [MCP and ACP](/docs/products/coding-agent/mcp-and-acp) | `agent/coding-agent-*` |
| FlowGram nodes and task API | [FlowGram Overview](/docs/products/flowgram/overview) | `flowgram/builtin-nodes` |

## Reading rules during migration

1. New users enter only from the canonical main line in the sidebar.
2. When you land on an old page from search, first check the top of the page for the official entry point.
3. Long code listings and implementation details on old pages can be used as reference, but don't treat old pages as the source of module boundaries.
4. New documentation must land in the canonical main line; do not keep adding new main-line content to `getting-started/`, `ai-basics/`, or `guides/`.

If you're still not sure which track to read, go back to [Choose Your Path](/docs/getting-started/choose-your-path).
