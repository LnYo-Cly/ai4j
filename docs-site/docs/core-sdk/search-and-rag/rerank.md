# Rerank

`Rerank` 解决的是“候选集已经有了，如何精排”。它不负责生成候选，而负责在候选已经召回之后，把更相关的内容重新排顺序。

如果文档只写一句“它是精排”，其实没有真正讲出 AI4J 是怎么把这件事纳进基座的。

## 1. 核心源码入口

- 抽象接口：`rag/Reranker.java`
- 默认空实现：`rag/NoopReranker.java`
- 模型化实现：`rag/ModelReranker.java`
- 服务工厂入口：`AiService#getModelReranker(...)`
- starter 默认 bean：`AiConfigAutoConfiguration#ragReranker()`

只看这些类，你就能明白 AI4J 的态度：

- rerank 是一等能力
- 但它默认不强绑某一个实现

## 2. 它在 RAG 里的准确位置

一条典型检索链路是：

1. 先 embedding
2. 向量召回候选 `RagHit`
3. `Reranker.rerank(query, hits)`
4. 再交给上下文组装

所以 rerank 的本质是：

- 已有候选的排序层
- 不是第一段召回层
- 也不是最终回答层

## 3. 为什么 starter 默认给 `NoopReranker`

这是一个很工程化的决定。

原因包括：

- rerank 往往需要额外模型调用
- 成本和延迟会明显增加
- 不同项目最优 rerank 策略差异很大

所以 AI4J 先给你一条：

- 能稳定工作
- 不引入额外依赖
- 可以随时替换升级

的基线实现。

## 4. `ModelReranker` 是怎么做的

`ModelReranker` 的做法并不复杂，但很有代表性：

- 把 `RagHit` 转成 `RerankDocument`
- 用 `IRerankService` 调用真正的 rerank 模型
- 按 `RerankResult` 重新排序
- 可选择 `appendRemainingHits`

这说明 AI4J 并没有把 rerank 做成某个 provider 私有逻辑，而是抽成了一个可插拔、可替换的基座能力。

## 5. 什么时候值得引入 rerank

- 候选能召回出来，但排序不稳
- 文档长、chunk 多，前几条经常不是最相关
- 你开始做混合检索，想统一不同召回源的排序

如果你现在连候选都召不出来，那问题多半不在 rerank，而在：

- embedding
- chunking
- vector store
- dataset / metadata 设计

## 6. 设计摘要

> AI4J 把 rerank 抽成了独立的 `Reranker` 能力，并默认给 `NoopReranker` 保证基础链路稳定；需要更高精度时，再用 `ModelReranker` 把 `IRerankService` 挂进来。所以 rerank 在 AI4J 里是可升级的排序层，不是写死的召回黑盒。

## 7. 继续阅读

- [Search and RAG / Hybrid Retrieval](/docs/core-sdk/search-and-rag/hybrid-retrieval)
- [Search and RAG / Vector Store and Backends](/docs/core-sdk/search-and-rag/vector-store-and-backends)
