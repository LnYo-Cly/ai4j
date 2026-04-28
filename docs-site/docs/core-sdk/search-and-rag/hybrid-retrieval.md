# Hybrid Retrieval

`Hybrid Retrieval` 讲的不是某个魔法开关，而是：**多种召回和排序能力如何组合成更稳定的检索链。**

## 1. AI4J 当前默认给你的是什么

从 `AiService#getRagService(...)` 可以直接看到默认主线：

- `DenseRetriever`
- `NoopReranker`
- `DefaultRagContextAssembler`

这很关键，因为它说明 AI4J 默认先给你一条：

- 稳定
- 易理解
- 容易调试

的 dense retrieval 基线，而不是一开始就塞进很多难以解释的策略。

## 2. 为什么叫 hybrid

在实际系统里，检索通常不只是一段向量召回。

常见组合包括：

- dense retrieval
- metadata filter
- rerank
- online search 增强

AI4J 没把这些都写死成一个黑盒，而是把它们拆成可组合层。这样你能明确知道问题出在哪一层，而不是只能“调一个大开关”。

## 3. 它最大的工程价值是什么

不是“框架替你找到最佳检索策略”，而是：

- 让各层职责清晰
- 让能力可以渐进式增强

例如：

- 候选不准 -> 看 embedding / chunking / vector store
- 候选有了但顺序不稳 -> 看 rerank
- 离线知识不够 -> 看 online search

这才是 hybrid retrieval 真正的价值。

## 4. 设计摘要

> AI4J 的 hybrid retrieval 不是一个写死的黑盒策略，而是把 dense retrieval、metadata filter、rerank、online search、context assembly 拆成可组合层。默认给的是稳定 dense 主线，需要更复杂策略时再逐层叠加。

## 5. 分层组合到底怎么理解

可以把 hybrid retrieval 看成四个逐层叠加的层次：

1. 基础召回层：先拿到候选
2. 过滤层：按 metadata 或 dataset 先缩小范围
3. 排序层：用 rerank 或规则重新排序
4. 组装层：把结果拼成最终上下文

这样分层之后，每一层都能独立观察和替换，而不需要改一个“大检索黑盒”。

## 6. 关键对象

如果你想进一步看代码，可以优先关注：

- `DenseRetriever`
- `rag/Reranker.java`
- `DefaultRagContextAssembler`
- `AiService#getRagService(...)`

这些对象一起定义了 AI4J 当前默认的 hybrid retrieval 基线。

## 7. 继续阅读

- [Search and RAG / Rerank](/docs/core-sdk/search-and-rag/rerank)
- [Search and RAG / Online Search](/docs/core-sdk/search-and-rag/online-search)
