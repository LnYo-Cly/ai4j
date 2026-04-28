# Chat

`Chat` 是 AI4J 里最成熟、也最容易打通的一条模型调用主线。它不是“低配版接口”，而是消息式模型访问的主入口。

如果你第一次接 AI4J，或者你正在迁移已有 chat-completions 风格代码，`Chat` 通常是最直接的起点。

## 1. 源码入口

最关键的几个类是：

- 服务接口：`service/IChatService.java`
- 请求对象：`platform/openai/chat/entity/ChatCompletion.java`
- 返回对象：`platform/openai/chat/entity/ChatCompletionResponse.java`
- 流式监听：`listener/SseListener.java`
- 工厂入口：`service/factory/AiService.java#getChatService(...)`

从 `AiService#createChatService(...)` 还能直接看出，`Chat` 目前支持的 provider 面非常广，包括 OpenAI、Zhipu、DeepSeek、Moonshot、Hunyuan、Lingyi、Ollama、Minimax、Baichuan、DashScope、Doubao 等。

这就是为什么 `Chat` 特别适合作为通用接入主线。

## 2. 它的调用心智为什么简单

`Chat` 的核心心智就是：

- 输入：`messages`
- 输出：一条 assistant message 或 tool call
- 流式：通过 `SseListener` 逐步消费增量

这和很多团队已有的 Chat Completions 心智几乎是同构的，所以迁移成本低。

## 3. 一个最小示例

```java
ChatCompletion request = ChatCompletion.builder()
        .model("gpt-4o-mini")
        .messages(memory.toChatMessages())
        .build();

ChatCompletionResponse response = chatService.chatCompletion(request);
```

如果你已经用了 `ChatMemory`，那么这条链几乎就是“会话 -> 请求 -> 响应”三步。

## 4. 为什么 `Chat` 不只是文本生成

这是很多文档会写浅的地方。

`ChatCompletion` 除了基础消息字段，还保留了：

- `functions`
- `mcpServices`
- `toolChoice`
- `parallelToolCalls`
- `passThroughToolCalls`
- `responseFormat`
- `streamExecution`

这意味着 `Chat` 在 AI4J 里已经不是“只会收发文本消息”的薄壳，而是能承接：

- 本地 Tool
- MCP Tool
- 流式执行控制
- provider 级 tool-choice 语义

## 5. 工具是怎么进来的

每个 provider 的 chat service 在发送前，会统一调用：

```java
ToolUtil.getAllTools(chatCompletion.getFunctions(), chatCompletion.getMcpServices())
```

然后把结果挂进 provider 请求。

所以 `Chat` 这一层的真正价值不是“某家 provider 支持 function calling”，而是 **AI4J 已经把本地 Tool 和 MCP Tool 的暴露方式统一了**。

## 6. `SseListener` 为什么重要

如果只把 `Chat` 的流式理解成“每次来一点文本就打印”，你就低估它了。

`SseListener` 会累计和暴露：

- `currStr`
- `currData`
- `reasoningOutput`
- `toolCalls`
- `finishReason`

这意味着 `Chat` 的流式链路已经能消费：

- 普通文本增量
- reasoning 片段
- tool call 片段

所以它完全可以作为中等复杂度 runtime 的基础输入面。

## 7. `passThroughToolCalls` 的价值

这是 `Chat` 主线里一个非常关键的字段。

它的含义不是“有没有工具”，而是：

- tool call 是不是要原样保留给上层 runtime 处理

当你做 Agent / Coding Agent 时，这个字段特别重要，因为你往往需要：

- 审批
- trace
- 执行前检查
- 自定义回填

如果没有 `passThroughToolCalls` 这类语义，你就很难把 `Chat` 接到真正的上层 runtime 里。

## 8. 什么时候优先选 `Chat`

- 第一次接 AI4J
- 现有代码就是 chat 风格
- 目标是文本生成 + 基础 tool 调用
- provider 覆盖面比结构化事件更重要

如果你现在还不确定该用 `Chat` 还是 `Responses`，通常先选 `Chat` 更稳。

## 9. 它的边界在哪

`Chat` 非常强，但它仍然更偏消息式接口。

如果你的需求已经开始强调：

- event 粒度消费
- response item 结构
- function argument delta
- runtime 级状态流

那你应该认真看 `Responses`。

## 10. 设计摘要

AI4J 的 `Chat` 不是简单的文本接口，而是覆盖最广的一条消息式模型访问主线。它已经把本地工具、MCP 工具、流式监听和 tool-pass-through 都纳进了统一请求对象，因此适合作为通用接入和增量升级的主线入口。

## 11. 继续阅读

- [Model Access / Responses](/docs/core-sdk/model-access/responses)
- [Model Access / Chat vs Responses](/docs/core-sdk/model-access/chat-vs-responses)
- [Model Access / Streaming](/docs/core-sdk/model-access/streaming)
