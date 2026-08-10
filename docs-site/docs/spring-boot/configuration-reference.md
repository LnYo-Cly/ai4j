---
title: Spring Boot Configuration Reference
description: 按 ai.* 能力面前缀梳理 AI4J 的 Spring Boot 配置，说明单实例与多实例注册表配置的流向与分层判断。
tags: [reference]
---

# Spring Boot Configuration Reference

这一页只讲配置入口，不讲业务调用。

## 1. 配置分层

AI4J 的 Spring Boot 配置不是一坨平铺字段，而是按能力面分层组织的。

常见前缀包括：

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

## 2. 这些配置最终流向哪里

可以先把主线记成：

```text
application.yml
  -> *ConfigProperties
  -> AiConfigAutoConfiguration
  -> Configuration / Bean graph
```

所以这页的重点不是字段列表本身，而是：

- 这组字段属于哪个能力面
- 它会进入单实例主线，还是多实例注册表主线

## 3. 单实例和多实例

### 单实例

像 `ai.openai.*` 这种配置，适合最直接的 provider 接入。

OpenAI-compatible 中转平台也属于这一类。比如 TroveBox：

```yaml
ai:
  openai:
    api-key: ${TROVEBOX_API_KEY}
    api-host: https://codex.trovebox.online/
```

此时业务代码仍然从 `AiService` 获取 `PlatformType.OPENAI` 的服务。

### 多实例

像 `ai.platforms[]` 这种配置，适合构建 `AiServiceRegistry`，用于多账号、多租户或多平台路由。

两条线不是互斥，而是粒度不同。

示例：

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

`id` 是业务路由名；`platform` 决定底层 provider 适配。多个 OpenAI-compatible endpoint 可以共享 `platform: openai`，只通过不同 `id` 和 `api-host` 区分。

## 4. `ai.okhttp.*` 的位置

`ai.okhttp.*` 不是 provider 配置，而是底层网络栈配置，绑定类 `OkHttpConfigProperties`（前缀 `ai.okhttp`）。它影响的是：

- 日志级别
- 超时时间
- 代理
- SSL 策略

这类配置会通过 `AiConfigAutoConfiguration.initOkHttp()` 进入整个 starter 共享的统一 `OkHttpClient`。

### 完整字段与默认值

| 字段 | 默认值 | 含义 |
| --- | --- | --- |
| `connect-timeout` | `300`（秒） | 连接超时 |
| `write-timeout` | `300`（秒） | 写超时 |
| `read-timeout` | `300`（秒） | 读超时 |
| `time-unit` | `SECONDS` | 上面三个超时的单位 |
| `log` | `BASIC` | OkHttp 日志级别（`NONE`/`BASIC`/`HEADERS`/`BODY`） |
| `proxy-type` | `HTTP` | 代理类型（`HTTP`/`SOCKS`/`DIRECT`） |
| `proxy-url` | 空 | 代理主机 |
| `proxy-port` | `0` | 代理端口 |
| `ignore-ssl` | `false` | 是否跳过 SSL 证书校验 |

示例：

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

### `ignore-ssl`：默认关闭，显式才打开

`ignore-ssl` 默认 `false` —— 生产环境不应跳过证书校验。历史上它用于请求某些证书不全的平台（如 Moonshot/Kimi），现在只有显式设 `ai.okhttp.ignore-ssl=true` 才会装 trust-all 的 `SSLSocketFactory` 和放行 hostname 的 `HostnameVerifier`。除非你明确知道目标证书不可信，否则保持 `false`。

:::warning trust-all 是安全降级
打开 `ignore-ssl=true` 等于放弃对该客户端所有请求的证书校验，属于安全降级，仅在受控内网或临时联调时使用。
:::

### OkHttp SPI 扩展点

并发调度与连接池不是写死的，由 SPI 提供（详见 [Auto Configuration / OkHttp SPI 扩展点](/docs/spring-boot/auto-configuration#7-okhttp-spi-扩展点)）：

- `DispatcherProvider`（默认 `DefaultDispatcherProvider`）
- `ConnectionPoolProvider`（默认 `DefaultConnectionPoolProvider`）

要换实现，走 Java SPI（`META-INF/services`）注册即可，无需改 starter。

## 5. `ai4j.flowgram.*`：FlowGram 后端配置

FlowGram 后端绑定类是 `FlowGramProperties`（前缀 `ai4j.flowgram`），只在 `ai4j.flowgram.enabled=true` 且 Web 环境下由 `FlowGramAutoConfiguration` 装配。这里只列与跨域、HTTP 节点安全相关的常用项。

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
      type: memory        # 或 jdbc
      table-name: ai4j_flowgram_task
      initialize-schema: true
    task-retention: 1h
    trace-enabled: true
```

| 字段 | 默认值 | 含义 |
| --- | --- | --- |
| `enabled` | `false` | 是否启用 FlowGram 后端 |
| `api.base-path` | `/flowgram` | 任务 API 基础路径 |
| `cors.allowed-origins` | 空列表 | 允许的前端画布来源 |
| `http-node.allow-private-network` | `false` | HTTP 节点 SSRF 防护开关（见下） |
| `task-store.type` | `memory` | 任务存储类型（`memory`/`jdbc`） |
| `task-store.table-name` | `ai4j_flowgram_task` | jdbc 模式表名 |
| `task-store.initialize-schema` | `true` | jdbc 模式是否自动建表 |
| `task-retention` | `1h` | 任务保留时长 |
| `trace-enabled` | `true` | 是否开启节点级 trace |

### CORS：`cors.allowed-origins`

FlowGram 任务 API 默认不限制来源（空列表）。当你的前端画布部署在独立域名时，把编辑器来源加进白名单：

```yaml
ai4j:
  flowgram:
    cors:
      allowed-origins:
        - https://editor.example.com
        - http://localhost:3000   # 本地开发
```

只有列出的 origin 才能跨域调用 `/flowgram/**`。

### HTTP 节点 SSRF 防护

`http-node.allow-private-network` 默认 `false`，HTTP 节点请求会先过 `HttpNodeSsrfGuard`，拦截回环/私网/链路本地/云元数据地址。仅在确需访问内网服务时才显式设为 `true`。详见 [Built-in Nodes / SSRF 防护](/docs/flowgram/built-in-nodes#71-ssrf-防护-httpnodessrfguard)。

## 6. 这页应该怎么用

当你要加一个新环境配置时，先问自己三个问题：

1. 这是 provider 级参数，还是 HTTP 栈参数
2. 这是单实例配置，还是多实例注册表配置
3. 这项配置是否会影响 RAG、Tool 或多实例路由链

如果这三个问题没想清楚，字段加对了也容易放错层。

## 7. 关键对象

继续对照源码时，优先看：

- `AiConfigProperties`
- 各类 `*ConfigProperties`
- `AiConfigAutoConfiguration`
- `Configuration`

它们共同构成了从 YAML 到运行时对象图的路径。

## 8. 继续阅读

- 首次接入：看 [Quickstart for Spring Boot](/docs/start-here/quickstart-spring-boot)
- 中转平台：看 [OpenAI-compatible 与 TroveBox](/docs/start-here/openai-compatible-and-trovebox)
- 多实例入口：看 [Service Entry and Registry](/docs/core-sdk/service-entry-and-registry)
