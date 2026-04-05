---
sidebar_position: 11
---

# Chat（流式）

本页聚焦 `IChatService#chatCompletionStream(...)` 与 `SseListener` 的使用细节。

## 1. 核心结论

- Chat 流式是 SSE 增量输出。
- 监听器里最常用字段是 `getCurrStr()`。
- 最终聚合内容可从 `getOutput()` 读取。

## 2. 最小示例

```java
ChatCompletion request = ChatCompletion.builder()
        .model("gpt-4o-mini")
        .message(ChatMessage.withUser("请分 5 点介绍 JVM 内存模型"))
        .stream(true)
        .streamOptions(new StreamOptions(true))
        .build();

SseListener listener = new SseListener() {
    @Override
    protected void send() {
        if (!getCurrStr().isEmpty()) {
            System.out.print(getCurrStr());
        }
    }
};

chatService.chatCompletionStream(request, listener);
System.out.println("\nstream finished");
```

## 3. `SseListener` 重要字段

- `getCurrStr()`：当前增量（文本或工具参数片段）
- `getCurrData()`：当前完整 SSE 数据包原文
- `getOutput()`：累计输出
- `getToolCalls()`：累计工具调用
- `getUsage()`：token 统计（需 `stream_options.include_usage=true`）
- `getFinishReason()`：结束原因（`stop` / `tool_calls` 等）

## 4. 如何区分文本与工具参数增量

在 `SseListener` 中：

- 常规文本：`currToolName` 为空且 `currStr` 为文本
- 工具参数：`currToolName` 不为空，`currStr` 可能是 JSON 参数片段

如果你只想打印文本，可在 `send()` 中加过滤逻辑。

## 5. 为什么看起来不是 token 级输出

SSE 分片粒度由平台决定，不一定“一个 token 一次回调”。
你可能看到按“字、词、短句、整段”输出，都是正常行为。

## 6. 流式 + 工具调用时的行为

当模型返回 `tool_calls` 时，Chat 服务实现会：

1. 收集工具调用参数
2. 调用 `ToolUtil.invoke(...)`
3. 回填 `tool` 消息
4. 自动继续下一轮模型请求

也就是“模型 -> 工具 -> 模型”的闭环是内置完成的。

## 7. 常见问题

### 7.1 只看到最终结果

- 检查是否在 `send()` 里打印 `getCurrStr()`。
- 不要只在结尾打印 `getOutput()`。

### 7.2 流式不结束

- 检查网络层是否被代理/网关中断。
- 检查监听器是否因为异常提前退出。

### 7.3 token usage 始终是 0

- 确认请求中包含 `stream_options.include_usage=true`。

## 8. 生产建议

- 给每次流式请求绑定 `requestId` 与用户标识。
- 对输出做长度上限，避免超长回包压垮前端。
- 前端支持“停止生成”按钮并联动取消请求。

下一页建议阅读：`Chat / Function Call 与 Tool 注册`。
