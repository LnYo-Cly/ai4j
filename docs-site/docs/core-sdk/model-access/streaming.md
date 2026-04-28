# Streaming

这一页只讲流式语义。理解 `Chat` 和 `Responses` 各自的流式模型，是把 AI4J 基座真正用顺的关键。

## 1. `Chat` 的流式心智

`Chat` 侧的核心对象是 `SseListener`。它不会只给你一串文本，而是会累计：

- `currStr`
- `currData`
- `reasoningOutput`
- `toolCalls`
- `finishReason`

这说明 `Chat` 的流式不是“控制台打印器”，而是已经具备：

- 文本增量
- reasoning 增量
- tool call 增量

的混合消费能力。

## 2. `Responses` 的流式心智

`Responses` 侧对应 `ResponseSseListener`，它会累计：

- `events`
- `outputText`
- `reasoningSummary`
- `functionArguments`
- 聚合后的 `response`

所以 `Responses` 流式更像一个事件状态机，而不只是文本流。

## 3. 为什么这对上层 runtime 很关键

上层 Agent / Coding runtime 真正关心的往往不是：

- 文本是不是及时显示

而是：

- 参数何时形成
- reasoning 何时出现
- response 何时闭合

这也是为什么：

- 普通对话更适合 `Chat` streaming
- 复杂 runtime 更适合 `Responses` streaming

## 4. 设计摘要

AI4J 的 streaming 不是一个统一的“token 流”概念。`Chat` 更偏消息增量，由 `SseListener` 聚合；`Responses` 更偏事件增量，由 `ResponseSseListener` 聚合。因此不同上层 runtime 会根据状态消费需求选择不同主线。

## 5. 为什么 `Chat` 和 `Responses` 不应该混成一种流式心智

两条流式主线虽然都走 SSE，但消费目标不同：

- `Chat` 更适合围绕消息增量和 tool call 增量做处理
- `Responses` 更适合围绕事件序列和状态闭合做处理

这也是为什么上层 runtime 在设计 UI、trace 和 tool 参数收集时，通常不会把两者完全等价对待。

## 6. 关键对象

这页最核心的代码锚点很集中：

- `listener/SseListener.java`
- `listener/ResponseSseListener.java`
- `ChatCompletion` 流式请求字段
- `ResponseRequest` 流式请求字段

先把这组对象对应上，再去看具体 provider 的流式实现，会更容易理解 AI4J 为什么同时保留 `Chat` 和 `Responses` 两条主线。

## 7. 继续阅读

- [Model Access / Chat](/docs/core-sdk/model-access/chat)
- [Model Access / Responses](/docs/core-sdk/model-access/responses)
