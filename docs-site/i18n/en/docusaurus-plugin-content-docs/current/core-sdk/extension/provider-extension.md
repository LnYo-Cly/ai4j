---
title: "Provider Extension"
description: "Explains the AI4J provider extension: integrating a new model platform is an explicit factory-dispatch extension that must simultaneously touch PlatformType, Configuration, AiService, DefaultAiServiceRegistry, and the Spring Boot starter; the provider support matrix is maintained explicitly rather than auto-discovered."
tags: [concept]
---

# Provider Extension

`provider extension` addresses one problem: **formally bringing a new model platform into AI4J's platform dispatch system**.
It is not as simple as "writing yet another service class", because AI4J currently models providers explicitly and in an enum-driven way.

## 1. Start with the real entry points

This kind of change flows through at least the following source files:

- `service/PlatformType.java`
- `service/Configuration.java`
- `service/factory/AiService.java`
- `service/factory/DefaultAiServiceRegistry.java`
- `ai4j-spring-boot-starter/.../AiConfigAutoConfiguration.java`

The most critical one is `AiService`. Whether the SDK formally recognizes a provider does not depend on "whether some implementation class sits on the classpath", but rather on whether it enters these `switch` dispatch branches:

- `createChatService(...)`
- `createResponsesService(...)`
- `createEmbeddingService(...)`
- `createImageService(...)`
- `createAudioService(...)`
- `createRealtimeService(...)`
- `createRerankService(...)`

This means the provider support matrix is maintained explicitly, not auto-discovered.

## 2. The real shape of provider extension today

AI4J does not currently offer a generic provider SPI where "registering a provider plugin is enough to wire it in".

The real flow is:

1. Name the platform: add a `PlatformType`
2. Give the platform configuration: add the corresponding `*Config`
3. Slot it into the unified configuration object: add fields in `Configuration`
4. Implement the capability surface: add concrete services such as chat / responses / embedding
5. Add the factory branch: wire the provider into the support matrix in `AiService`
6. Add multi-instance config-copy logic: add a branch in `DefaultAiServiceRegistry.applyPlatformConfig(...)`
7. Augment the Spring Boot starter with property binding and initialization logic

Doing only one or two of these steps is rarely enough. For example, if you only write `FooChatService` but it never enters `AiService.createChatService(...)`, external users still cannot obtain it.

## 3. When it should not be called a provider extension

The following cases generally should not be escalated to a provider extension:

- Adding a new model name under the same provider
- Mapping one more field on an existing request object
- Adding an optional option to an existing `Chat` capability of a provider
- Just switching `apiHost` within the same protocol family

These look more like [Model Extension](/docs/core-sdk/extension/model-extension).

Only when you need:

- A new `PlatformType`
- A new provider configuration type
- A new factory dispatch branch
- New starter auto-configuration

...does it count as a real provider extension.

## 4. What the existing code chain requires you to change

### 4.1 `PlatformType`

This is the formal enum entry point for platform names. A new provider must land here, otherwise factory dispatch has no legal enum value to use.

One detail to watch:

- `DefaultAiServiceRegistry.resolvePlatformType(...)` throws directly on unknown platforms
- `PlatformType.getPlatform(...)` falls back to `OPENAI` for unknown values

:::warning
The former suits formal configuration validation; the latter can mask typos. Do not treat `getPlatform(...)` as a strict validation entry point in extension code.
:::

### 4.2 `Configuration`

All provider configuration ultimately hangs off the unified `Configuration`. When adding a provider, you need not only its own configuration class but also a `Configuration` that can carry it.

Otherwise, even after you write the service, there is no unified configuration object for downstream implementations to read.

### 4.3 `AiService`

This is the most important layer. `AiService` is currently the final router from platform to capability implementation.

For example, `Chat` currently covers far more providers than `Responses`. This is not a documentation policy but the actual branch matrix inside `AiService`.

So when adding a provider, you must explicitly answer:

- Which top-level services it supports
- Which top-level services it does not support
- Whether to keep an explicit `IllegalArgumentException` when unsupported

It is better to keep the "unsupported" fact explicit here, rather than doing a vague fallback.

### 4.4 `DefaultAiServiceRegistry`

The multi-instance mode does not simply reuse the same `Configuration`. It first copies the base `Configuration`, then copies the fields of the current `AiPlatform` into the corresponding provider config.

If you forget to add a new-provider branch in `applyPlatformConfig(...)`, then:

- Single-instance manual `new AiService` may still work
- The multi-instance registry scenario breaks outright

This kind of issue is common, because extension authors often succeed on the shortest-path demo first and then miss the registry path.

### 4.5 Spring Boot starter

On the starter side, you must also add at least two kinds of things:

- `*ConfigProperties`
- Initialization logic in `AiConfigAutoConfiguration`

Otherwise the repository ends up with a very typical inconsistency:

- The core SDK works when assembled manually
- The Spring Boot scenario cannot be fully landed through configuration

## 5. Boundaries and costs of the current implementation

### No provider auto-discovery

The current provider extension is explicit code wiring, not an auto-registration-style extension. The upside is a clear capability matrix; the downside is that every new provider forces changes in multiple central points.

### Service objects are not cached by default

`AiService.getChatService(...)` currently calls `createChatService(...)` directly. The comments still show that caching was once considered, but it is not enabled now.

This means:

- Concrete provider services should be as stateless as possible
- Heavy resources should reuse the shared `OkHttpClient` in `Configuration`
- Do not bury one-time initialization cost inside every `get*Service()` call

### Multi-instance is just provider config sharding, not a fully isolated container

When `DefaultAiServiceRegistry` copies `Configuration`, it inherits shared objects such as the base `OkHttpClient`, then overrides the current provider's scoped configuration.

So multi-instance is more like "multiple sets of platform configuration + shared underlying client", rather than each id building its entire network stack from scratch.

## 6. Which chain to inspect first when debugging

### Can `new` it manually, but cannot declare it in configuration

Check first:

- `DefaultAiServiceRegistry.applyPlatformConfig(...)`
- `AiConfigAutoConfiguration`

This is the most common "demo works, starter does not" failure.

### `getChatService(...)` works, `getResponsesService(...)` errors

First check whether the corresponding `create*Service(...)` in `AiService` actually has a branch for that provider.
AI4J deliberately allows asymmetric support matrices across service surfaces, so this kind of error is usually not a runtime fluke but an undefined factory matrix.

### Provider name configured, but routes to the wrong platform

Check whether `PlatformType.getPlatform(...)` was used by mistake. It falls back to `OPENAI` on unknown values, which turns a typo into "looks like it runs, but on the wrong platform".

## 7. A practical decision line

If this change must simultaneously touch:

- `PlatformType`
- `Configuration`
- `AiService`
- `DefaultAiServiceRegistry`

Then what you are doing is essentially a formal provider extension, not a local patch.

## 8. Conclusion of this page

> AI4J's current provider extension is an explicit factory-dispatch extension, not a plugin-style auto-wiring. The essence of adding a provider is wiring a new platform into the main chain `PlatformType + Configuration + AiService + Registry + Starter`; writing only the implementation class without completing this chain means the extension is not yet truly done.
