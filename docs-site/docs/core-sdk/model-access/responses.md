# Responses

`Responses` 是 AI4J 里更现代、更结构化的一条模型访问主线。它不是“另一个名字的 Chat”，而是专门为**事件化输出和 runtime 消费**准备的接口面。

## 1. 源码入口

这一层的关键类有：

- 服务接口：`service/IResponsesService.java`
- 请求对象：`platform/openai/response/entity/ResponseRequest.java`
- 请求工具解析：`tool/ResponseRequestToolResolver.java`
- 流式监听：`listener/ResponseSseListener.java`
- OpenAI 实现：`platform/openai/response/OpenAiResponsesService.java`

从 `AiService#createResponsesService(...)` 也能看出，`Responses` 当前 provider 覆盖比 `Chat` 更聚焦，这本身就说明它更偏“结构化能力线”，不是“万能通用线”。

## 2. 为什么它不是 `Chat` 的重命名

`Chat` 的基本心智是“消息进、消息出”。

`Responses` 的基本心智则是：

- 事件流进程
- output item
- reasoning 片段
- function arguments 增量
- 聚合后的 response 对象

所以它更适合：

- runtime
- structured UI
- 精细化工具调度

而不是只把模型当作字符串生成器。

## 3. 一个最小示例

```java
ResponseRequest request = ResponseRequest.builder()
        .model("gpt-4.1")
        .input(memory.toResponsesInput())
        .build();

Response response = responsesService.create(request);
```

这段看起来和 `Chat` 很像，但真正的区别在于：

- 返回对象结构不同
- 流式事件语义不同
- 工具参数和推理内容的消费方式不同

## 4. 工具是怎么进来的

`ResponseRequest` 里仍然保留：

- `functions`
- `mcpServices`
- `parallelToolCalls`
- `toolChoice`

但发送前不会直接把这些字段原样发给 provider，而是先走：

- `ResponseRequestToolResolver.resolve(request)`

这一步把本地 Tool 和 MCP Tool 白名单解析成真正的 provider `tools`。

所以 `Responses` 不是另一套平行工具体系，而是和 `Chat` 共用同一套基座工具暴露逻辑。

## 5. `ResponseSseListener` 的意义

`ResponseSseListener` 是理解 `Responses` 流式语义的关键组件。

它会累计：

- `events`
- `outputText`
- `reasoningSummary`
- `functionArguments`
- 聚合后的 `response`

这意味着在流式场景下，你并不是只能拿到“屏幕上现在该显示的文本”，而是能拿到**足够构建 runtime 状态机的事件流**。

例如你可以分别处理：

- `response.output_text.delta`
- `response.reasoning_summary_text.delta`
- `response.function_call_arguments.delta`

这对上层 Agent/Coding runtime 非常重要。

## 6. 为什么它更适合做 runtime

当你开始搭复杂运行时，最关心的往往不是“最后回答是什么”，而是：

- 模型什么时候开始推理
- 工具参数怎么逐步成形
- 输出 item 如何组织
- 最终 response 状态怎么聚合

`Responses` 正是为这类需求服务的，所以它天然更适合：

- 工具执行编排
- reasoning 可视化
- 结构化交互界面

## 7. 什么时候不要硬上 `Responses`

如果你的目标只是：

- 先跑通一个 provider
- 做普通多轮文本问答
- 搭一个基础 function-calling demo

那直接从 `Chat` 开始通常更省心。`Responses` 的强大，是有学习成本和心智成本的。

## 8. 设计摘要

AI4J 的 `Responses` 不是 `Chat` 的别名，而是结构化事件主线。请求前会走 `ResponseRequestToolResolver` 统一解析工具，流式阶段由 `ResponseSseListener` 累积 output text、reasoning 和 function arguments，因此更适合承担 runtime 侧的事件消费。

## 9. 继续阅读

- [Model Access / Chat vs Responses](/docs/core-sdk/model-access/chat-vs-responses)
- [Model Access / Streaming](/docs/core-sdk/model-access/streaming)
