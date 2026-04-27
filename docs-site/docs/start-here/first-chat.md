# First Chat

先把第一条聊天链路打通，再去看工具、MCP、Agent。

这一页的目标不是把全部模型调用细节一次讲完，而是先让你知道：

- 为什么第一次接入建议先从 `Chat` 开始
- 一条最小聊天调用链路到底由哪些对象组成
- 跑通后应该继续看 `Chat`、还是转去 `Responses`

## 1. 为什么第一次接入先看 `Chat`

AI4J 里最常用的模型调用主线有两条：

- `Chat`
- `Responses`

如果你第一次接入，建议先从 `Chat` 开始，因为它更适合先验证：

- provider 配置
- 最普通的同步请求
- 最直接的消息输入输出

换句话说，`Chat` 更像“先把第一条链路跑通”，而 `Responses` 更像“进入更细的事件模型”。

## 2. 先记住这条最小链路

```text
Configuration
    -> AiService
        -> IChatService
            -> ChatCompletion
                -> ChatCompletionResponse
```

只要这条链路已经成立，后面再理解：

- 流式
- 多模态
- Tool / Function Call
- `Responses`

就不会乱。

## 3. 最短 `Chat` 示例

```java
ChatCompletion req = ChatCompletion.builder()
        .model("gpt-4o-mini")
        .message(ChatMessage.withUser("用一句话介绍 AI4J"))
        .build();

ChatCompletionResponse resp = chatService.chatCompletion(req);
String text = resp.getChoices().get(0).getMessage().getContent().getText();
```

这段示例验证的其实是三件事：

- 模型请求已经成功发出
- 响应对象已经成功返回
- 你已经知道最短的文本结果从哪里读出来

## 4. 什么情况下继续留在 `Chat`

继续读 `Chat` 主线通常是因为你现在更关心：

- 普通消息式调用
- 多轮对话
- 常规流式输出
- 最常见的函数调用入口

这时建议继续看：

- [Core SDK / Model Access / Overview](/docs/core-sdk/model-access/overview)
- [Core SDK / Model Access / Chat](/docs/core-sdk/model-access/chat)
- [Core SDK / Model Access / Streaming](/docs/core-sdk/model-access/streaming)

## 5. 什么情况下看 `Responses`

当你需要：

- 结构化事件流
- reasoning / output item / function args
- 更细的流式可观测

就应该继续看 `Responses`。

建议继续看：

- [Core SDK / Model Access / Responses](/docs/core-sdk/model-access/responses)
- [Core SDK / Model Access / Chat vs Responses](/docs/core-sdk/model-access/chat-vs-responses)

## 6. 推荐阅读顺序

如果你已经跑通第一条聊天链路，推荐顺序是：

1. [Core SDK / Model Access Overview](/docs/core-sdk/model-access/overview)
2. [Core SDK / Chat](/docs/core-sdk/model-access/chat)
3. [Core SDK / Responses](/docs/core-sdk/model-access/responses)
4. [Core SDK / Chat vs Responses](/docs/core-sdk/model-access/chat-vs-responses)
5. [First Tool Call](/docs/start-here/first-tool-call)
