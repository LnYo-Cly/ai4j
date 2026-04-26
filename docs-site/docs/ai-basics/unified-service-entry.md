---
sidebar_position: 2
---

# 统一服务入口与调用方式

如果只看一句话，AI4J 的基础用法就是：

1. 先准备 `Configuration`
2. 再创建 `AiService`
3. 最后按 `PlatformType` 取出具体服务接口

这一页专门把这条主线讲清楚。

如果你当前关心的不是“怎么用”，而是“为什么同时还有 `AiServiceRegistry` / `FreeAiService` / `AiServiceFactory` 这些骨架类”，先补看：

- [服务工厂与多实例注册表](/docs/ai-basics/service-factory-and-registry)

---

## 1. 三个核心对象

### 1.1 `Configuration`

用来承载平台配置、网络配置和公共 HTTP 客户端。

它负责：

- API Key
- Base URL
- OkHttpClient
- 各平台特定配置

### 1.2 `AiService`

这是统一服务工厂。

你不需要直接依赖某家平台 SDK，而是统一通过它获取服务：

```java
AiService aiService = new AiService(configuration);
```

### 1.3 `PlatformType`

这是平台选择入口。

你最终并不是“拿到一个 OpenAI SDK”，而是“向统一服务工厂声明：我要哪个平台的哪个能力”。

---

## 2. 最小初始化

### 2.1 非 Spring

```java
OpenAiConfig openAiConfig = new OpenAiConfig();
openAiConfig.setApiKey(System.getenv("OPENAI_API_KEY"));

Configuration configuration = new Configuration();
configuration.setOpenAiConfig(openAiConfig);

OkHttpClient okHttpClient = new OkHttpClient.Builder()
        .addInterceptor(new ErrorInterceptor())
        .connectTimeout(300, TimeUnit.SECONDS)
        .writeTimeout(300, TimeUnit.SECONDS)
        .readTimeout(300, TimeUnit.SECONDS)
        .build();

configuration.setOkHttpClient(okHttpClient);

AiService aiService = new AiService(configuration);
```

### 2.2 Spring Boot

Spring Boot 项目通常直接注入：

```java
@Autowired
private AiService aiService;
```

是否从非 Spring 开始，还是从 Spring Boot 开始，不影响后续服务接口的用法。

---

## 3. 先选“服务”，再选“平台”

AI4J 的统一方式不是“先找平台 SDK”，而是“先明确我要什么能力”。

| 需求 | 服务接口 | 请求对象 | 返回对象 / 监听器 | 入口页 |
| --- | --- | --- | --- | --- |
| 普通对话 | `IChatService` | `ChatCompletion` | `ChatCompletionResponse` / `SseListener` | `Chat` |
| 事件化响应 | `IResponsesService` | `ResponseRequest` | `Response` / Responses 流监听器 | `Responses` |
| 向量化 | `IEmbeddingService` | `Embedding` | `EmbeddingResponse` | `Embedding` |
| 候选重排 | `IRerankService` | `RerankRequest` | `RerankResponse` | `Rerank` |
| 文本转语音 / 转录 | `IAudioService` | `TextToSpeech` / `Transcription` / `Translation` | `InputStream` / `TranscriptionResponse` / `TranslationResponse` | `Audio` |
| 图片生成 | `IImageService` | `ImageGeneration` | `ImageGenerationResponse` / `ImageSseListener` | `Image` |
| 实时连接 | `IRealtimeService` | 模型名 + 监听器 | `WebSocket` + `RealtimeListener` | `Realtime` |

最常见的写法：

```java
IChatService chat = aiService.getChatService(PlatformType.OPENAI);
IResponsesService responses = aiService.getResponsesService(PlatformType.DOUBAO);
IEmbeddingService embedding = aiService.getEmbeddingService(PlatformType.OLLAMA);
IRerankService rerank = aiService.getRerankService(PlatformType.JINA);
IImageService image = aiService.getImageService(PlatformType.DOUBAO);
IngestionPipeline ingestion = aiService.getIngestionPipeline(
        PlatformType.OPENAI,
        aiService.getQdrantVectorStore()
);
```

