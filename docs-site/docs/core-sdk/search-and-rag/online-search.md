# Online Search

AI4J 当前的 `online search` 不是一套通用检索框架，也不是把网页抓取结果自动接进 `Retriever` 体系。  
从源码看，它其实是一个更具体的东西：

**一个包裹 `IChatService` 的 prompt 增强层。**

这点如果不说透，最容易把它和离线 RAG、MCP 工具、function calling 混在一起。

## 1. 源码入口在哪里

当前核心入口非常明确：

- `websearch/ChatWithWebSearchEnhance.java`
- `websearch/searxng/SearXNGConfig.java`
- `websearch/searxng/SearXNGRequest.java`
- `websearch/searxng/SearXNGResponse.java`

主类定义是：

```java
public class ChatWithWebSearchEnhance implements IChatService
```

这个签名已经说明了它的架构定位：

- 它不是 `Retriever`
- 它不是 `RagService`
- 它不是工具执行器
- 它是 `IChatService` 的包装器

## 2. 它真实是怎样工作的

当前实现非常直接。

无论你走的是：

- `chatCompletion(...)`
- `chatCompletionStream(...)`

它都会先调用：

```java
addWebSearchResults(chatCompletion)
```

然后再把修改后的 `ChatCompletion` 交给底层 `chatService`。

`addWebSearchResults(...)` 的逻辑是：

1. 取最后一条 message 的文本内容
2. 用这段文本作为 query 去做联网搜索
3. 把搜索结果序列化成 JSON 文本
4. 重写最后一条用户消息内容
5. 在新 prompt 里塞入：
   - 一段固定中文指令
   - “网络资料”区块
   - “用户问题”区块

所以 Online Search 在当前 AI4J 里的本质不是“查询 + 检索 + grounding”三段式，而是：

**先搜，再把搜索结果直接拼进最后一条用户 prompt。**

## 3. 为什么说它不是传统 RAG

它和离线 RAG 的差别非常大。

离线 RAG 一般会经过：

- ingest
- chunk
- embed
- retrieve
- rerank
- assemble context

而 `ChatWithWebSearchEnhance` 完全没有这些层：

- 没有 `RagQuery`
- 没有 `RagHit`
- 没有 `Retriever`
- 没有 `Reranker`
- 没有 `RagTrace`

它只做两件事：

- 调外部搜索接口
- 改写聊天请求中的最后一条文本 message

所以这层更准确的定位是：

**freshness augmentation，而不是知识库 RAG。**

## 4. SearXNG 在这条链里扮演什么角色

当前在线搜索直接依赖 `SearXNG`。

`performWebSearch(query)` 会：

1. 从 `Configuration` 里取 `SearXNGConfig`
2. 校验 `searXNGConfig.getUrl()` 非空
3. 用 `OkHttpClient` 发 GET 请求
4. 把 JSON 解析成 `SearXNGResponse`
5. 按 `searXNGConfig.getNums()` 截断结果数量
6. 再把结果整体 `JSON.toJSONString(...)`

这里有一个很重要的工程事实：

**搜索结果在当前实现里不是结构化地进入模型上下文，而是先被整体序列化成 JSON 字符串，再拼进 prompt。**

也就是说，模型最后看到的是“带 JSON 搜索结果的文本提示”，不是一组强类型文档对象。

## 5. 为什么它被设计成 `IChatService` 包装层

从代码看，这个设计的核心优点是低侵入：

- 不用改 provider SDK 主流程
- 不用引入新的 tool 协议
- 同步与流式 chat 都能复用
- 对上层调用方来说，仍然是 `IChatService`

这很适合做“快速把联网搜索接进对话”的增强层。

但这个设计也天然带来边界：

- 只能增强 chat 路径
- 不能天然复用到 `Responses` 风格事件流
- 不能像 RAG 一样保留独立检索命中结构
- 不会产出像 `RagTrace` 那样的中间状态

## 6. 当前实现最值得注意的三个默认行为

