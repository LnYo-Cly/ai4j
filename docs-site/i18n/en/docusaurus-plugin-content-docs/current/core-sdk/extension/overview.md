---
title: "Extension Overview"
description: "Establish the AI4J extension mental model: provider/service goes through the PlatformType+AiService+Registry code main chain, HTTP concurrency and connection governance goes through the underlying SPI, and third-party plugin packages go through ai4j-extension-api+ServiceLoader+ExtensionRegistry; the extension surfaces are not symmetric."
tags: [concept]
---

# Extension Overview

The `extension` chapter is not about "where you can wedge custom code", but rather **which variations AI4J currently treats as formal extensions inside the foundation, and which variations still require you to enter the factory and configuration main chain to change code**.

This must be stated up front, because AI4J does not currently use the same mechanism for every extension surface:

| Extension surface | Current implementation form | Real entry point |
| --- | --- | --- |
| Provider extension | In-code dispatch extension, not a general-purpose SPI | `service/PlatformType.java`, `service/factory/AiService.java` |
| Model extension | Internal capability extension within an existing provider | request object + provider service implementation |
| Service extension | In-code capability-surface extension, not a general-purpose SPI | `service/*.java`, `AiService`, `AiServiceRegistry`, `FreeAiService` |
| HTTP stack extension | A genuine SPI extension | `network/*Provider.java`, `META-INF/services/*`, `AiConfigAutoConfiguration.initOkHttp()` |
| Plugin package | Runtime resource extension in the form of a third-party jar | `ai4j-extension-api`, `ServiceLoader`, `ExtensionRegistry`, Agent / Coding Agent `.extensions(...)`, Spring Boot `ai.extensions.*` |

## 1. Where this chapter sits in the Core SDK

If `service-entry-and-registry` covers "where to get a service", then `extension` covers:

- When the existing platforms are not enough, which layer to change
- Which changes affect only a single provider
- Which changes will balloon into a cross-SDK abstraction change
- Which underlying behaviors already have a formal SPI, so you can avoid touching the provider main chain

This chapter still belongs to the `ai4j/` foundation itself. It is not a patch-notes page for Spring Boot, Agent, or Coding Agent.

But starting with plugin packages, the extension surface enters the boundary between `ai4j-extension-api`, `ai4j-agent`, and `ai4j-coding`. Plugin packages are not a way to add new providers; rather, they hand runtime resources such as tools, commands, Skills, Prompts, and Guardrails to Agent / Coding Agent for use.

## 2. Look at the real execution chain first

AI4J's current extension flow is roughly the following:

1. Construct a `Configuration`
2. Place the provider configuration into the corresponding field of `Configuration`
3. Select a platform via `AiService` or `AiServiceRegistry`
4. The `create*Service(...)` methods inside `AiService` dispatch to concrete implementations by `PlatformType`
5. The concrete provider service then projects the unified request object onto the external platform protocol

The multi-instance scenario adds one more layer:

1. `DefaultAiServiceRegistry.from(...)` reads `AiConfig.platforms`
2. Copy a scoped `Configuration` for each `AiPlatform`
3. `applyPlatformConfig(...)` writes that instance's provider configuration back into the scoped `Configuration`
4. Produce an `AiServiceRegistration(id, platformType, aiService)`

There are two very practical consequences here:

- Provider-dimension extensions must enter `PlatformType`, `AiService`, and `DefaultAiServiceRegistry`
- Service instances are not cached singletons by default; entry points like `AiService.getChatService(...)` create a new concrete service every time

The second point means that if your extension implementation depends on expensive initialization, you should fold the shared resources into `Configuration` or `OkHttpClient`, rather than assuming `AiService` will reuse concrete service objects for you.

## 3. What each of the four extension lines solves

### Provider extension

You are adding a new platform boundary, e.g. a new `PlatformType`, a new provider configuration object, and the various service implementations it supports.

This kind of change will definitely enter the factory dispatch main chain.

### Model extension

