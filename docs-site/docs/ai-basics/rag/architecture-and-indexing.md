---
sidebar_position: 2
---

# RAG 架构、分块与索引设计

大多数 RAG 做不稳，不是模型不够强，而是检索链路本身设计得太粗糙。

在 AI4J 这条链路里，建议把问题拆成四层：

- 数据准备
- 切片与索引
- 召回与过滤
- 生成与引用

---

## 1. 数据准备

当前常见入口是先把原始文件解析成纯文本。

典型工具类：

- `TikaUtil`
- `IngestionPipeline`
- `DocumentLoader`

常见输入包括：

- PDF
- Word
- TXT
- HTML
- 内部导出的结构化文档

这一层的目标不是“解析得完美”，而是尽量保留：

- 标题层级
- 段落边界
- 表格附近语义
- 文档来源信息

---

## 2. 分块策略

当前项目里常见的是：

- `RecursiveCharacterTextSplitter`
- `RecursiveTextChunker`

基础示例：

```java
RecursiveCharacterTextSplitter splitter = new RecursiveCharacterTextSplitter(1000, 200);
List<String> chunks = splitter.splitText(content);
```

如果你不想自己再把“解析 -> 分块 -> metadata -> embedding -> upsert”手动串一次，可以继续看：

- [Ingestion Pipeline 文档入库流水线](/docs/ai-basics/rag/ingestion-pipeline)

建议：

- 技术文档先从 `800~1200` 字符试起；
- 法律、制度、FAQ 这类短段落内容先从 `600~1000` 试起；
- `chunkOverlap` 先取 `10%~25%`。

分块过大，容易召回噪声高。
分块过小，容易丢上下文。

如果你要进一步区分：

- Markdown / FAQ / 制度 / 合同 / 代码 / API 文档分别怎么切
- 什么时候不该继续用纯字符分块
- overlap 到底怎么选

建议继续看：

- [Chunking 策略详解](/docs/ai-basics/rag/chunking-strategies)

---

## 3. 元数据设计

不要只存纯文本。

至少建议保留：

- `content`
- `source`
- `title`
- `section`
- `version`
- `updatedAt`
- `tenant`
- `biz`

原因很直接：

- 召回后要展示来源；
- 过滤时要按租户、业务线、版本做隔离；
- 文档更新后要做重建和回滚。

---

## 4. 索引设计

向量库不是“一个库全塞进去”就结束。

在当前实现里，SDK 上层统一使用逻辑概念：

- `dataset`

底层再映射到不同向量库的真实概念：

- `Pinecone -> namespace`
- `Qdrant -> collection`
- `Milvus -> collection`
- `pgvector -> table + dataset column`

至少要先规划：

- dataset 如何分租户；
- 是否按业务域拆索引；
- 文档更新时如何重建；
- embedding 模型切换时如何迁移。

实践上建议：

- 逻辑 dataset 设计成 `tenant + biz + version`
- embedding 模型固定，避免混维度
- 大版本升级时直接重建索引，不做脏兼容

如果你现在希望把“文档入库”这条链路收口成统一 API，可以优先用：

- `IngestionPipeline`
- `IngestionRequest`
- `IngestionResult`

---

## 5. 召回与过滤

最小链路通常是：

1. 问题做 embedding
2. 向量检索 `topK`
3. 按 metadata 做过滤
4. 拼接上下文
5. 再交给模型回答

如果回答质量差，优先不要先怪模型，先检查：

- 问题有没有被正确向量化；
- 分块是不是太碎或太大；
- `topK` 是否过高；
- metadata 过滤是不是缺失；
- 召回出的证据是否真的能回答问题。

如果你的问题更偏“术语匹配”或“编号命中”，可以把召回层再细分成三类：

- 稠密检索：`DenseRetriever`
- 稀疏关键词检索：`Bm25Retriever`
- 混合检索：`HybridRetriever`

适用建议：

- 产品手册、知识问答、自然语言问题：优先稠密检索
- 制度编号、错误码、接口名、术语表：优先 BM25
- 文档很杂、既要语义又要精确命中：用混合检索

