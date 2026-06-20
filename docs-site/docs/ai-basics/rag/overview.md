---
sidebar_position: 1
---

# RAG 与知识库增强总览

RAG 在这套文档里，指的是“基于私域知识库的检索增强生成”，不是泛指所有外部检索。

它的标准链路通常是：

```text
文档解析
  -> 文本切片
  -> embedding
  -> 向量库入库
  -> 查询向量化
  -> 召回 / 过滤
  -> 上下文拼接
  -> 模型生成
```

---

## 1. 这一组文档解决什么问题

当模型本身不知道你的内部资料、制度、合同、产品文档时，RAG 解决的是：

- 如何把资料变成可检索的索引；
- 如何在提问时召回最相关片段；
- 如何把证据安全地塞回模型上下文；
- 如何把“回答质量差”的问题拆成可排查的检索环节。

---

## 2. 和联网增强的边界

这里的 `RAG` 不等于公网搜索。

- 联网增强：解决“查最新网页”
- RAG：解决“查内部知识库”

所以：

- `SearXNG` 不属于 RAG；
- 当前官方主线是 `Embedding + VectorStore + 检索编排`；
- `Pinecone`、`Qdrant`、`pgvector`、`Milvus` 都属于可接入的向量存储实现。

---

## 3. 当前官方主线

如果你要在 AI4J 里落地一条最小可用的 RAG 链路，推荐从这三页开始：

1. [RAG 架构、分块与索引设计](/docs/ai-basics/rag/architecture-and-indexing)
2. [VectorStore 与存储后端](/docs/ai-basics/rag/vector-store-and-storage-backends)
3. [Ingestion Pipeline 文档入库流水线](/docs/ai-basics/rag/ingestion-pipeline)
4. [Chunking 策略详解](/docs/ai-basics/rag/chunking-strategies)
5. [混合检索与 Rerank 实战工作流](/docs/ai-basics/rag/hybrid-retrieval-and-rerank-workflow)
6. [Embedding 接口](/docs/ai-basics/services/embedding)
7. [引用、Trace 与前端展示](/docs/ai-basics/rag/citations-trace-and-ui-integration)
8. [Pinecone RAG 工作流](/docs/ai-basics/rag/pinecone-workflow)

当前代码层新增的统一核心包括：

- `VectorStore`
- `IngestionPipeline`
- `DocumentLoader`
- `Chunker`
- `MetadataEnricher`
- `RagService`
- `DenseRetriever`
- `Bm25Retriever`
- `HybridRetriever`
- `Reranker`
- `RagContextAssembler`
- `RagTrace`
- `RagEvaluator`

如果你想直接调用模型重排服务，而不是只在 RAG 里把它当黑盒精排器，额外看：

- [Rerank 接口](/docs/ai-basics/services/rerank)

---

## 4. 源码包地图

RAG 相关源码并不只在一个目录里，而是分散在语义层和存储层：

- `ai4j/src/main/java/io/github/lnyocly/ai4j/rag`
- `ai4j/src/main/java/io/github/lnyocly/ai4j/rag/ingestion`
- `ai4j/src/main/java/io/github/lnyocly/ai4j/vector/store`

可以按下面方式理解：

| 包 | 作用 |
| --- | --- |
| `rag` | 检索增强语义抽象，包含 `RagService / Retriever / Reranker / ContextAssembler / Trace / Evaluator` |
| `rag.ingestion` | 文档入库流水线，负责加载、切片、embedding、写入向量库 |
| `vector.store` | 向量存储统一抽象，如 `VectorStore / VectorRecord / SearchRequest / UpsertRequest` |
| `vector.store.pinecone` | Pinecone 适配 |
| `vector.store.qdrant` | Qdrant 适配 |
| `vector.store.milvus` | Milvus 适配 |
| `vector.store.pgvector` | pgvector 适配 |

这也是为什么官方文档一直强调：

- `RagService` 是上层语义入口
- `VectorStore` 是底层存储入口
- 不要把业务逻辑直接耦合到某一个具体向量库实现

---

## 5. 当前涉及到的核心类

- `IEmbeddingService`
- `Embedding` / `EmbeddingResponse`
- `TikaUtil`
- `RecursiveCharacterTextSplitter`
- `IngestionPipeline`
- `IngestionRequest` / `IngestionResult`
- `DocumentLoader`
- `Chunker`
- `MetadataEnricher`
- `VectorStore`
- `RagService`
- `DenseRetriever`
- `Bm25Retriever`
- `HybridRetriever`
- `Reranker`
- `RagContextAssembler`
- `RagTrace`
- `RagEvaluator`
- `PineconeConfig`
- `PineconeVectorStore`
- `QdrantVectorStore`
- `PgVectorStore`
- `MilvusVectorStore`

其中：

