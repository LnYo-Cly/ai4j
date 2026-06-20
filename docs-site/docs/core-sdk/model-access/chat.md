# Chat

`Chat` 是 AI4J 当前最成熟、provider 覆盖最广、也最容易先跑通的一条模型访问主线。

但它不是“老接口兼容层”。从实现看，`Chat` 已经同时承载了：

- 本地 function tools
- MCP tools
- 自动 tool loop
- 流式 tool call 聚合
- reasoning 片段聚合
- pass-through runtime 接力

## 1. 关键源码入口

理解 `Chat` 最值得先看的对象是：

- `platform/openai/chat/entity/ChatCompletion.java`
- `platform/openai/chat/entity/ChatMessage.java`
- `platform/openai/chat/entity/Content.java`
- `platform/openai/chat/OpenAiChatService.java`
- `listener/SseListener.java`
- `service/factory/AiService.java`

其中真正定义“Chat 在 AI4J 中是什么”的，不只是请求对象，还包括 `OpenAiChatService` 内部那条自动工具调用循环。

## 2. `ChatCompletion` 到底承载了什么

`ChatCompletion` 的主心智当然是：

- `model`
- `messages`

但当前实现里它还保留了很多运行时级字段：

- `stream`
- `streamOptions`
- `functions`
- `mcpServices`
- `tools`
- `toolChoice`
- `parallelToolCalls`
- `passThroughToolCalls`
- `responseFormat`
- `extraBody`
- `streamExecution`
- `builtInToolContext`

其中一个特别重要的边界是：

- `functions` / `mcpServices` 是本地注册辅助字段
- `tools` 才是最终送给 provider 的实际 tool payload

也就是说，AI4J 在 `Chat` 层已经把“本地能力注册”和“provider payload 组装”分开了。

## 3. 为什么 `Chat` 容易先跑通

`Chat` 的基本输入语义非常稳定：

- 中心对象是 `messages`
- 返回中心通常是 `choice.message`
- 继续对话时直接把新消息拼回会话

这和很多团队已有的 chat-completions 心智几乎同构，所以迁移成本很低。

同时，`AiService.createChatService(...)` 也说明它是当前最广覆盖的主线，支持：

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

## 4. provider 发送前，AI4J 会对请求做什么

以 `OpenAiChatService.chatCompletion(...)` 为例，发送前会显式做这些事情：

1. `ToolUtil.pushBuiltInToolContext(...)`
2. 强制同步模式下 `stream=false`
3. 如果配置了 `functions` 或 `mcpServices`，就调用 `ToolUtil.getAllTools(...)`
4. 把解析出的工具塞到 `chatCompletion.tools`
5. 如果最终没有 tools，就把 `parallelToolCalls` 置空

这说明 `Chat` 不是“把请求对象序列化后原样发出”，而是先在本地运行时完成一次能力展开。

## 5. `Chat` 的一个关键特性：自动 tool loop

这是理解 AI4J `Chat` 和普通 provider SDK 差异的关键。

在同步模式里，`OpenAiChatService.chatCompletion(...)` 内部会循环请求，直到 `finishReason` 不再是：

- `first`
- `tool_calls`

当收到 `tool_calls` 时，如果没有开启 `passThroughToolCalls`，它会：

1. 取出 assistant message 里的 `toolCalls`
2. 把这条 assistant message 回填进 `messages`
3. 对每个 tool call 执行 `ToolUtil.invoke(functionName, arguments)`
4. 把工具输出包装成 `ChatMessage.withTool(...)`
5. 再把这些 tool 输出消息追加到 `messages`
6. 继续下一轮请求

这意味着 `Chat` 在 AI4J 中不是单次 RPC，而是一个可以本地闭环执行工具的对话循环。

## 6. `passThroughToolCalls` 为什么非常关键

`passThroughToolCalls` 决定的是：

- tool call 是让 SDK 直接自动执行
- 还是把控制权交回上层 runtime

同步场景下，如果 `passThroughToolCalls=true`，收到 `tool_calls` 后会直接返回当前 response，而不是继续本地自动执行。

流式场景下，如果 `passThroughToolCalls=true`，`chatCompletionStream(...)` 在拿到流式聚合后的 tool calls 后会直接 `return`，不再继续追加 tool 消息和递归下一轮。

这对 Agent / Coding Agent 很重要，因为上层往往还要做：

- 审批
- trace
- 沙箱执行
- 结果裁剪

## 7. `SseListener` 的真实职责

`SseListener` 不是控制台打印回调，而是 Chat 流式聚合器。

它会维护：

- `output`
- `currStr`
- `currData`
- `currToolName`
- `reasoningOutput`
- `usage`
- `toolCalls`
- `toolCall`
- `finishReason`

并且能同时处理：

- 普通文本 delta
- reasoning 片段
- 完整或碎片化 tool call arguments
- `stop` / `tool_calls` / `[DONE]`

这说明 Chat 流式在 AI4J 里已经是“可供运行时消费的聚合状态”，不是单纯 token 输出。

## 8. 多模态如何进入 Chat

`ChatMessage.withUser(String content, String... images)` 最终会构造成：

- 一段 `text`
- 多个 `image_url`

其底层由 `Content.ofMultiModals(...)` 与 `Content.MultiModal.withMultiModal(...)` 组织。

再往上一层，`ChatMemoryItem.toChatMessage()` 会自动把带图片的 user item 投影成多模态 `ChatMessage`。

这意味着在 AI4J 里，多模态并不是 Chat 之外的独立特殊链路，而是消息内容编码方式的扩展。

## 9. `Chat` 的边界在哪里

虽然 `Chat` 很强，但它的核心心智仍然是：

- message 列表
- 一轮一轮追加上下文
- 在必要时穿插 tool 调用

如果你的需求已经开始强调：

- event 粒度的状态消费
- response item 结构
- function arguments delta 的独立观察
- `previous_response_id` 之类的 response-graph 语义

那就应该认真评估 `Responses`。

## 10. 什么时候优先选 `Chat`

下面这些情况，通常先选 `Chat` 更稳：

- 第一次接 AI4J
- 现有代码就是 chat-completions 心智
- 需要最广 provider 覆盖
- 想先跑通文本 + tool 调用主线
- 上层暂时不需要事件化消费

## 11. 这一页的结论

> AI4J 的 `Chat` 不是薄薄一层请求封装，而是一条成熟的消息式运行链：请求前会解析工具注册，收到 `tool_calls` 时可以自动闭环执行，流式阶段又由 `SseListener` 聚合文本、reasoning 和工具参数。因此它既适合快速接入，也足以支撑中等复杂度的本地 tool runtime。
