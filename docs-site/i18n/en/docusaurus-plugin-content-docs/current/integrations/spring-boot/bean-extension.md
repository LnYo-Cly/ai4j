---
title: "Spring Boot Bean Extension"
description: "Explains when to override default Beans along AI4J's abstraction layers in Spring Boot, which layer to choose, and typical override points, so you avoid bypassing the unified container model."
tags: [integration]
---

# Spring Boot Bean Extension

The default auto-configuration is only the starting point. Real production systems usually override some Beans, but you should take them over along AI4J's abstraction layers.

## 1. When to Override

Common scenarios:

- Customize the `OkHttpClient`
- Specify your own `VectorStore`
- Customize `RagContextAssembler`
- Customize `Reranker`
- Wire in enterprise-internal assembly or routing strategies

## 2. Which Layer to Override

The preferred order is:

1. Replace the container-layer Bean first
2. Then consider business-layer composition
3. Only consider modifying the underlying SDK implementation last

In other words, the goal of Bean extension is not to bypass AI4J, but rather to put your custom logic back into its container model.

## 3. Where It Goes Wrong Most Often

The most common problem is not writing the Bean — it is choosing the wrong place:

- Replacing at the container layer was the right move, but instead the underlying provider implementation was modified
- The unified abstraction should have been used, but platform-specific code leaked into business code
- The registry or service layer should have handled it, but it was hard-coded into a Controller

This quickly erodes the layered value AI4J was designed to provide.

## 4. Typical Override Points

The base Beans the starter provides by default can usually be taken over on demand:

- `AiService`
- `AiServiceRegistry`
- `FreeAiService`
- `VectorStore`
- `RagContextAssembler`
- `Reranker`

If you only want to add a token budget to the RAG context, you do not need to modify the starter or the provider — just override this Bean:

```java
@Bean
public RagContextAssembler ragContextAssembler() {
    return new TokenAwareRagContextAssembler("gpt-4o-mini", 3000);
}
```

If the model name cannot yet be mapped to a tokenizer, but you know the encoding explicitly, use an explicit override:

```java
@Bean
public RagContextAssembler ragContextAssembler() {
    return TokenAwareRagContextAssembler.withEncoding(EncodingType.O200K_BASE, 3000);
}
```

:::tip Don't guess when the encoding is uncertain
When the encoding is uncertain, do not force a guess; keep the model name or pass only the budget, and let the SDK use its default estimation with a more conservative budget.
:::

### OkHttp SPI extension points: Dispatcher and ConnectionPool

OkHttp's concurrency scheduling and connection pooling are **not overridden via `@Bean`** — they go through Java SPI (`META-INF/services`). Both extension points live in `io.github.lnyocly.ai4j.network`:

- `DispatcherProvider` — controls concurrent request scheduling (default implementation `DefaultDispatcherProvider`)
- `ConnectionPoolProvider` — controls connection reuse and recycling (default implementation `DefaultConnectionPoolProvider`)

To swap the implementation, place a `META-INF/services/io.github.lnyocly.ai4j.network.DispatcherProvider` (or `...ConnectionPoolProvider`) in your own JAR with the fully qualified implementation class name — no need to modify the starter or business Beans. Tunable parameters such as timeout, proxy, and SSL are still controlled by `ai.okhttp.*` (see [Configuration Reference §4](/docs/integrations/spring-boot/configuration-reference#4-aiokhttp-的位置)).

:::tip When to use SPI instead of @Bean
Spring `@Bean` is suitable for replacing business/container-layer objects (`RagContextAssembler`, `VectorStore`, etc.). The concurrency primitives of `OkHttpClient` are shared across the entire starter, and the starter resolves them through SPI, so SPI is the correct layer here; do not attempt to indirectly change the scheduling strategy by force-overriding with `@Bean OkHttpClient`.
:::

## 5. Engineering Principles

- Prefer replacing Beans behind the unified abstraction over modifying the private implementation of an underlying provider
- When multiple Beans of the same type coexist, explicitly declare the selection strategy
- Keep business routing logic in the service layer; do not let it flow back into controllers or util classes

## 6. The Takeaway from This Page

The core of Bean extension is not "can you rewrite a Bean", but rather **whether you stay within AI4J's container and abstraction boundary**.

As long as the override still happens at the Spring layer, you typically preserve the starter's unified model; once you start bypassing these abstractions, governance costs rise quickly.