You are still staying within the same provider, only adding model names, request fields, response fields, or variants of the same capability surface.

This kind of change should usually not touch `PlatformType`.

### Service extension

You are adding a new top-level capability surface, rather than adding fields to an existing capability.

This kind of change widens the SDK's public abstraction surface, and the cost is usually higher than model extension and provider extension.

### HTTP stack extension

You do not want to change the model protocol itself; you only want to control the underlying `OkHttp` concurrency scheduling and connection pool strategy.

This kind of change has already been formalized as an SPI, and is the extension surface in this chapter closest to "pluggable".

### Plugin package

You do not want to change the core SDK main chain; you only want to provide a set of reusable resources to the host application, e.g.:

- tools the agent can call
- coding agent auxiliary tools
- extension manifests the CLI can inspect
- prompt, skill, or guardrail resources
- agent lifecycle observation hooks (session/turn/model/tool/compact events)

This kind of extension goes through `ai4j-extension-api`. The consumer first places the plugin jar onto the classpath via Maven / Gradle, then discovers it with `ExtensionRegistry.discover()` and enables it with `enable(...)`. Tools must then be explicitly exposed to the model via `exposeTool(...)`; commands, Skills, Prompts, and Guardrails can keep using the default compatible whole-package enablement semantics, or be authorized item-by-item via the `requireExplicitResourceActivation()` and `allow*` APIs. Spring Boot projects can use `ai.extensions.enabled`, `ai.extensions.tools.expose`, `ai.extensions.explicit-resource-activation`, and `ai.extensions.{commands,skills,prompts,guardrails}.allow` to do the same configuration, but this still does not auto-create an Agent or auto-install plugin dependencies.

Plugin authors and consumers can run local validation with `ExtensionValidator` or `ai4j-cli extension validate <id>|--all`. Validation invokes the plugin's `apply(...)` to collect runtime contributions and only reports manifest, runtime resource, tool schema, and classpath resource issues; it does not expose tools to the model or execute commands. Before wiring, you can also use `ai4j-cli extension plan <id> --enable ... --strict` to inspect the activation state after the planned enable, authorize, and expose steps; once the recipe is frozen, use `ai4j-cli extension check <id> --enable ... --strict` as a CI or pre-release gate. `check` returns non-zero when validation fails or an explicitly requested resource is not active, but it does not force-enable unrequested resources.

The official `ai4j-plugin-ask-user` is the first sample plugin shipped with the SDK, demonstrating how to express the user confirmation an Agent needs as a host-mediated JSON envelope; the standalone repository `ai4j-plugin-dynamic-workflow` shows how to express a dynamic workflow request as a plugin envelope under the same host control. When you are ready to wire in plugins, start with [Plugin Recipes](/docs/core-sdk/extension/plugin-recipes), which strings dependencies, validation, enablement, authorization, exposure, and Spring Boot / CLI configuration into copyable recipes.

#### 3.1 Plugin capability list (six kinds)

`ai4j-extension-api` groups the resources a plugin can contribute into six `ExtensionCapability` values. A plugin declares in its manifest which kinds it wants to contribute, and only then will the registry accept the corresponding registrations:

| Capability | Registration entry (`ExtensionContext`) | What it contributes | Stability |
| --- | --- | --- | --- |
| `TOOL` | `tools()` | Model-callable tool (spec + executor); requires `exposeTool(...)` to be visible | Stable |
| `COMMAND` | `commands()` | Human / host-executed command; not auto-exposed to the model | Stable |
| `SKILL` | `skills()` | Classpath text Skill resource | Experimental |
| `PROMPT` | `prompts()` | Classpath text Prompt resource | Experimental |
| `GUARDRAIL` | `guardrails()` | Pre-tool-execution allow/deny decision | Experimental |
| `LIFECYCLE` | `lifecycle()` | Agent lifecycle event hook (session/turn/model/tool/compact) | Experimental |

