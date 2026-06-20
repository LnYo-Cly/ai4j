# Responses

`Responses` 是 AI4J 当前更现代、更结构化的一条模型访问主线。

它和 `Chat` 最大的差异，不是字段名从 `messages` 变成 `input`，而是 **它把模型输出首先当成事件和 item 流，而不是单条 assistant message**。

## 1. 关键源码入口

理解 `Responses` 最关键的对象是：

- `platform/openai/response/entity/ResponseRequest.java`
- `platform/openai/response/entity/Response.java`
- `platform/openai/response/OpenAiResponsesService.java`
- `tool/ResponseRequestToolResolver.java`
- `listener/ResponseSseListener.java`
- `service/factory/AiService.java`

其中 `OpenAiResponsesService` 很重要，因为它把“请求对象字段”和“最终 provider payload”之间的映射写得非常清楚。

## 2. `ResponseRequest` 的中心语义是什么

`ResponseRequest` 当前的主字段包括：

- `model`
- `input`
- `instructions`
- `previousResponseId`
- `maxOutputTokens`
- `parallelToolCalls`
- `reasoning`
- `store`
- `stream`
- `streamOptions`
- `text`
- `toolChoice`
- `tools`
- `truncation`
- `user`
- `extraBody`

同时，它也保留了两个本地注册辅助字段：

- `functions`
- `mcpServices`

和 `Chat` 一样，这两个字段不会直接发给 provider；它们只是本地解析工具时的输入。

## 3. provider 发送前会做什么

`OpenAiResponsesService.create(...)` 与 `createStream(...)` 的第一件关键事，都是：

`request = ResponseRequestToolResolver.resolve(request);`

`ResponseRequestToolResolver` 会：

1. 检查 request 中是否存在 `functions` 或 `mcpServices`
2. 如果有，就调用 `ToolUtil.getAllTools(...)`
3. 把解析出的本地 function tools 和 MCP tools 合并进 `request.tools`
4. 返回新的 request

所以 `Responses` 和 `Chat` 并不是两套互不相干的工具体系，而是共享同一条工具解析基座，只是入口不同：

- `Chat` 直接在 chat service 中解析
- `Responses` 先经过 `ResponseRequestToolResolver`

## 4. `Responses` 的 provider 覆盖为什么更聚焦

从 `AiService.createResponsesService(...)` 当前实现看，`Responses` 只覆盖：

- OpenAI
- Doubao
- DashScope

这和 `Chat` 的广覆盖不同。

这说明在 AI4J 当前阶段，`Responses` 更像：

- 结构化能力主线
- runtime 友好主线
- 但 provider 生态仍在收敛中的主线

如果你要优先追求最大 provider 兼容性，通常先看 `Chat`。

## 5. `OpenAiResponsesService` 如何构建最终 payload

`OpenAiResponsesService.buildOpenAiPayload(...)` 当前会显式组装这些字段：

- `model`
- `input`
- `include`
- `instructions`
- `max_output_tokens`
- `metadata`
- `parallel_tool_calls`
- `previous_response_id`
- `reasoning`
- `store`
- `stream`
- `stream_options`
- `temperature`
- `text`
- `tool_choice`
- `tools`
- `top_p`
- `truncation`
- `user`

然后再从 `extraBody` 中补充白名单允许的额外字段。

这有两个重要含义：

1. `ResponseRequest` 不是直接裸序列化后发给 provider
2. SDK 会控制哪些扩展字段可以进入最终 OpenAI payload

这比把 request 原样扔出去更稳定，也更容易调试。

## 6. `Responses` 流式为什么更适合 runtime

`ResponseSseListener` 会维护：

- `events`
- `currEvent`
- `response`
- `outputText`
- `reasoningSummary`
- `functionArguments`
- `currText`
- `currFunctionArguments`

并根据 event type 更新这些聚合状态，例如：

- `response.output_text.delta`
- `response.reasoning_summary_text.delta`
- `response.function_call_arguments.delta`
- `response.completed`
- `response.failed`
- `response.incomplete`

这意味着在 `Responses` 里，流式消费的重点不再只是“当前应该向界面打印哪段字”，而是：

- 当前 response 状态到哪了
- reasoning 有没有形成
- function arguments 是否在逐步成形
- 最终 response 结构有没有闭合

## 7. `Responses` 为什么更适合状态机而不是自动 tool loop

和 `Chat` 不同，当前 `OpenAiResponsesService` 并没有在 service 内部做那种 `while finishReason == tool_calls` 的本地自动循环。

它更偏向于：

- 把工具解析好
- 把 request 发出去
- 把事件和 response 聚合好
- 由上层 runtime 决定后续怎么编排

这就是为什么 `Responses` 更适合：

- agent runtime
- coding runtime
- 复杂交互界面
- 需要精细事件追踪的系统

而不是单纯追求“一次调用内部自动把所有工具跑完”。

## 8. `previousResponseId` 与 `store` 暗示了什么

这两个字段在 `Chat` 主线里没有同等中心位置。

它们说明 `Responses` 更自然地承载：

- 响应链式延续
- provider 侧持久化或追踪语义
- 面向 response graph 的后续操作

这也是为什么它更接近“结构化交互协议”，而不是“消息式问答接口”。

## 9. 什么时候不要急着上 `Responses`

下面这些情况，先用 `Chat` 往往更省成本：

- 只是普通文本问答
- 只是基础 tool calling demo
- 最在意 provider 覆盖而不是事件语义
- 当前上层还没有状态机、trace 或复杂 UI 需求

`Responses` 的价值很高，但它不是所有项目的最短路径。

## 10. 这一页的结论

> AI4J 的 `Responses` 是结构化 response/event 主线，而不是 `Chat` 的重命名版本。它会先通过 `ResponseRequestToolResolver` 把本地工具和 MCP 工具并入请求，再由 `OpenAiResponsesService` 构建 provider payload，并在流式阶段用 `ResponseSseListener` 聚合事件、reasoning 和函数参数。因此它更适合 runtime、trace 和复杂交互，而不是只把模型当作一条文本回复。
