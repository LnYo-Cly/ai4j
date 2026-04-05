---
sidebar_position: 15
---

# ChatMemory：基础会话上下文

`ChatMemory` 是 `ai4j` 核心层新增的轻量会话上下文能力，面向“直接调用 LLM 服务”的场景。

它的目标很简单：

- 不让调用方每轮都手动维护完整 `messages`
- 同一份上下文既能给 `Chat` 用，也能给 `Responses` 用
- 默认提供内存版实现，也提供官方 JDBC 持久化实现

它不是 `AgentMemory` 的下沉版本，也不负责：

- agent loop
- planning
- runtime state
- 分布式 session 管理

先看一句话区别：

- `ChatMemory`：给基础 `Chat / Responses` 调用维护上下文
- `AgentMemory`：给 `Agent runtime` 维护运行中状态

## 1. 核心类

- `ChatMemory`
- `InMemoryChatMemory`
- `JdbcChatMemory`
- `JdbcChatMemoryConfig`
- `ChatMemoryItem`
- `ChatMemorySnapshot`
- `ChatMemoryPolicy`
- `UnboundedChatMemoryPolicy`
- `MessageWindowChatMemoryPolicy`

默认行为：

- 默认实现：`InMemoryChatMemory`
- 默认策略：`UnboundedChatMemoryPolicy`
- 默认不裁剪、不压缩

如果你要把会话直接落到 MySQL / PostgreSQL / H2 这类关系库里，可以用：

- `JdbcChatMemory`

如果你需要控制上下文增长，可以显式切换到：

- `MessageWindowChatMemoryPolicy`

## 2. 什么时候该用它

适合：

- 你直接调用 `IChatService`
- 你直接调用 `IResponsesService`
- 你只想维护多轮聊天上下文
- 你暂时不需要完整 Agent runtime

不适合：

- 你要自动工具循环 + 规划 + trace
- 你要 Coding Agent / ReAct / CodeAct 级别的状态管理

如果你只是想给基础 `Chat / Responses` 会话加持久化，而不是升级到完整 Agent runtime，现在可以继续用 `ChatMemory`，只需要把实现从 `InMemoryChatMemory` 换成 `JdbcChatMemory`。

## 3. Chat 链路最小示例

```java
IChatService chatService = aiService.getChatService(PlatformType.OPENAI);

ChatMemory memory = new InMemoryChatMemory();
memory.addSystem("你是一个简洁的 Java 助手");

memory.addUser("请用三点介绍 AI4J");

ChatCompletion request = ChatCompletion.builder()
        .model("gpt-4o-mini")
        .messages(memory.toChatMessages())
        .build();

ChatCompletionResponse response = chatService.chatCompletion(request);
String answer = response.getChoices().get(0).getMessage().getContent().getText();

memory.addAssistant(answer);
```

第二轮继续时，只需要再往 memory 里追加用户输入：

```java
memory.addUser("再补一段关于 MCP 的说明");

ChatCompletion nextRequest = ChatCompletion.builder()
        .model("gpt-4o-mini")
        .messages(memory.toChatMessages())
        .build();
```

## 4. Responses 链路最小示例

```java
IResponsesService responsesService = aiService.getResponsesService(PlatformType.DOUBAO);

ChatMemory memory = new InMemoryChatMemory();
memory.addSystem("你是一个简洁的中文助手");
memory.addUser("请用一句话介绍 Responses API");

ResponseRequest request = ResponseRequest.builder()
        .model("doubao-seed-1-8-251228")
        .input(memory.toResponsesInput())
        .build();

Response response = responsesService.create(request);
String answer = extractOutputText(response);

memory.addAssistant(answer);
```

一个简单的文本提取函数可以这样写：

```java
private static String extractOutputText(Response response) {
    if (response == null || response.getOutput() == null) {
        return null;
    }
    for (ResponseItem item : response.getOutput()) {
        if (item == null || item.getContent() == null) {
            continue;
        }
        for (ResponseContentPart part : item.getContent()) {
            if (part != null && part.getText() != null && !part.getText().trim().isEmpty()) {
                return part.getText();
            }
        }
    }
    return null;
}
```

## 5. 多模态输入

`ChatMemory` 也支持基础文本 + 图片 URL 输入：

```java
ChatMemory memory = new InMemoryChatMemory();
memory.addUser(
        "请描述图片里的内容",
        "https://example.com/cat.jpg"
);
```

然后：

- `memory.toChatMessages()` 可直接给 `ChatCompletion`
- `memory.toResponsesInput()` 可直接给 `ResponseRequest`

## 6. 工具结果回填

如果你在业务层自己处理工具调用，也可以把结果回写到 `ChatMemory`：

```java
memory.addAssistant("我先查询天气", toolCalls);
memory.addToolOutput("call_123", "{\"weather\":\"sunny\"}");
```

这意味着下一轮模型仍然能看到工具输出。

## 7. 快照与恢复

```java
ChatMemory memory = new InMemoryChatMemory();
memory.addUser("hello");

ChatMemorySnapshot snapshot = memory.snapshot();

memory.clear();
memory.restore(snapshot);
```

这个能力适合：

- 单进程里的临时会话保存
- 请求链中断后的恢复
- 业务层自己实现 session 管理

## 8. 官方 JDBC 持久化实现

如果你希望会话跨进程保留，但又不想一上来就自己封装 Redis / MySQL session store，可以先直接用官方 JDBC 版：

```java
ChatMemory memory = new JdbcChatMemory(
        JdbcChatMemoryConfig.builder()
                .jdbcUrl("jdbc:mysql://localhost:3306/ai4j")
                .username("root")
                .password("123456")
                .sessionId("chat-session-001")
                .policy(new MessageWindowChatMemoryPolicy(20))
                .build()
);
```

也支持直接传 `DataSource`：

```java
ChatMemory memory = new JdbcChatMemory(
        JdbcChatMemoryConfig.builder()
                .dataSource(dataSource)
                .sessionId("chat-session-001")
                .build()
);
```

如果你是 Spring Boot + MySQL，通常直接复用容器里的 `DataSource` 即可：

```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/ai4j?useUnicode=true&characterEncoding=UTF-8&serverTimezone=Asia/Shanghai
    username: root
    password: 123456
```

```java
public ChatMemory create(String sessionId) {
    return new JdbcChatMemory(
            JdbcChatMemoryConfig.builder()
                    .dataSource(dataSource)
                    .sessionId(sessionId)
                    .policy(new MessageWindowChatMemoryPolicy(20))
                    .build()
    );
}
```

## 9. 淘汰策略

默认策略是 `Unbounded`，也就是不做任何处理。

如果你不希望上下文无限增长，可以手动配置消息窗口：

```java
ChatMemory memory = new InMemoryChatMemory(
        new MessageWindowChatMemoryPolicy(12)
);
```

当前规则是：

- 永远保留 `system`
- 非 `system` 只保留最近 N 条

## 10. 与 AgentMemory 的区别

`ChatMemory`：

- 属于 `ai4j`
- 面向基础 LLM 会话上下文
- 默认轻量、透明

`AgentMemory`：

- 属于 `ai4j-agent`
- 面向 agent runtime
- 包含 tool output write-back、memory item、压缩等更强语义

如果你只是做普通聊天、问答、摘要、改写，优先从 `ChatMemory` 开始。

## 11. 继续阅读

- [ChatMemory 与 sessionId 管理](/docs/core-sdk/chat/chat-memory-session-management)
