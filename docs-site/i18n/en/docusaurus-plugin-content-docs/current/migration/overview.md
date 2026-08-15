---
sidebar_position: 1
title: "Migration Guide"
description: "The AI4J docs site is converging from the old getting-started/ai-basics/guides structure to a canonical structure organized by module and integration path. Describes the migration rules: strong content is not deleted outright, stable conclusions are moved first, legacy notices are added, and the sidebar is the official reading path."
tags: [reference]
---

# Migration Guide

The AI4J docs site is converging from the old `getting-started/`, `ai-basics/`, and `guides/` structure to a canonical structure organized by module and integration path. This page explains the migration rules, so users do not treat historical pages as the new source of truth.

## Migration principles

1. Do not delete strong content outright.
2. Move stable conclusions from the old pages into the canonical pages first.
3. Then add a legacy notice or redirect note to the old pages.
4. Write new documentation only into the canonical main line.
5. After migration, the pages in the sidebar are the official reading path.

## Old path to new path

| Old path | New path | Status |
| --- | --- | --- |
| `getting-started/installation` | [Quickstart for Java](/docs/start-here/quickstart-java) | Migrating |
| `getting-started/quickstart-openai-jdk8` | [Quickstart for Java](/docs/start-here/quickstart-java) | Migrating |
| `getting-started/quickstart-springboot` | [Quickstart for Spring Boot](/docs/start-here/quickstart-spring-boot) | Migrating |
| `getting-started/version-compatibility` | [Version Compatibility](/docs/reference/version-compatibility) | New entry established |
| `getting-started/modules-and-maven-central` | [Release and Artifacts](/docs/reference/release-and-artifacts) | New entry established |
| `ai-basics/chat/*` | [Model Access](/docs/core-sdk/model-access/overview) | Migrating |
| `ai-basics/responses/*` | [Model Access](/docs/core-sdk/model-access/overview) | Migrating |
| `ai-basics/rag/*` | [Search & RAG](/docs/core-sdk/search-and-rag/overview) | Migrating |
| `ai-basics/services/*` | [Platform and Service Matrix](/docs/core-sdk/platform-service-matrix) and related capability pages | Migrating |
| `ai-basics/provider-and-model-extension` | [Extension](/docs/core-sdk/extension/overview) | Migrating |
| `core-sdk/mcp/*` | [MCP Overview](/docs/mcp/overview) | Top-level MCP is now the official main line |
| `guides/*` | [Solutions](/docs/solutions/overview) | Migrating |
| `agent/coding-agent-*` | [Coding Agent](/docs/coding-agent/overview) | Split into a new main line |
| `flowgram/builtin-nodes` | [Built-in Nodes](/docs/flowgram/built-in-nodes) | Naming being consolidated |

## API and usage migration

### Migrating from the old FreeAiService mental model

Some examples in the old documentation present `FreeAiService` as the most direct entry point. The new documentation should make the following clear first:

- The official unified entry point of the Core SDK is `AiService`.
- For multiple instances or multiple providers, understand `AiServiceRegistry`.
- `FreeAiService` is better suited as a compatibility shell or a migration lead for old examples, and should not be the first main line for new users.

Official entry points:

- [Service Entry and Registry](/docs/core-sdk/service-entry-and-registry)
- [Spring Boot Overview](/docs/spring-boot/overview)

### Migrating from the scattered Chat / Responses pages

The old pages are split fairly finely by interface shape, which is useful for looking up implementation details. New users should start from:

- [Model Access Overview](/docs/core-sdk/model-access/overview)
- [Chat vs Responses](/docs/core-sdk/model-access/chat-vs-responses)

Then proceed to the specific chat, responses, streaming, and multimodal pages.

### Migrating from the old MCP paths

The top-level `docs/mcp/` is the current official MCP main line. The details unique to `core-sdk/mcp/*` will gradually be migrated into:

- [Client Integration](/docs/mcp/client-integration)
- [Gateway Management](/docs/mcp/gateway-management)
- [Build Your MCP Server](/docs/mcp/build-your-mcp-server)
- [Tool Exposure Semantics](/docs/mcp/tool-exposure-semantics)

### Migrating from the old guides

`guides/` reads more like a historical accumulation of blog posts and tutorials. Reusable solutions should go into:

- [Solutions Overview](/docs/solutions/overview)

Production checklists, troubleshooting, security, and version and release notes should go into:

- [Production Checklist](/docs/operations/production-checklist)
- [Troubleshooting](/docs/troubleshooting/overview)
- [Security Overview](/docs/security/overview)
- [Version Compatibility](/docs/reference/version-compatibility)
- [Release and Artifacts](/docs/reference/release-and-artifacts)

## New documentation write rules

| New content type | Where to write it |
| --- | --- |
| First integration, path selection | `start-here/` |
| Core SDK capabilities | `core-sdk/` |
| MCP | `mcp/` |
| Spring Boot | `spring-boot/` |
| Agent runtime | `agent/` |
| Coding Agent | `coding-agent/` |
| FlowGram | `flowgram/` |
| Scenario cookbook | `solutions/` |
| Version, release, compatibility | `reference/` |
| Security and go-live | `security/`, `operations/` |
| Migration and troubleshooting | `migration/`, `troubleshooting/` |

Do not add new main-line pages to `getting-started/`, `ai-basics/`, or `guides/` anymore.
