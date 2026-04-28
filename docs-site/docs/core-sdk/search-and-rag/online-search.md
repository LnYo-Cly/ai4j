# Online Search

`Online Search` 讲的是联网增强，不是传统离线 RAG 入库。它解决的是：**当内部知识库不够，或者问题强依赖时效性时，怎么把实时外部世界引进来。**

## 1. 核心源码入口

- 包装入口：`AiService#webSearchEnhance(IChatService)`
- 具体实现：`websearch/ChatWithWebSearchEnhance.java`
- 搜索配置：`websearch/searxng/SearXNGConfig.java`
- starter 配置初始化：`AiConfigAutoConfiguration#initSearXNGConfig()`

从这些入口就能看出，AI4J 当前的在线搜索主线不是独立 agent 系统，而是一个**对 `IChatService` 的联网增强包装层**。

## 2. 它到底怎么工作

`ChatWithWebSearchEnhance` 的逻辑很清楚：

1. 读取用户最后一条问题
2. 发起 `SearXNG` 搜索
3. 把搜索结果拼进当前 prompt
4. 再交给底层 `IChatService`

也就是说，它不是传统 RAG 那种“先入库再召回”的链，而是“先实时搜索，再把资料并进本次上下文”。

## 3. 它和传统 RAG 的边界

- 在线搜索：偏实时外部信息
- 离线 RAG：偏内部知识资产

在线搜索适合：

- 新闻、版本更新、公开网页资料
- 需要查外部世界现状的问题

离线 RAG 更适合：

- 企业内部知识库
- 稳定可控语料
- 可审核的资料源

这两条链可以组合，但不能混成一个概念。

## 4. 为什么 AI4J 把它设计成 `IChatService` 包装层

因为在线搜索增强的核心其实不是新增一个 service 面，而是在：

- 发送前补外部资料
- 保持对下层 chat service 的复用

这正是装饰器式设计最自然的地方。你不需要重写一整套 provider 逻辑，就能把联网能力加到现有聊天链里。

## 5. 注意事项

### 5.1 把搜索结果无限制塞进 prompt

这会让上下文迅速变脏、变长。

### 5.2 把在线搜索当成内部知识库替代品

实时搜索的新鲜度高，但稳定性和可控性通常不如你自己的 RAG 语料。

### 5.3 忽略配置

`SearXNGConfig.url` 不对时，这条增强链会直接失败。

## 6. 设计摘要

> AI4J 当前的在线搜索不是独立 RAG 体系，而是通过 `ChatWithWebSearchEnhance` 把 `SearXNG` 结果在请求前并入 `IChatService` 上下文。它更适合实时外部信息增强，而不是替代离线知识库。

## 7. 它如何和离线 RAG 协同

在线搜索和离线 RAG 最常见的协同方式通常是：

- 先用私域知识库回答稳定事实
- 再用在线搜索补实时信息或外部引用
- 对搜索结果做最小必要注入，而不是覆盖私域上下文

这样既能保持内部知识的稳定性，也能避免把时效性问题全都压给离线知识库。

## 8. 关键对象

如果你要继续看实现，优先关注：

- `AiService#webSearchEnhance(IChatService)`
- `websearch/ChatWithWebSearchEnhance.java`
- `websearch/searxng/SearXNGConfig.java`

这组对象已经足够说明 AI4J 的在线搜索为什么是“聊天增强层”，而不是独立知识库主线。

## 9. 继续阅读

- [Search and RAG / Hybrid Retrieval](/docs/core-sdk/search-and-rag/hybrid-retrieval)
- [Model Access / Chat](/docs/core-sdk/model-access/chat)