如果某平台当前不支持这类服务，`AiService` 会直接抛出异常，而不是静默降级。

如果你要做多轮对话，但又不想每轮都手动维护完整上下文，可以直接在 `ai4j` 这一层配合 `ChatMemory` 使用：

- `ChatMemory`
- `InMemoryChatMemory`
- `MessageWindowChatMemoryPolicy`

---

## 4. 每类服务怎么传参、怎么读返回

### 4.1 Chat

最核心的三个对象：

- 请求：`ChatCompletion`
- 消息：`ChatMessage`
- 返回：`ChatCompletionResponse`

最小调用：

```java
IChatService chatService = aiService.getChatService(PlatformType.OPENAI);

ChatCompletion request = ChatCompletion.builder()
        .model("gpt-4o-mini")
        .message(ChatMessage.withUser("用一句话介绍 AI4J"))
        .build();

ChatCompletionResponse response = chatService.chatCompletion(request);
```

读取结果时，优先从：

- `response.getChoices()`
- `choices[0].message`

这条主路径去拿最终文本。

如果你要维护多轮上下文，推荐不要手写 `List<ChatMessage>` 拼装，而是直接使用 [ChatMemory：基础会话上下文](/docs/ai-basics/chat/chat-memory)。

### 4.2 Responses

最核心的三个对象：

- 服务：`IResponsesService`
- 请求：`ResponseRequest`
- 返回：`Response`

最小调用：

```java
IResponsesService responsesService = aiService.getResponsesService(PlatformType.DOUBAO);

ResponseRequest request = ResponseRequest.builder()
        .model("doubao-seed-1-8-251228")
        .input("请用一句话介绍 Responses API")
        .instructions("用中文输出")
        .build();

Response response = responsesService.create(request);
```

读取结果时要注意：

- `Responses` 不是单一 `content` 字段
- 最终文本通常要从 `response.output` 里的 `message` 项提取

如果你希望 `Responses` 与 `Chat` 共享同一份会话上下文，也可以直接把 `ChatMemory` 输出成 `input`：

- `memory.toResponsesInput()`

### 4.3 Embedding

最常见的是取第一条向量：

```java
Embedding request = Embedding.builder()
        .model("text-embedding-3-small")
        .input("Explain JVM class loading")
        .build();

EmbeddingResponse response = embeddingService.embedding(request);
List<Float> vector = response.getData().get(0).getEmbedding();
```

### 4.4 Audio

你要先区分三种能力：

- TTS：返回 `InputStream`
- 转录：返回 `TranscriptionResponse`
- 翻译：返回 `TranslationResponse`

最常见读取方式：

- `textToSpeech(...)` 后自己落盘或转发给前端
- `transcription(...).getText()`
- `translation(...).getText()`

### 4.5 Image

图片生成可以是：

- 普通生成：直接拿 `ImageGenerationResponse`
- 流式生成：通过 `ImageSseListener` 监听 partial / completed 事件

### 4.6 Rerank

如果你已经有一批候选文档，希望再排得更准，可以直接拿 `IRerankService`：

```java
IRerankService rerankService = aiService.getRerankService(PlatformType.JINA);

RerankResponse response = rerankService.rerank(RerankRequest.builder()
        .model("jina-reranker-v2-base-multilingual")
        .query("哪段最适合回答 Java 8 升级成本")
        .documents(Arrays.asList(
                RerankDocument.builder().text("很多传统系统依赖 JDK8 和历史中间件").build(),
                RerankDocument.builder().text("AI4J 提供统一 Chat、Responses 和 RAG 能力").build()
        ))
        .topN(1)
        .build());
```

如果你不是直接做重排服务，而是要把它接进 RAG，则优先用：

```java
Reranker reranker = aiService.getModelReranker(
        PlatformType.JINA,
        "jina-reranker-v2-base-multilingual",
        5,
        "优先包含制度原文和编号的片段"
);
```

### 4.7 Realtime

Realtime 的主入口不是普通请求对象，而是：

