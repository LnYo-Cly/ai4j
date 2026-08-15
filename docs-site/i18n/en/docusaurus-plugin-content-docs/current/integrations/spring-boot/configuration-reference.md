---
title: "Spring Boot Configuration Reference"
description: "Maps AI4J Spring Boot configuration by capability surface via the ai.* prefixes, explaining the flow and layering decisions for single-instance versus multi-instance registry configuration."
tags: [reference]
---

# Spring Boot Configuration Reference

This page covers only the configuration entry points, not business calls.

## 1. Configuration layering

AI4J's Spring Boot configuration is not a flat pile of fields; it is organized by capability surface.

Common prefixes include:

- `ai.openai.*`
- `ai.doubao.*`
- `ai.dashscope.*`
- `ai.ollama.*`
- `ai.jina.*`
- `ai.okhttp.*`
- `ai.platforms[]`
- `ai.vector.*`
- `ai.agentflow.*`
- `ai.extensions.*`
- `ai4j.flowgram.*`

## 2. Where these configurations end up

You can remember the main flow as:

```text
application.yml
  -> *ConfigProperties
  -> AiConfigAutoConfiguration
  -> Configuration / Bean graph
```

So the point of this page is not the field list itself, but rather:

- Which capability surface a group of fields belongs to
- Whether it flows into the single-instance path or the multi-instance registry path

## 3. Single-instance and multi-instance

### Single-instance

Configuration like `ai.openai.*` fits the most direct provider wiring.

OpenAI-compatible relay platforms also fall into this category. For example, TroveBox:

```yaml
ai:
  openai:
    api-key: ${TROVEBOX_API_KEY}
    api-host: https://codex.trovebox.online/
```

At this point business code still obtains the `PlatformType.OPENAI` service from `AiService`.

### Multi-instance

Configuration like `ai.platforms[]` fits building an `AiServiceRegistry`, used for multi-account, multi-tenant, or multi-platform routing.

These two paths are not mutually exclusive; they differ in granularity.

Example:

```yaml
ai:
  platforms:
    - id: openai-main
      platform: openai
      api-key: ${OPENAI_API_KEY}
      api-host: https://api.openai.com/
    - id: trovebox-low-cost
      platform: openai
      api-key: ${TROVEBOX_API_KEY}
      api-host: https://codex.trovebox.online/
```

```java
IChatService chatService = aiServiceRegistry.getChatService("trovebox-low-cost");
```

`id` is the business routing name; `platform` determines the underlying provider adapter. Multiple OpenAI-compatible endpoints can share `platform: openai` and be distinguished only by different `id` and `api-host`.

## 4. Where `ai.okhttp.*` fits

`ai.okhttp.*` is not provider configuration; it is low-level network stack configuration, bound to the class `OkHttpConfigProperties` (prefix `ai.okhttp`). It affects:

- Log level
- Timeouts
- Proxy
- SSL policy

This configuration flows through `AiConfigAutoConfiguration.initOkHttp()` into a single shared `OkHttpClient` for the entire starter.

### Full fields and defaults

| Field | Default | Meaning |
| --- | --- | --- |
| `connect-timeout` | `300` (seconds) | Connect timeout |
| `write-timeout` | `300` (seconds) | Write timeout |
| `read-timeout` | `300` (seconds) | Read timeout |
| `time-unit` | `SECONDS` | Unit for the three timeouts above |
| `log` | `BASIC` | OkHttp log level (`NONE`/`BASIC`/`HEADERS`/`BODY`) |
| `proxy-type` | `HTTP` | Proxy type (`HTTP`/`SOCKS`/`DIRECT`) |
| `proxy-url` | empty | Proxy host |
| `proxy-port` | `0` | Proxy port |
| `ignore-ssl` | `false` | Whether to skip SSL certificate verification |

Example:

```yaml
ai:
  okhttp:
    connect-timeout: 15
    read-timeout: 60
    time-unit: seconds
    log: basic
    ignore-ssl: false
    proxy-type: HTTP
    proxy-url: 127.0.0.1
    proxy-port: 7890
```

### `ignore-ssl`: off by default, opt-in only

