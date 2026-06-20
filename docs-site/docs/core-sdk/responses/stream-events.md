---
sidebar_position: 21
---

# Responses（流式事件模型）

这是 `Responses` 和 `Chat` 差异最大的地方。

如果你先看统一抽象，建议直接连读：[Model Access / Streaming](/docs/core-sdk/model-access/streaming)。

## 1. 先给一句工程结论

`Responses` 流式不是“文本 token 流”，而是“事件驱动的 response 生命周期流”。

在当前实现里：

- `createStream(...)` 负责把请求组织成 SSE 调用
- `ResponseSseListener` 负责把离散事件聚合成状态

所以你消费的重点不应该只是“现在屏幕上打印什么字”，而应该是：

- 当前 event 是什么
- 文本形成到哪了
- reasoning 有没有形成
- function arguments 是否闭合
- response 是否已进入终态

## 2. 关键源码入口

建议重点看：

- `platform/openai/response/OpenAiResponsesService.java`
- `listener/ResponseSseListener.java`
- `platform/openai/response/entity/ResponseStreamEvent.java`

其中：

- service 定义了何时补默认流参数、何时结束流
- listener 定义了怎样把 event 序列收敛成可读状态

## 3. 发起流式请求前，service 会先做什么

以 `OpenAiResponsesService.createStream(...)` 为例：

1. 如果 `request.getStream()` 不是 `true`，就强制设为 `true`
2. 如果 `streamOptions` 为空，补一个 `new StreamOptions(true)`
3. 调用 `ResponseRequestToolResolver.resolve(request)` 合并本地 tools
4. 构建 provider payload
5. 通过 `EventSource` 发起流式请求

这说明 `Responses` 流式和非流式的差异主要在输出模式，不在工具收敛和 payload 构建逻辑。

## 4. 一个最小监听器示例

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
```

这里真正重要的不是打印文本，而是明白 `onEvent()` 每次拿到的是“已经被 listener 更新过的当前状态”。

## 5. `ResponseSseListener` 会聚合哪些状态

当前 listener 维护：

- `events`
- `currEvent`
- `response`
- `outputText`
- `reasoningSummary`
- `functionArguments`
- `currText`
- `currFunctionArguments`

这意味着它同时承担了三种角色：

- 完整事件序列缓存
- 当前事件视图
- 最终 response 快照聚合器

因此它更像一个轻量状态机，而不是 callback 打印器。

## 6. 当前实现明确处理了哪些核心事件

从 `ResponseSseListener` 可以直接看到，当前重点聚合这些事件：

- `response.output_text.delta`
- `response.output_text.done`
- `response.reasoning_summary_text.delta`
- `response.reasoning_summary_text.done`
- `response.function_call_arguments.delta`
- `response.function_call_arguments.done`

这些事件分别驱动：

- `outputText`
- `reasoningSummary`
- `functionArguments`

的累积。

所以如果你只读最终 `response`，会错过很多对 runtime 很关键的中间状态。

## 7. 为什么这条流更适合 runtime，而不是 service 内自动闭环

和 `Chat` 最大的不同是：

- `Responses` service 不会在内部帮你收到函数参数后立刻执行工具，再自动发下一轮

它做的只是：

- 把流接起来
- 把事件聚合起来
- 在终态时结束

这给上层留下了更大的编排空间，例如：

- 是否等待完整 reasoning
- 是否在参数闭合前就开始预检查
- 是否把工具执行放到专门的 agent runtime

## 8. 终态判定和 `Chat` 完全不同

`OpenAiResponsesService` 当前把下面几类事件视为终态：

- `response.completed`
- `response.failed`
- `response.incomplete`

另外 SSE 的 `[DONE]` 也会触发完成。

这说明 `Responses` 的结束语义是“response lifecycle 结束”，不是 `finish_reason=stop` 那种消息式终止心智。

## 9. 为什么你看到的刷新粒度不像 token

这里的单位首先是 event，不是 token。

即使是 `response.output_text.delta`，上游也可能按：

- 字
- 词
- 短句
- 整段

发出来。

因此“不是 token-by-token”通常只是 provider 的事件切片策略不同，不是 SDK 流式坏了。

## 10. 什么时候该看 `currText`，什么时候该看 `currFunctionArguments`

### 你在做普通文本 UI

优先看：

- `getCurrText()`
- `getOutputText()`

### 你在排查函数调用或想做工具预览

优先看：

- `getCurrFunctionArguments()`
- `getFunctionArguments()`
- `getCurrEvent().getType()`

这能帮你判断到底是：

- 模型根本没规划工具
- 还是你只消费了文本事件

## 11. 常见失败路径

### 11.1 只看 `getResponse()`，误判“流式没有过程”

`getResponse()` 是聚合快照，不是事件回放本身。

如果你不同时观察 `events` 或 `currText`，自然会以为中间什么都没发生。

### 11.2 流结束了，但只有 `incomplete`

这表示 response 生命周期终止了，但没有形成一个完整可用结果。

这时更应该保留：

- `currEvent`
- `events`
- 错误 payload

而不是只看最终文本为空。

### 11.3 你在回调里把异常抛出去了

回调层异常会直接破坏流式消费，因此高价值场景最好在 `onEvent()` 内自己捕获并记录错误。

## 12. 生产使用时最有价值的日志字段

如果你要把 `Responses` 真正用到 runtime 或前端事件渲染，至少建议记录：

- event `type`
- 时间戳或序号
- `currText`
- `currFunctionArguments`
- 终态类型
- `response` 标识信息

因为很多线上问题不是“模型没回答”，而是：

- reasoning 到了，正文没到
- 参数流到了，工具没执行
- response 进入了 `failed` 或 `incomplete`

## 13. 这一页的结论

> `Responses` 流式是事件生命周期接口，不是文本输出接口。`ResponseSseListener` 会同时聚合事件序列、文本、reasoning、函数参数和最终 response 快照，而 service 本身只负责把流接起来并在终态收口，不会替你自动完成工具闭环。因此它天然更适合 agent runtime、trace 和结构化前端，而不是只打印一段回复。
