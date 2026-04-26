---
sidebar_position: 3
---

# VectorStore 与存储后端

这一页专门讲 `vector.store` 这一层。

在 AI4J 里，很多人会先看到：

- `RagService`
- `IngestionPipeline`
- `DenseRetriever`

但真正把数据落到向量库、再从向量库查回来的底层统一抽象，其实是：

- `VectorStore`

如果这一层边界不清楚，RAG 文档就会只剩“工作流说明”，而很难回答下面这些工程问题：

- 数据集 / namespace / collection 在不同后端里分别对应什么
- 哪些后端支持 metadata filter
- 哪些后端能把原始向量回传出来
- Spring Boot 为什么有的向量库默认就能注入，有的必须 `enabled=true`

---

## 1. `VectorStore` 不是 `RagService`

源码入口：

- `ai4j/src/main/java/io/github/lnyocly/ai4j/vector/store/VectorStore.java`

接口很小，只定义四件事：

- `upsert(VectorUpsertRequest)`
- `search(VectorSearchRequest)`
- `delete(VectorDeleteRequest)`
- `capabilities()`

这代表它只负责“存储与检索接口约定”，不负责：

- query 向量怎么生成
- 召回之后怎么做多路融合
- 上下文怎么拼给模型

这些职责在更上层：

- `IEmbeddingService`
- `Retriever`
- `HybridRetriever`
- `Reranker`
- `RagContextAssembler`

所以要始终区分：

- `VectorStore` 是底层存储边界
- `RagService` 是上层检索增强语义入口

---

## 2. 核心数据对象有哪些

### 2.1 写入

- `VectorRecord`
- `VectorUpsertRequest`

你写入时至少要关心：

- `dataset`
- `records[].id`
- `records[].content`
- `records[].vector`
- `records[].metadata`

### 2.2 查询

- `VectorSearchRequest`
- `VectorSearchResult`

查询时最关键的字段是：

- `dataset`
- `vector`
- `topK`
- `filter`
- `includeMetadata`
- `includeVector`

### 2.3 删除

- `VectorDeleteRequest`

删除可以按三种方式走：

- `ids`
- `filter`
- `deleteAll`

### 2.4 能力自描述

- `VectorStoreCapabilities`

这个对象当前暴露四个能力位：

- `dataset`
- `metadataFilter`
- `deleteByFilter`
- `returnStoredVector`

它的价值不是“花哨功能标记”，而是让上层在接不同后端时知道边界差异。

---

## 3. 当前四个后端是怎么映射的

### 3.1 Pinecone

源码入口：

- `vector.store.pinecone.PineconeVectorStore`

它内部仍复用了旧的 `PineconeService`，但对上层暴露的是新的统一 `VectorStore` 抽象。

`dataset` 在这里对应：

- Pinecone `namespace`

请求行为：

- `upsert` 把 `VectorRecord` 转成 `PineconeVectors`
- `search` 走 `PineconeQuery`
- `delete` 走 `PineconeDelete`

特点：

- 支持 metadata filter
- 支持返回已存向量
- 适合直接托管型云向量库场景

### 3.2 Qdrant

源码入口：

- `vector.store.qdrant.QdrantVectorStore`

`dataset` 在这里对应：

- Qdrant collection 名

Qdrant 这条线还有一个源码级细节：

- 如果配置了 `vectorName`，会自动按 named vector 方式写入和读取

特点：

- 支持 metadata filter
- 支持 delete by filter
- 支持返回已存向量
- 支持 named vector

### 3.3 Milvus

源码入口：

- `vector.store.milvus.MilvusVectorStore`

`dataset` 在这里对应：

- `collectionName`

另外还可能叠加：

- `dbName`
- `partitionName`

也就是说，Milvus 里“数据集”和“物理分区”不是同一个概念：

- `dataset` 是本次请求映射到哪个 collection
- `partitionName` 是配置层补充的固定分区作用域

特点：

- 支持 metadata filter
- 支持 delete by filter
- 默认不返回已存向量
- 结果分数可能由 distance 推导得到

### 3.4 pgvector

源码入口：

- `vector.store.pgvector.PgVectorStore`

这是四个后端里最不一样的一条，因为它不是远端 HTTP 向量库，而是直接走 JDBC。

`dataset` 在这里对应：

- 表中的 `dataset` 列

也就是说，pgvector 默认不是“一库一个集合”的模式，而是“一张表里按 dataset 字段做逻辑隔离”。

特点：

