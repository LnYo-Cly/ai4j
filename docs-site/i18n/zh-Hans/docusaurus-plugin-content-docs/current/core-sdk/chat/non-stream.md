---
sidebar_position: 10
---

# Chat（非流式）

本页聚焦 `IChatService#chatCompletion(...)` 的标准调用路径。

## 1. 核心对象

- 请求：`ChatCompletion`
- 消息：`ChatMessage`
- 响应：`ChatCompletionResponse`

最常用构造方式：

```java
ChatCompletion request = ChatCompletion.builder()
        .model("gpt-4o-mini")
        .message(ChatMessage.withSystem("你是一个简洁的 Java 助手"))
        .message(ChatMessage.withUser("用 3 点解释线程池拒绝策略"))
        .build();
```

## 2. 最小示例

```java
IChatService chatService = aiService.getChatService(PlatformType.OPENAI);
ChatCompletionResponse response = chatService.chatCompletion(request);

String text = response.getChoices().get(0).getMessage().getContent().getText();
System.out.println(text);
```

## 3. 常用参数

`ChatCompletion` 关键参数：

- `model`：模型 ID
- `messages`：对话消息
- `temperature` / `topP`：采样控制
- `maxCompletionTokens`：输出上限
- `responseFormat`：结构化输出（例如 json_object）
- `user`：用户标识
- `extraBody`：平台扩展参数

## 4. 多轮对话写法

```java
List<ChatMessage> history = new ArrayList<>();
history.add(ChatMessage.withSystem("你是代码审查助手"));
history.add(ChatMessage.withUser("请审查这段 SQL"));
history.add(ChatMessage.withAssistant("请把 SQL 发我"));
history.add(ChatMessage.withUser("select * from user where id = ?"));

ChatCompletion request = ChatCompletion.builder()
        .model("gpt-4o-mini")
        .messages(history)
        .build();
```

## 5. 平台覆写参数

如果你要传某平台特有字段，用 `extraBody`：

```java
ChatCompletion request = ChatCompletion.builder()
        .model("gpt-4o-mini")
        .message(ChatMessage.withUser("hello"))
        .extraBody("seed", 2026)
        .extraBody("custom_flag", true)
        .build();
```

## 6. 错误处理建议

- SDK 层已集成统一错误拦截器（starter 默认启用 `ErrorInterceptor`）。
- 业务层建议封装统一异常：`AI_TIMEOUT`、`AI_RATE_LIMIT`、`AI_INVALID_REQUEST`。

## 7. 质量基线测试

建议在集成测试中至少断言：

- `response != null`
- `choices` 非空
- 最终文本非空
- token usage（如果开启）可读

## 8. 何时不适合用非流式

以下场景建议切流式：

- 输出较长，用户等待感强
- 需要实时展示中间结果
- 需要尽早判断是否中止请求

下一页：`Chat（流式）`。
