# Legal Assistant

这个方案解决的是“高证据要求的法律助手 RAG”，重点不是把回答说得更像人，而是把依据链做得更可追溯。

## 1. 适合什么场景

- 法规、政策、案例知识库问答
- 需要证据引用的专业助手
- 对版本、出处和审计要求较高的行业场景

它本质上是 RAG 的高约束变体，不适合用“普通聊天 + 长 prompt”直接硬撑。

## 2. 核心模块组合

这条方案通常会组合：

- 文档解析与分块
- `IngestionPipeline`
- `VectorStore`
- `RagService`
- metadata 治理
- citations / trace / evidence output

和普通 RAG 相比，它更强调：

- 元数据完整性
- 版本治理
- 证据优先

## 3. 为什么它是高约束场景

法律场景里，真正重要的通常不是“文案自然”，而是：

- 这段话依据什么得出
- 引用来自哪份文档、哪个版本
- 结果是否允许人工复核与回放

所以检索质量和证据引用，往往比回答表述本身更重要。

## 4. 需要特别注意什么

- 法律场景属于高风险场景
- 输出最好显式带证据来源
- 关键结果应有人工复核流程
- 不要把“检索不到”伪装成“法律上没有”

## 5. 先补哪些主线页

1. [Core SDK / Search & RAG](/docs/core-sdk/search-and-rag/overview)
2. [Core SDK / Citations and Trace](/docs/core-sdk/search-and-rag/citations-and-trace)
3. [RAG Ingestion Vector Store](/docs/solutions/rag-ingestion-vector-store)

## 6. 继续看实现细节

如果你要看：

- 数据流程
- 元数据设计建议
- 证据链组织方式
- 示例伪代码

继续看深页：

- [旧路径案例页](/docs/guides/rag-legal-assistant)
