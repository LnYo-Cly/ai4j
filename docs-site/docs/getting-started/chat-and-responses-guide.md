---
sidebar_position: 6
---

# Chat 与 Responses 实战指南

这页聚焦你最常用的两条链路：`Chat Completions` 和 `Responses API`，包含同步、流式、工具调用差异和选型建议。

## 1. 一句话区别

- `Chat`：消息对话模型，兼容性和迁移性强。
- `Responses`：事件化响应模型，结构化流式信息更丰富。

## 2. 对应接口与监听器

| 维度 | Chat | Responses |
| --- | --- | --- |
| 服务接口 | `IChatService` | `IResponsesService` |
| 请求对象 | `ChatCompletion` | `ResponseRequest` |
| 响应对象 | `ChatCompletionResponse` | `Response` |
| 流式监听器 | `SseListener` | `ResponseSseListener` |
| 增量文本字段 | `getCurrStr()` | `getCurrText()` |
| 事件对象 | `ChatCompletionResponse` chunk | `ResponseStreamEvent` |

## 3. Chat：同步调用

```java
IChatService chatService = aiService.getChatService(PlatformType.OPENAI);

ChatCompletion req = ChatCompletion.builder()
        .model("gpt-4o-mini")
        .message(ChatMessage.withUser("请用一句话介绍 ai4j"))
        .build();

ChatCompletionResponse resp = chatService.chatCompletion(req);
String text = resp.getChoices().get(0).getMessage().getContent().getText();
System.out.println(text);
```

如果你是多轮会话，推荐直接配合 `ChatMemory` 使用，而不是自己拼 `List<ChatMessage>`。

## 4. Chat：流式调用

```java
ChatCompletion req = ChatCompletion.builder()
        .model("gpt-4o-mini")
        .message(ChatMessage.withUser("分三点介绍 ai4j"))
        .build();

SseListener listener = new SseListener() {
    @Override
    protected void send() {
        if (!getCurrStr().isEmpty()) {
            System.out.print(getCurrStr());
        }
    }
};

chatService.chatCompletionStream(req, listener);
System.out.println("\nstream finished");
```

## 5. Responses：同步调用

```java
IResponsesService responsesService = aiService.getResponsesService(PlatformType.DOUBAO);

ResponseRequest request = ResponseRequest.builder()
        .model("doubao-seed-1-8-251228")
        .input("用一句话介绍 Responses API")
        .instructions("请使用中文")
        .build();

Response response = responsesService.create(request);
System.out.println(response);
```

如果你想让 `Chat` 和 `Responses` 共享同一份会话上下文，建议使用 `ChatMemory`，再通过：

- `memory.toChatMessages()`
- `memory.toResponsesInput()`

分别适配两条链路。

## 6. Responses：流式调用

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
System.out.println(listener.getResponse());
```

## 7. 为什么流式看起来不像 token 级输出

这是正常现象：Responses 流式是 **事件驱动**，不保证每个事件只包含一个 token。

常见事件类型：

- `response.output_text.delta`：输出文本增量
- `response.reasoning_summary_text.delta`：推理摘要增量
- `response.function_call_arguments.delta`：函数参数增量
- `response.completed` / `response.failed` / `response.incomplete`：终态

不同平台可能把文本切成“字 / 词 / 短句 / 长片段”，所以视觉上不一定是 token-by-token。

## 8. Tool 调用：Chat 与 Responses 的关键差异

### 8.1 Chat（要区分直接 SDK 和 Agent 路径）

如果你直接调用 `IChatService`，Chat 链路默认就是经典自动工具循环：

1. 解析 tool call
2. 调用 `ToolUtil.invoke(...)`
3. 把 tool 结果作为 `tool` 消息回填
4. 再次请求模型直到得到最终文本

这也是很多人觉得 Chat 链路“开箱即用”的原因。

但如果你走的是 `ChatModelClient -> Agent/Coding Agent`，当前实现会自动开启 `passThroughToolCalls`：

- provider 只负责把 `tool_calls` 返回给 runtime
- tool 的真正执行者变成 `toolExecutor`
- 适合 `read_file`、`write_file`、MCP tool、审批装饰器这类 runtime 级工具

也就是说，**直接 SDK 的默认语义没有变；Agent/Coding 路径则自动切到 runtime 执行模式。**

### 8.2 Responses（基础服务层不自动执行工具）

`IResponsesService` 目前做的是请求与事件解析，不做自动 tool 执行循环。

同时它暴露的是底层 `ResponseRequest.tools` / `ResponseRequest.toolChoice` 字段，但 SDK 也支持 `ResponseRequest.functions(...)` / `mcpServices(...)` 这种便捷白名单写法。

也就是说：

- `ChatCompletion.functions(...)`：面向 Chat 的工具暴露便捷层
- `ResponseRequest.functions(...)` / `mcpServices(...)`：面向 Responses 的便捷工具暴露层
- `ResponseRequest.tools(...)`：面向 Responses 的原始工具声明层
- `@FunctionCall/@FunctionRequest/@FunctionParameter`：仍然是推荐的本地工具定义方式
- SDK 会在发送前自动用 `ToolUtil` 把便捷白名单转换成 `Responses` 需要的 `tools`

如果你要在 Responses 模式下做自动工具循环，建议两种方式：

- 使用 `Agent`（推荐）
- 自己在业务层根据 `ResponseStreamEvent` 实现循环

## 9. 选型建议（工程视角）

优先选 `Chat`：

- 你有大量现存 Chat Completions 代码
- 你主要需求是稳定文本输出 + function call
- 你希望最低迁移成本

优先选 `Responses`：

- 你要更细颗粒的事件可观测
- 你要处理 reasoning / output item / function args 的结构化流
- 你在构建新一代 Agent runtime

## 9.1 多轮上下文建议

如果你只是做基础多轮对话，不需要一上来就用 `AgentMemory`。

推荐顺序是：

1. 直接调用 `IChatService` / `IResponsesService`
2. 用 `ChatMemory` 维护基础会话上下文
3. 等你需要自动工具循环、规划、trace 时，再升级到 `Agent`

对应文档：

- [ChatMemory：基础会话上下文](/docs/ai-basics/chat/chat-memory)

## 10. 常见排障

### 10.1 流式迟迟不结束

- 检查是否接收到终态事件（`response.completed` 等）
- 检查监听器是否在 `onFailure/onClosed` 调用了 `complete()`

### 10.2 控制台只看到最终结果

- Chat：确认你打印的是 `getCurrStr()`，不是最后汇总字段。
- Responses：确认你打印的是 `getCurrText()`，而不是只看最终 `listener.getResponse()`。

### 10.3 测试日志里 `Results :` 看起来“空”

这是 surefire 的常见显示样式，不代表没有输出；关键看：

- `Failures/Errors/Skipped`
- 具体用例日志
- `target/surefire-reports` 文件
