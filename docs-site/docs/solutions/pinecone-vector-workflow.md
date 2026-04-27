# Pinecone Vector Workflow

这个方案回答的是：当你已经确定底层向量库就是 Pinecone，AI4J 里最自然的工作流应该怎么搭。

## 1. 适合什么场景

- 明确使用 Pinecone
- 需要 namespace 级知识隔离
- 想把入库、检索、rerank 串成一条工作流

它是标准 RAG 基线在 Pinecone 场景下的具体落地。

## 2. 核心模块组合

主链通常是：

- `PineconeVectorStore`
- `IngestionPipeline`
- `RagService`
- 可选 `Reranker`
- citations / trace

重点是：

> 面向统一 `VectorStore` / `RagService` 抽象做业务，而不是把逻辑绑死在旧式 `PineconeService` 上。

## 3. 这条方案的优势

- Pinecone 作为托管向量库，上手直接
- namespace 语义适合做知识隔离
- 和统一 RAG 抽象兼容，后续仍可升级 rerank、citation

## 4. 和标准 RAG 的关系

它不是另一套完全不同的架构，而是：

- 标准 RAG 主线
- 在 Pinecone 这个具体后端上的更具体实践

如果你还没确定向量库，先回到：

- [RAG Ingestion Vector Store](/docs/solutions/rag-ingestion-vector-store)

## 5. 先补哪些主线页

1. [Core SDK / Search & RAG](/docs/core-sdk/search-and-rag/overview)
2. [Spring Boot / Auto Configuration](/docs/spring-boot/auto-configuration)
3. [RAG Ingestion Vector Store](/docs/solutions/rag-ingestion-vector-store)

## 6. 继续看实现细节

如果你要看：

- Pinecone 配置
- namespace 策略
- embedding 模型选择
- rerank 组合方式

继续看深页：

- [旧路径案例页](/docs/guides/pinecone-vector-workflow)