The first five capabilities cover "what resources a plugin contributes". `LIFECYCLE` is the sixth, covering a different class of need: **the plugin wants to be notified at key points during agent execution (session start/end, before and after each turn, before and after model requests, before and after tool calls, before and after context compaction)**, rather than contributing tools or resources. It addresses observation / telemetry / audit extensions, not new capabilities.

After declaring `LIFECYCLE`, the plugin registers an `AgentLifecycleHook` in `apply(...)` via `context.lifecycle().register(hook)`; at runtime the agent dispatches `AgentLifecycleEvent`s to each hook through `AgentLifecycleHookDispatcher`. See [Lifecycle Extensions](/docs/core-sdk/extension/lifecycle-extensions) for details.

#### 3.2 Plugin SPI stability matrix

The public elements inside `ai4j-extension-api` are not all at the same stability level. AI4J distinguishes them with three markers:

| Marker | Meaning | Backward compatibility commitment |
| --- | --- | --- |
| No annotation | Stable | Backward compatible within the current major version |
| `@Experimental` | Experimental: released but design still converging | **No** backward compatibility commitment; signature/behavior/existence may change in minor or patch versions, and it may be removed or promoted to stable |
| `@Internal` | Internal implementation | Not for consumer use, no commitments |

:::warning
When depending on APIs marked `@Experimental`, pin the exact version and proactively run regression tests when upgrading minor versions. These APIs are not given deprecation grace periods.
:::

As of `2.4.3`, the actual distribution of the extension SPI:

| Element | Type | Stability |
| --- | --- | --- |
| `Ai4jExtension`, `ExtensionManifest`, `ExtensionRegistry`, `ExtensionContext` | Core SPI entry | Stable |
| `ToolRegistry`, `CommandRegistry` | tool/command registration | Stable |
| `SkillRegistry`, `PromptRegistry`, `GuardrailRegistry` | skill/prompt/guardrail registration | `@Experimental(since = "2.4.3")` |
| `ExtensionGuardrail` | guardrail interface | `@Experimental(since = "2.4.3")` |
| `LifecycleHookRegistry`, `AgentLifecycleHook` | lifecycle hook registration and interface | `@Experimental(since = "2.4.3")` |
| `ServiceLoaderExtensionLoader` | default ServiceLoader loader | `@Internal` (depends on `ExtensionLoader`; do not depend on the implementation class directly) |

In other words: the tool / command main path is stable; the four resource registration interfaces — skill, prompt, guardrail, and lifecycle — are still experimental, and their design may shift in later versions. Both `@Experimental` and `@Internal` are retained at runtime via reflection, so plugin authors can see the annotations directly in the IDE without memorizing this table.

## 4. In the current implementation, what is a "real SPI" and what is not

This is the most easily misread part of this chapter.

### What is not a general-purpose SPI

- provider extension
- top-level service extension

Although the repository has abstractions like `AiServiceFactory`, the provider capability matrix itself is still hardcoded in the `switch` dispatches of `AiService.createChatService(...)`, `createResponsesService(...)`, `createImageService(...)`, and so on.

That is, adding a provider today is not "register an implementation and it becomes visible automatically"; you have to enter the main-line factory code.

### What actually takes effect via SPI

- `DispatcherProvider`
- `ConnectionPoolProvider`
- `Ai4jExtension`, for plugin package manifest and runtime resource discovery

The Spring Boot starter loads these two extension points via `ServiceLoaderUtil.load(...)` inside `AiConfigAutoConfiguration.initOkHttp()`, and injects the returned `Dispatcher` and `ConnectionPool` into the unified `OkHttpClient.Builder`.

`Ai4jExtension` is also discovered via `ServiceLoader`, but it serves plugin package resource registration and does not change provider factory dispatch automatically.

