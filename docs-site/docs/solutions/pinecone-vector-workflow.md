# Pinecone Vector Workflow

这个案例给的是一条 Pinecone 场景下更推荐的 RAG 基线：统一入库、统一检索，需要时再接重排。

## 1. 适合什么场景

- 你已经确定使用 Pinecone
- 想把 Pinecone 放进统一 `VectorStore` / `RagService` 主线
- 需要从入库到查询保持一套稳定工程模型

如果你还没决定向量库，先读通用 RAG 主线更合适。

## 2. 技术链路

核心组合是：

- `PineconeVectorStore`
- `IngestionPipeline`
- `RagService`
- 可选 `ModelReranker`

它强调的是：即使你选了特定后端，也尽量不要绕开统一抽象。

## 3. 什么时候先不看它

如果你只是想理解 RAG 的共性，不要一上来就从 Pinecone 细节入手。

更合适的顺序是：

- 先读通用 `Search & RAG`
- 再看通用入库主线
- 最后再回到 Pinecone 这种后端特化方案

## 4. 先补哪些主线页

1. [Core SDK / Search & RAG](/docs/core-sdk/search-and-rag/overview)
2. [RAG Ingestion Vector Store](/docs/solutions/rag-ingestion-vector-store)
3. [Core SDK / Vector Store and Backends](/docs/core-sdk/search-and-rag/vector-store-and-backends)

## 5. 深入实现细节

如果你要看 Pinecone 配置、入库流程、查询流程和 rerank 示例，继续看旧实现细节页：

- [旧路径案例页](/docs/guides/pinecone-vector-workflow)
