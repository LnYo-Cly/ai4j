# Memory and State

`Agent` 这一层最容易被低估的，不是 prompt，而是 state。

因为一旦进入多轮推理，真正决定系统是否稳定的，往往不是模型本身，而是：

- 用户输入如何进入上下文
- 模型输出如何保留
- 工具结果如何回灌
- 什么时候压缩，什么时候恢复

## 1. 先分清三层 memory

这三层很容易被混在一起：

- `ChatMemory`：`Core SDK` 的基础会话上下文
- `AgentMemory`：`Agent` runtime 的任务级记忆层
- `CodingSession`：`Coding Agent` 的长期会话与事件账本

一句话区分：

- `ChatMemory` 重点是“多轮聊天上下文”
- `AgentMemory` 重点是“runtime 每一步怎么延续任务状态”
- `CodingSession` 重点是“本地代码仓任务如何长期保存、恢复、分叉”

## 2. Agent 侧真正使用的是哪些类

主路径：

- `ai4j-agent/src/main/java/io/github/lnyocly/ai4j/agent/memory`

核心类：

- `AgentMemory`
- `InMemoryAgentMemory`
- `JdbcAgentMemory`
- `JdbcAgentMemoryConfig`
- `MemoryCompressor`
- `WindowedMemoryCompressor`
- `MemorySnapshot`

默认情况下，`AgentBuilder` 会给你装 `InMemoryAgentMemory`。

## 3. Runtime 在哪些时机读写 memory

以 `BaseAgentRuntime` 为例，memory 参与的是每一轮循环，而不是只在开头或结尾碰一下。

主链路是：

1. `request.input` 进入 `memory.addUserInput(...)`
2. 模型输出进入 `memory.addOutputItems(...)`
3. 工具结果进入 `memory.addToolOutput(callId, output)`
4. 下一轮 `buildPrompt(...)` 重新从 `memory.getItems()` 取上下文

所以 memory 不是“聊天记录存储器”，而是 runtime 的状态源。

## 4. 为什么这层重要

如果没有 memory，下面这些事情都会断掉：

- 工具调用结果无法被下一轮模型看到
- CodeAct 的 `CODE_RESULT` 无法进入后续推理
- 子任务状态无法被 workflow 或 handoff 继续消费
- 长任务无法压缩上下文成本

这就是为什么 `Agent` 的 state 设计是架构问题，不是附加功能。

## 5. InMemory 和 JDBC 怎么选

### `InMemoryAgentMemory`

适合：

- 本地开发
- 单进程短任务
- 临时验证 runtime 行为

优点是简单，缺点是进程退出后状态就没了。

### `JdbcAgentMemory`

适合：

- 任务需要持久化
- 会话可能跨进程恢复
- 需要把 Agent 状态落库审计

如果你已经在 Spring Boot 环境中运行，JDBC 路线通常更贴近生产现实。

## 6. 压缩策略怎么理解

一旦会话变长，记忆管理就不只是“存下来”，还要考虑：

- token 成本
- 无关上下文噪声
- 工具结果膨胀
- 长任务的可恢复性

对应扩展点：

- `MemoryCompressor`
- `WindowedMemoryCompressor`
- `MemorySnapshot`

最简单的内置策略是窗口压缩，只保留最近 N 条 item。

如果你要更稳的工程方案，通常会走：

- 历史摘要 + 最近窗口
- 按任务阶段分段压缩
- 针对工具结果做选择性保留

## 7. state 不只等于 memory

在 `Agent` 语境里，state 至少包含四层：

- memory items
- 当前 step loop 位置
- workflow / handoff / team 的任务状态
- trace 和诊断附属信息

所以这页谈的是“任务如何持续”，而不是单纯“对话记录怎么存”。

## 8. 什么时候应该优先补这层设计

下面这些场景说明你不该再只依赖简单消息列表：

- 会话很长，需要压缩
- 工具很多，需要保留关键结果
- 任务有明确阶段和状态流转
- 你需要恢复、分叉、复盘
- 你要解释某一步为什么继续、为什么停止

## 9. 推荐连读

1. [Tools and Registry](/docs/agent/tools-and-registry)
2. [StateGraph](/docs/agent/orchestration/stategraph)
3. [Subagent Handoff](/docs/agent/orchestration/subagent-handoff)
4. [Trace](/docs/agent/observability/trace)

如果你想先回到“Agent 最小运行路径”，可以回看 [Agent Quickstart](/docs/agent/quickstart)。
