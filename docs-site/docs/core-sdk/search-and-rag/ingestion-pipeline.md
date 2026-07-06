# Ingestion Pipeline

这页讲“文档如何进入知识库”。在 AI4J 里，`IngestionPipeline` 不是一个随手拼出来的 demo helper，而是一条明确的 RAG 入库编排层。

如果你想真正看懂 AI4J 的 RAG 基座，这页必须讲透。因为它决定了：

- 文档身份是怎么建立的
- chunk 是怎么切的
- metadata 是怎么一路带下去的
- embedding 和向量写入怎么串起来

## 1. 核心源码入口

- 主入口：`rag/ingestion/IngestionPipeline.java`
- 请求对象：`rag/ingestion/IngestionRequest.java`
- source 对象：`rag/ingestion/IngestionSource.java`
- 文档对象：`RagDocument`
- chunk 对象：`RagChunk`

默认装配还能直接从 `IngestionPipeline` 构造器看到：

- `TextDocumentLoader`
- `TikaDocumentLoader`
- `RecursiveTextChunker(1000, 200)`
- `WhitespaceNormalizingDocumentProcessor`
- `DefaultMetadataEnricher`

这不是“几段工具类拼起来”，而是一条已经预先抽象好的标准流水线。

## 2. 一条 ingest 请求至少包含什么

从 `IngestionRequest` 可以直接看到，核心字段有：

- `dataset`
- `embeddingModel`
- `document`
- `source`
- `chunker`
- `documentProcessors`
- `metadataEnrichers`
- `batchSize`
- `upsert`
- `skipExistingContentHash`

这组字段很说明问题：

- `dataset` 定义写入边界
- `embeddingModel` 定义向量语义
- `source` 定义原始语料入口
- `chunker / processors / enrichers` 定义知识工程策略

也就是说，AI4J 没把 ingest 简化成“给我一个文件，我帮你随便入库”，而是保留了对关键策略的显式控制位。

## 3. 一次真正的执行流程

从 `ingest(...)` 方法往下看，完整流程大致是：

1. 校验 `dataset` 和 `embeddingModel`
2. 用 `DocumentLoader` 加载原始 source
3. 通过 `LoadedDocumentProcessor` 做文本清洗
4. 构造或补齐 `RagDocument`
5. 用 `Chunker` 切出 `RagChunk`
6. 为每个 chunk 写入稳定 `contentHash`
7. 如果开启 `skipExistingContentHash`，先用支持 metadata lookup 的 `VectorStore.exists(...)` 跳过已存在 chunk
8. 把剩余 chunk 批量送去 embedding
9. 组装 `VectorRecord`
10. 写入 `VectorStore`

这个顺序非常重要，因为它说明：

- metadata 不是向量写入时临时补的
- chunk identity 不是搜索时临时算的
- ingest 阶段已经决定了后续引用和 trace 的上限

## 4. `documentId` 和 `chunkId` 为什么是这条链的核心

`IngestionPipeline` 会根据 source / path / uri 等信息构造稳定 `documentId`，并进一步规范化：

- `chunkId`
- `chunkIndex`
- `documentId`

这意味着 AI4J 非常强调：

- 文档身份要稳定
- chunk 身份要可追踪

如果这一步做不好，后面你再想做：

- 引用回溯
- UI 证据展示
- rerank 结果解释
- 文档版本比较

都会变得很难。

## 5. 默认给你的不只是“能跑通”

默认构造器已经内置了：

- 两种 loader
- 递归 chunker
- 文本清洗 processor
- metadata enricher
- batch embedding

这说明 AI4J 不是把 ingest 当成“用户自己随便写一条 for 循环”的外围能力，而是直接把一套常见知识工程默认值做到了基座层。


## 6. 增量入库：`contentHash` 与 `skipExistingContentHash`

AI4J 现在会为每个标准化后的 chunk 写入：

- `contentHash`：chunk content 的 SHA-256，用来判断同一份内容是否已经入过库

默认行为不变：即使已有相同内容，也会继续 embedding 和 upsert。
如果你希望重复 ingest 同一批资料时少做无效 embedding，可以显式开启：

```java
IngestionResult result = ingestionPipeline.ingest(IngestionRequest.builder()
        .dataset("kb_docs")
        .embeddingModel("text-embedding-3-small")
        .source(IngestionSource.text("员工手册内容"))
        .skipExistingContentHash(Boolean.TRUE)
        .build());

int skipped = result.getSkippedCount();
```

这不是新的索引框架，也不会替你做版本发布、删除旧文档或权限治理。它只做一件事：

1. 先算 chunk `contentHash`
2. 后端支持 metadata-only lookup 时，用 `VectorStore.exists(...)` 查这个 hash 是否存在
3. 已存在则跳过 embedding / upsert，并计入 `skippedCount`
4. 后端不支持或 lookup 出错时 fail-open，按普通 ingest 继续写入

当前内置后端中，Qdrant / Milvus / PgVector / Redis 提供 metadata lookup；Pinecone 的现有封装仍保持默认不跳过。

## 7. 你可以在哪些位置扩展

这是这页最有工程价值的部分。

你可以替换或扩展：

- `DocumentLoader`
- `LoadedDocumentProcessor`
- `Chunker`
- `MetadataEnricher`
- `batchSize`
- `upsert` 策略

这意味着 AI4J 的 ingest 不是封闭黑盒，而是一条**可插拔的知识工程流水线**。

## 8. 它不负责什么

这条链只负责把知识规范地“送进库里”，不负责：

- 检索排序
- 最终回答拼接
- 用户权限过滤
- 引用 UI 呈现

这些能力属于：

- retriever / reranker
- rag service
- 上层应用

如果把这些都压进 `IngestionPipeline`，反而会让边界失真。

## 9. 常见坑

### 9.1 `dataset` 设计随意

后面检索隔离、删除和回放都会很难治理。

### 9.2 只关心 embedding，不关心 metadata

向量能召回，但证据链讲不清。

### 9.3 把 chunking 当成次要细节

实际很多 RAG 效果问题根源就在这里。

## 10. 设计摘要

> AI4J 的 `IngestionPipeline` 是一条显式的 RAG 入库编排层：source 加载、文本处理、chunk、metadata、embedding、vector upsert 都在这里被串成统一流水线。它的价值不是“帮你省胶水代码”，而是把文档 identity 和知识工程策略稳定下来。

## 11. 继续阅读

- [Search and RAG / Chunking Strategies](/docs/core-sdk/search-and-rag/chunking-strategies)
- [Search and RAG / Vector Store and Backends](/docs/core-sdk/search-and-rag/vector-store-and-backends)