当前内置 `HybridRetriever` 使用轻量级结果融合，不强依赖额外搜索基础设施，适合作为 SDK 内默认可落地的混合方案。

当前支持的融合策略包括：

- `RrfFusionStrategy`
- `RsfFusionStrategy`
- `DbsfFusionStrategy`

建议：

- 默认先用 `RrfFusionStrategy`
- 只有在你确认多路检索分数具备可用的比较价值时，再尝试 `RsfFusionStrategy`
- 如果你想保留 score 信息，又担心不同检索器分数尺度差异过大，可以尝试 `DbsfFusionStrategy`

另外要注意：

- `FusionStrategy` 不是 `Reranker`
- `FusionStrategy` 发生在多路召回结果合并阶段
- `Reranker` 发生在融合之后、送入上下文之前

---

## 6. 可观测性与调优

如果你想把 RAG 做成可调优、可解释的基础设施，而不是“只要能回答就行”，建议重点关注这些字段：

- `rank`
- `retrieverSource`
- `retrievalScore`
- `fusionScore`
- `rerankScore`
- `scoreDetails`
- `trace.retrievedHits`
- `trace.rerankedHits`

用途分别是：

- 看某条结果是第几名
- 看这条命中来自 `dense / bm25 / hybrid`
- 看它在原始召回、融合、重排三个阶段的分数变化
- 看混合检索里每一路结果对最终排序的贡献
- 看 rerank 前后顺序是否真的改善

如果你只看最终 `context`，后面很难回答这些问题：

- 为什么这条会被召回？
- 为什么它排第一？
- 是 dense 起作用还是 bm25 起作用？
- rerank 到底有没有带来收益？

---

## 7. 向量存储接入建议

当前统一抽象是 `VectorStore`，底层可接：

- `Pinecone`
- `Qdrant`
- `Milvus`
- `pgvector`

Spring Boot 配置前缀分别是：

- `ai.vector.pinecone.*`
- `ai.vector.qdrant.*`
- `ai.vector.milvus.*`
- `ai.vector.pgvector.*`

示例：

```yaml
ai:
  vector:
    qdrant:
      enabled: true
      host: http://localhost:6333
      api-key: ""
    pgvector:
      enabled: false
      jdbc-url: jdbc:postgresql://localhost:5432/postgres
      username: postgres
      password: postgres
      table-name: ai4j_vectors
```

补充说明：

- `pgvector` 走 JDBC，消费方应用需要自己带上 PostgreSQL JDBC 驱动；
- `Flowgram` 的 `KnowledgeRetrieve` 默认要求容器里只有一个可用 `VectorStore`，如果你同时注册多个，需要自己指定 `@Primary` 或自定义执行器；
- 上层代码应尽量面向 `VectorStore` / `RagService` 写，不要把业务逻辑写死在某个具体库上。

---

## 8. 生成阶段

召回只是第一步。

生成阶段至少还要约束两件事：

- 明确要求“仅基于给定证据回答”
- 尽量输出引用来源或片段依据

否则很容易出现：

- 明明召回对了，但模型自由发挥
- 看起来像引用了证据，其实并没有严格依赖证据

---

## 9. 评测与排障优先级

建议按这个顺序排障：

1. 文档是否被正确解析
2. 分块是否合理
3. 向量模型是否一致
4. 索引 / dataset 是否正确
5. 稠密 / BM25 / 混合检索策略是否选对
6. 召回结果是否可用
7. prompt 是否限制生成边界

如果第 1 到第 5 步都没稳住，调模型通常不会解决根因。

---

## 10. 继续阅读

1. [Embedding 接口](/docs/ai-basics/services/embedding)
2. [Ingestion Pipeline 文档入库流水线](/docs/ai-basics/rag/ingestion-pipeline)
3. [Chunking 策略详解](/docs/ai-basics/rag/chunking-strategies)
4. [混合检索与 Rerank 实战工作流](/docs/ai-basics/rag/hybrid-retrieval-and-rerank-workflow)
5. [引用、Trace 与前端展示](/docs/ai-basics/rag/citations-trace-and-ui-integration)
6. [Pinecone RAG 工作流](/docs/ai-basics/rag/pinecone-workflow)
