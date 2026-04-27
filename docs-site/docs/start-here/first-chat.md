# First Chat

先把第一条聊天链路打通，再去看工具、MCP、Agent。

## 1. 两条主线

AI4J 里最常用的模型调用主线有两条：

- `Chat`
- `Responses`

如果你第一次接入，建议先从 `Chat` 开始，因为它更直接，也更适合验证基础链路。

## 2. 最短 `Chat` 示例

```java
ChatCompletion req = ChatCompletion.builder()
        .model("gpt-4o-mini")
        .message(ChatMessage.withUser("用一句话介绍 AI4J"))
        .build();

ChatCompletionResponse resp = chatService.chatCompletion(req);
String text = resp.getChoices().get(0).getMessage().getContent().getText();
```

## 3. 什么情况下看 `Responses`

当你需要：

- 结构化事件流
- reasoning / output item / function args
- 更细的流式可观测

就应该继续看 `Responses`。

## 4. 推荐阅读顺序

1. [Core SDK / Model Access Overview](/docs/core-sdk/model-access/overview)
2. [Core SDK / Chat](/docs/core-sdk/model-access/chat)
3. [Core SDK / Responses](/docs/core-sdk/model-access/responses)
4. [Core SDK / Chat vs Responses](/docs/core-sdk/model-access/chat-vs-responses)
