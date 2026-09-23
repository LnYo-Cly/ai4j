---
title: Spring Boot 配置参考
description: 按 ai.* 能力面前缀梳理 AI4J 的 Spring Boot 配置，说明单实例与多实例注册表配置的流向与分层判断。
tags: [reference]
---

# Spring Boot 配置参考
这一页只讲配置入口，不讲业务调用。

## 1. 配置分层

AI4J 的 Spring Boot 配置不是一坨平铺字段，而是按能力面分层组织的。

常见前缀包括：

- `ai.openai.*`
- `ai.doubao.*`
- `ai.dashscope.*`
- `ai.ollama.*`
- `ai.jina.*`
- `ai.mineru.*`
- `ai.okhttp.*`
- `ai.platforms[]`
- `ai.vector.*`
- `ai.agentflow.*`
- `ai.extensions.*`
- `ai.agent.*`（Agent Blueprint 声明式装配）
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

并发调度与连接池不是写死的，由 SPI 提供（详见 [Auto Configuration / OkHttp SPI 扩展点](/docs/integrations/spring-boot/auto-configuration#8-okhttp-spi-扩展点)）：

- `DispatcherProvider`（默认 `DefaultDispatcherProvider`）
- `ConnectionPoolProvider`（默认 `DefaultConnectionPoolProvider`）

要换实现，走 Java SPI（`META-INF/services`）注册即可，无需改 starter。

## 5. `ai.mineru.*`：MinerU 云端文档解析

`MinerUConfigProperties`（前缀 `ai.mineru`）装配 `Configuration.mineruConfig`，并暴露 `minerUService` bean。核心类是 `MinerUService`（v4 精准解析 + v1 免 token lite 解析）和 `MinerUDocumentLoader`（RAG 入库 loader，见 [摄取管线](/docs/capabilities/rag/ingestion-pipeline)）。

```yaml
ai:
  mineru:
    api-key: ${MINERU_API_KEY}     # 留空则走免 token 的 lite 接口（IP 限频）
    model-version: vlm
    is-ocr: false
    enable-formula: true
    enable-table: true
    language: ch
```

| 字段 | 默认值 | 含义 |
| --- | --- | --- |
| `api-key` | 空 | MinerU API token；为空时所有请求走 lite 接口 |
| `base-url` | `https://mineru.net/api/v4` | v4 精准解析 API 根地址 |
| `lite-base-url` | `https://mineru.net/api/v1/agent` | v1 lite API 根地址 |
| `model-version` | `vlm` | 模型版本（`pipeline`/`vlm`/`MinerU-HTML`） |
| `is-ocr` | `false` | 是否启用 OCR |
| `enable-formula` | `true` | 是否开启公式识别 |
| `enable-table` | `true` | 是否开启表格识别 |
| `language` | `ch` | 文档语言 |
| `page-ranges` | 空 | 页码范围，如 `2,4-6` |
| `extra-formats` | 空 | 额外导出格式（`docx`/`html`/`latex`） |
| `data-id` | 空 | 业务数据 ID 透传（`data_id`） |
| `poll-interval-ms` | `3000` | 任务轮询间隔（毫秒） |
| `poll-timeout-ms` | `600000` | 任务轮询总超时（毫秒） |

限制：v4 本地文档 ≤200MB；lite ≤10MB/约 20 页且按 IP 限频，免费队列繁忙时轮询可能超时——超时抛 `AiTimeoutException`（带 taskId，可稍后手动查）。

## 6. `ai4j.flowgram.*`：FlowGram 后端配置

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

`http-node.allow-private-network` 默认 `false`，HTTP 节点请求会先过 `HttpNodeSsrfGuard`，拦截回环/私网/链路本地/云元数据地址。仅在确需访问内网服务时才显式设为 `true`。详见 [Built-in Nodes / SSRF 防护](/docs/products/flowgram/built-in-nodes#ssrf-guard)。

## 7. 这页应该怎么用

当你要加一个新环境配置时，先问自己三个问题：

1. 这是 provider 级参数，还是 HTTP 栈参数
2. 这是单实例配置，还是多实例注册表配置
3. 这项配置是否会影响 RAG、Tool 或多实例路由链

如果这三个问题没想清楚，字段加对了也容易放错层。

## 8. 关键对象

继续对照源码时，优先看：

- `AiConfigProperties`
- 各类 `*ConfigProperties`
- `AiConfigAutoConfiguration`
- `Configuration`

它们共同构成了从 YAML 到运行时对象图的路径。

## 9. 继续阅读

- 首次接入：看 [Spring Boot 快速开始](/docs/getting-started/quickstart-spring-boot)
- 中转平台：看 [OpenAI-compatible 与 TroveBox](/docs/capabilities/models/openai-compatible-and-trovebox)
- 多实例入口：看 [服务入口与注册表](/docs/capabilities/service-entry)
