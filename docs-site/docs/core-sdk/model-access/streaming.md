# Streaming

这一页只讲流式语义。

在 AI4J 里，“streaming” 不是一个统一的 token 输出概念，而是两条不同主线各自对应的聚合模型：

- `Chat` 流式
- `Responses` 流式

它们都基于 SSE，但消费目标和状态组织方式并不相同。

## 1. `Chat` 流式到底在聚合什么

`Chat` 侧核心对象是 `SseListener`。

它维护的不是一段裸文本，而是一组运行时状态：

- `output`
- `currStr`
- `currData`
- `currToolName`
- `reasoningOutput`
- `usage`
- `toolCalls`
- `toolCall`
- `finishReason`

从这组字段就能看出，AI4J 的 Chat 流式并不只是“token 来了就打印”，而是已经支持同时消费：

- 普通文本 delta
- reasoning content
- 完整或碎片化 tool calls
- usage 汇总

## 2. `Chat` 的流式 tool call 为什么不简单

`SseListener.onEvent(...)` 当前对 tool call 做了专门处理：

- 能识别完整 tool call
- 能识别碎片化 arguments delta
- 能合并同一个 tool call 的多个片段
- 在 `finishReason = tool_calls` 时完成最终聚合

这很关键，因为很多 provider 的流式 tool call 不是一次性给出完整参数，而是分块送达。

AI4J 在 listener 层已经把这件事吸收掉了，上层运行时不必重新自己拼装。

## 3. `Chat` 流式和自动 tool loop 的关系

在 `OpenAiChatService.chatCompletionStream(...)` 中，流式请求结束后会读取：

- `eventSourceListener.getFinishReason()`
- `eventSourceListener.getToolCalls()`

如果 `finishReason == tool_calls` 且没有开启 `passThroughToolCalls`，就会：

1. 把 assistant tool call message 回填到 `messages`
2. 执行 `ToolUtil.invoke(...)`
3. 追加 tool output message
4. 再发起下一轮流式请求

也就是说，Chat 流式不是“单次 SSE 输出”，而可以成为自动工具循环的一部分。

## 4. `Responses` 流式到底在聚合什么

`Responses` 侧核心对象是 `ResponseSseListener`。

它当前会维护：

- `events`
- `currEvent`
- `response`
- `outputText`
- `reasoningSummary`
- `functionArguments`
- `currText`
- `currFunctionArguments`

并通过 event type 驱动聚合：

- `response.output_text.delta`
- `response.output_text.done`
- `response.reasoning_summary_text.delta`
- `response.reasoning_summary_text.done`
- `response.function_call_arguments.delta`
- `response.function_call_arguments.done`

这更像一个事件驱动状态机，而不是消息增量打印器。

## 5. `Responses` 的终止条件和 `Chat` 不一样

在 `OpenAiResponsesService.convertEventSource(...)` 中，`Responses` 流式会在以下终止事件出现时完成：

- `response.completed`
- `response.failed`
- `response.incomplete`

也就是说，`Responses` 判断“流是否结束”的心智更偏 response 生命周期，而不是 `finish_reason`。

相比之下，`Chat` 侧更强调：

- `stop`
- `tool_calls`
- `[DONE]`

这就是两条主线在流式终止语义上的根本差异。

## 6. 为什么上层 runtime 会偏好不同主线

### 偏好 `Chat` streaming 的场景

更适合：

- 直接展示对话输出
- 顺着 message 心智做增量 UI
- 把 tool call 当作对话中的插入步骤

### 偏好 `Responses` streaming 的场景

更适合：

- 事件驱动状态机
- 需要单独观察 reasoning
- 需要单独观察 function arguments 形成过程
- 需要保留完整 event 序列做 trace 或 replay

## 7. 流式不是“打开 `stream=true`”就结束

在 AI4J 里，流式还涉及两类本地运行时控制：

- `streamOptions`
- `streamExecution`

其中 `streamExecution` 会交给 `StreamExecutionSupport.execute(...)` 控制实际 EventSource 的执行方式。

这说明 SDK 不只是把 provider SSE 打开，还给了宿主额外的执行层钩子。

## 8. 调试流式问题时应该先看哪里

### Chat 流式

先看：

- `SseListener.currData`
- `finishReason`
- `toolCalls`
- `reasoningOutput`

### Responses 流式

先看：

- `currEvent`
- `events`
- `outputText`
- `reasoningSummary`
- `functionArguments`

如果一开始就只盯最终字符串，很容易忽略真正的问题其实是工具参数没闭合、reasoning 事件没到齐，或者 response 已进入 `incomplete`。

## 9. 这一页的结论

> AI4J 的 streaming 不是单一“token 流”抽象。`Chat` 流式围绕消息增量、finish reason 和 tool call 聚合组织；`Responses` 流式围绕 event type、response 生命周期和状态闭合组织。两者都走 SSE，但适合完全不同的上层消费方式。