- 支持 metadata filter
- 支持 delete by filter
- 默认不返回已存向量
- 过滤逻辑通过 `jsonb ->> key` 生成 SQL 条件

---

## 4. 当前能力矩阵

以下矩阵直接对应各实现的 `capabilities()` 返回值。

| 后端 | `dataset` | `metadataFilter` | `deleteByFilter` | `returnStoredVector` |
| --- | --- | --- | --- | --- |
| `PineconeVectorStore` | ✅ | ✅ | ✅ | ✅ |
| `QdrantVectorStore` | ✅ | ✅ | ✅ | ✅ |
| `MilvusVectorStore` | ✅ | ✅ | ✅ | ❌ |
| `PgVectorStore` | ✅ | ✅ | ✅ | ❌ |

这一点对上层很重要：

- 如果你的前端或调试链路需要把原始向量也取回来，优先选 Pinecone / Qdrant
- 如果你只需要检索结果和 metadata，Milvus / pgvector 也完全够用

---

## 5. `dataset` 这个字段为什么必须讲清楚

表面上四个后端都要求 `dataset`，但语义并不完全一样：

| 后端 | `dataset` 实际含义 |
| --- | --- |
| Pinecone | `namespace` |
| Qdrant | `collection` |
| Milvus | `collectionName` |
| pgvector | 表里的逻辑分区列 |

所以业务层应该做的不是“把 dataset 当成某家向量库专用概念”，而是把它当成：

- 检索数据域
- 知识库分区
- 业务隔离键

这样后续切后端时，业务语义不会被具体存储产品绑死。

---

## 6. Metadata 过滤在不同后端里怎么落地

虽然四个实现都支持 `metadataFilter`，但底层表达方式不同：

- Pinecone：直接走 filter payload
- Qdrant：转成 `must/match` 结构
- Milvus：转成布尔表达式 / `in` 过滤
- pgvector：转成 `jsonb ->> key = ?` 或 `in (...)`

这就是为什么官方文档一直建议：

- 业务层面向统一 `Map<String, Object> filter`
- 不要直接在业务里手写某个后端的原生过滤 DSL

---

## 7. 与 `IngestionPipeline` 的关系

`IngestionPipeline` 的最后一步就是：

- `VectorStore.upsert(...)`

所以这条链路的依赖方向是：

```text
DocumentLoader / Chunker / Embedding
  -> VectorStore
  -> Retriever / RagService
```

这意味着：

- 你可以先选好 `VectorStore`，再决定上层用 `DenseRetriever` 还是 `HybridRetriever`
- 也可以先把存储层换掉，而不重写整个 RAG 编排层

---

## 8. Spring Boot 下怎么创建这些 Bean

Starter 的当前行为是：

- `PineconeVectorStore`：默认可创建，且可被你自定义 Bean 覆盖
- `QdrantVectorStore`：`ai.vector.qdrant.enabled=true` 时自动创建
- `MilvusVectorStore`：`ai.vector.milvus.enabled=true` 时自动创建
- `PgVectorStore`：`ai.vector.pgvector.enabled=true` 时自动创建

这说明 starter 并没有“强推一种向量库”。

它提供的是：

- Pinecone 的开箱即用入口
- 以及本地 / 自建向量库的条件式装配入口

---

## 9. 工程建议

### 9.1 业务层只依赖 `VectorStore`

不建议：

- 业务代码里直接 new `QdrantVectorStore`
- 查询层直接拼 Qdrant / Milvus 原生 DSL

建议：

- 业务层以 `VectorStore` 为依赖边界
- 需要具体后端特性时，再下沉到配置层或适配层

### 9.2 把 `dataset` 设计成业务概念

例如：

- `tenant-kb`
- `legal-docs`
- `product-handbook`

不要把它命名成：

- `qdrant_collection_xxx`
- `pinecone_namespace_xxx`

否则后续切后端时会连数据域命名都一起污染。

### 9.3 让 metadata 结构稳定

统一约定常见字段，例如：

- `documentId`
- `sourceName`
- `sourcePath`
- `tenant`
- `biz`
- `chunkIndex`

这样后续：

- filter
- trace
- citations
- evaluator

都能稳定工作。

---

## 10. 推荐继续阅读

1. [RAG 与知识库增强总览](/docs/ai-basics/rag/overview)
2. [RAG 架构、分块与索引设计](/docs/ai-basics/rag/architecture-and-indexing)
3. [Ingestion Pipeline 文档入库流水线](/docs/ai-basics/rag/ingestion-pipeline)
4. [Rerank 接口](/docs/ai-basics/services/rerank)
