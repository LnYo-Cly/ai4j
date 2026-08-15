---
title: "Spring Boot Auto Configuration"
description: "Deep dive into the real auto-configuration chain of ai4j-spring-boot-starter, its initialization order and conditional configuration boundaries, to understand the unified Configuration and the failure propagation paths."
tags: [integration]
---

# Spring Boot Auto Configuration

This page covers the starter's real auto-configuration chain, rather than vaguely saying "it auto-configures some beans".

## 1. The real entry point class

The core entry point is:

- `AiConfigAutoConfiguration`

What it does is not a single injection point, but rather a whole auto-configuration chain:

- `@EnableConfigurationProperties(...)` binds a set of `ai.*` property classes
- `@PostConstruct` initializes the unified `Configuration`
- creates `AiService`
- creates `AiServiceFactory`
- creates `AiServiceRegistry`
- creates `FreeAiService`
- conditionally creates `VectorStore`, `RagContextAssembler`, `Reranker`

## 2. Initialization order

The most critical part of `AiConfigAutoConfiguration` is `init()`:

1. Initialize `OkHttpClient`
2. Initialize the various vector database configurations
3. Initialize the `SearXNG` configuration
4. Initialize each provider configuration
5. Write these objects back into the unified `Configuration`

This means that under Spring Boot what you get is not a pile of unrelated configuration beans, but rather an already-organized runtime graph.

## 3. The significance of `initOkHttp()`

`initOkHttp()` is not an ordinary utility method; it determines the underlying network stack of the entire starter.

It:

- Constructs `HttpLoggingInterceptor`
- Loads `DispatcherProvider` via `ServiceLoaderUtil.load(...)`
- Loads `ConnectionPoolProvider` via `ServiceLoaderUtil.load(...)`
- Assembles the unified `OkHttpClient.Builder`
- Applies proxy and SSL policies based on configuration
- Finally writes back to `Configuration.okHttpClient`

:::warning initOkHttp failure propagates across the whole chain
If this step fails, every downstream capability related to provider, vector, RAG, and websearch is affected together, because they all share the same underlying client entry point.
:::

## 4. How single-instance and multi-instance are routed here

### Single instance

If you configure only one provider, the main path is usually:

- `AiService`

### Multiple instances

If you configure `ai.platforms[]`, the main path is usually:

- `AiServiceRegistry`
- `AiServiceRegistration`
- `FreeAiService`

These are not different names for the same thing, but rather two different ways of organizing.

## 5. Boundaries of conditional configuration

Not everything in the starter is created unconditionally.

Some beans are:

- `@ConditionalOnMissingBean`
- `@ConditionalOnProperty`
- `@ConditionalOnBean`

This means the default beans are designed to be taken over, not to forcibly override your business implementations.

## 6. Extension and plugin configuration: `ai.extensions.*`

`AiConfigAutoConfiguration` does not only configure models and the network; it also wires ai4j's extension/plugin system into Spring automatically. The binding entry point is `AiExtensionProperties` (prefix `ai.extensions`), producing two beans:

- `ExtensionRegistry`: `@ConditionalOnMissingBean`; first runs `ExtensionRegistry.discover()` for classpath discovery, then applies the YAML configuration item-for-item.
- `ExtensionRuntimeSnapshot`: an immutable snapshot of `registry.snapshot()`, read at runtime to see the currently enabled tools / commands / skills / prompts / guardrails.

The configuration chain maps each YAML group into a single method call on the registry:

| YAML config | registry call | meaning |
| --- | --- | --- |
| `ai.extensions.enabled` | `enableAll(...)` | Explicitly enable a batch of extensions |
| `ai.extensions.explicit-resource-activation` | `requireExplicitResourceActivation()` | Force resources to be explicitly activated |
| `ai.extensions.tools.expose` | `exposeTools(...)` | Which tools to expose |
| `ai.extensions.commands.allow` | `allowCommands(...)` | Which commands to allow |
| `ai.extensions.skills.allow` | `allowSkills(...)` | Which skills to allow |
| `ai.extensions.prompts.allow` | `allowPrompts(...)` | Which prompts to allow |
| `ai.extensions.guardrails.allow` | `allowGuardrails(...)` | Which guardrails to allow |

Example:

```yaml
ai:
  extensions:
    enabled: [filesystem, search]
    explicit-resource-activation: true
    tools:
      expose: [read-file, web-search]
    skills:
      allow: [summarize]
```

After startup, `ExtensionRuntimeSnapshot` is the currently-effective extension view; business code no longer needs to assemble these lists itself.

:::tip Both beans are @ConditionalOnMissingBean
If you want to fully take over extension configuration (e.g. to dynamically generate lists from an enterprise-internal permission system), simply declare an `ExtensionRegistry` bean of the same name in your own `@Configuration`; the starter's default configuration will automatically step aside.
:::

## 7. OkHttp SPI extension points

The two key parts of the network stack inside `initOkHttp()` are not hard-coded; they are loaded via SPI:

```text
ServiceLoaderUtil.load(DispatcherProvider.class)    // concurrency dispatch policy
ServiceLoaderUtil.load(ConnectionPoolProvider.class) // connection pool policy
```

The interfaces are defined in `io.github.lnyocly.ai4j.network`:

```java
// ai4j 2.4.2, groupId io.github.lnyo-cly
public interface DispatcherProvider {
    okhttp3.Dispatcher getDispatcher();
}

public interface ConnectionPoolProvider {
    okhttp3.ConnectionPool getConnectionPool();
}
```

The default implementations are `DefaultDispatcherProvider` / `DefaultConnectionPoolProvider` (each returns a `new Dispatcher()` / `new ConnectionPool()`). To replace them, implement the corresponding interface and register it via Java SPI (`META-INF/services/...`); the `OkHttpClient` shared across the entire starter will then go through your implementation. Common uses: customizing the maximum concurrent requests, connection pool keep-alive duration, or isolating dispatchers per tenant.

## 8. How you should read this page

Treat it as an object-graph reference page:

- How configuration comes in
- How the unified `Configuration` is assembled
- Which objects are foundational entry points
- Which objects are optional enhancements

If you understand it only as an "auto-configuration example", you will miss the parts that really matter: **the configuration order and the failure propagation paths**.
