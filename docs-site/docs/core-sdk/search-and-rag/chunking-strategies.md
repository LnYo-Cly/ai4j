# Chunking Strategies

分块不是附属细节，而是 RAG 结果质量的重要来源。AI4J 把 chunking 明确抽成独立能力，而不是埋在某个黑盒向量库适配器里。

## 1. 代码锚点

- 核心编排：`rag/ingestion/IngestionPipeline.java`
- 默认 chunker：`RecursiveTextChunker(1000, 200)`
- 结果对象：`RagChunk`

从 `IngestionPipeline` 默认构造可以直接看到，AI4J 不是“只切一刀就完”，而是默认就带了递归分块和 overlap 思路。

## 2. 为什么 chunking 是质量开关

你要同时平衡三件事：

- chunk 太小：上下文断裂，语义不完整
- chunk 太大：召回粒度过粗，rerank 成本上升
- overlap 太弱：边界处的信息容易丢

所以 chunking 不是“预处理小细节”，而是召回质量的第一道闸。

## 3. `RagChunk` 不是只有文本

在 AI4J 里，一个 chunk 通常还会带：

- `chunkId`
- `documentId`
- `chunkIndex`
- `pageNumber`
- `sectionTitle`
- `metadata`

这些字段决定了后续是否能做：

- 引用回溯
- 页面定位
- 章节级解释
- UI 侧证据展示

## 4. 默认策略适合什么

`RecursiveTextChunker(1000, 200)` 更适合作为“先跑通”的默认值，而不是所有文档类型的最优值。

它通常适合：

- 普通技术文档
- FAQ / 帮助中心
- 相对线性的说明文本

如果你处理的是代码、论文、规约、表格文档，往往需要自定义 chunker。

## 5. 在 AI4J 里怎么扩展

`IngestionPipeline` 支持在请求层传入自定义 `Chunker`。这意味着 chunking 不是写死在框架里的，你可以按文档类型替换：

- 章节型分块
- 代码块分块
- 段落 + 标题分块
- 基于页码或 section 的混合分块

## 6. 一条很实用的建议

如果你已经能召回，但引用混乱、答案边界感差、chunk 看起来像“碎片拼接”，先不要急着怪模型，先回来看 chunking。

## 7. 评估 chunking 时应该看什么

如果要判断 chunking 是否合适，至少应观察：

- 召回结果是否经常只命中半句或半段
- 相邻 chunk 是否过度重复
- 引用定位能否回到正确页面和章节
- rerank 结果是否长期被超大 chunk 干扰

这些现象通常比单看“回答像不像”更能暴露 chunking 设计问题。

## 8. 继续阅读

- [Search and RAG / Ingestion Pipeline](/docs/core-sdk/search-and-rag/ingestion-pipeline)
- [Search and RAG / Citations and Trace](/docs/core-sdk/search-and-rag/citations-and-trace)