- 模型名
- `RealtimeListener`
- 返回 `WebSocket`

它更适合长期连接场景，而不是普通 HTTP 请求。

### 4.8 RAG Ingestion

如果你现在不是“查知识库”，而是“先把资料写进知识库”，统一入口就是：

```java
IngestionPipeline ingestionPipeline = aiService.getIngestionPipeline(
        PlatformType.OPENAI,
        aiService.getQdrantVectorStore()
);

IngestionResult ingestResult = ingestionPipeline.ingest(IngestionRequest.builder()
        .dataset("kb_docs")
        .embeddingModel("text-embedding-3-small")
        .source(IngestionSource.text("AI4J 提供统一的 Java 大模型接入能力"))
        .build());
```

如果你已经决定走 Pinecone，也可以直接用快捷入口：

```java
IngestionPipeline ingestionPipeline = aiService.getPineconeIngestionPipeline(PlatformType.OPENAI);
```

这条链路会统一完成：

- `DocumentLoader`
- `Chunker`
- `MetadataEnricher`
- `embedding`
- `VectorStore.upsert`

详细说明看：

- [Ingestion Pipeline 文档入库流水线](/docs/ai-basics/rag/ingestion-pipeline)

---

## 5. 多模态、Tool、MCP 在哪里接

### 5.1 多模态

多模态仍然走 `ChatCompletion` 主线。

例如可以直接构造带图片 URL 的用户消息：

```java
ChatCompletion request = ChatCompletion.builder()
        .model("gpt-4o-mini")
        .message(ChatMessage.withUser("图片中有什么东西", "https://example.com/cat.jpg"))
        .build();
```

### 5.2 Tool / Function

最小方式仍然是在 `ChatCompletion` 里显式声明函数名：

```java
ChatCompletion request = ChatCompletion.builder()
        .model("gpt-4o-mini")
        .message(ChatMessage.withUser("查询北京天气"))
        .functions("queryWeather")
        .build();
```

### 5.3 MCP

MCP 不属于这章的主线入口，但它最终也会进入“工具能力”这层。

建议理解成：

- 基础服务层先解决“模型请求怎么发”
- MCP 再解决“工具能力从哪里来”

如果你现在只是第一次接入模型，不要先把 MCP 混进首调路径。

---

## 6. 怎么选择 Chat 还是 Responses

优先选 `Chat`：

- 你想先走最成熟的普通对话链路
- 你需要更直接的消息式交互
- 你当前重点是 Tool / Function 调用

优先选 `Responses`：

- 你需要更强的事件化语义
- 你要消费更丰富的流式事件
- 你希望面向新一代响应模型组织业务

如果你还不确定，先用 `Chat` 打通第一个成功请求，再切 `Responses`。

---

## 7. 推荐的学习顺序

1. 先掌握这一页的统一入口
2. 再看 [平台适配与统一接口](/docs/ai-basics/platform-adaptation)
3. 再看 [统一请求与返回读取约定](/docs/ai-basics/request-and-response-conventions)
4. 再看 [ChatMemory：基础会话上下文](/docs/ai-basics/chat/chat-memory)
5. 然后在 `Chat` 与 `Responses` 中选一条主线深入
6. 再按业务需要补多模态、Tool、Embedding、Rerank、Audio、Image、Realtime
7. 如果要做知识库，再进入 `IngestionPipeline + Retriever + Reranker`

---

## 8. 下一步阅读

1. [平台适配与统一接口](/docs/ai-basics/platform-adaptation)
2. [统一请求与返回读取约定](/docs/ai-basics/request-and-response-conventions)
3. [Chat（非流式）](/docs/ai-basics/chat/non-stream)
4. [ChatMemory：基础会话上下文](/docs/ai-basics/chat/chat-memory)
5. [Responses（非流式）](/docs/ai-basics/responses/non-stream)
6. [Rerank 接口](/docs/ai-basics/services/rerank)
7. [Ingestion Pipeline 文档入库流水线](/docs/ai-basics/rag/ingestion-pipeline)
