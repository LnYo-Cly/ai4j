---
title: "Spring Boot Overview"
description: "Overview of ai4j-spring-boot-starter: when to use it, the minimum integration path, auto-configured capabilities and extension points, plus a pre-launch checklist."
tags: [integration]
---

# Spring Boot Overview

`ai4j-spring-boot-starter` wires the Core SDK into Spring Boot's configuration, Bean lifecycle, and business layering. It is not a new AI implementation, nor is it a mandatory entry point — if you are not on a Spring project, start directly with the [Core SDK](/docs/core-sdk/overview).

## In one sentence

The Spring Boot starter solves:

> Inside a Spring Boot application, manage AI4J's model services, service registry, HTTP client, RAG components, and extension points through configuration and Beans.

It handles "wiring into the Spring container"; it does not redefine the underlying semantics of Chat, Responses, Tool, MCP, or RAG.

## When to use the starter

| Scenario | Fits? |
| --- | --- |
| Validate a model call from a plain Java main method first | No starter needed |
| Existing Spring Boot project, config-driven model integration | Fits |
| Need Bean injection of `AiService` or `AiServiceRegistry` | Fits |
| Need multi-provider / multi-instance configuration | Fits |
| Need to override the HTTP client, service, or RAG Beans on the business side | Fits |
| Need Agent, Coding Agent, or FlowGram | Understand the Core SDK first, then wire the corresponding upper-layer module |

## Minimum path

For a first integration, follow this order:

1. [Quickstart for Spring Boot](/docs/start-here/quickstart-spring-boot)
2. [Spring Boot Quickstart](/docs/spring-boot/quickstart)
3. [Auto Configuration](/docs/spring-boot/auto-configuration)
4. [Configuration Reference](/docs/spring-boot/configuration-reference)
5. [Bean Extension](/docs/spring-boot/bean-extension)

Get a single provider working first, then consider the `ai.platforms[]` multi-instance registry.

## What the starter auto-configures for you

| Capability | Description |
| --- | --- |
| Configuration binding | Reads `ai.*` configuration and maps it to configuration property objects |
| Unified service entry point | Creates or exposes `AiService` |
| Multi-instance registry | Manages multiple provider instances via `AiServiceRegistry` |
| HTTP client | Unifies OkHttp configuration, timeouts, and connection capabilities |
| Compatibility entry point | Keeps the legacy entry point or compatibility shim to help migrate old examples |
| RAG-related Beans | When conditions are met, auto-configures vector, assembler, reranker, and other capabilities |

The actual model protocol, Tool schema, MCP transport, and RAG ingestion still belong to the Core SDK.

## Single instance and multi-instance

### Single instance

Suitable for getting one provider working first:

```yaml
ai:
  openai:
    api-key: ${OPENAI_API_KEY}
    base-url: https://api.openai.com
```

### Multi-instance

Suitable for managing multiple providers, multiple model configurations, or multiple tenant-level entry points at the same time:

```yaml
ai:
  platforms:
    - id: primary-openai
      type: OPENAI
      api-key: ${OPENAI_API_KEY}
      base-url: https://api.openai.com
```

Multi-instance is not an alias for the single-instance configuration. It enters the `AiServiceRegistry` mental model, and downstream business code should take services on demand by id.

## Extension points

| What you want to extend | Start here |
| --- | --- |
| Override default Beans | [Bean Extension](/docs/spring-boot/bean-extension) |
| Inspect configuration options | [Configuration Reference](/docs/spring-boot/configuration-reference) |
| Common business patterns | [Common Patterns](/docs/spring-boot/common-patterns) |
| Return to model and Tool semantics | [Core SDK](/docs/core-sdk/overview) |
| Build a Spring-scoped solution | [Solutions](/docs/solutions/overview) |

## Pre-launch checklist

- key, baseUrl, and model are not hard-coded.
- dev/test/prod configuration sources are distinguishable.
- Single-instance and multi-instance are not mixed into ambiguous routing.
- HTTP timeouts, proxies, logging, and error handling meet project requirements.
- The override order of custom Beans is explainable.
- Security boundaries for RAG, MCP, and Tool are still confirmed against their respective main lines.

Related pages:

- [Version Compatibility](/docs/reference/version-compatibility)
- [Security Overview](/docs/security/overview)
- [Production Checklist](/docs/operations/production-checklist)
- [Troubleshooting](/docs/troubleshooting/overview)

## Further reading

1. [Quickstart](/docs/spring-boot/quickstart)
2. [Auto Configuration](/docs/spring-boot/auto-configuration)
3. [Configuration Reference](/docs/spring-boot/configuration-reference)
4. [Bean Extension](/docs/spring-boot/bean-extension)
5. [Common Patterns](/docs/spring-boot/common-patterns)
