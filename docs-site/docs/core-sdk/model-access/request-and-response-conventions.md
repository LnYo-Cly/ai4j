# Request and Response Conventions

这一页统一解释：**在 AI4J 里，请求应该怎么构造，返回应该怎么读，哪些字段是 SDK 本地语义，哪些字段才会真正进入 provider payload。**

很多接入问题看起来像 provider 不稳定，实际上只是没有建立统一的请求/返回约定。

## 1. AI4J 的基本策略是什么

AI4J 当前在模型访问层采用的是一种很明确的策略：

1. 用统一请求对象承载主语义
2. 用本地辅助字段承载工具注册和运行时上下文
3. 在 provider service 层显式构造最终 payload
4. 用 listener 或 response entity 聚合返回结果

这套策略在两条主线上都能看到：

- `ChatCompletion`
- `ResponseRequest`

## 2. 哪些字段是“本地注册语义”，不是 provider 原生字段

以 `ChatCompletion` 为例，下面这些字段在 AI4J 中很重要，但并不意味着它们会原样发送给 provider：

- `functions`
- `mcpServices`
- `builtInToolContext`
- `streamExecution`
- `passThroughToolCalls`

对应到 `ResponseRequest`，也有类似情况：

- `functions`
- `mcpServices`
- `streamExecution`

这些字段的作用是：

- 帮 SDK 在本地先解析工具和执行策略
- 而不是直接成为 provider JSON payload

如果把这层边界搞混，业务层就很容易误以为“请求对象里写了就一定已经发给模型了”。

## 3. 最终 payload 是在哪里确定的

### Chat

`OpenAiChatService` 发送前会：

- 先用 `ToolUtil.getAllTools(...)` 展开 `functions` / `mcpServices`
- 再把结果写进 `chatCompletion.tools`
- 视情况清空 `parallelToolCalls`
- 最后序列化整个 `ChatCompletion`

### Responses

`OpenAiResponsesService` 则更显式：

1. 先 `ResponseRequestToolResolver.resolve(request)`
2. 再调用 `buildOpenAiPayload(request)`
3. 只把允许字段放进最终 `payload`
4. 再从 `extraBody` 中补允许的扩展字段

这说明 AI4J 并不是“请求对象怎么长，provider 就收什么”，而是在 service 层有一层正式的投影。

## 4. `extraBody` 的角色到底是什么

`ChatCompletion` 和 `ResponseRequest` 都提供了 `extraBody`。

它的定位不是让业务层随意绕过 SDK，而是：

- 在不破坏主语义建模的前提下
- 给 provider 特定扩展字段留一个正式出口

尤其在 `OpenAiResponsesService` 中，`extraBody` 还会经过允许字段过滤后才进入 payload。

所以正确用法应该是：

- 主语义优先走正式字段
- provider 特殊扩展才放 `extraBody`

而不是把所有东西都一股脑堆进 `extraBody`。

## 5. 返回值读取为什么也要有统一约定

如果没有统一约定，最常见的坏结果是：

- 不同调用点各自解析 provider 原始 JSON
- 同步和流式结果混着读
- `Chat` 和 `Responses` 返回对象被业务层当成同一种结构

AI4J 当前给出的推荐读取方式其实很清晰：

### Chat 非流式

优先围绕：

- `ChatCompletionResponse`
- `Choice`
- `ChatMessage`
- `Usage`

### Chat 流式

优先围绕：

- `SseListener.output`
- `reasoningOutput`
- `toolCalls`
- `finishReason`

### Responses 非流式

优先围绕：

- `Response`

### Responses 流式

优先围绕：

- `ResponseSseListener.events`
- `outputText`
- `reasoningSummary`
- `functionArguments`
- `response`

## 6. 一个很实用的团队规则

建议统一这条规则：

1. 业务层先读统一实体
2. provider 特有细节只在适配层或极少数边界层读取
3. 不要在业务层到处手搓原始 JSON 路径

这不是“写法更优雅”的问题，而是为了让 SDK 演进时不把 provider 差异扩散到整条调用链。

## 7. 常见误区

### 误区一：把 `functions` 当成最终 provider 字段

它只是本地工具注册输入，最终真正送给 provider 的是解析后的 `tools`。

### 误区二：把流式 listener 当作打印回调

实际上它们是聚合器，里面已经有大量结构化状态。

### 误区三：业务层直接依赖 provider 原始 JSON 结构

短期灵活，长期会让所有适配成本泄漏到业务层。

### 误区四：`extraBody` 变成主通道

一旦所有字段都走 `extraBody`，统一请求对象就失去意义了。

## 8. 这一页的结论

> AI4J 在请求/返回层的核心约定是：统一实体承载主语义，本地辅助字段承载注册与运行时语义，provider service 负责把它们投影成最终 payload，而 listener/response entity 负责把返回重新聚合成稳定读取面。理解这层约定，才能避免把 provider 差异和本地运行时语义混在一起。
