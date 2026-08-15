---
title: "SPI HTTP Stack"
description: "Explains the AI4J HTTP stack SPI: Dispatcher and ConnectionPool are injected via ServiceLoader into the unified OkHttpClient built by the starter, default implementations are registered through META-INF/services, and losing them causes startup failure — only the starter auto-configuration chain activates it automatically."
tags: [concept]
---

# SPI HTTP Stack

This page covers one of the few extension surfaces in AI4J that is genuinely SPI-ized today: **low-level `OkHttp` concurrency dispatching and connection-pool strategy**.

The biggest difference between it and a provider extension is that it is not wired through a `switch` inside `AiService` — it actually goes through the Java `ServiceLoader`.

## 1. Where the real entry points are

The core entry points are highly concentrated:

- `network/DispatcherProvider.java`
- `network/ConnectionPoolProvider.java`
- `network/impl/DefaultDispatcherProvider.java`
- `network/impl/DefaultConnectionPoolProvider.java`
- `service/spi/ServiceLoaderUtil.java`
- `ai4j-spring-boot-starter/.../AiConfigAutoConfiguration.initOkHttp()`

The default implementations take effect not because the code hardcodes a fallback, but because:

- `ai4j/src/main/resources/META-INF/services/io.github.lnyocly.ai4j.network.DispatcherProvider`
- `ai4j/src/main/resources/META-INF/services/io.github.lnyocly.ai4j.network.ConnectionPoolProvider`

have already registered the default implementations on the classpath.

## 2. The real assembly order inside the Spring Boot starter

`AiConfigAutoConfiguration.initOkHttp()` currently assembles the unified `OkHttpClient` in roughly the following order:

1. Create the `HttpLoggingInterceptor`
2. Read the log level from `okHttpConfigProperties`
3. Load the `DispatcherProvider` via `ServiceLoaderUtil.load(...)`
4. Load the `ConnectionPoolProvider` via `ServiceLoaderUtil.load(...)`
5. Construct the `OkHttpClient.Builder`
6. Attach the `ErrorInterceptor` and `ContentTypeInterceptor`
7. Apply connect/write/read timeout
8. Inject the SPI-provided `Dispatcher` and `ConnectionPool`
9. Decide whether to enable a proxy based on configuration
10. Decide whether to ignore SSL verification based on configuration
11. `build()` and write the result back to the unified `Configuration`

This shows that the HTTP stack extension sits very early in the starter scenario.
Once something goes wrong here, every capability that relies on the same `Configuration.okHttpClient` — OpenAI, DashScope, Doubao, Qdrant and so on — is affected together.

## 3. What this layer actually controls

Only two points are officially exposed right now:

- `Dispatcher`
- `ConnectionPool`

That may look like very little, but they are already enough to govern many key runtime characteristics:

- How requests are dispatched concurrently
- Queuing and contention between requests of the same kind
- Resource reuse between long-lived connections and short requests
- How the unified network stack is shared across chat, vector, and external-protocol requests

AI4J pulls these two points out not to add a flashy option for demos, but to give low-level network governance a proper entry point.

## 4. Why it deserves to be its own extension surface

In the typical scenarios for this repository, network behavior is not uniform:

- `Chat` and `Responses` run in both synchronous and streaming modes
- `Embedding` and `Rerank` are often batch requests
- Vector stores and external service integrations also reuse the unified client

If every scenario could only rely on one set of implicit default network parameters, the problems usually would not surface during the demo stage — they would show up in real environments as:

- Hard concurrency governance
- Long-lived connections and short requests interfering with each other
- Network tuning in one module unexpectedly affecting other capability surfaces

The point of the HTTP SPI is to elevate this kind of low-level strategy difference from "casually `new`-ing a client in business code" into a reusable, replaceable, packageable formal extension point.

## 5. The boundary between this layer and a hand-assembled `OkHttpClient`

Two usage modes must be kept distinct.

### Spring Boot starter path

If you go through the starter, `initOkHttp()` actively uses `ServiceLoaderUtil` to find the SPI implementation, then produces the unified `OkHttpClient`.

At this point:

- The SPI is the effective path
- Default implementations come from `META-INF/services`
- A custom implementation affects the entire unified client assembled by the starter

### Hand-assembled `Configuration` path

If you do not go through the starter, but instead manually `new` a `Configuration` and call `setOkHttpClient(...)`, the SPI itself does not kick in automatically.
Here the real extension point is the `OkHttpClient` assembly code in your hands, not the `ServiceLoader`.

This boundary matters, otherwise it is easy to assume "I implemented a `DispatcherProvider`, so it will take effect in every scenario". It will not — it only triggers when the corresponding assembly chain is used.

## 6. What the current default implementation actually means

`DefaultDispatcherProvider` and `DefaultConnectionPoolProvider` currently just return a fresh OkHttp default object:

- `new Dispatcher()`
- `new ConnectionPool()`

So the essence of the default behavior is "follow OkHttp's own default strategy", not that AI4J does any particularly aggressive tuning of concurrency and connections.

This has both an upside and a cost:

- Upside: the default path is simple enough, and the behavior stays close to native OkHttp
- Cost: once you enter high-concurrency, mixed long/short request, or strict resource governance scenarios, you usually need to provide a more explicit strategy yourself

## 7. The most critical failure paths for this layer

### `META-INF/services` is missing

When `ServiceLoaderUtil.load(...)` cannot find an implementation, it throws directly:

- `IllegalStateException("No implementation found for ...")`

:::warning
So if you produce a fat jar, shade, repackage, or accidentally exclude resource files, the starter may fail to start before it even initializes the `OkHttpClient`.
:::

### Over-globalizing a custom SPI implementation

The same `Configuration.okHttpClient` is shared across multiple capability surfaces.
If you turn a dispatcher/connection-pool strategy that only suits a single path into a global implementation, you can spread a problem from one provider to the entire SDK.

### Assuming that changing the SPI equals changing every runtime behavior

If some piece of code never goes through the starter and instead plugs in its own `OkHttpClient`, SPI changes will not take effect for it automatically.

## 8. Debugging suggestions

When a network-layer exception occurs, rule things out in the following order:

1. Check the startup log to see whether `ServiceLoaderUtil` printed the loaded implementation class
2. Verify that the final artifact still contains `META-INF/services/*`
3. Confirm whether the current path is starter auto-configuration or a manual `Configuration.setOkHttpClient(...)`
4. Confirm whether your custom dispatcher / connection pool is inadvertently affecting every provider

If the problem only shows up in one provider's field mapping or response parsing, you usually should not come back to this layer — go look at the corresponding provider service instead.

## 9. When you should use this layer

Scenarios suited to sinking down into the HTTP SPI include:

- You want to govern the low-level concurrency behavior of all AI requests uniformly
- You want to make connection-pool strategy a classpath-level replacement
- You use the starter and want network strategy to land uniformly via auto-configuration

Scenarios not suited to sinking down prematurely include:

- Only the business semantics of one provider are changing
- You are only adding a model field
- A single demo just wants to temporarily tweak one request

## 10. Conclusion for this page

> AI4J's HTTP stack extension is one of the few parts that genuinely goes through the SPI. It injects the `Dispatcher` and `ConnectionPool` via `ServiceLoader` into the unified `OkHttpClient` built by the starter, thereby influencing the low-level network behavior of the entire SDK. The key here is not "can it be customized" — it is understanding that it is a global strategy entry point, depends on the packaging completeness of `META-INF/services`, and only takes effect automatically on the corresponding assembly chain.
