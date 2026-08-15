---
title: Responses 主线
description: 解析 AI4J Responses 主线的 ResponseRequest 语义、工具解析基座、payload 构建、流式事件聚合与 runtime 友好的运行模型。
tags: [concept]
---

# Responses 主线
`Responses` 是 AI4J 当前更现代、更结构化的一条模型访问主线。

它和 `Chat` 最大的差异，不是字段名从 `messages` 变成 `input`，而是 **它把模型输出首先当成事件和 item 流，而不是单条 assistant message**。

:::tip 本页代码都是可跑通的
下面每段 Java 示例都来自仓库里的可执行测试
[`ResponsesDocExamplesLiveTest`](https://github.com/LnYo-Cly/ai4j/blob/main/ai4j/src/test/java/io/github/lnyocly/ai4j/docs/ResponsesDocExamplesLiveTest.java)，
已针对真实 OpenAI 兼容网关跑通。本地复跑：

```bash
export OPENAI_API_KEY=sk-...
export OPENAI_API_HOST=https://your-gateway/   # 可选
export OPENAI_CHAT_MODEL=gpt-4o-mini           # 可选

mvn -pl ai4j test -Plive-provider-tests -Dtest=ResponsesDocExamplesLiveTest
```

没有 `OPENAI_API_KEY` 时这些测试会自动跳过，不会让构建失败。
:::

## 0. 先跑起来

最小可用调用。注意 Responses 的输出是 **item 列表**，每个 item 带 content parts——读取助手文本要走这个结构，不像 Chat 直接读 `choice.message.content`：

```java
OpenAiConfig openAiConfig = new OpenAiConfig();
openAiConfig.setApiKey(System.getenv("OPENAI_API_KEY"));
openAiConfig.setApiHost("https://api.openai.com/");   // 换成你的网关地址

Configuration configuration = new Configuration();
configuration.setOpenAiConfig(openAiConfig);

IResponsesService responsesService = new AiService(configuration)
        .getResponsesService(PlatformType.OPENAI);

ResponseRequest request = ResponseRequest.builder()
        .model("gpt-4o-mini")
        .input("用一句话解释什么是响应式编程")
        .build();

Response response = responsesService.create(request);

// Responses 的输出是 item 列表：遍历 output，每个 item 带 content parts
String answer = "";
for (ResponseItem item : response.getOutput()) {
    if (item.getContent() == null) continue;
    for (ResponseContentPart part : item.getContent()) {
        if (part.getText() != null) answer += part.getText();
    }
}
System.out.println(answer);

System.out.println("input=" + response.getUsage().getInputTokens()
        + " output=" + response.getUsage().getOutputTokens()
        + " total=" + response.getUsage().getTotalTokens());
```

`response.getStatus()` 通常为 `"completed"`，`getObject()` 为 `"response"`。

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

`instructions` 相当于系统级指令，`maxOutputTokens` 限制输出长度：

```java
Response response = responsesService.create(ResponseRequest.builder()
        .model("gpt-4o-mini")
        .instructions("你只能用中文回答，且不超过 20 个字。")
        .input("What is a vector database?")
        .maxOutputTokens(200)
        .build());
```

## 3. provider 发送前会做什么

`OpenAiResponsesService.create(...)` 与 `createStream(...)` 的第一件关键事，都是：

`request = ResponseRequestToolResolver.resolve(request);`

`ResponseRequestToolResolver` 会：

1. 检查 request 中是否存在 `functions` 或 `mcpServices`
2. 如果有，就调用 `ToolUtil.getAllTools(...)`
3. 把解析出的本地 function tools 和 MCP tools **投影**进 `request.tools`
4. 返回新的 request

所以 `Responses` 和 `Chat` 并不是两套互不相干的工具体系，而是共享同一条工具解析基座，只是入口不同：

- `Chat` 直接在 chat service 中解析
- `Responses` 先经过 `ResponseRequestToolResolver`

:::warning Responses 的 function tool 形状和 Chat 不同
Chat Completions 把函数声明**嵌套**在 `function` 键下，而 Responses API 要求它们**扁平**（`type` / `name` / `description` / `parameters` 在顶层）。`ResponseRequestToolResolver` 会自动做这层投影——所以你用同一套 `@FunctionCall` 注册，两条线都能正确发出。

```java
@FunctionCall(name = "getStockPrice", description = "查询股票当前价格")
public static class GetStockPrice implements Function<GetStockPrice.Request, String> {
    @Data @FunctionRequest
    public static class Request {
        @FunctionParameter(description = "股票代码，例如 AAPL")
        private String symbol;
    }

    @Override
    public String apply(Request request) {
        return "{\"symbol\":\"" + request.getSymbol() + "\",\"price\":195.42}";
    }
}

// Responses 主线：functions(...) 注册，SDK 自动投影成扁平形状
ResponseRequest request = ResponseRequest.builder()
        .model("gpt-4o-mini")
        .input("AAPL 现在多少钱？")
        .functions("getStockPrice")
        .build();

Response response = responsesService.create(request);
```

与 `Chat` 的自动 tool loop 不同，`Responses` **不做**服务内自动循环：`output[]` 里出现的 `function_call` item 会原样交回上层，由你的 runtime 决定是否执行、如何回填 `function_call_output`。

需要让模型更严格地遵循 schema 时，在注册时开启 [strict 模式](/docs/capabilities/models/chat#5-chat-的一个关键特性自动-tool-loop)：

```java
@FunctionCall(name = "getStockPrice", description = "...", strict = true)
```
:::

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

流式用法——继承 `ResponseSseListener`，实现 `onEvent()`，流结束后从 listener 上取聚合状态：

```java
ResponseRequest request = ResponseRequest.builder()
        .model("gpt-4o-mini")
        .input("从 1 数到 5，只输出数字")
        .stream(Boolean.TRUE)
        .build();

ResponseSseListener listener = new ResponseSseListener() {
    @Override
    protected void onEvent() {
        // currText 是本次事件带来的文本增量
        String delta = getCurrText();
        if (delta != null && !delta.isEmpty()) {
            System.out.print(delta);
        }
    }
};

responsesService.createStream(request, listener);

// 流结束后，聚合状态都在 listener 上
System.out.println("\n完整文本: " + listener.getOutputText());
System.out.println("事件条数: " + listener.getEvents().size());
```

除了 `getOutputText()`，listener 还聚合了 `getReasoningSummary()`（推理摘要）、`getFunctionArguments()`（函数参数）、`getResponse()`（最终结构）。

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

```java
// 第一轮：让 provider 侧留存这次响应
Response first = responsesService.create(ResponseRequest.builder()
        .model("gpt-4o-mini")
        .input("记住数字 42。只回复 STORED")
        .store(Boolean.TRUE)
        .build());

// 第二轮：只带 id，不重发历史
Response second = responsesService.create(ResponseRequest.builder()
        .model("gpt-4o-mini")
        .previousResponseId(first.getId())
        .input("我让你记住的数字是多少？只回复数字")
        .build());
```

已存留的响应也可以单独取回或删除：

```java
Response fetched = responsesService.retrieve(first.getId());
responsesService.delete(first.getId());
```

:::note 并非所有网关都支持存储态
`store` / `previous_response_id` / `retrieve` / `delete` 依赖 provider 侧保存响应。实测某 OpenAI 兼容网关明确回
`previous_response_id is only supported on Responses WebSocket v2`，且 `retrieve` 返回 404——这是网关能力差异，不是 SDK 缺陷。
官方 OpenAI 支持这些操作。
:::

## 9. 什么时候不要急着上 `Responses`

下面这些情况，先用 `Chat` 往往更省成本：

- 只是普通文本问答
- 只是基础 tool calling demo
- 最在意 provider 覆盖而不是事件语义
- 当前上层还没有状态机、trace 或复杂 UI 需求

`Responses` 的价值很高，但它不是所有项目的最短路径。

## 10. 这一页的结论

> AI4J 的 `Responses` 是结构化 response/event 主线，而不是 `Chat` 的重命名版本。它会先通过 `ResponseRequestToolResolver` 把本地工具和 MCP 工具并入请求，再由 `OpenAiResponsesService` 构建 provider payload，并在流式阶段用 `ResponseSseListener` 聚合事件、reasoning 和函数参数。因此它更适合 runtime、trace 和复杂交互，而不是只把模型当作一条文本回复。
