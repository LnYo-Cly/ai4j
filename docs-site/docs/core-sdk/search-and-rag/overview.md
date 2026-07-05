# Search and RAG 总览

这一章如果只写成“支持在线搜索、向量库、重排、引用”，其实还没讲到重点。  
真正重要的是：**AI4J 是怎样把这些零散能力串成一条知识增强链的。**

从源码看，这一章至少有两条主分支：

- 在线开放知识增强：`ChatWithWebSearchEnhance`
- 离线私域知识增强：`IngestionPipeline -> VectorStore -> Retriever -> Reranker -> ContextAssembler`

把这两条线分开看，才不会把 Search、RAG、在线搜索、向量库混成一个词。

## 1. 先看它在 Core SDK 里的真实位置

这一章横跨的核心包，不止一个：

- `rag`
- `rag.ingestion`
- `vector.store`
- `rerank`
- `websearch`
- `document`
- `service`

这已经说明它不是单点 API，而是一组跨包协作的知识增强子系统。

## 2. 源码里真正的主装配点是什么

如果你想找“默认链路是怎么被串起来的”，最值得看的不是某个 demo，而是：

- `service/factory/AiService.java`

从这里可以直接看到几条默认装配：

```java
getIngestionPipeline(platform, vectorStore)
getRagService(platform, vectorStore)
webSearchEnhance(chatService)
getModelReranker(platform, model)
```

也就是说，Search & RAG 在 AI4J 当前代码里，并不是松散概念，而是已经被工厂层抽成了几组固定入口。

## 3. 离线 RAG 的默认主线到底是什么

如果走 `AiService.getRagService(platform, vectorStore)`，默认装配是：

```java
new DefaultRagService(
    new DenseRetriever(getEmbeddingService(platform), vectorStore),
    new NoopReranker(),
    new DefaultRagContextAssembler()
)
```

如果你需要检索前 query planning，可以走重载入口：

```java
getRagService(platform, vectorStore, queryPlanner)
getModelRagQueryPlanner(platform, model)
```

这几句代码非常关键，因为它把默认 RAG 的真实形态说透了：

- 默认检索器是 `DenseRetriever`
- 默认不做模型重排，走 `NoopReranker`
- 默认引用与上下文拼装走 `DefaultRagContextAssembler`
- 默认不做 query planning；显式传入 `RagQueryPlanner` 才会启用检索前处理，内置模型 planner 默认只做 rewrite，多策略需要显式指定

所以如果你没有显式接入 hybrid 或 model rerank，AI4J 默认给你的并不是“全功能 RAG 套餐”，而是：

**dense retrieval + no-op rerank + default citation-aware assembly**

如果你需要 BM25 或 Dense + BM25 混合召回，不是换另一个 `RagService` 工厂，而是换底层 `Retriever`：

```java
Retriever hybrid = new HybridRetriever(Arrays.asList(denseRetriever, bm25Retriever));
RagService rag = new DefaultRagService(hybrid);
```

详见 [Hybrid Retrieval](/docs/core-sdk/search-and-rag/hybrid-retrieval)。

## 4. 入库主线默认怎么跑

如果走 `AiService.getIngestionPipeline(platform, vectorStore)`，默认装配则是：

- `TextDocumentLoader`
- `TikaDocumentLoader`
- `RecursiveTextChunker(1000, 200)`
- `WhitespaceNormalizingDocumentProcessor`
- `DefaultMetadataEnricher`

而 `IngestionPipeline` 本身会把一条 ingest 请求拆成：

1. source load
2. loaded document process
3. `RagDocument` resolve
4. chunk normalize
5. embedding batch generate
6. vector record build
7. `VectorStore.upsert(...)`

这说明 AI4J 当前的离线 RAG，不是“向量库接好了就完成”，而是一条独立 ingestion 编排链。

## 5. 在线搜索为什么不属于离线 RAG 主线

在线搜索入口是：

- `websearch/ChatWithWebSearchEnhance.java`

它的做法不是走 `Retriever`，而是包一层 `IChatService`，然后：

- 用最后一条消息做搜索 query
- 调 SearXNG
- 把搜索结果 JSON 直接拼进 prompt

所以 Online Search 的定位是：

- 实时公网补充
- prompt 级增强

而不是：

- 私域知识库检索
- chunk / vector / rerank 链的一部分

