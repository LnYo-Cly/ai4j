---
title: "Service Entry and Registry"
description: "Clarifies the real responsibilities of the AiService single-instance entry point and the AiServiceRegistry multi-instance registry, their configuration fallback, and extension boundaries."
tags: [concept]
---

# Service Entry and Registry

This page answers one of the most central engineering questions of the `Core SDK`: **when you actually start wiring providers, switching models, and adding capabilities, where should your code enter.**

## 1. Keep two main lines in mind first

### Single-instance line

```text
Configuration
  -> AiService
    -> IChatService / IResponsesService / IEmbeddingService / ...
```

This is the most common first wiring chain.

### Multi-instance line

```text
Configuration + AiConfig.platforms
  -> DefaultAiServiceRegistry.from(...)
  -> AiServiceRegistry
  -> get(id)
  -> registration.platformType + registration.aiService
```

This chain fits multi-account, multi-tenant, and multi-provider configurations that coexist.

Both lines are public, real, and extensible entry points. The docs do not promote `ChatClient.openAi(...)` or a hidden `Ai4j.chat()` as the main entry, because they obscure the boundaries of `Tool`, `MCP`, `RAG`, `Memory`, `Responses`, and custom network stacks.

## 2. What `AiService` is actually responsible for

`AiService` is not an entry point only for `Chat`; it is the unified capability factory of the current Core SDK.

From the implementation side, it currently owns:

- `getChatService(...)`
- `getResponsesService(...)`
- `getEmbeddingService(...)`
- `getAudioService(...)`
- `getRealtimeService(...)`
- `getImageService(...)`
- `getRerankService(...)`
- `getRagService(...)`
- `getIngestionPipeline(...)`
- `getModelReranker(...)`
- `getAgentFlow(...)`
- `webSearchEnhance(...)`

This means it is not simply a "provider chooser" — it funnels model access, retrieval augmentation, and some composition capabilities into a single entry object.

## 3. `AiService` is an explicit factory today, not dynamic discovery

This fact must be stated up front.

Internally, `AiService` currently decides which implementation class to create through a set of `switch(platform)` statements.
So today's provider/service capability matrix is not auto-discovered; it is explicitly maintained.

This has two consequences:

- The support matrix is clear.
- Adding a new provider or a new service surface requires editing the factory's main chain.

This is exactly why `AiService` itself is the first entry point for understanding the support matrix and the cost of extension.

## 4. What `AiServiceRegistry` actually adds

`AiServiceRegistry` is not simply a `Map<String, AiService>`.

What it formally adds is:

- Managing multiple registration entries by `id`
- Each entry is bound to a `PlatformType`
- Exposing convenience methods that fetch `Chat / Responses / Embedding / RAG / Ingestion / Reranker` by `id` directly

In other words, it does not merely store objects for you; it turns the "multi-instance capability entry point" itself into a formal abstraction.

## 5. The real behavior of `DefaultAiServiceRegistry`

The most notable thing about this implementation is not "it can register", but how it registers.

It will:

1. Read `AiConfig.platforms`
2. Validate each `AiPlatform.id`
3. Resolve `platform` -> `PlatformType`
4. Copy the base `Configuration`
5. Override only the configuration fields belonging to the current instance's provider
6. Construct a scoped `AiService` via `AiServiceFactory.create(...)`
7. Produce an `AiServiceRegistration`

This means multi-instance is not a set of fully independent containers; it is closer to:

- Sharing the underlying base configuration
- Each id owning its own provider-scoped configuration and `AiService`

## 6. The role of `FreeAiService`

`FreeAiService` is currently a compatibility layer, not a new main-line entry point.

It retains:

- The static `getChatService(id)`
- The static `getEmbeddingService(id)`
- The static `getResponsesService(id)` and other legacy entry points

But the more robust way to understand it in the docs is:

- Main-line entry: `AiService`
- Formal multi-instance abstraction: `AiServiceRegistry`
- Legacy compatibility shell: `FreeAiService`

## 7. Where to place an OpenAI-compatible profile

Do not create a new provider type first for an OpenAI-compatible relay platform. Typically you configure it as `platform: openai`, then distinguish it with a different `id` and `api-host`:

```yaml
ai:
  platforms:
    - id: trovebox-low-cost
      platform: openai
      api-key: ${TROVEBOX_API_KEY}
      api-host: https://codex.trovebox.online/
```

```java
IChatService chatService = aiServiceRegistry.getChatService("trovebox-low-cost");
```

Here `trovebox-low-cost` is a business profile id; `openai` indicates the underlying protocol adaptation. For the full recipe, see [OpenAI-compatible and TroveBox](/docs/capabilities/models/openai-compatible-and-trovebox).

## 8. How this page divides labor with adjacent pages

- `service-entry-and-registry` covers "where to enter the capabilities"
- `platform-service-matrix` covers "which services each platform supports"
- `model-access` covers "how to model request semantics once inside a service"
- `extension` covers "which line to extend along when the default entry points are not enough"

## 9. A few facts most easily overlooked

### Service objects are not cached by default

The `AiService` code still shows traces of having considered caching `chatService` / `embeddingService`, but the current implementation does not enable it.
This means `get*Service()` creates a concrete service instance per call by default.

### `AiServiceRegistry.get(id)` throws directly on unknown ids

This makes the registry better suited as a formal multi-instance entry point, rather than a loose lookup tool that "might or might not have it".

### `PlatformType.getPlatform(...)` is tolerant by design

:::warning
This method falls back to `OPENAI` for unknown values, while `DefaultAiServiceRegistry.resolvePlatformType(...)` throws explicitly on unknown platforms.
Formal multi-instance configuration should rely on the latter's strict behavior.
:::

## 10. Conclusion of this page

> AI4J's current service entry system is explicit and layered: `AiService` is the single-instance unified capability factory, `AiServiceRegistry` handles formal multi-instance registration and routing, and `FreeAiService` only plays the role of a compatibility shell. Understanding this entry chain matters more than memorizing any single provider's API, because the support matrix, extension cost, and upper-layer wiring all build on top of it.

## 11. API Javadoc

→ [`AiService`](https://javadoc.io/doc/io.github.lnyo-cly/ai4j/2.4.2/io/github/lnyocly/ai4j/service/factory/AiService.html) · [`AiServiceRegistry`](https://javadoc.io/doc/io.github.lnyo-cly/ai4j/2.4.2/io/github/lnyocly/ai4j/service/factory/AiServiceRegistry.html)