Plugin discovery itself has a small SPI layer too: the `ExtensionLoader` interface (default implementation is the `@Internal` `ServiceLoaderExtensionLoader`). `ExtensionRegistry.discover()` goes through ServiceLoader by default, but you can pass a custom `ExtensionLoader` to implement non-ServiceLoader discovery (e.g. a fixed list, runtime scanning). When reading Skill / Prompt text resources inside a plugin jar, the public helper `ExtensionResourceResolver` resolves them in the order "plugin classloader → TCCL → resolver classloader", and constrains resolution to the plugin's own classloader to prevent same-named resources from being crossed-read by another jar. Both of these are runtime wiring details; see [Extension SPI Internals](/docs/core-sdk/extension/extension-spi).

## 5. Extension decision order

When you hit "the existing SDK is not enough", first judge in the following order:

1. Is this a new platform, or a new model within the same platform
2. Can the existing `Chat / Responses / Embedding / Image / Audio / Realtime / Rerank` contracts still carry it
3. Is the problem in request semantics, or only in the network stack
4. Does this change need to cover the multi-instance registry and Spring Boot auto-configuration

Judging in this order avoids two common structural mistakes:

- It is only a model change, yet you balloon `PlatformType` and the factory layer together
- It is only a concurrency and connection governance issue, yet you go off and add a provider branch

## 6. Default behaviors that need special attention

### `PlatformType.getPlatform(...)` fault tolerance is not strict

:::warning
This method falls back to `OPENAI` when no matching value is found. This may be convenient for a quick demo, but it is unsafe for formal extensions, because a misspelled provider name may not surface immediately.
:::

By contrast, `DefaultAiServiceRegistry.resolvePlatformType(...)` throws `Unsupported ai platform ...` directly on an unknown platform, which is the behavior more appropriate for formal configuration.

### The starter's HTTP SPI is not optional

`ServiceLoaderUtil.load(...)` throws `IllegalStateException` directly when it cannot find an implementation. The default implementation works not because the code has a `new DefaultDispatcherProvider()` fallback, but because `ai4j/src/main/resources/META-INF/services/` has already registered the default implementation.

So this layer is sensitive to the packaging result. When `META-INF/services` is missing, Spring Boot startup will fail at the `initOkHttp()` stage.

## 7. When not to enter this chapter

If you are only:

- Calling an existing provider to send a request
- Changing the prompt / tool / memory combination
- Troubleshooting why some field is not being sent out

Then you should usually go back to the corresponding capability page first, rather than jumping straight to the extension docs. `extension` fits the "the current abstraction is no longer enough" scenario better.

## 8. Recommended reading order

1. [Provider Extension](/docs/core-sdk/extension/provider-extension)
2. [Model Extension](/docs/core-sdk/extension/model-extension)
3. [Service Extension](/docs/core-sdk/extension/service-extension)
4. [SPI HTTP Stack](/docs/core-sdk/extension/spi-http-stack)
5. [Plugin Packages](/docs/core-sdk/extension/plugin-packages)
6. [Plugin Recipes](/docs/core-sdk/extension/plugin-recipes)
7. [Plugin Author Cookbook](/docs/core-sdk/extension/plugin-author-cookbook)
8. [Ask User Plugin](/docs/core-sdk/extension/ask-user-plugin)
9. [Dynamic Workflow Plugin](/docs/core-sdk/extension/dynamic-workflow-plugin)
10. [Lifecycle Extensions](/docs/core-sdk/extension/lifecycle-extensions)
11. [Extension SPI Internals](/docs/core-sdk/extension/extension-spi)

## 9. Conclusion of this page

> AI4J's current extension surfaces are not symmetric. Provider and top-level service still go through the `PlatformType + AiService + Registry` code main chain, while HTTP concurrency and connection governance go through the underlying SPI; third-party plugin packages go through `ai4j-extension-api + ServiceLoader + ExtensionRegistry` to expose controllable runtime resources to Agent / Coding Agent, and Spring Boot simply externalizes the same set of registry/snapshot configuration. Before you actually start extending, first judge whether what you are hitting is a platform boundary, a model variant, a new capability, network stack governance, or plugin resource reuse.