`ignore-ssl` defaults to `false` — production should not skip certificate verification. Historically it was used to call platforms with incomplete certificates (such as Moonshot/Kimi); now the trust-all `SSLSocketFactory` and the permissive `HostnameVerifier` are only installed when you explicitly set `ai.okhttp.ignore-ssl=true`. Keep `false` unless you clearly know the target certificate is untrusted.

:::warning trust-all is a security downgrade
Enabling `ignore-ssl=true` means giving up certificate verification for every request made by that client. This is a security downgrade — use it only in controlled internal networks or temporary debugging.
:::

### OkHttp SPI extension points

Concurrency dispatch and connection pooling are not hard-coded; they are provided via SPI (see [Auto Configuration / OkHttp SPI extension points](/docs/integrations/spring-boot/auto-configuration#7-okhttp-spi-扩展点)):

- `DispatcherProvider` (default `DefaultDispatcherProvider`)
- `ConnectionPoolProvider` (default `DefaultConnectionPoolProvider`)

To swap implementations, register them through Java SPI (`META-INF/services`); no starter changes required.

## 5. `ai4j.flowgram.*`: FlowGram backend configuration

The FlowGram backend is bound to the class `FlowGramProperties` (prefix `ai4j.flowgram`), and is only assembled by `FlowGramAutoConfiguration` when `ai4j.flowgram.enabled=true` and a web environment is present. Only the common items related to CORS and HTTP node security are listed here.

```yaml
ai4j:
  flowgram:
    enabled: true
    api:
      base-path: /flowgram
    cors:
      allowed-origins:
        - https://your-flowgram-editor.example.com
    http-node:
      allow-private-network: false
    task-store:
      type: memory        # or jdbc
      table-name: ai4j_flowgram_task
      initialize-schema: true
    task-retention: 1h
    trace-enabled: true
```

| Field | Default | Meaning |
| --- | --- | --- |
| `enabled` | `false` | Whether to enable the FlowGram backend |
| `api.base-path` | `/flowgram` | Base path of the task API |
| `cors.allowed-origins` | empty list | Allowed origins for the frontend canvas |
| `http-node.allow-private-network` | `false` | HTTP node SSRF protection switch (see below) |
| `task-store.type` | `memory` | Task store type (`memory`/`jdbc`) |
| `task-store.table-name` | `ai4j_flowgram_task` | Table name in jdbc mode |
| `task-store.initialize-schema` | `true` | Whether to auto-create the table in jdbc mode |
| `task-retention` | `1h` | Task retention duration |
| `trace-enabled` | `true` | Whether to enable node-level tracing |

### CORS: `cors.allowed-origins`

The FlowGram task API does not restrict origins by default (empty list). When your frontend canvas is deployed on a separate domain, add the editor origin to the allowlist:

```yaml
ai4j:
  flowgram:
    cors:
      allowed-origins:
        - https://editor.example.com
        - http://localhost:3000   # local development
```

Only listed origins can make cross-origin calls to `/flowgram/**`.

### HTTP node SSRF protection

`http-node.allow-private-network` defaults to `false`. HTTP node requests pass through `HttpNodeSsrfGuard` first, which blocks loopback, private network, link-local, and cloud metadata addresses. Set it to `true` explicitly only when you genuinely need to reach internal services. See [Built-in Nodes / SSRF protection](/docs/products/flowgram/built-in-nodes#ssrf-guard).

## 6. How to use this page

When you are about to add a new environment configuration, ask yourself three questions first:

1. Is this a provider-level parameter or an HTTP stack parameter?
2. Is this a single-instance configuration or a multi-instance registry configuration?
3. Will this configuration affect the RAG, Tool, or multi-instance routing chain?

If these three questions are not clear, even correctly defined fields can end up in the wrong layer.

## 7. Key objects

When cross-referencing against source, look at these first:

- `AiConfigProperties`
- The various `*ConfigProperties`
- `AiConfigAutoConfiguration`
- `Configuration`

Together they form the path from YAML to the runtime object graph.

## 8. Further reading

- First-time wiring: see [Quickstart for Spring Boot](/docs/getting-started/quickstart-spring-boot)
- Relay platforms: see [OpenAI-compatible and TroveBox](/docs/capabilities/models/openai-compatible-and-trovebox)
- Multi-instance entry point: see [Service Entry and Registry](/docs/capabilities/service-entry)
