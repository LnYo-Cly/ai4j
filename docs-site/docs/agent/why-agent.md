# Why Agent

`Agent` 这一层存在的原因，不是为了把 SDK 再包一层，而是为了解决 Core SDK 之上无法回避的运行时问题。

一旦系统从“一次请求一次回答”进入“多步推理、工具调用、状态延续、协作编排”，应用层就不再只是模型调用者，而是在实现一个 runtime。`ai4j-agent` 的价值就在这里。

## 1. 什么时候 Core SDK 已经足够

下面这些场景通常不需要 Agent：

- 单次问答或单次结构化生成
- 工具调用由业务代码显式决定，而不是由模型决定
- 工作流已经由应用层明确编排，模型只是其中一个节点
- 你只需要基础多轮上下文，而不需要 tool loop

此时直接使用 Core SDK 通常更简单、更可控。

## 2. 什么时候问题已经升级为 Agent 问题

当系统出现下面这些信号时，问题的本质已经不再是“怎么调模型”，而是“怎么组织运行时”：

- 一轮模型输出后，需要决定是否继续下一轮
- 模型返回工具调用后，需要统一校验、执行、回写
- 工具结果必须进入后续推理，而不是只返回给业务层
- 同一个任务需要跨多步保持状态
- 需要根据任务类型切换不同执行策略
- 需要 trace、event、session、subagent 或 team

这时继续用“手写一堆 if/else + 消息列表”往往会越来越脆弱。

## 3. Agent 解决的是哪几类工程问题

### 3.1 Loop 管理

Core SDK 负责单次请求；Agent 负责“本轮结束后怎么办”。

典型问题包括：

- 没有 tool call 时是否结束
- 有 tool call 时如何继续
- `maxSteps` 如何限制无界循环
- 流式模式下事件如何统一发出

### 3.2 Tool Governance

当模型开始主动调用工具后，系统需要明确：

- 模型能看到哪些工具
- 哪些工具真的允许执行
- 参数是否合法
- 工具失败后是中断还是回灌

这已经不是 SDK 层的 schema 问题，而是运行时治理问题。

### 3.3 State Continuation

多步执行必须定义状态语义：

- 用户输入怎么存
- 模型输出怎么存
- 工具输出怎么回灌
- 什么时候压缩
- session 如何隔离

Agent 用 `AgentMemory` 把这类问题显式化。

### 3.4 Strategy Selection

并不是所有任务都适合同一种中间表示：

- 有些任务适合标准 ReAct 文本循环
- 有些任务适合让模型先产代码再执行
- 有些任务适合先规划再推进

Agent 用 `AgentRuntime` 把执行策略独立成可替换模块。

### 3.5 Observability

如果没有标准事件流，系统很难知道：

- 当前卡在哪一步
- 模型是否重试
- 工具是否成功
- 最终输出是在第几步形成的

Agent 把这些事件变成一等能力，而不是事后日志拼接。

## 4. 为什么不是直接在业务代码里自己写一套

当然可以自己写一套，但问题会很快出现：

- 模型协议逻辑、工具治理逻辑、状态逻辑会纠缠在一起
- 一个项目里写出来的主循环难以复用到另一个项目
- 想从 ReAct 切到 CodeAct 时，往往要重写整条调用链
- trace、session、workflow、subagent 会变成额外补丁，而不是架构组成部分

`ai4j-agent` 的意义不是“神奇地让 Agent 更聪明”，而是把这些横跨多个项目的 runtime 共性抽成稳定边界。

## 5. 为什么是 AI4J 里的 Agent，而不是另一套独立框架

AI4J 这里的 Agent 有一个非常现实的设计目标：它必须与已有的 Java SDK 基座连续，而不是引入第二套完全不同的技术栈。

直接收益有三点：

- 继续复用 AI4J 的 provider、tool、MCP、RAG、memory 基础设施
- 继续留在 Java 8 兼容边界内，而不是强制升级运行时
- Core SDK、Agent、Coding Agent、Flowgram 可以沿同一能力面逐层向上构建

这和“引入一个独立 Agent Framework，再额外挂桥回业务系统”的路径不同。

## 6. Agent 带来的主要收益

### 6.1 统一主循环

`BaseAgentRuntime` 统一了：

- prompt 组装
- 模型调用
- tool call 归一化
- tool 执行
- memory 回写
- 事件发布

### 6.2 可替换策略

任务复杂度提升时，可以：

- 从 `ReActRuntime` 切到 `CodeActRuntime`
- 用 `DeepResearchRuntime` 先做规划
- 自己扩展新的 runtime

而不必推倒整个调用链。

### 6.3 统一状态语义

`AgentMemory` 让多步任务真正有一份可恢复、可压缩、可持久化的上下文状态，而不是只靠一串历史消息凑 prompt。

### 6.4 统一工具治理边界

`AgentToolRegistry` 和 `ToolExecutor` 分离后：

- 白名单暴露更清晰
- 权限审批更容易挂在执行面
- 本地函数、MCP、subagent tools 可以统一进入一条治理链

### 6.5 可观测与可调试

标准事件流让 Trace、CLI、TUI、ACP、外部观测系统可以消费同一执行语义。

## 7. Agent 不解决什么

Agent 并不是所有复杂问题的最终答案，它也有明确边界。

### 7.1 不替代 Core SDK

如果你只需要单次调用模型，Core SDK 仍然是更轻量、更直接的入口。

### 7.2 不自动变成产品级 coding assistant

Agent 有 tool loop，但不自动具备：

- workspace-aware 权限模型
- 进程管理
- checkpoint / compact
- CLI / TUI / ACP host

这些属于 `ai4j-coding` 和 `ai4j-cli`。

### 7.3 不替代显式 workflow 设计

当任务天然就是图式状态流转时，Agent 不是唯一答案。此时 `workflow` / `StateGraph` 或 `Flowgram` 可能更合适。

## 8. 使用 Agent 的代价和约束

进入 Agent 之后，系统复杂度会真实增加：

- 需要管理 step budget
- 需要管理 state 和 session
- 需要定义工具治理边界
- 需要做回归验证，不再只是验证一次 API 调用

所以“进入 Agent”是能力升级，也是工程责任升级。

## 9. 一个实用决策表

| 场景 | 更合适的选择 |
| --- | --- |
| 单次回答、单次结构化输出 | Core SDK |
| 模型按需调用工具、需要多轮闭环 | Agent |
| 本地代码仓交互、审批、会话恢复 | Coding Agent |
| 节点图式流程、任务 API、平台化执行 | Flowgram |

## 10. 继续阅读

如果你已经确认需要 Agent，建议按这个顺序继续：

1. [Agent Overview](/docs/agent/overview)
2. [Architecture](/docs/agent/architecture)
3. [Tools and Registry](/docs/agent/tools-and-registry)
4. [Memory and State](/docs/agent/memory-and-state)
5. [Runtime Implementations](/docs/agent/runtime-implementations)

如果你想先跑起来，再回来看设计，可以先看 [Quickstart](/docs/agent/quickstart)。
