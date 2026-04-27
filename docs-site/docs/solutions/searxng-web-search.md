# SearXNG Web Search

这个方案解决的是“如何给回答链补上一条公网实时搜索能力”，而不是替代私域知识库。

## 1. 适合什么场景

- 需要最新网页信息
- 需要时效强的联网搜索
- 想把公网搜索作为可选增强链

它的定位是“联网搜索增强”，不是 RAG 的替身。

## 2. 核心模块组合

主链通常是：

- `SearXNGConfig`
- `ChatWithWebSearchEnhance`
- 模型回答链
- 可选流式输出

重点是把“搜索结果注入回答链”，而不是自己手工拼接很多网页文本。

## 3. 和 RAG 的边界

- `SearXNG`：公网检索，时效强
- `RAG`：私域知识库检索，边界更可控

如果你要查内部知识库，不要用 SearXNG 替代 RAG。

## 4. 这条方案的价值

- 补齐时效信息
- 适合做可选增强链，而不是默认重路径
- 更容易和已有聊天或 RAG 系统组合

## 5. 先补哪些主线页

1. [Core SDK / Search & RAG](/docs/core-sdk/search-and-rag/overview)
2. [Core SDK / Extension](/docs/core-sdk/extension/overview)
3. [DeepSeek Stream Search RAG](/docs/solutions/deepseek-stream-search-rag)

## 6. 继续看实现细节

如果你要看：

- `SearXNGConfig`
- 超时与降级策略
- 本地部署建议

继续看深页：

- [旧路径案例页](/docs/guides/searxng-web-search)
