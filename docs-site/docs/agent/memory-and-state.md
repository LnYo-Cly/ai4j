# Memory and State

这页负责把 `Agent` 层最容易混淆的三件事讲清楚：记忆、运行时状态、和上层会话边界。

## 1. `Agent` 这一层到底管理什么

在 `Agent` 语境里，`state` 不只是聊天历史。

它通常至少包括：

- `ChatMemory` 之上承接的上下文
- `AgentMemory` 自己的运行时记忆
- tool loop 过程中的中间状态
- workflow / subagent / team 的任务状态
- trace 和诊断信息

所以这里的重点是“任务如何持续”，而不是单纯“对话记录怎么存”。

## 2. 三层边界不要混

最常见的混淆，是把下面三层当成一个东西：

- `ChatMemory`：`Core SDK` 的基础会话上下文
- `AgentMemory`：`Agent runtime` 的任务级记忆与状态
- `CodingSession`：`Coding Agent` 为本地代码仓交互增加的长期会话层

如果你只是在做普通智能体 runtime，重点先落在前两层；如果你做的是本地 coding assistant，再往上才会进入 `CodingSession`。

## 3. 什么时候要特别关注这一层

以下情况基本都说明你不能只停留在简单消息列表：

- 会话很长，需要压缩
- 工具调用很多，需要保留关键结果
- 任务有阶段或节点状态
- 需要恢复、分叉或复盘
- 需要解释“为什么这一步会继续或停止”

## 4. 推荐连读

如果你要把 memory 边界读透，建议连读：

1. [Core SDK / Memory Boundaries](/docs/core-sdk/memory/memory-and-tool-boundaries)
2. [Agent Architecture](/docs/agent/architecture)
3. [Trace](/docs/agent/observability/trace)
4. [Reference Core Classes](/docs/agent/reference-core-classes)
