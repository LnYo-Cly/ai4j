# Embedding

`Embedding` 是 AI4J 知识增强链路的起点。只要你开始做 RAG、向量召回或知识库入库，就应该先把这一层理解清楚。

## 1. 它在 Core SDK 里的位置

Embedding 解决的是“把文本统一映射成向量表征”。在 AI4J 里，这一层被统一抽象为：

- `IEmbeddingService`
- `Embedding`
- `EmbeddingResponse`

它既服务于手工向量化，也服务于 `IngestionPipeline` 这样的标准入库链路。

## 2. 代码锚点

- 统一接口：`service/IEmbeddingService.java`
- 工厂入口：`service/factory/AiService.java#getEmbeddingService(...)`
- 多实例入口：`service/factory/AiServiceRegistry.java`、`FreeAiService.java`
- 入库编排：`rag/ingestion/IngestionPipeline.java`

从 `AiService#createEmbeddingService(...)` 可以看出，当前 Core SDK 的 embedding 主线主要支持：

- `OPENAI`
- `OLLAMA`

## 3. 它怎么进入 RAG 链路

`IngestionPipeline` 在构建 `VectorRecord` 之前，会先把 chunk 文本批量送进 `embeddingService.embedding(...)`，默认按批次分段调用。

也就是说，Embedding 不只是一个独立 API，它是：

- chunk -> vector 的转换层
- 检索质量的上游约束
- 不同向量存储后端之间的共同前提

## 4. 你真正该关心什么

- 同一个索引内向量维度必须一致
- embedding 模型最好固定，不要半路混用
- 批量向量化要考虑吞吐、限流和超时

很多 RAG 效果问题看起来像“检索不准”，实际根因是 embedding 模型换了、向量维度漂了，或者入库时混用了多套模型。

## 5. Core SDK 的边界

Core SDK 负责：

- 统一请求 / 返回实体
- provider 差异屏蔽
- 让 `IngestionPipeline`、`RagService`、手工调用共用同一套接口

但它不替你做：

- 文本清洗策略
- chunk 粒度设计
- 召回评测

这些仍然属于知识工程本身。

## 6. 实用建议

- 先确定 embedding 模型，再建索引和数据集。
- 对重复文本做缓存，避免重复向量化。
- 如果后续要更换模型，按“新索引 / 新数据集”重建，不要原地混写。

## 7. 关键对象

如果你要继续从文档进入实现，优先看下面这些入口：

- `service/IEmbeddingService.java`
- `service/factory/AiService.java#getEmbeddingService(...)`
- `rag/ingestion/IngestionPipeline.java`
- `vector/entity/VectorRecord`

它们把“调用 embedding 模型”和“把结果稳定送入向量体系”连接成了一条完整主线。

## 8. 这一层不要替你决定什么

Embedding 是必要前提，但它不会自动替你决定：

- 文本应该怎么切分
- 哪些字段要进入 metadata
- 召回效果该如何评估

把这些责任分清楚，有助于避免把所有 RAG 质量问题都错误归因到 embedding 模型本身。

## 9. 继续阅读

- [Search and RAG / Ingestion Pipeline](/docs/core-sdk/search-and-rag/ingestion-pipeline)
- [Search and RAG / Vector Store and Backends](/docs/core-sdk/search-and-rag/vector-store-and-backends)
