---
sidebar_position: 11
---

# Chat（流式）

这一页只讲旧入口下的 `Chat` 流式运行链。

如果你想先建立统一心智，建议先读：[Model Access / Streaming](/docs/core-sdk/model-access/streaming)。

## 1. 先给一句工程结论

AI4J 的 `Chat` 流式不是“收到 token 就打印一下”这么简单。

它实际由两部分组成：

- `OpenAiChatService.chatCompletionStream(...)` 负责一轮或多轮流式请求编排
- `SseListener` 负责把 SSE 事件聚合成可消费的运行时状态

所以你监听到的不是裸 token，而是文本、reasoning、tool call 参数和结束语义的组合结果。

## 2. 关键源码入口

建议重点看：

- `platform/openai/chat/OpenAiChatService.java`
- `listener/SseListener.java`

如果你在排查 provider 差异，也要注意同样的 `passThroughToolCalls` 和 follow-up loop 已经出现在多家 chat provider 实现里，而不是 OpenAI 独有逻辑。

## 3. 流式调用前，SDK 会先改写什么

以 `OpenAiChatService.chatCompletionStream(...)` 为例，发送前会先做这些事情：

1. 强制 `stream=true`
2. 如果你没传 `streamOptions`，默认补一个 `new StreamOptions(true)`
3. 如果传了 `functions` 或 `mcpServices`，就调用 `ToolUtil.getAllTools(...)` 展开工具
4. 把展开结果写入 `tools`
5. 如果没有工具，则清掉 `parallelToolCalls`

所以“流式”只是输出模式；请求在发出去之前，仍然会先经过一次本地运行时整理。

## 4. 一个最小监听器示例

```java
ChatCompletion request = ChatCompletion.builder()
        .model("gpt-4o-mini")
        .message(ChatMessage.withUser("请分 5 点介绍 JVM 内存模型"))
        .stream(true)
        .build();

SseListener listener = new SseListener() {
    @Override
    protected void send() {
        if (!getCurrStr().isEmpty() && getCurrToolName().isEmpty()) {
            System.out.print(getCurrStr());
        }
    }
};

chatService.chatCompletionStream(request, listener);
```

这里最值得注意的是：`SseListener` 的回调入口是 `send()`，不是“收到一个 event 就原样抛给你”。

## 5. `SseListener` 真正聚合了哪些状态

`SseListener` 当前会维护：

- `currStr`
- `currData`
- `currToolName`
- `output`
- `reasoningOutput`
- `usage`
- `toolCalls`
- `finishReason`

这组字段背后的语义分别是：

- `currStr`：本次回调可消费的当前片段
- `currData`：原始 SSE 数据包，排查 provider 行为时最有用
- `currToolName`：当前是否正在累积某个 tool call 的参数
- `output`：累计文本输出
- `reasoningOutput`：累计 reasoning 片段
- `toolCalls`：已聚合完成的工具调用列表
- `finishReason`：本轮流的终止类型

如果你只盯 `currStr`，很容易把流式误判成“只是打印器”。

## 6. 为什么流式 tool call 比看起来复杂

很多 provider 的工具调用参数并不是一次给完整 JSON，而是分多段 delta 送达。

`SseListener` 已经在内部处理了这些问题：

- 能识别完整 tool call
- 能识别碎片化 `arguments`
- 能按 `id` 或函数名把多个片段归并到同一个 tool call
- 在 `finishReason=tool_calls` 时完成最终闭合

这也是为什么你不应该自己只靠字符串拼接去理解工具参数流，除非你明确在做更底层的协议调试。

## 7. 一次 `chatCompletionStream(...)` 可能不止一轮 SSE

这是最容易被忽略的地方。

在 `OpenAiChatService.chatCompletionStream(...)` 内部，外层仍然是：

- `while ("first".equals(finishReason) || "tool_calls".equals(finishReason))`

这意味着：

- 第一轮流结束后，如果结果是 `tool_calls`
- 且你没有开启 `passThroughToolCalls`

SDK 会：

1. 取出 `SseListener` 已聚合好的 `toolCalls`
2. 追加 assistant 的 tool call message
3. 执行 `ToolUtil.invoke(...)`
4. 追加 tool output message
5. 再发起下一轮流式请求

所以一次表面的流式调用，底层可能是“流一轮 -> 本地跑工具 -> 再流一轮”。

## 8. `passThroughToolCalls` 会改变什么

`passThroughToolCalls=true` 的含义不是“禁用工具”，而是：

- 工具规划仍然由模型完成
- 但工具执行权不再由 chat service 静默接管

在流式场景下，这会直接改变控制流：

- 如果 `finishReason=tool_calls` 且 listener 已聚合出工具列表
- service 会直接 `return`
- 不再自动回填 tool output 并继续下一轮流

这对 agent、coding runtime、审批流和沙箱执行尤其重要。

## 9. 为什么你看到的输出不一定是 token 级

SSE 的分片粒度由 provider 决定，不是 AI4J 自己决定。

你可能会看到：

- 按字
- 按词
- 按短句
- 按整段

都属于正常现象。

因此“不是 token-by-token”通常不表示 SDK 异常，而是上游的分块策略本来就不同。

## 10. 调试时最该先看什么

### 只想看文本

优先看：

- `getCurrStr()`
- `getOutput()`

同时过滤掉 `currToolName` 非空的场景。

### 怀疑工具参数没拼好

优先看：

- `getCurrData()`
- `getToolCalls()`
- `getFinishReason()`

必要时打开 `showToolArgs=true`，让 `send()` 能收到参数片段。

### 怀疑 provider 在推理而不是输出正文

优先看：

- `isReasoning()`
- `getReasoningOutput()`

## 11. 常见失败路径

### 11.1 你只在最后拿到结果

通常是因为：

- 你只在结束后读了 `getOutput()`
- `send()` 里没有消费 `currStr`

### 11.2 流结束了，但没有正文

先判断是否其实停在：

- `finishReason=tool_calls`
- 或者 provider 只返回了 reasoning / 工具参数

### 11.3 usage 一直为空

只有当 provider 支持并返回 usage，且流末尾带 usage 包时，`SseListener` 才能累积到统计值。

## 12. 这一页的结论

> AI4J 的 `Chat` 流式是消息式聚合器，不是简单的 token 输出接口。`SseListener` 会同时聚合文本、reasoning、工具参数和 finish reason，而 `chatCompletionStream(...)` 在需要时还会把多轮 `tool_calls -> tool output -> follow-up` 串成一条完整运行链。因此它适合实时展示，但也足以承担较复杂的本地工具闭环。
