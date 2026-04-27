# RAG Ingestion Vector Store

这个案例给的是一条标准 RAG 入库主线：文档进库、向量化、统一检索，再把证据结果交回模型或上层 runtime。

## 1. 适合什么场景

- 企业知识库
- 文档问答
- FAQ / Wiki / 制度库
- Flowgram 的知识检索节点
- Agent 的外部知识增强

如果你的目标是“先把知识库能力搭稳，再决定上层怎么调用”，这页比单独看某个向量库更有价值。

## 2. 技术链路

核心组合是：

- `IngestionPipeline`
- `VectorStore`
- `RagService`
- `Qdrant / Milvus / pgvector`

这意味着重点不在单个库，而在统一抽象下的入库和查询主线。

## 3. 这页适合先学什么

你应该先建立三件事的关系：

- 文档如何进库
- 查询结果如何保留来源信息
- 这条知识链如何再接到 Agent 或 Flowgram

## 4. 先补哪些主线页

1. [Core SDK / Search & RAG](/docs/core-sdk/search-and-rag/overview)
2. [Core SDK / Ingestion Pipeline](/docs/core-sdk/search-and-rag/ingestion-pipeline)
3. [Core SDK / Vector Store and Backends](/docs/core-sdk/search-and-rag/vector-store-and-backends)

## 5. 深入实现细节

如果你要看完整 `application.yml`、多种向量库配置和示例代码，继续看旧实现细节页：

- [旧路径案例页](/docs/guides/rag-ingestion-vector-store)
