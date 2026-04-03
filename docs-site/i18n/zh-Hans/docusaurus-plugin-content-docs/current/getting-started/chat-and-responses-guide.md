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

## 7. 为什么你看到“不是 token 级输出”

你观察到的现象是正常的：Responses 流式是 **事件驱动**，不保证每个事件只包含一个 token。

常见事件类型：

- `response.output_text.delta`：输出文本增量
- `response.reasoning_summary_text.delta`：推理摘要增量
- `response.function_call_arguments.delta`：函数参数增量
- `response.completed` / `response.failed` / `response.incomplete`：终态

不同平台可能把文本切成“字 / 词 / 短句 / 长片段”，所以视觉上不一定是 token-by-token。

## 8. Tool 调用：Chat 与 Responses 的关键差异

### 8.1 Chat（SDK 已内置 tool 循环）

`OpenAiChatService` 等实现中，当 `finish_reason=tool_calls` 时会自动：

1. 解析 tool call
2. 调用 `ToolUtil.invoke(...)`
3. 把 tool 结果作为 `tool` 消息回填
4. 再次请求模型直到得到最终文本

这也是很多人觉得 Chat 链路“开箱即用”的原因。

### 8.2 Responses（基础服务层不自动执行工具）

`IResponsesService` 目前做的是请求与事件解析，不做自动 tool 执行循环。

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
