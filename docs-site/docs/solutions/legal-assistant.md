# Legal Assistant

这个案例解决的是“高证据要求的法律助手 RAG”，重点不是把回答说得更像人，而是把依据链做得更可追溯。

## 1. 适合什么场景

- 法规、政策、案例知识库问答
- 需要证据引用的专业助手
- 对版本、出处和审计要求较高的行业场景

它本质上是 RAG 的高约束变体，不适合用“普通聊天 + 长 prompt”直接硬撑。

## 2. 技术链路

核心组合是：

- 文档解析与分块
- `IngestionPipeline`
- `VectorStore`
- `RagService`
- 元数据治理与证据输出

和普通 RAG 相比，这里更强调：

- 元数据完整性
- 版本治理
- 证据优先

## 3. 需要特别注意什么

- 法律场景属于高风险场景
- 输出最好显式带证据来源
- 关键结果应有人工复核流程

这类场景里，“检索质量”和“证据引用”通常比回答文案更重要。

## 4. 先补哪些主线页

1. [Core SDK / Search & RAG](/docs/core-sdk/search-and-rag/overview)
2. [Core SDK / Citations and Trace](/docs/core-sdk/search-and-rag/citations-and-trace)
3. [RAG Ingestion Vector Store](/docs/solutions/rag-ingestion-vector-store)

## 5. 深入实现细节

如果你要看数据流程、元数据建议和伪代码细节，继续看旧实现细节页：

- [旧路径案例页](/docs/guides/rag-legal-assistant)
