# Citations and Trace

知识增强链路最终不只看“答得像不像”，还要看“能不能解释来源”。在 AI4J 里，引用和 trace 的基础不是回答阶段临时拼出来的，而是 ingest 阶段就保留下来的 identity 和 metadata。

## 1. 引用能力从哪里来

`IngestionPipeline` 在处理文档时会保留：

- `documentId`
- `chunkId`
- `sourceName`
- `sourcePath`
- `sourceUri`
- chunk / document metadata

这些信息会一路进入：

- `VectorRecord`
- `VectorSearchResult`

也就是说，citation 不是回答阶段才“附加”的东西，而是从入库开始就必须保住的证据链。

## 2. 为什么 trace 不能只靠最终回答

如果你最后只有一段文本，却没有：

- chunk identity
- source 信息
- metadata 边界

那你后续几乎做不好：

- 引用回溯
- UI 证据展示
- 排障
- 检索效果解释

所以 AI4J 基座层真正做的是：**把证据链需要的最小骨架保留下来**，上层再决定如何展示和审计。

## 3. 设计摘要

> AI4J 的 citations/trace 不是靠回答阶段临时加工，而是依赖 `IngestionPipeline` 在文档、chunk、metadata 层建立稳定 identity。基座层负责保存证据链骨架，上层 runtime 或 UI 再决定怎么展示和追踪。

## 4. 关键对象

这一页对应的关键对象主要有：

- `rag/RagChunk`
- `vector/entity/VectorRecord`
- `vector/entity/VectorSearchResult`
- `rag/ingestion/IngestionPipeline`

它们串起来的意义在于：文档身份、chunk 身份和检索结果身份是连续保存的，而不是到了回答阶段才第一次出现。

## 5. 这一层真正解决什么

`Citations and Trace` 在基座层解决的是“证据链骨架是否存在”，而不是“最终 UI 长什么样”。

因此这一页最该关注的是：

- identity 是否稳定
- metadata 是否够用
- 召回结果能否回指到原始来源

至于引用卡片怎么画、trace 面板怎么展示，那是上层产品和前端的职责。

## 6. 设计注意事项

如果要把这条证据链长期维护稳定，至少要注意三件事：

- `documentId` 和 `chunkId` 的生成规则不要频繁漂移
- metadata 既要够用，也不要无限膨胀到难以治理
- 检索结果和最终回答之间最好保留明确映射关系

否则即使今天能生成引用，后续版本迁移、重建索引或做审计时也会很痛苦。

## 7. 继续阅读

- [Search and RAG / Ingestion Pipeline](/docs/core-sdk/search-and-rag/ingestion-pipeline)
- [Search and RAG / Chunking Strategies](/docs/core-sdk/search-and-rag/chunking-strategies)
