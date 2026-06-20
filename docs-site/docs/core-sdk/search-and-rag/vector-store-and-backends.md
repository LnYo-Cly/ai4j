# Vector Store and Backends

AI4J 这一层如果只写成“支持 Pinecone / Qdrant / Milvus / PgVector”，信息密度其实很低。  
真正重要的是：**它怎样用统一 `VectorStore` 契约把不同后端收口，同时又不假装这些后端完全等价。**

## 1. 统一契约到底有多大

`VectorStore` 接口本身只有 4 个方法：

```java
int upsert(VectorUpsertRequest request) throws Exception;
List<VectorSearchResult> search(VectorSearchRequest request) throws Exception;
boolean delete(VectorDeleteRequest request) throws Exception;
VectorStoreCapabilities capabilities();
```

这个抽象很克制，但已经覆盖了 RAG 最核心的三件事：

- 向量写入
- 向量检索
- 数据删除

以及一件非常关键的事：

- 后端能力声明

也就是说，AI4J 当前不是靠“文档备注”来告诉你后端差异，而是把差异正式变成了 `capabilities()`。

## 2. `dataset` 在这层不是附属字段，而是硬边界

看请求对象你就会发现：

- `VectorUpsertRequest.dataset`
- `VectorSearchRequest.dataset`

都是主字段，不是可选标签。

更关键的是，4 个当前内置后端都把 `dataset` 当作必填：

- Pinecone：`requiredDataset(...)`
- Qdrant：`requiredDataset(...)`
- Milvus：`requiredDataset(...)`
- PgVector：`requiredDataset(...)`

也就是说，在 AI4J 当前实现里，`dataset` 不是“如果你想分库再填”，而是：

**所有向量写入、检索、删除操作的默认边界。**

## 3. 同一个 `dataset`，在不同后端里具体落到哪里

这恰恰说明统一接口不等于相同语义实现。

### Pinecone

`dataset` 被映射到：

- `namespace`

### Qdrant

`dataset` 被嵌进：

- URL 模板路径

它更像集合级作用域。

### Milvus

`dataset` 会被写到：

- `collectionName`

### PgVector

`dataset` 则被当成：

- 表中的筛选字段 / 查询条件

所以从业务角度看大家都叫 `dataset`，但从存储现实看，它在不同后端对应的是：

- namespace
- collection
- URL scope
- relational filter column

这也是为什么你不能把“切换后端”理解成“换个连接串”。

## 4. `VectorSearchRequest` 真正抽象了哪些检索语义

搜索请求目前有这些关键字段：

- `dataset`
- `vector`
- `topK`，默认 `10`
- `filter`
- `includeMetadata`，默认 `true`
- `includeVector`，默认 `false`

这个设计说明 AI4J 当前的 vector search 抽象，不只是“给个向量查最近邻”，而是已经把：

- 检索边界
- 过滤条件
- 返回内容粒度

一起纳入了统一接口。

返回对象 `VectorSearchResult` 也对应给出：

- `id`
- `score`
- `content`
- `vector`
- `metadata`

这正是 `DenseRetriever` 后面能把结果继续装回 `RagHit` 的前提。

## 5. `capabilities()` 为什么是这层最有价值的设计

4 个当前后端都会显式返回 `VectorStoreCapabilities`。  
而且这不是装饰性信息，里面真的有差异。

当前源码里：

- Pinecone：`dataset=true` `metadataFilter=true` `deleteByFilter=true` `returnStoredVector=true`
- Qdrant：`dataset=true` `metadataFilter=true` `deleteByFilter=true` `returnStoredVector=true`
- Milvus：`dataset=true` `metadataFilter=true` `deleteByFilter=true` `returnStoredVector=false`
- PgVector：`dataset=true` `metadataFilter=true` `deleteByFilter=true` `returnStoredVector=false`

这里最值得注意的是：

**`returnStoredVector` 不是所有后端都支持。**

也就是说，如果你的上层逻辑依赖“检索时顺便把已存向量取回来”，那么当前：

- Pinecone / Qdrant 更自然
- Milvus / PgVector 不是同样语义

这就是 `capabilities()` 的意义：  
统一接口之上，仍然允许你看见真实差异。

## 6. `VectorStore` 和 `DenseRetriever` 的关系是什么

`DenseRetriever` 并不会直接关心你后面是哪种向量库。  
它只做两件事：

1. 用 `IEmbeddingService` 生成 query 向量
2. 调 `vectorStore.search(...)`

这意味着 `VectorStore` 在整条 RAG 链里的位置是：

- 上接 embedding/query 向量
- 下接具体后端
- 输出统一 `VectorSearchResult`

这就是它真正的抽象价值。  
不是为了“把 4 个 SDK 名字收进一个列表”，而是为了把检索链的上下游断开。

## 7. 为什么这一层仍然不能替你隐藏所有后端现实

虽然 `VectorStore` 已经做了统一抽象，但下面这些东西，AI4J 当前并没有帮你完全抹平：

- 后端的运维方式
- 向量列或 collection 的初始化策略
- 维度管理
- 性能特征
- 过滤表达式成本
- SaaS 与自管的部署差异

所以这层 abstraction 的正确理解不是“后端完全同构”，而是：

**主链调用方式统一，但存储现实仍然存在。**

## 8. 当前实现最容易踩的 5 个点

### 8.1 把 `dataset` 当标签用

在当前实现里，它是写入、检索、删除的边界，不是随手可空的 metadata。

### 8.2 忽略 `capabilities()` 差异

尤其 `returnStoredVector`，并不是所有后端都支持。

### 8.3 只从产品名选后端

你真正该看的是：

- 你是 SaaS 优先还是自管优先
- 你要不要借 PostgreSQL 现有基础设施
- 你需不需要取回存量向量

### 8.4 让上层直接依赖具体后端语义

一旦这样写，`VectorStore` 这层抽象就被绕空了。

### 8.5 以为统一接口会自动解决维度与 schema 治理

当前它只统一调用，不替你做数据治理。

## 9. 从当前源码看，最稳的使用建议

在 AI4J 当前架构里，一个很稳的策略是：

1. 先把 `dataset` 设计成稳定边界
2. 用 `VectorStore` 写业务主链
3. 仅在必要时根据 `capabilities()` 分支处理后端差异
4. 不要把后端专有逻辑泄漏进 retriever / ingestion 上层

这样你既能吃到统一接口的好处，又不会被“假等价”误导。

## 10. 这页最该记住的结论

AI4J 当前的向量存储层，本质上是：

- 用 `VectorStore` 统一 upsert / search / delete
- 用 `dataset` 统一知识边界
- 用 `VectorStoreCapabilities` 显式暴露后端差异

它做的是“主链抽象统一”，不是“后端现实消失”。  
把这一层看清楚之后，再去看 ingest、dense retrieval、hybrid，就能知道哪些问题该在向量层解决，哪些不该。

## 11. 继续阅读

- [Embedding](/docs/core-sdk/search-and-rag/embedding)
- [Ingestion Pipeline](/docs/core-sdk/search-and-rag/ingestion-pipeline)
- [Hybrid Retrieval](/docs/core-sdk/search-and-rag/hybrid-retrieval)
