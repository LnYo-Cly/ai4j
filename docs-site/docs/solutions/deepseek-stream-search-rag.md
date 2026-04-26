# DeepSeek Stream Search RAG

这个案例不是单点能力，而是一条组合应用主线：流式输出、联网搜索、RAG 检索和多轮会话一起工作。

## 1. 适合什么场景

- 需要低等待感的聊天应用
- 既要公网时效信息，又要私域知识
- 想把流式、搜索和知识库串成一个完整产品路径

它更像“应用架构模板”，而不是单个 SDK API 演示。

## 2. 技术链路

这条方案通常会组合：

- 流式输出
- SearXNG 或其他联网增强
- RAG / 向量库检索
- 会话状态管理

真正的难点不在某个单独接口，而在这些层如何按优先级和成本组织起来。

## 3. 推荐上线顺序

比较稳的顺序通常是：

1. 先把流式链路跑稳
2. 再加联网增强
3. 再加 RAG
4. 最后再做 trace、评估和运营指标

## 4. 先补哪些主线页

1. [Core SDK / Model Access / Streaming](/docs/core-sdk/model-access/streaming)
2. [Core SDK / Search & RAG / Online Search](/docs/core-sdk/search-and-rag/online-search)
3. [Core SDK / Search & RAG](/docs/core-sdk/search-and-rag/overview)
4. [Spring Boot / Common Patterns](/docs/spring-boot/common-patterns)

## 5. 深入实现细节

如果你要看控制器骨架、分层建议、上线顺序和评估指标细节，继续看旧实现细节页：

- [旧路径案例页](/docs/guides/deepseek-stream-search-rag)
