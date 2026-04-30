---
sidebar_position: 10
---

# Chat（非流式）

这一页保留旧入口的写法，但内容按当前实现来解释 `IChatService#chatCompletion(...)` 的真实执行链。

如果你想先看主线定位，建议先读：[Model Access / Chat](/docs/core-sdk/model-access/chat)。

## 1. 先给一句工程结论

`Chat` 非流式不是“一次请求换一条文本”的薄封装。

在当前实现里，它实际是一条消息式运行链：

- 入口对象是 `ChatCompletion`
- 中心状态是 `messages`
- 工具注册会在发送前展开成 `tools`
- 收到 `tool_calls` 时，SDK 可以在本地继续执行并自动补下一轮请求

所以一次 `chatCompletion(...)` 调用，底层未必只有一次 provider HTTP 往返。

## 2. 关键源码入口

如果你要从代码理解这条链，最值得先看：

- `service/factory/AiService.java`
- `platform/openai/chat/entity/ChatCompletion.java`
- `platform/openai/chat/entity/ChatMessage.java`
- `platform/openai/chat/OpenAiChatService.java`

`AiService.createChatService(...)` 也说明了 `Chat` 为什么经常被当作默认主线。当前 provider 覆盖包括：

- OpenAI
- Zhipu
- DeepSeek
- Moonshot
- Hunyuan
- Lingyi
- Ollama
- Minimax
- Baichuan
- DashScope
- Doubao

如果你的首要目标是尽快打通、或者优先保留 provider 兼容面，`Chat` 通常比 `Responses` 更稳。

## 3. 一次非流式调用到底会发生什么

以 `OpenAiChatService.chatCompletion(...)` 为例，典型执行顺序是：

1. 读取 `builtInToolContext` 并推入本地运行时上下文。
2. 强制把请求改成同步模式：`stream=false`，`streamOptions=null`。
3. 如果你传了 `functions` 或 `mcpServices`，SDK 会调用 `ToolUtil.getAllTools(...)` 展开成本次请求真正发送给 provider 的 `tools`。
4. 如果最终没有解析出任何工具，`parallelToolCalls` 会被清空，避免向 provider 暗示一个不存在的并行工具面。
5. 进入 `while ("first" || "tool_calls")` 这条本地循环，请求 provider。
6. 如果本轮返回普通 assistant message，直接结束并返回。
7. 如果本轮返回 `tool_calls`，再根据 `passThroughToolCalls` 决定是本地继续执行，还是把控制权交还上层。

这就是为什么“非流式”只描述输出方式，不代表内部没有多轮运行。

## 4. `ChatCompletion` 里哪些字段最值得关注

真正影响运行语义的，不只有 `model` 和 `messages`。

常见关键字段包括：

- `messages`
- `functions`
- `mcpServices`
- `tools`
- `toolChoice`
- `parallelToolCalls`
- `passThroughToolCalls`
- `responseFormat`
- `extraBody`

其中最容易误解的一组边界是：

- `functions` / `mcpServices` 是本地注册辅助字段
- `tools` 才是最终 provider payload 里的工具数组

也就是说，`Chat` 层已经把“本地工具暴露策略”和“最终请求协议”分开了。

## 5. 一个最小但真实的调用写法

```java
IChatService chatService = aiService.getChatService(PlatformType.OPENAI);

ChatCompletion request = ChatCompletion.builder()
        .model("gpt-4o-mini")
        .message(ChatMessage.withSystem("你是一个简洁的 Java 助手"))
        .message(ChatMessage.withUser("用 3 点解释线程池拒绝策略"))
        .build();

ChatCompletionResponse response = chatService.chatCompletion(request);
String text = response.getChoices().get(0).getMessage().getContent().getText();
```

这段代码最适合：

- 普通问答
- 摘要、改写、分类这类只关心最终结果的调用
- 先打通文本主线，再逐步加 tool calling

## 6. 多轮对话时，`messages` 才是状态真相

