# Vector Store and Backends

这一页讲向量存储后端，不讲模型调用。AI4J 的做法不是让你直接依赖某一个向量数据库 SDK，而是先定义一层统一 `VectorStore` 契约，再把不同后端塞到这个契约后面。

这页如果写浅了，用户会只记住“支持 Pinecone / Qdrant / Milvus / PgVector”，却看不懂真正的抽象价值。

## 1. 统一契约到底是什么

Core SDK 的统一抽象集中在：

- `VectorStore`
- `VectorUpsertRequest`
- `VectorSearchRequest`
- `VectorDeleteRequest`
- `VectorSearchResult`
- `VectorStoreCapabilities`

从请求对象也能直接看出这层设计的重点：

- `dataset`
- `vector`
- `filter`
- `includeMetadata`
- `includeVector`

也就是说，AI4J 抽象的不是“存个向量”这么简单，而是把：

- 写入
- 检索
- metadata 过滤
- 向量回传能力

全部做成了稳定契约。

## 2. 为什么 `dataset` 是核心字段

这一点非常关键。

AI4J 几乎所有后端都围绕 `dataset` 做逻辑边界：

- Pinecone：namespace
- Milvus：collection scope
- PgVector：查询筛选条件
- Qdrant：集合/作用域级访问上下文

所以 `dataset` 不是装饰字段，而是：

- 数据隔离边界
- 检索边界
- 删除边界

如果文档不强调这一点，用户很容易在实际接入场景中把索引和租户治理做乱。

## 3. 当前后端各自代表什么

### Pinecone

源码：`vector/store/pinecone/PineconeVectorStore.java`

特点：

- 更偏 SaaS 向量库
- 以 namespace 承载 `dataset`
- 支持 metadata 过滤和 stored vector 返回

### Qdrant

源码：`vector/store/qdrant/QdrantVectorStore.java`

特点：

- 走 HTTP API
- 支持 payload
- 支持 named vector
- filter 会被转成 Qdrant 的查询表达式

### Milvus

源码：`vector/store/milvus/MilvusVectorStore.java`

特点：

- 以 collection 为核心
- 可叠加 partition / dbName
- 更适合独立向量服务基础设施

### PgVector

源码：`vector/store/pgvector/PgVectorStore.java`

特点：

- 直接基于 PostgreSQL
- metadata 走 `jsonb`
- 距离计算和筛选直接进 SQL

这四个后端不只是“换个驱动”，而是对应四种完全不同的运维和数据基础设施路线。

## 4. `VectorStoreCapabilities` 为什么重要

这是很多人第一次看时会忽略的点。

不同后端不是完全等价的，所以 AI4J 没假装它们 100% 对齐，而是通过 `VectorStoreCapabilities` 显式声明：

- 支不支持 dataset
- 支不支持 metadata filter
- 能不能按 filter 删除
- 能不能返回 stored vector

这让上层 RAG 代码能在统一接口下，仍然知道后端能力差异。

## 5. 和 `IngestionPipeline` 的关系

`IngestionPipeline` 并不关心你后面具体是哪种向量库，它只关心最终有一个 `VectorStore`。

这层解耦非常有价值，因为它意味着：

- 文档加载和 chunking 策略不必绑死后端
- embedding 策略不必绑死后端
- 你可以在不重写入库流水线的前提下替换存储基础设施

这就是基座抽象真正的工程意义。

## 6. Spring Boot 里怎么落地

`AiConfigAutoConfiguration` 已经帮你准备了自动装配：

- `PineconeVectorStore`
- `QdrantVectorStore`
- `MilvusVectorStore`
- `PgVectorStore`

这说明在 starter 视角里，这些后端已经被视为一等 RAG 组件，而不是额外插件。

## 7. 注意事项

### 7.1 以为后端只是“换个连接串”

实际每个后端的元数据、过滤、隔离、运维方式都不一样。

### 7.2 不先想清楚 `dataset`

后面检索和删除边界都会混乱。

### 7.3 把向量库选择建立在“社区热度”上

真正应该看的是你团队现有基础设施：

- 是更偏云 SaaS
- 更偏自管服务
- 还是更偏关系库合并方案

## 8. 设计摘要

> AI4J 用 `VectorStore` 把 Pinecone、Qdrant、Milvus、PgVector 收到统一契约后面，但没有假装它们完全等价，而是通过 `VectorStoreCapabilities` 暴露后端能力差异。`dataset` 在这层是核心边界，不只是一个附加字段。

## 9. 继续阅读

- [Search and RAG / Ingestion Pipeline](/docs/core-sdk/search-and-rag/ingestion-pipeline)
- [Search and RAG / Hybrid Retrieval](/docs/core-sdk/search-and-rag/hybrid-retrieval)