这两条线在产品层可能都会被叫“检索增强”，但在源码层是完全不同的实现路线。

## 6. 这一章里最重要的 4 个中枢对象

如果你只抓核心骨架，最值得先记住的是这 4 个：

- `IngestionPipeline`
- `VectorStore`
- `DefaultRagService`
- `ChatWithWebSearchEnhance`

它们分别对应：

- 知识如何进入系统
- 向量如何被存取
- 知识如何被召回并转成上下文
- 公开网络知识如何临时注入对话

后面的 `HybridRetriever`、`ModelReranker`、`RagTrace`、`RagCitation`，都是围绕这条主骨架展开的增强件。

## 7. 当前默认链路里哪些能力是“可选增强”

从代码看，下面这些都不是默认主线的一部分，而是你显式接入才有：

- `HybridRetriever`
- `RagQueryPlanner`
- `ModelReranker`
- 自定义 `Chunker`
- 自定义 `MetadataEnricher`
- 自定义 `RagContextAssembler`

这点很重要，因为很多文档会把它们写成“RAG 默认就有的完整能力”。  
AI4J 当前不是这么设计的，它更像一个：

**先给你稳定骨架，再允许你逐层替换和增强。**

## 8. 读这一章时最该抓住哪几个边界

### 8.1 `Model Access` 不是 `Search & RAG`

`Model Access` 解决的是：

- 怎么调聊天模型
- 怎么调 embedding 模型
- 怎么调 rerank provider

`Search & RAG` 解决的是：

- 外部知识怎么进入系统
- 进入以后如何被检索、重排、引用

### 8.2 `MCP` 不是 `Search & RAG`

MCP 解决的是协议化外部能力接入。  
RAG 解决的是知识检索和上下文增强。  
两者可以协同，但不是同一层抽象。

### 8.3 `Agent` 也不是 `Search & RAG`

Agent 负责多步决策、工具使用、状态推进；RAG 负责给模型补知识。  
把 RAG 理解成 agent 的一个知识供应层，会比把它理解成“另一个 agent runtime”更准确。

## 9. 当前实现最容易被写错的地方

### 9.1 把 Online Search 写成统一搜索框架

它当前只是 `IChatService` 包装层，不是统一 `Retriever` 体系。

### 9.2 把默认 RAG 写成“已集成重排和混合检索”

默认工厂其实是 `DenseRetriever + NoopReranker`。

### 9.3 把 Vector Store 当成唯一主角

向量库只是中间一层。没有 ingest、metadata、retriever、assembler，这条链根本不完整。

### 9.4 把 citation 当成最终 answer grounding 证明

当前 citation 是上下文组装产物，不是“模型确实使用了该证据”的严格证明。

## 10. 推荐阅读顺序

如果你想建立最稳的源码心智模型，建议按下面顺序读：

1. [Ingestion Pipeline](/docs/core-sdk/search-and-rag/ingestion-pipeline)
2. [Chunking Strategies](/docs/core-sdk/search-and-rag/chunking-strategies)
3. [Embedding](/docs/core-sdk/search-and-rag/embedding)
4. [Vector Store and Backends](/docs/core-sdk/search-and-rag/vector-store-and-backends)
5. [Query Planning](/docs/core-sdk/search-and-rag/query-planning)
6. [Hybrid Retrieval](/docs/core-sdk/search-and-rag/hybrid-retrieval)
7. [Rerank](/docs/core-sdk/search-and-rag/rerank)
8. [Citations and Trace](/docs/core-sdk/search-and-rag/citations-and-trace)
9. [Online Search](/docs/core-sdk/search-and-rag/online-search)

这个顺序的核心逻辑是：

- 先看知识怎么进来
- 再看知识怎么被存和取
- 再看取回后怎么被修正和组装
- 最后再看公网增强这一条旁路

## 11. 这页最该记住的结论

AI4J 当前的 Search & RAG，不是一组零散功能点，而是两条并行但边界清晰的知识增强路线：

- 离线私域路线：`IngestionPipeline -> VectorStore -> Retriever -> Reranker -> ContextAssembler`
- 在线公网路线：`ChatWithWebSearchEnhance -> prompt augmentation`

默认工厂给你的骨架是能直接跑通的，但增强件并不会自动开启。  
理解这条“默认骨架 + 可选增强”的结构，再看后面的页面就不会失焦。
