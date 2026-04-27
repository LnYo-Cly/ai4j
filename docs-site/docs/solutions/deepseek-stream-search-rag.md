# DeepSeek Stream Search RAG

这个方案关注的不是单一能力，而是“流式输出 + 联网搜索 + 私域 RAG”三者的组合链路。

## 1. 适合什么场景

- 需要边生成边返回结果
- 既要查公网，也要查私域知识库
- 想做“先搜、再检索、再回答”的增强型回答链

它更像一个组合回答系统，而不是纯聊天或纯 RAG 基线。

## 2. 核心模块组合

主链通常是：

- 流式模型输出
- `ChatWithWebSearchEnhance` 或联网搜索增强链
- `RagService`
- 最终回答拼装

重点不是把所有能力硬塞进一个 prompt，而是明确区分：

- 时效信息来自公网搜索
- 私域知识来自向量检索
- 最终答案再统一组织

## 3. 这条方案的价值

- 公网信息和私域知识可以同时进入回答链
- 流式输出更适合前台交互体验
- 更容易解释“哪部分是搜索得到，哪部分是内部知识得到”

## 4. 什么时候没必要这么重

如果你只有一种需求：

- 只做私域知识库：先看 [RAG Ingestion Vector Store](/docs/solutions/rag-ingestion-vector-store)
- 只做公网搜索增强：先看 [SearXNG Web Search](/docs/solutions/searxng-web-search)

## 5. 先补哪些主线页

1. [Core SDK / Search & RAG](/docs/core-sdk/search-and-rag/overview)
2. [Core SDK / Model Access](/docs/core-sdk/model-access/overview)
3. [SearXNG Web Search](/docs/solutions/searxng-web-search)

## 6. 继续看实现细节

如果你要看：

- 组合代码
- 流式接法
- 搜索与 RAG 的职责拆分

继续看深页：

- [旧路径案例页](/docs/guides/deepseek-stream-search-rag)