- `PineconeVectorStore / QdrantVectorStore / PgVectorStore / MilvusVectorStore` 是当前推荐的统一存储入口
- `PineconeService` 已视为 Deprecated，更适合作为旧项目兼容或底层专用控制 API，而不是新的主线入口

---

## 6. 推荐阅读顺序

### 6.1 想先搞明白链路怎么设计

先看：

- [RAG 架构、分块与索引设计](/docs/ai-basics/rag/architecture-and-indexing)
- [VectorStore 与存储后端](/docs/ai-basics/rag/vector-store-and-storage-backends)
- [Ingestion Pipeline 文档入库流水线](/docs/ai-basics/rag/ingestion-pipeline)
- [Chunking 策略详解](/docs/ai-basics/rag/chunking-strategies)
- [混合检索与 Rerank 实战工作流](/docs/ai-basics/rag/hybrid-retrieval-and-rerank-workflow)
- [引用、Trace 与前端展示](/docs/ai-basics/rag/citations-trace-and-ui-integration)

### 6.2 想先接代码

先看：

- [Embedding 接口](/docs/ai-basics/services/embedding)
- [VectorStore 与存储后端](/docs/ai-basics/rag/vector-store-and-storage-backends)
- [Ingestion Pipeline 文档入库流水线](/docs/ai-basics/rag/ingestion-pipeline)
- [Chunking 策略详解](/docs/ai-basics/rag/chunking-strategies)
- [混合检索与 Rerank 实战工作流](/docs/ai-basics/rag/hybrid-retrieval-and-rerank-workflow)
- [引用、Trace 与前端展示](/docs/ai-basics/rag/citations-trace-and-ui-integration)
- [Pinecone RAG 工作流](/docs/ai-basics/rag/pinecone-workflow)

如果你只是想理解统一抽象，不必先被具体向量库绑定，优先记住：

- 上层用 `RagService`
- 检索层用 `Retriever`
- 存储层用 `VectorStore`
- 底层才是 `Pinecone / Qdrant / pgvector / Milvus`

检索策略上，目前可以按场景选：

- `DenseRetriever`：默认主线，适合 embedding 语义召回
- `Bm25Retriever`：适合关键词、术语、编号、精确短语
- `HybridRetriever`：把多个检索器结果做融合，适合希望兼顾语义召回与关键词命中

`HybridRetriever` 当前支持多种融合策略：

- `RrfFusionStrategy`：默认策略，按 rank 做融合，适合作为通用基线
- `RsfFusionStrategy`：按每路分数相对归一化后融合
- `DbsfFusionStrategy`：按每路分数分布标准化后融合

要区分两个概念：

- `FusionStrategy`：解决多路召回结果怎么合并
- `Reranker`：解决融合后的候选是否还要做进一步语义精排

所以标准链路是：

- `Dense / BM25 / Hybrid` 负责召回
- `RRF / RSF / DBSF` 负责融合排序
- `Reranker` 负责可选的后置精排

如果你希望直接复用统一模型重排服务，不自己手写 `ModelReranker` 构造，也可以直接用：

- `AiService#getModelReranker(...)`

### 6.3 想排查召回质量

优先检查：

- 分块策略
- 元数据设计
- embedding 模型是否一致
- `topK`
- 稠密检索还是关键词检索是否选对
- prompt 是否限制“仅基于证据回答”

---

## 7. 与 Agent、Flowgram 的关系

RAG 在这里仍然是“基础能力层”。

它可以被：

- `Agent` 消费成工具或上下文来源；
- `Flowgram` 编排成可视化知识问答流程。

但这组文档先只讲 SDK 层的知识库增强链路，不把工作流编排混进来。

---

## 8. 运行时结果与离线评测

要区分两层能力：

- 运行时检索结果：这次搜索到底召回了什么、怎么排的
- 离线评测指标：这套检索策略在标注集上效果怎么样

当前运行时可直接拿到：

- `RagResult.hits`
- `RagHit.rank`
- `RagHit.retrieverSource`
- `RagHit.retrievalScore`
- `RagHit.fusionScore`
- `RagHit.rerankScore`
- `RagHit.scoreDetails`
- `RagResult.trace.retrievedHits`
- `RagResult.trace.rerankedHits`

当前离线评测可通过 `RagEvaluator` 计算：

- `Precision@K`
- `Recall@K`
- `F1@K`
- `MRR`
- `NDCG`

注意：

- `F1` 这类指标依赖标注好的 relevant 集合，不是每次在线检索天然就有
- 在线返回里的 `score` 更适合看排序依据，不适合作为不同检索策略间的绝对可比分数
- `RagEvaluator` 默认按稳定 key 匹配 relevant 集合，优先级依次是：`id -> documentId#chunkIndex -> sourcePath#chunkIndex -> sourceUri#chunkIndex -> sourceName#sectionTitle#chunkIndex -> content`
