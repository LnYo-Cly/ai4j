# Service Entry and Registry

这一页负责回答 `Core SDK` 最核心的工程问题之一：当你真正开始接 provider、切模型、加能力时，代码应该从哪里进入。

## 1. 先记住真实入口链

如果先按“单实例默认主线”理解，最重要的入口链是：

```text
Configuration
    -> AiService
        -> IChatService / IResponsesService / IEmbeddingService / ...
```

其中：

- `Configuration` 负责装平台配置
- `AiService` 是最常见的统一服务入口
- 各 `I*Service` 是具体能力接口

也就是说，大多数第一次接 AI4J 的用户，首先并不是从很多分散类开始，而是先从 `AiService` 进入。

## 2. 再记住注册与多实例主线

可以先把这一层理解成两层：

- `AiService`：单实例统一入口
- `AiServiceRegistry`：多实例、多 provider、多租户的注册与路由层

如果你只是先把一个模型能力跑起来，通常从 `AiService` 入手；如果你已经进入多环境、多账号或多 provider 调度，再进入 `AiServiceRegistry`。

当前源码里与这条线直接对应的包，主要是：

- `io.github.lnyocly.ai4j.service`
- `io.github.lnyocly.ai4j.service.factory`

## 3. `AiService` 到底负责什么

AI4J 的基座不是一组分散的 API，而是试图把模型能力收束到统一服务入口下。

这意味着你不需要分别为：

- `Chat`
- `Responses`
- `Embedding`
- `Rerank`
- `Audio`
- `Image`
- `Realtime`

各写一套完全不同的接入心智。

从当前实现看，`AiService` 这层还会进一步延伸到：

- `RagService`
- `IngestionPipeline`
- `VectorStore`
- `Reranker`

所以它不是只给 `Chat` 用的入口，而是 `Core SDK` 多能力面的统一进入点。

## 4. `AiServiceRegistry` 什么时候值得引入

`AiServiceRegistry` 适合这些场景：

- 一个系统里要同时维护多套 provider 配置
- 你希望按 `id` 管理多组模型能力
- 你需要多环境、多租户或多账号的明确注册与路由

源码里这条线的典型实现包括：

- `AiServiceRegistry`
- `DefaultAiServiceRegistry`
- `AiServiceRegistration`

更具体地说，它做的是：

- 按 `id` 保存多套 `AiServiceRegistration`
- 每套注册项都绑定一个 `PlatformType` 和对应的 `AiService`
- 然后再暴露按 `id` 获取 `Chat / Responses / Embedding / RAG / Ingestion` 的能力

## 5. 兼容壳和主线入口的边界

当前代码里还能看到一个兼容层：

- `FreeAiService`

它的定位更像旧版本的多实例兼容壳，而不是新的主线入口。

更稳的理解应该是：

- 默认主线：`Configuration -> AiService`
- 正式多实例抽象：`AiServiceRegistry`
- 兼容旧入口：`FreeAiService`

## 6. 和相邻页面的边界

- `service-entry-and-registry` 讲“从哪里接入能力”
- `model-access` 讲“请求语义和协议族怎么选”
- `tools` / `skills` / `mcp` 讲“模型之外还能接什么能力”
- `extension` 讲“默认入口不够时该沿哪条线扩展”

## 7. 推荐阅读顺序

1. [Model Access](/docs/core-sdk/model-access/overview)
2. [Tools](/docs/core-sdk/tools/overview)
3. [Skills](/docs/core-sdk/skills/overview)
4. [MCP](/docs/core-sdk/mcp/overview)
5. [Search & RAG](/docs/core-sdk/search-and-rag/overview)
6. [Extension](/docs/core-sdk/extension/overview)

如果你下一步想从源码入口往下钻，建议连读：

1. [Package Map](/docs/core-sdk/package-map)
2. [Architecture and Module Map](/docs/core-sdk/architecture-and-module-map)
