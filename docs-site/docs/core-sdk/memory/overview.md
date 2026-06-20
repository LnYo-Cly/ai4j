# Memory 总览

`memory` 这一章讲的不是 agent 长期状态机，而是 **Core SDK 如何保存、裁剪、压缩并重新投影多轮会话事实**。

这里的重点对象是 `ChatMemory`，但它的真实作用比“聊天记录数组”要重得多。

## 1. 这一章在 Core SDK 里的位置

这一层主要处理四件事：

- 会话事实如何被记录
- 这些事实如何被投影成 `Chat` 或 `Responses` 输入
- 上下文过长时如何裁剪或摘要
- 会话状态是只存在内存里，还是持久化到外部存储

它不负责：

- agent 级任务推进
- 多 agent 协作状态
- coding runtime 的 checkpoint / compact / resume
- tool 审批与副作用治理

这些已经是 `ai4j-agent` 或 `ai4j-coding` 的层次。

## 2. 真实入口

继续看源码时，优先看下面这些对象：

- `memory/ChatMemory.java`
- `memory/ChatMemoryItem.java`
- `memory/InMemoryChatMemory.java`
- `memory/JdbcChatMemory.java`
- `memory/ChatMemoryPolicy.java`
- `memory/MessageWindowChatMemoryPolicy.java`
- `memory/SummaryChatMemoryPolicy.java`

这组对象共同构成了一条非常清楚的设计：

- `ChatMemory` 定义能力契约
- `ChatMemoryItem` 定义会话事实格式
- 存储实现决定事实放在哪里
- policy 决定事实保留多少

## 3. 这一层真正保存的是什么

AI4J 保存的不是“最后一句聊天文本”，而是更完整的会话事实：

- `system`
- `user`
- `assistant`
- `assistant tool calls`
- `tool output`
- 图文输入
- 摘要条目

这点从 `ChatMemoryItem` 的字段就能直接看出来：

- `role`
- `text`
- `imageUrls`
- `toolCallId`
- `toolCalls`
- `summary`

所以 memory 记录的是“模型看到过什么、工具返回过什么”，而不是只记录自然语言对话。

## 4. 为什么 `Chat` 和 `Responses` 能共用一份 memory

`ChatMemory` 同时暴露：

- `toChatMessages()`
- `toResponsesInput()`

这意味着 AI4J 在基座层已经把“会话事实”和“请求协议格式”分开了。

同一份上下文可以：

- 投影成 `List<ChatMessage>`
- 投影成 `Responses` 所需的 `List<Object>`

这不是文档层概念，而是接口层明确承诺。

## 5. 存储和策略是分开的

这一层有一个很重要的设计点：**会话放在哪里** 与 **会话保留多少** 是两个独立问题。

### 存储实现

- `InMemoryChatMemory`
- `JdbcChatMemory`

### 保留策略

- `UnboundedChatMemoryPolicy`
- `MessageWindowChatMemoryPolicy`
- `SummaryChatMemoryPolicy`

这样你可以分别决定：

- 会话是否要跨进程恢复
- 上下文是否要做窗口裁剪
- 历史是否要压缩成摘要

## 6. 当前默认行为

默认情况下，`InMemoryChatMemory` 和 `JdbcChatMemory` 都会在没有显式 policy 时使用：

- `UnboundedChatMemoryPolicy`

也就是说，AI4J 默认不会自动截断历史。  
如果你担心上下文无限膨胀，应该显式配置窗口或摘要策略，而不是假设 SDK 会自动帮你做长度控制。

## 7. 持久化实现的真实边界

`JdbcChatMemory` 不是“把内存对象顺手存一下”，而是一个正式实现：

- 通过 `sessionId` 标识会话
- 默认表名是 `ai4j_chat_memory`
- 默认会自动初始化 schema
- 每次写入会先删后插整段会话
- 条目以 JSON 持久化

这个实现的重点是可恢复性和一致性，不是高并发增量写优化。

## 8. 什么时候停在这一层，什么时候往上走

如果你只是需要：

- 多轮对话
- session 级上下文
- 图文输入历史
- tool output 回写
- 基础裁剪与摘要

这一层通常就够。

如果你已经开始关心：

- runtime loop
- 计划状态
- 审批与副作用
- 多 agent handoff

那说明你要继续进入 `Agent` 或 `Coding Agent`，而不是继续给 `ChatMemory` 叠责任。

## 9. 推荐阅读顺序

1. [Chat Memory](/docs/core-sdk/memory/chat-memory)
2. [Memory and Tool Boundaries](/docs/core-sdk/memory/memory-and-tool-boundaries)
3. [Agent / Memory and State](/docs/agent/memory-and-state)

## 10. 这一页的结论

> AI4J 的 memory 基座不是简单的聊天记录容器，而是“会话事实 + 存储实现 + 裁剪/摘要策略 + 多协议投影”的组合层。只要你的问题仍然属于多轮上下文管理，这一层就已经足够；一旦进入任务推进和治理语义，就应该上升到更高层 runtime。