### 6.1 它会直接改写原始 `ChatCompletion`

`addWebSearchResults(...)` 不是复制一份 request，而是直接改最后一条 message 内容。

这意味着：

- 如果调用方后面还复用同一个 `ChatCompletion` 对象
- 它看到的已经是“被增强后的 prompt”

这是非常典型的包装器副作用，写文档时必须说透。

### 6.2 它只看最后一条消息

代码直接取：

```java
chatCompletion.getMessages().get(chatLen - 1)
```

所以当前语义是：

- 只用最后一条消息文本做搜索 query
- 不会综合整段会话做搜索 query rewrite

如果你的多轮对话需要“结合上下文搜”，这层默认实现并没有帮你做。

### 6.3 它默认塞的是一段固定中文 instruction

这段 instruction 明确要求模型：

- 根据网络资料和用户问题回答
- 使用 Markdown
- 在回答末尾列出参考资料
- 资料不足时可以根据自身知识补充或说明不确定

这说明 online search 当前不仅是数据增强，还是 **prompt policy 注入**。

## 7. 当前失败路径是什么样的

`performWebSearch(...)` 的失败处理相对保守：

- 没配置 `SearXNG url`，直接抛 `CommonException`
- 请求失败，抛 `CommonException("SearXNG request failed")`
- 解析或网络异常，也统一抛这个错误

这带来的好处是接口表面简单；代价是错误被压平了。  
排障时你往往还得自己补日志，否则很难区分：

- 网络错误
- 上游返回异常
- JSON 解析失败
- 配置错误

## 8. 这一层最真实的安全与质量边界

因为它本质上是把网页搜索结果原样注入 prompt，所以你要非常清楚它和离线知识库的差异：

- 结果新鲜，但稳定性弱
- 数据开放，但噪音和 prompt injection 风险更高
- 没有 chunk 级结构控制
- 没有独立 rerank/trace 机制

也就是说，Online Search 更适合解决：

- “今天的新闻是什么”
- “某个库最近版本改了什么”
- “联网补充最新公开资料”

不适合直接替代：

- 企业内部知识库
- 严格可审计引用系统
- 需要稳定回放的生产 RAG

## 9. 和离线 RAG 最稳的协作方式是什么

如果把这层放进更完整系统里，最稳的角色分工通常是：

- 离线 RAG 负责稳定、结构化、可回溯的内部知识
- Online Search 负责时间敏感的开放网络补充

不要反过来让在线搜索承担主知识源，再拿离线库做点缀。  
因为从当前实现看，online search 的结构化治理能力明显比离线 RAG 弱。

## 10. 最容易踩坑的 5 个点

### 10.1 把它写成“AI4J 的通用搜索子系统”

当前实现只是 `IChatService` 包装器，不是统一搜索框架。

### 10.2 以为它会自动进入 `Retriever`/`Reranker` 链

不会。它没有接入 `RagService` 这条主线。

### 10.3 忽略 request 被原地改写

如果同一个 `ChatCompletion` 对象被复用，这个副作用会直接传播。

### 10.4 把最后一条消息当成完整对话意图

当前搜索 query 只来自最后一条消息文本，多轮语义可能丢失。

### 10.5 以为“列出参考资料”就等于强引用约束

当前只是 prompt 级要求，不是硬约束引用系统。

## 11. 这页最该记住的结论

AI4J 当前的 Online Search，本质上是一个 `IChatService` 级的联网搜索增强包装器：

- 它用最后一条消息做 query
- 调 SearXNG 拉公开网络结果
- 把结果 JSON 直接拼进用户 prompt
- 再交给底层 chat service

它解决的是“让对话具备联网补充能力”，不是“替代离线 RAG 的结构化检索链”。

## 12. 继续阅读

- [Search and RAG 总览](/docs/core-sdk/search-and-rag/overview)
- [Hybrid Retrieval](/docs/core-sdk/search-and-rag/hybrid-retrieval)
- [Citations and Trace](/docs/core-sdk/search-and-rag/citations-and-trace)
