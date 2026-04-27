---
sidebar_position: 2
---

# 平台与服务能力矩阵

> Legacy note: 本页保留为历史能力清单。当前正式主线优先从 [Core SDK / Service Entry and Registry](/docs/core-sdk/service-entry-and-registry) 与 [Core SDK / Model Access](/docs/core-sdk/model-access/overview) 进入。

这页用于回答一个核心问题：**ai4j 到底统一了什么，以及每个平台当前支持到什么程度**。

## 1. ai4j 的核心价值：消除接口协议歧义

ai4j 的早期定位就是：

- 用统一的 Java 实体封装不同平台的 API 细节；
- 让业务层尽量不感知“这是 OpenAI 还是豆包、DashScope、Ollama”；
- 通过 `AiService` 提供统一服务入口，降低平台切换成本。

也就是你在业务代码里主要面对这些统一接口：

- `IChatService`
- `IResponsesService`
- `IEmbeddingService`
- `IRerankService`
- `IAudioService`
- `IRealtimeService`
- `IImageService`

## 2. 统一服务入口

```java
AiService aiService = new AiService(configuration);

IChatService chatService = aiService.getChatService(PlatformType.OPENAI);
IResponsesService responsesService = aiService.getResponsesService(PlatformType.DOUBAO);
IEmbeddingService embeddingService = aiService.getEmbeddingService(PlatformType.OLLAMA);
IRerankService rerankService = aiService.getRerankService(PlatformType.JINA);
IImageService imageService = aiService.getImageService(PlatformType.DOUBAO);
IngestionPipeline ingestionPipeline = aiService.getIngestionPipeline(
        PlatformType.OPENAI,
        aiService.getQdrantVectorStore()
);
```

> 如果某个平台不支持某服务，`AiService` 会抛出 `IllegalArgumentException`。

## 3. 平台能力矩阵（以当前代码实现为准）

| 平台 (`PlatformType`) | Chat | Responses | Embedding | Rerank | Audio | Realtime | Image |
| --- | --- | --- | --- | --- | --- | --- | --- |
| `OPENAI` | ✅ | ✅ | ✅ | ❌ | ✅ | ✅ | ✅ |
| `DOUBAO` | ✅ | ✅ | ❌ | ✅ | ❌ | ❌ | ✅ |
| `DASHSCOPE` | ✅ | ✅ | ❌ | ❌ | ❌ | ❌ | ❌ |
| `OLLAMA` | ✅ | ❌ | ✅ | ✅ | ❌ | ❌ | ❌ |
| `JINA` | ❌ | ❌ | ❌ | ✅ | ❌ | ❌ | ❌ |
| `ZHIPU` | ✅ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ |
| `DEEPSEEK` | ✅ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ |
| `MOONSHOT` | ✅ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ |
| `HUNYUAN` | ✅ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ |
| `LINGYI` | ✅ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ |
| `MINIMAX` | ✅ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ |
| `BAICHUAN` | ✅ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ |

补充说明：

- `JINA` 这一行代表 Jina-compatible rerank 接口，不只限于 Jina 官方
- 自托管 `BAAI/bge-reranker-*` 如果暴露成 `/v1/rerank`，也建议走 `PlatformType.JINA`
- 对 `Chat` 能力来说，当前已接入 provider 同时支持两种工具语义：
  - 直接 `IChatService` 调用时，默认保留 provider 侧自动 tool loop
  - 通过 `ChatModelClient` 进入 Agent / Coding Agent 时，使用 `passThroughToolCalls` 把 `tool_calls` 交给 runtime

## 4. 每类服务对应的统一实体

### 4.1 Chat Completions

- 请求：`ChatCompletion`
- 消息：`ChatMessage`
- 响应：`ChatCompletionResponse`
- 流式监听：`SseListener`

### 4.2 Responses API

- 请求：`ResponseRequest`
- 响应：`Response`
- 流式事件：`ResponseStreamEvent`
- 流式监听：`ResponseSseListener`

### 4.3 Embedding

- 请求：`Embedding`
- 响应：`EmbeddingResponse`

### 4.4 Rerank

- 请求：`RerankRequest`
- 文档：`RerankDocument`
- 响应：`RerankResponse`

### 4.5 Audio

- TTS：`TextToSpeech`
- STT：`Transcription`
- 翻译：`Translation`

### 4.6 Image

- 请求：`ImageGeneration`
- 响应：`ImageGenerationResponse`
- 流式监听：`ImageSseListener`

### 4.7 RAG Ingestion

- 入口：`IngestionPipeline`
- 请求：`IngestionRequest`
- 返回：`IngestionResult`
- 输入源：`IngestionSource`

## 5. Spring Boot 配置前缀

在 `ai4j-spring-boot-starter` 中，你可以按平台配置：

- `ai.openai.*`
- `ai.doubao.*`
- `ai.jina.*`
- `ai.dashscope.*`
- `ai.zhipu.*`
- `ai.deepseek.*`
- `ai.moonshot.*`
- `ai.hunyuan.*`
- `ai.lingyi.*`
- `ai.ollama.*`
- `ai.minimax.*`
- `ai.baichuan.*`

通用网络层配置：

- `ai.okhttp.*`

如果你使用 `ai.platforms[]` 维护多实例路由，也可以额外配置：

- `rerankApiHost`
- `rerankUrl`

## 6. 多平台实例管理（AiServiceRegistry）

如果你希望以“配置驱动”的方式维护多平台实例（例如租户按平台路由），优先使用 `AiServiceRegistry`。`FreeAiService` 仍保留兼容静态方法。

```java
// 通过 ai.platforms 配置多个平台，按 id 获取
IChatService chat = aiServiceRegistry.getChatService("tenant-a-openai");

// 兼容旧用法
IChatService legacy = FreeAiService.getChatService("tenant-a-openai");
```

适合场景：

- 多租户平台路由
- 灰度切模型/切平台
- A/B 模型对比

## 7. 何时用 Chat，何时用 Responses

- 如果你需要兼容性高、生态成熟、迁移存量代码：优先 `Chat`。
- 如果你要结构化事件流（reasoning / output item / function args）：优先 `Responses`。
- 如果你已经有候选集合，只想做精排：优先 `Rerank`。

详细对比见：[Core SDK / Model Access / Chat vs Responses](/docs/core-sdk/model-access/chat-vs-responses)。

## 8. 建议的工程分层

业务层建议只持有接口，不要直接依赖平台实现类：

- Controller -> Service -> `IChatService` / `IResponsesService`
- 平台选择逻辑下沉到配置或工厂层
- 统一在日志中记录 `platform + service + model`

这样后续切平台时，影响面最小。
