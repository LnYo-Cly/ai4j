# Chat Memory

`ChatMemory` 是 AI4J 基座层里很容易被低估的一块能力。很多人以为它只是“帮你存一下聊天记录”，但从源码看，它实际上是 **`Chat` 和 `Responses` 的共享会话上下文容器**。

如果你把这页看透，就能明白：

- 为什么 AI4J 没让每个调用方自己维护 `messages`
- 为什么 `Chat` 和 `Responses` 可以共用一套会话状态
- 为什么它和 `AgentMemory` / `CodingSession` 不是一回事

## 1. 先看核心接口

`ai4j/src/main/java/io/github/lnyocly/ai4j/memory/ChatMemory.java` 定义了这套能力的最小契约：

- `addSystem(...)`
- `addUser(...)`
- `addAssistant(...)`
- `addToolOutput(...)`
- `toChatMessages()`
- `toResponsesInput()`
- `snapshot()`
- `restore(...)`
- `clear()`

只看这组方法就能知道，`ChatMemory` 的重点不是某个 provider，而是：

- 维护多轮上下文
- 把上下文投影到不同模型接口
- 支持回放、恢复和裁剪

## 2. 它为什么不是“只给 Chat 用的”

这是最容易写漏的一点。

`ChatMemory` 同时提供：

- `toChatMessages()`
- `toResponsesInput()`

这两个方法已经明确说明，它不是某个 `ChatCompletion` 的附属物，而是 AI4J 基座层抽出来的统一会话表示。

你可以把它理解成：

- `Chat` 需要的投影：`List<ChatMessage>`
- `Responses` 需要的投影：`List<Object>`

底层会话事实只有一份，投影方式不同。

## 3. 两个最常用的实现

### 3.1 `InMemoryChatMemory`

源码位置：

- `memory/InMemoryChatMemory.java`

特征：

- 默认实现
- 数据保存在 JVM 内存里
- 默认 policy 是 `UnboundedChatMemoryPolicy`
- 适合先跑通单进程多轮对话

### 3.2 `JdbcChatMemory`

源码位置：

- `memory/JdbcChatMemory.java`
- `memory/JdbcChatMemoryConfig.java`

特征：

- 以 `sessionId` 作为持久化主键
- 支持 `DataSource` 或 JDBC 参数
- 可以初始化 schema
- 仍然可以叠加 `MessageWindow` / `Summary` policy

也就是说，AI4J 已经把“跨进程恢复多轮上下文”这件事做到基座层了，而不是要求业务方自己临时造一个 session 表。

## 4. 为什么 policy 设计非常关键

很多人会说“上下文太长就截断”。AI4J 没有把这件事做成黑盒，而是抽成了 `ChatMemoryPolicy`。

当前常见策略有：

- `UnboundedChatMemoryPolicy`
- `MessageWindowChatMemoryPolicy`
- `SummaryChatMemoryPolicy`

### `MessageWindowChatMemoryPolicy` 做什么

它会保留最近 N 条非 system 消息，但会尽量保住 system role。

### `SummaryChatMemoryPolicy` 做什么

它会在达到触发阈值后，把较早消息总结成一条 summary，再保留最近消息继续滚动。

这一点很重要，因为这代表 AI4J 对“上下文变长”的态度不是简单粗暴裁掉，而是允许你做 **可解释的压缩**。

## 5. 最小使用心智

一个很典型的基座层用法是：

```java
ChatMemory memory = new InMemoryChatMemory();
memory.addSystem("你是一个简洁的 Java 助手");
memory.addUser("请用三点介绍 AI4J");

ChatCompletion request = ChatCompletion.builder()
        .model("gpt-4o-mini")
        .messages(memory.toChatMessages())
        .build();
```

如果你切到 `Responses`，只是把最后一行换成：

```java
ResponseRequest request = ResponseRequest.builder()
        .model("gpt-4.1")
        .input(memory.toResponsesInput())
        .build();
```

这就说明 `ChatMemory` 的真正价值是：**把上下文和接口形式解耦**。

## 6. 它不只存文本

`ChatMemory` 还能记录：

- 图文用户输入
- assistant tool calls
- tool output

这意味着它记录的是“对话事实”，而不是只记录最后的自然语言文本。

例如 `addToolOutput(toolCallId, output)` 的存在就说明，工具结果也被视为会话上下文的一部分。

## 7. 但它为什么仍然不是工具治理系统

这是一个非常关键的边界。

`ChatMemory` 能记录 tool calls 和 tool outputs，不代表它负责：

- 允许不允许调用工具
- 工具需不需要审批
- 工具执行失败后怎么补偿
- 多步任务如何推进

它保存的是“发生过什么”，不是“应该让什么发生”。

所以它和 `ToolUtil`、`McpGateway`、Agent runtime 是邻接关系，不是包含关系。

## 8. 和 `AgentMemory` / `CodingSession` 的区别

如果你是做普通多轮对话：

- `ChatMemory` 足够

如果你已经进入：

- 任务规划
- 长程状态机
- 审批
- checkpoint / compact / resume

那就不是 `ChatMemory` 该解决的层了，而是 `ai4j-agent` / `ai4j-coding` 的状态系统。

这条边界必须写清楚，否则用户会误以为“AI4J 既然有 memory，就应该自动等于 agent memory”。

## 9. 注意事项

### 9.1 把 `InMemoryChatMemory` 当成长会话存储

单进程 demo 没问题，跨进程恢复就不对。

### 9.2 上下文太长时直接手工裁消息

更稳的方式是显式使用 memory policy，而不是业务代码里随手 `subList`。

### 9.3 把工具审批结果塞进 memory 里当权限系统

memory 只能记录事实，不能替代治理。

## 10. 设计摘要

AI4J 的 `ChatMemory` 不是一个“聊天记录数组工具类”，而是 `Chat` 和 `Responses` 共用的会话抽象。它把上下文维护、投影、裁剪、摘要和持久化都收进了基座层，但不承担 agent 级状态推进和工具治理职责。

## 11. 继续阅读

- [Memory / Memory and Tool Boundaries](/docs/core-sdk/memory/memory-and-tool-boundaries)
- [Model Access / Chat](/docs/core-sdk/model-access/chat)
- [Model Access / Responses](/docs/core-sdk/model-access/responses)
