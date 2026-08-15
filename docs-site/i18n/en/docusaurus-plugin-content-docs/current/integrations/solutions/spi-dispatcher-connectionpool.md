---
title: "SPI Dispatcher ConnectionPool"
description: "AI4J HTTP stack extension approach: govern production concurrency, connection pools, and network isolation via the DispatcherProvider and ConnectionPoolProvider SPIs."
tags: [integration]
---

# SPI Dispatcher ConnectionPool

This solution is not about model capabilities. It is about how to extend the AI4J HTTP stack once you have entered the stage of production concurrency and network governance.

## 1. When it fits

- High-concurrency model calls
- Multiple providers sharing a single network pool
- Customizing OkHttp to match your business traffic model

The focus here is network-layer extension, not AI capability itself.

## 2. Core module combination

The main chain of this solution is:

- `DispatcherProvider`
- `ConnectionPoolProvider`
- `ServiceLoader`
- `OkHttpClient.Builder`

This shows that AI4J does not hard-code the network layer; it deliberately leaves explicit SPI extension points.

## 3. Why this line matters

When you start production concurrency governance, what you actually care about is usually:

- Per-provider concurrency limits
- Per-host connection reuse
- Network stability and isolation
- Interference between different business traffic models

At this point, "can the AI SDK be invoked" is no longer the core question — HTTP stack governance is.

## 4. When you do not need to read it first

If you are still validating functionality, or you remain in a single-machine, low-concurrency stage, there is no need to spend effort on this page yet.

Get the functional surface working first, then deal with network pool and concurrency governance — the cost is lower.

## 5. Main-line pages to read first

1. [Core SDK / Service Entry and Registry](/docs/capabilities/service-entry)
2. [Spring Boot / Auto Configuration](/docs/integrations/spring-boot/auto-configuration)
3. [Core SDK / SPI HTTP Stack](/docs/extending/code-level/spi-http-stack)

## 6. Diving into implementation details

If you want to look at:

- Custom `DispatcherProvider`
- Custom `ConnectionPoolProvider`
- SPI registration mechanisms

Continue to the deep page:

- [Legacy path case page](/docs/integrations/solutions/spi-dispatcher-connectionpool)

## 7. Key objects

The key objects behind this solution are quite concentrated:

- `DispatcherProvider`
- `ConnectionPoolProvider`
- `ServiceLoader`
- `OkHttpClient.Builder`

This set of objects is enough to explain why the AI4J HTTP stack can be extended formally, rather than only hand-patching the default client.

## 8. When it is worth entering this layer

Typically, this solution only becomes the main line when you start hitting the problems below:

- Concurrent multi-provider calls interfering with each other
- The default connection pool no longer matching the business traffic model
- A need for network isolation and governance across different business traffic

Before that point, it is usually more appropriate to get the upper-layer capability chain working first.

## 9. What to constrain first during implementation

- Whether concurrency scheduling is isolated per provider or per business domain
- Whether connection pool parameters match the actual deployment topology
- Whether the extension can still preserve unified configuration and observability
