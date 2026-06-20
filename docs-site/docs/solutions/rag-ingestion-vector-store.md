# RAG Ingestion Vector Store

这个方案回答的是：当你已经决定做 RAG，第一条标准工程路径应该是什么。

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

## 7. 关键对象

如果你要从方案进一步进入实现，优先看这些对象：

- `rag/ingestion/IngestionPipeline.java`
- `vector/store/VectorStore.java`
- `rag/Reranker.java`
- `rag/RagService`

这四类对象分别对应入库、存储、排序和检索服务主线。

## 8. 这条方案最重要的边界

这条方案真正要先立住的边界有三条：

- ingestion 负责知识如何进入系统，不负责回答生成
- vector store 负责候选存取，不决定最终回答策略
- rerank 和 citations 是在基线之上逐步增强，而不是一开始就强绑定

边界清楚后，后续替换向量库、加入重排或增加证据链时，成本会低很多。

## 9. 落地时优先验证什么

建议优先验证：

1. 文档是否已稳定分块并成功向量化
2. dataset / metadata 是否能支持后续过滤与引用
3. 检索结果是否能稳定回指到原始文档

这三件事比“先把回答修饰得更自然”更重要。
