# SearXNG Web Search

这个案例解决的是“模型本身不带联网，或者你需要更可控的检索增强链路”。

## 1. 适合什么场景

- 需要公网时效信息
- 不想把联网完全交给模型平台黑盒
- 想保留统一 `IChatService` 调用方式

它本质上是“检索增强装饰器”案例，而不是完整 RAG 方案。

## 2. 技术链路

核心组合是：

- `SearXNGConfig`
- `AiService.webSearchEnhance(...)`
- `ChatWithWebSearchEnhance`
- 原始 `IChatService`

也就是说，业务层仍然可以围绕同一套聊天接口工作，只是在调用前插入了联网结果增强。

## 3. 和 RAG 的边界

这里最容易混掉的是：

- `SearXNG`：公网搜索，强调时效
- `RAG`：私域知识库检索，强调资料边界和证据组织

两者可以一起用，但不是一回事。

## 4. 先补哪些主线页

1. [Core SDK / Search & RAG / Online Search](/docs/core-sdk/search-and-rag/online-search)
2. [Core SDK / Search & RAG](/docs/core-sdk/search-and-rag/overview)
3. [DeepSeek Stream Search RAG](/docs/solutions/deepseek-stream-search-rag)

## 5. 深入实现细节

如果你要看非 Spring / Spring 两种配置、增强调用方式和安全建议，继续看旧实现细节页：

- [旧路径案例页](/docs/guides/searxng-web-search)