`Chat` 的核心心智始终是“把当前上下文消息列表发出去”。

如果你自己维护历史，至少要保证：

- 用户消息顺序正确
- assistant 回复被回填
- 如果你启用了 `passThroughToolCalls` 并在上层自己处理工具，还要把 assistant tool call 和 tool output 也正确补回消息列表

手工维护时一般像这样：

```java
List<ChatMessage> history = new ArrayList<ChatMessage>();
history.add(ChatMessage.withSystem("你是代码审查助手"));
history.add(ChatMessage.withUser("请审查这段 SQL"));

ChatCompletion request = ChatCompletion.builder()
        .model("gpt-4o-mini")
        .messages(history)
        .build();
```

如果你不想手工拼这些消息，直接转到 [Memory / Chat Memory](/docs/core-sdk/memory/chat-memory) 会更合适。

## 7. 为什么工具调用会改变非流式的成本模型

默认情况下，`Chat` 的同步调用会在 service 内部做自动 tool loop：

1. 模型先返回 `tool_calls`
2. SDK 用 `ToolUtil.invoke(...)` 执行工具
3. SDK 把 assistant 的 tool call message 和 tool output message 回填进 `messages`
4. SDK 再发下一轮请求，直到 `finishReason` 不再是 `tool_calls`

这带来两个直接后果：

- 你得到的是“闭环后的最终结果”，接入体验简单
- 但延迟和请求次数可能高于表面看到的一次调用

如果你的上层还需要审批、trace、沙箱执行或结果裁剪，就不应让 SDK 静默吞掉这段中间过程，而应该考虑 `passThroughToolCalls=true` 或直接升级到 agent runtime。

## 8. `extraBody` 的边界

`extraBody` 适合补 provider 特有字段，但它不是跨平台语义。

例如：

```java
ChatCompletion request = ChatCompletion.builder()
        .model("gpt-4o-mini")
        .message(ChatMessage.withUser("hello"))
        .extraBody("seed", 2026)
        .build();
```

这样做的代价是：

- 你的请求开始依赖具体 provider 协议
- 将来切换平台时，这些字段很可能失效或语义变化

因此更稳的做法是：把 `extraBody` 看成局部覆写，而不是业务主干字段。

## 9. 常见失败路径和调试抓手

### 9.1 你拿不到最终文本

先分清楚是两种情况：

- provider 真正返回了空内容
- 模型其实停在 `tool_calls`，而你开启了 `passThroughToolCalls`

后一种情况下，返回对象不是坏了，而是 SDK 按设计把工具执行权交回了上层。

### 9.2 工具一直没有触发

先检查：

- `functions(...)` 里的名字是否真的注册过
- `mcpServices(...)` 是否可解析到对应 MCP 工具
- prompt 是否明确要求“先调用工具再回答”

### 9.3 provider 切换后行为变了

先排查：

- 是否依赖了 `extraBody`
- 是否依赖了某个平台专有的 tool-calling 或 reasoning 表达
- 是否把“Chat 覆盖广”误解成“所有 provider 行为完全相同”

## 10. 什么时候不该继续留在这一页

如果你的关注点已经转向：

- 流式聚合和中间状态
- 事件级 reasoning 观察
- `previous_response_id` 这类 response graph 语义

那就不要继续在旧入口里补心智了，直接跳去：

- [Model Access / Streaming](/docs/core-sdk/model-access/streaming)
- [Model Access / Responses](/docs/core-sdk/model-access/responses)
- [Model Access / Chat vs Responses](/docs/core-sdk/model-access/chat-vs-responses)

## 11. 这一页的结论

> AI4J 的 `Chat` 非流式不是单次 RPC 包装，而是一条消息式执行链。发送前会展开本地工具注册，执行中可能经历多轮 `tool_calls -> tool output -> follow-up`，最终再把闭环结果返回给调用方。这也是它既容易快速接入，又足以承载中等复杂度工具调用场景的原因。
