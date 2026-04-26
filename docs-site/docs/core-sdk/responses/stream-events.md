---
sidebar_position: 21
---

# Responses（流式事件模型）

这是 Responses 与 Chat 最大差异点：Responses 是**事件流**，而不是只吐文本 token。

## 1. 调用方式

```java
ResponseRequest request = ResponseRequest.builder()
        .model("doubao-seed-1-8-251228")
        .input("Describe the Responses API in one sentence")
        .stream(true)
        .build();

ResponseSseListener listener = new ResponseSseListener() {
    @Override
    protected void onEvent() {
        if (!getCurrText().isEmpty()) {
            System.out.print(getCurrText());
        }
    }
};

responsesService.createStream(request, listener);
System.out.println("\nstream finished");
```

## 2. 典型事件类型

常见事件（平台可能有差异）：

- `response.created`
- `response.in_progress`
- `response.output_text.delta`
- `response.reasoning_summary_text.delta`
- `response.function_call_arguments.delta`
- `response.completed`
- `response.failed`
- `response.incomplete`

## 3. `ResponseSseListener` 字段说明

- `getCurrEvent()`：当前事件对象
- `getEvents()`：全部事件列表
- `getCurrText()`：当前文本增量
- `getOutputText()`：累计文本
- `getReasoningSummary()`：累计 reasoning summary
- `getCurrFunctionArguments()`：当前函数参数增量
- `getFunctionArguments()`：累计函数参数
- `getResponse()`：聚合后的 Response 快照

## 4. 为什么看起来“不是 token-by-token”

因为事件粒度由平台决定：

- 有的平台按字输出
- 有的平台按词或短句输出
- 有的平台一次给整段

所以你看到“一句话才刷新一次”不一定是错误，有可能是上游分片策略。

## 5. 终态判定

`OpenAiResponsesService` / `DoubaoResponsesService` 里，以下事件会触发完成：

- `response.completed`
- `response.failed`
- `response.incomplete`

以及 SSE 的 `[DONE]`。

## 6. 参数流观察技巧

如果你在做函数调用排障，建议在回调里打印：

```java
if (!getCurrFunctionArguments().isEmpty()) {
    System.out.println("ARGS DELTA=" + getCurrFunctionArguments());
}
```

这样可以确认参数是模型没生成，还是你只打印了文本增量。

## 7. 常见坑

### 7.1 只看最终 `listener.getResponse()`，误判“流式没输出”

`getResponse()` 是聚合快照，不代表中间增量没来。

### 7.2 控制台缓冲导致晚显示

IDE 控制台可能缓冲，建议：

- 简化输出
- 使用 `System.out.print` + 手动换行
- 对比事件时间戳

### 7.3 流式回调异常中断

在 `onEvent()` 里抛异常会直接中断流式处理，建议回调内部捕获异常。

## 8. 生产建议

- 将事件写入结构化日志（type、sequence、latency、traceId）
- 前端按事件类型渲染，而不是只按文本渲染
- 高价值场景保存 `response.failed` 的 error payload 便于追查

## 9. 与 Agent 的关系

如果你要自动处理“函数参数流 -> 执行工具 -> 再请求模型”，
建议在 Agent runtime 层实现，不要把流程硬写在 Controller。

## 10. 继续阅读

- [Model Access / Streaming](/docs/core-sdk/model-access/streaming)
- [Model Access / Chat vs Responses](/docs/core-sdk/model-access/chat-vs-responses)
