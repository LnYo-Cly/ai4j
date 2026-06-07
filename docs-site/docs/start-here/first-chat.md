# First Chat

先把第一条聊天链路打通，再去看工具、MCP、Agent。

如果你还没有跑过代码，先走 [5 分钟首聊](/docs/start-here/five-minute-first-chat)。本页继续解释 `Chat` 主线的对象边界和后续阅读方向。

这一页的目标不是把全部模型调用细节一次讲完，而是先让你知道：

- 为什么第一次接入建议先从 `Chat` 开始
- 第一条聊天请求的最短写法，以及它背后的对象链
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

## 2. 第一条请求先用真实对象链

普通 Java 首聊不要绕过核心对象链，先明确这几个对象：

```text
Configuration -> AiService -> IChatService -> ChatCompletion -> ChatCompletionResponse
```

这条链路的价值是把“依赖、密钥、provider、请求、响应对象、文本读取”一次跑通，后续流式、Tool、MCP、RAG 都沿着同一套对象边界扩展。

## 3. 核心链路

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

## 4. 完整对象链里的 `Chat` 示例

完整可运行版本见 [5 分钟首聊](/docs/start-here/five-minute-first-chat) 和 [Quickstart for Java](/docs/start-here/quickstart-java)。这里只保留核心请求片段：

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

## 5. 什么情况下继续留在 `Chat`

继续读 `Chat` 主线通常是因为你现在更关心：

- 普通消息式调用
- 多轮对话
- 常规流式输出
- 最常见的函数调用入口

这时建议继续看：

- [Core SDK / Model Access / Overview](/docs/core-sdk/model-access/overview)
- [Core SDK / Model Access / Chat](/docs/core-sdk/model-access/chat)
- [Core SDK / Model Access / Streaming](/docs/core-sdk/model-access/streaming)

## 6. 什么情况下看 `Responses`

当你需要：

- 结构化事件流
- reasoning / output item / function args
- 更细的流式可观测

就应该继续看 `Responses`。

建议继续看：

- [Core SDK / Model Access / Responses](/docs/core-sdk/model-access/responses)
- [Core SDK / Model Access / Chat vs Responses](/docs/core-sdk/model-access/chat-vs-responses)

## 7. 推荐阅读顺序

如果你还没跑通过，先读：

1. [5 分钟首聊](/docs/start-here/five-minute-first-chat)
2. [Quickstart for Java](/docs/start-here/quickstart-java) 或 [Quickstart for Spring Boot](/docs/start-here/quickstart-spring-boot)

如果你已经跑通第一条聊天链路，推荐顺序是：

1. [Core SDK / Model Access Overview](/docs/core-sdk/model-access/overview)
2. [Core SDK / Chat](/docs/core-sdk/model-access/chat)
3. [Core SDK / Responses](/docs/core-sdk/model-access/responses)
4. [Core SDK / Chat vs Responses](/docs/core-sdk/model-access/chat-vs-responses)
5. [First Tool Call](/docs/start-here/first-tool-call)
