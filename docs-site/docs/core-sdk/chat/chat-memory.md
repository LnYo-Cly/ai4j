---
sidebar_position: 15
---

# ChatMemory：基础会话上下文

如果你是从旧 `Chat` 目录点进来的，这页最重要的作用是建立一个准确认知：

`ChatMemory` 并不是“给 Chat 专用的 history list 包装器”，而是 AI4J 当前基础会话层的统一事实存储。

canonical 说明请直接连读：[Memory / Chat Memory](/docs/core-sdk/memory/chat-memory)。

## 1. 先给一句工程结论

`ChatMemory` 解决的是：

- 同一份会话事实如何被保存
- 如何投影到 `Chat`
- 如何投影到 `Responses`

它不解决的是：

- agent loop
- planning
- runtime trace
- 分布式 session 编排

所以它是基础会话层，不是 agent runtime 的缩小版。

## 2. 关键源码入口

建议重点看：

- `memory/ChatMemory.java`
- `memory/InMemoryChatMemory.java`
- `memory/JdbcChatMemory.java`
- `memory/ChatMemoryItem.java`
- `memory/MessageWindowChatMemoryPolicy.java`
- `memory/SummaryChatMemoryPolicy.java`

真正定义它“为什么既能给 Chat 用、又能给 Responses 用”的核心类是 `ChatMemoryItem`。

## 3. `ChatMemoryItem` 才是统一抽象的中心

`ChatMemoryItem` 当前能表达：

- `role`
- `text`
- `imageUrls`
- `toolCallId`
- `toolCalls`
- `summary`

它最关键的两个投影方法是：

- `toChatMessage()`
- `toResponsesInput()`

这意味着 AI4J 的设计不是：

- Chat 一套 memory 结构
- Responses 一套 memory 结构

而是：

1. 先统一保存会话事实
2. 再按目标协议投影

这也是它和很多“只会吐 messages 数组”的轻量 helper 的根本区别。

## 4. 这层会话到底能保存什么

除了普通用户和 assistant 文本，`ChatMemory` 还能保存：

- 多模态 user 输入
- assistant tool calls
- tool outputs
- summary item

例如：

```java
memory.addUser("请描述图片里的内容", "https://example.com/cat.jpg");
memory.addAssistant("我先查询天气", toolCalls);
memory.addToolOutput("call_123", "{\"weather\":\"sunny\"}");
```

这样做的意义是：你不仅保留“模型最后说了什么”，也保留“它中间是怎么调用工具的”。

## 5. 为什么它既适合 `Chat`，也适合 `Responses`

对同一条 user 图文输入：

- `toChatMessage()` 会生成 `text + image_url` 的 `Content.MultiModal`
- `toResponsesInput()` 会生成 `input_text + input_image` 的结构化 content

对同一条 tool output：

- `Chat` 投影成 `role=tool` 的消息
- `Responses` 投影成 `type=function_call_output`

所以如果你需要在两条模型访问主线之间切换，保留 `ChatMemory` 往往比手写协议对象稳得多。

## 6. 默认实现和默认策略的真实含义

当前实现里：

- `new InMemoryChatMemory()` 默认走 `UnboundedChatMemoryPolicy`
- `JdbcChatMemoryConfig` 不传 policy 时也默认 `UnboundedChatMemoryPolicy`

这意味着开箱即用时：

- 不会自动裁剪
- 不会自动总结
- 上下文会持续增长

这对 demo 和短会话很好理解，但对长会话意味着成本和噪声会持续上升。

## 7. 三种常见策略分别意味着什么

### `UnboundedChatMemoryPolicy`

优点是透明。

代价是：

- token 成本只增不减
- 历史噪声越来越大

### `MessageWindowChatMemoryPolicy`

适合你接受“旧消息直接丢弃”的场景。

当前规则是：

- 永远保留 `system`
- 非 `system` 只保留最近 N 条

### `SummaryChatMemoryPolicy`

适合更长会话。

它不是简单删旧消息，而是把更早消息收敛成一条 summary item，再继续保留最近原始轮次。

这条策略的代价也很明确：

- 需要你提供 `ChatMemorySummarizer`
- 摘要本身可能引入信息损失

## 8. 最小使用方式

```java
ChatMemory memory = new InMemoryChatMemory(
        new MessageWindowChatMemoryPolicy(12)
);
memory.addSystem("你是一个简洁的 Java 助手");
memory.addUser("请用三点介绍 AI4J");

ChatCompletion request = ChatCompletion.builder()
        .model("gpt-4o-mini")
        .messages(memory.toChatMessages())
        .build();
```

第二轮继续时，通常只需要：

```java
memory.addAssistant(answer);
memory.addUser("再补一段关于 MCP 的说明");
```

重点不是代码简短，而是你的业务不必重新思考“上一轮 assistant 和 tool transcript 是否回填完整”。

## 9. `JdbcChatMemory` 解决了什么，没有解决什么

`JdbcChatMemory` 的价值是：

- 会话可持久化
- 服务重启后可恢复
- 不必自己先写一套关系库存储层

它没有自动解决的是：

- 多实例并发冲突
- session 生命周期管理
- 分布式锁
- agent 状态统一编排

所以“落库”和“会话治理”不是同一个问题。

## 10. 快照与恢复适合什么场景

`snapshot()` / `restore(...)` 更像轻量运行时能力，而不是完整持久化方案。

它适合：

- 单进程内的暂存与回滚
- 业务层中断恢复
- 测试里构造一份可复制上下文

如果你真正需要跨实例、跨进程、跨部署面恢复，优先看 `JdbcChatMemory` 或更高层 runtime。

## 11. 与 agent runtime 的边界

`ChatMemory` 适合：

- 普通问答
- 摘要、改写、分类
- 基础 tool transcript 回填
- `Chat` / `Responses` 双投影

它不适合直接承担：

- 计划状态
- 多步代理决策
- trace 树
- 审批与沙箱执行态

如果你的问题已经变成“工具运行后谁来决定下一步”，那关注点就不在 `ChatMemory` 了，而在 `ai4j-agent` 或 `ai4j-coding`。

## 12. 这一页的结论

> `ChatMemory` 在 AI4J 中不是旧式消息列表缓存，而是基础会话事实层。它把文本、图片、tool call、tool output 统一保存为 `ChatMemoryItem`，再分别投影到 `Chat` 和 `Responses`。默认它很轻，也因此不会替你解决 session 管理和 runtime 编排，但正因为分层清晰，它非常适合作为直接 LLM 调用的上下文基座。
