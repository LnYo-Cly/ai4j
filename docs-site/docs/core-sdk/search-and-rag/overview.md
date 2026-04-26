# Search and RAG 总览

这一章讲“模型之外的知识增强能力”。

## 1. 能力范围

`Search & RAG` 在 AI4J 里不是单一功能，而是一条完整能力链：

- 在线搜索
- `Embedding`
- `Rerank`
- 向量存储
- ingestion pipeline
- 检索与引用

如果你要向别人讲 AI4J 的知识增强体系，这一章比单独讲某一个向量库更重要。

## 2. 为什么单独成章

因为它解决的问题不是“发一个模型请求”，而是：

- 模型如何拿到额外知识
- 私域知识库和公网搜索怎么分工
- 检索链路如何进入业务系统
- 结果如何保持引用、trace 与可解释性

## 3. 和相邻能力面的边界

- `Model Access` 解决“怎么调模型”
- `Search & RAG` 解决“模型之外的知识从哪里来”
- `MCP` 解决“外部能力怎么协议化接入”

这三者会协同出现，但不是同一层。

## 4. 推荐阅读顺序

1. [Online Search](/docs/core-sdk/search-and-rag/online-search)
2. [Embedding](/docs/core-sdk/search-and-rag/embedding)
3. [Rerank](/docs/core-sdk/search-and-rag/rerank)
4. [Vector Store and Backends](/docs/core-sdk/search-and-rag/vector-store-and-backends)
5. [Ingestion Pipeline](/docs/core-sdk/search-and-rag/ingestion-pipeline)
6. [Hybrid Retrieval](/docs/core-sdk/search-and-rag/hybrid-retrieval)
7. [Citations and Trace](/docs/core-sdk/search-and-rag/citations-and-trace)
