# Memory 总览

`Memory` 这一章只讲基座层的上下文概念，不展开 `Agent runtime` 的内部状态机。

## 1. 先确定这一章的边界

这里不是在讲复杂 agent 的长期状态管理，而是在讲：

- 基础会话上下文如何持续
- `ChatMemory` 这一类能力在 `Core SDK` 里怎么归位
- memory 和 tool / runtime 的边界是什么

## 2. 这一层主要讲什么

当前主线是三件事：

- `ChatMemory`
- 基础多轮上下文
- memory 与 tool 的边界

这意味着它关注的是“基础会话怎么持续”，而不是上层 runtime 怎样编排复杂任务。

## 3. 为什么它重要

很多项目在还不需要 `Agent` 时，就已经需要：

- 多轮对话
- session 级上下文
- 上下文裁剪

这时先把 `ChatMemory` 理顺，比过早引入更重的 runtime 更稳。

## 4. 和 `AgentMemory` 的边界

可以先这样记：

- `ChatMemory`：`Core SDK` 的基础上下文层
- `AgentMemory`：`Agent` 层更强的 runtime 记忆语义
- `CodingSession`：`Coding Agent` 的长期任务会话层

三者相关，但不是一个层次。

## 5. 什么时候该停在这一层，什么时候该往上走

如果你只是需要：

- 多轮聊天
- session 级上下文
- memory 与 tool 的基础边界

这一层通常就够。

如果你已经开始关心：

- runtime step loop
- 多 agent 协作
- 长期 coding session

那通常要继续进入 `Agent` 或 `Coding Agent`。

## 6. 推荐阅读顺序

1. [Chat Memory](/docs/core-sdk/memory/chat-memory)
2. [Memory and Tool Boundaries](/docs/core-sdk/memory/memory-and-tool-boundaries)
3. [Agent / Memory and State](/docs/agent/memory-and-state)

## 7. 关键对象

如果你接下来要进一步看实现，建议先从这些类入手：

- `memory/ChatMemory.java`：基础上下文存取契约
- `memory/InMemoryChatMemory.java`：内存态实现
- `memory/JdbcChatMemory.java`：持久化实现
- `memory/MessageWindowChatMemoryPolicy.java`：窗口裁剪策略
- `memory/SummaryChatMemoryPolicy.java`：摘要压缩策略

这几个对象已经足够说明：AI4J 的 memory 不是单一容器，而是“存储实现 + 裁剪策略 + 压缩策略”的组合层。

## 8. 这一层的设计重点

- 会话上下文的持久方式和上下文压缩方式是分离的，可以分别替换
- 这一层主要关心消息历史如何进入模型请求，而不是任务执行图如何管理长期状态
- 只要业务仍然是“多轮上下文 + 适度压缩”，通常不需要立刻升级到更重的 agent memory

因此，这一章的重点不是功能数量，而是边界控制得足够清楚：什么时候 `ChatMemory` 已经足够，什么时候才需要进入上层 runtime 的状态语义。
