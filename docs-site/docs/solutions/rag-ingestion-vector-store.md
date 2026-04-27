# RAG Ingestion Vector Store

这个方案回答的是：当你已经决定做 RAG，第一条最标准、最稳的工程路径是什么。

## 1. 适合什么场景

- 企业知识库问答
- PDF / Word / 网页等文档入库
- 想先把 ingestion、embedding、vector store、检索链打通

这是 RAG 的标准基线，不绑定某个特定向量库品牌。

## 2. 核心模块组合

主链通常是：

- 文档加载 / 清洗 / 分块
- `IngestionPipeline`
- `VectorStore`
- `RagService`
- 可选 `Reranker` / citations / trace

它的重点是建立一条“可演进的标准 RAG 管线”，而不是只做某个厂商 demo。

## 3. 这条方案的优点

- 向量库选择更自由
- 便于后续升级 rerank、citation、trace
- 入库链和检索链清晰分离
- 更适合作为团队的 RAG 基线方案

## 4. 什么时候不够

下面这些情况需要继续细化：

- 已经指定使用 Pinecone
- 需要公网搜索增强
- 需要高证据要求的专业领域方案

这时继续看：

- [Pinecone Vector Workflow](/docs/solutions/pinecone-vector-workflow)
- [SearXNG Web Search](/docs/solutions/searxng-web-search)
- [Legal Assistant](/docs/solutions/legal-assistant)

## 5. 先补哪些主线页

1. [Core SDK / Search & RAG](/docs/core-sdk/search-and-rag/overview)
2. [Core SDK / Package Map](/docs/core-sdk/package-map)
3. [Spring Boot / Bean Extension](/docs/spring-boot/bean-extension)

## 6. 继续看实现细节

如果你要看：

- 完整 ingestion 流水线
- 文档处理器
- 向量库存储样例
- 检索验证与 rerank 升级

继续看深页：

- [旧路径案例页](/docs/guides/rag-ingestion-vector-store)
