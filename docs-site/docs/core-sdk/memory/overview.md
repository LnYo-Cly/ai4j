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
