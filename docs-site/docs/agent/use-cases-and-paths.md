---
sidebar_position: 3
---

# Agent 使用路径与场景选择

这页回答的不是“哪个能力更强”，而是“当前任务应该落在哪个抽象层”。

在 `ai4j-agent` 里，`ReAct`、`CodeAct`、`Workflow`、`SubAgent`、`Teams` 并不是同一层的五个功能按钮，而是五种不同的控制边界：

- `ReAct` / `CodeAct` 是 runtime 级差异
- `Workflow` 是 Agent 之上的编排层
- `SubAgent` 是单 Agent 的受控委派能力
- `Teams` 是独立的协作运行模型

如果这个边界一开始没分清，最常见的结果就是：

- 本来只要 ReAct，却过早上 Team
- 本来该做 Workflow，却把流程硬塞进 prompt
- 本来只是委派一个专门子任务，却把整个系统改成多成员协作

## 1. 先按“系统结构改变了什么”来选

最短判断方式不是看功能列表，而是看你引入新能力后，源码里的主控制面会发生什么变化。

| 入口 | 真正改变的控制面 | 适合解决的问题 |
| --- | --- | --- |
| `ReAct` | `AgentRuntime` 仍然是标准 `BaseAgentRuntime` loop | 单 Agent、多步推理、少量工具 |
| `CodeAct` | runtime 改成“模型产代码再执行”的闭环 | 复杂工具编排、结构化中间程序 |
| `Workflow / StateGraph` | 在 Agent 之上增加显式节点和边 | 固定节点流、审批、路由、回退 |
| `SubAgent` | 在单 Agent 工具面里注入受控 handoff | 主 Agent 仍是唯一调度者，但需要专门子角色 |
| `Teams` | 引入 planner / task board / message bus / synthesizer | 多角色长期协作、任务认领与转派 |

这个表里最关键的一行是：

- `Workflow` 和 `Teams` 不是 `ReAct` 的“增强版”

它们改变的是系统结构，而不是 prompt 多几句说明。

## 2. `ReAct`：默认起点，不是低配版

适合：

- 一次目标
- 少量工具
- 不需要显式节点流转
- 不需要代码执行环境
- 不需要多角色协作

为什么它应该是默认起点：

- `AgentBuilder.build()` 默认 runtime 就是 `ReActRuntime`
- `ReActRuntime` 几乎完全复用 `BaseAgentRuntime`
- memory、tool result 回写、事件发布、并行工具调用这些基础语义都已经具备

因此很多任务的最优解不是“再上更高级的抽象”，而是：

- 把 tool surface、prompt、memory policy 先在 ReAct 层调对

不适合继续停在 ReAct 的信号通常是：

- 工具调用顺序开始变得像一个程序
- 输出不再只是自然语言，而是要依赖中间代码或固定阶段产物
- 提示词里开始出现大量“先做 A，再做 B，失败则做 C”之类的流程描述

继续看：

- [Minimal ReAct Agent](/docs/agent/minimal-react-agent)

## 3. `CodeAct`：你需要的是中间程序，而不是更多文字链路

适合：

- 模型先写一段临时代码
- 代码里多次调用工具
- 复杂处理逻辑更适合交给代码，而不是让模型纯文本多轮推理

它和 ReAct 的本质差别不是“多了个 code executor”，而是执行语义变了：

- ReAct 是“模型直接给 tool call”
- CodeAct 是“模型先产代码，再由代码驱动工具和计算”

所以以下信号通常说明应该从 ReAct 升到 CodeAct：

- 单轮里要对同一批数据做多次处理
- 需要循环、过滤、格式化、聚合
- 工具之间的控制逻辑开始更像脚本 than prompt

但也不要把 CodeAct 当成一切复杂任务的默认答案。它的代价是：

- 执行环境安全边界更重要
- runtime 语义更复杂
- 代码执行失败和工具失败的排障面更大

继续看：

- [CodeAct Runtime](/docs/agent/codeact-runtime)
- [CodeAct 自定义沙箱](/docs/agent/codeact-custom-sandbox)

## 4. `Workflow / StateGraph`：你需要显式节点，不需要模型假装流程图

适合：

- 任务天然就是多节点过程
- 节点之间存在固定分工
- 有条件路由、循环、审批、失败回退
- 你希望流程结构可见、可测、可改，而不是埋在 prompt 里

这里最重要的边界是：

- Workflow 不是 runtime 变体
- 它是 Agent 之上的编排层

所以当你的问题已经是“节点怎么接、边怎么选、状态怎么传”，再继续在 ReAct prompt 里描述流程，通常只会让系统更脆弱。

典型信号：

- 你已经能画出流程图
- 你想明确知道当前卡在哪个节点
- 你需要固定的节点输入输出语义

不建议一开始就上 Workflow 的情况：

- 只是普通问答 + 工具
- 还没确定工具白名单和基本行为
- 实际上没有明确节点边界，只是任务略复杂

继续看：

- [Workflow StateGraph](/docs/agent/workflow-stategraph)
- [天气工作流 Cookbook](/docs/agent/weather-workflow-cookbook)

## 5. `SubAgent`：主 Agent 仍是唯一大脑，只是开始受控委派

适合：

- 主 Agent 仍然负责统一调度
- 只是某些子任务想交给专门子 Agent
- 需要 handoff policy、深度限制、deny/allow 规则

这和 Team 的关键区别是：

- SubAgent 仍然是单 Agent 体系里的一个工具面扩展
- 子 Agent 不是平权长期协作成员

如果你的系统形态仍然是“一个主 Agent + 若干专门 delegate”，优先考虑 SubAgent，而不是直接上 Team。

继续看：

- [Subagent Handoff Policy](/docs/agent/subagent-handoff-policy)

## 6. `Teams`：不是“更多 agent”，而是协作组织模型

适合：

- 多个角色长期存在
- 需要 planner / synthesizer / task board
- 成员之间要发消息、认领任务、释放、转派
- 单一主 Agent 的 handoff 模型已经不够表达业务结构

Team 的本质变化是：

- 系统不再只是“一个 agent 偶尔委派别人”
- 而是引入了显式协作运行模型

因此，不应该因为“以后可能会复杂”就一开始上 Team。真正适合 Team 的信号通常是：

- 你已经明确有长期角色分工
- 任务需要共享任务板而不是临时 handoff
- 需要记录成员间协作过程，而不是只关心最终回答

继续看：

- [Agent Teams](/docs/agent/agent-teams)
- [Agent Teams API Reference](/docs/agent/agent-teams-api-reference)

## 7. 一张更实用的决策表

| 任务形态 | 更适合的入口 | 不该先上的层 |
| --- | --- | --- |
| 一次任务 + 少量工具 | ReAct | Teams |
| 多步数据处理，最好写成临时代码 | CodeAct | Teams |
| 显式节点、条件边、审批流 | Workflow / StateGraph | Teams |
| 主 Agent 偶尔把专门子任务委派出去 | SubAgent | Teams |
| 多角色长期协作、共享任务板 | Teams | 用 ReAct / SubAgent 硬撑 |

这个表背后的原则只有一条：

- 先选满足当前需求的最小正确抽象层

不是最强，也不是最全，而是最小正确。

## 8. 一个更稳的演进顺序

真实工程里，更稳的顺序通常是：

1. 先跑通单 Agent + 明确工具白名单
2. 再判断是升级到 CodeAct，还是升级到 Workflow
3. 确认需要委派后，再引入 SubAgent
4. 只有当协作结构已经明确时，再上 Teams
5. 全程补齐 trace、session 和回归

这样做的好处是：

- 基础 loop 先稳定
- 工具边界先稳定
- 每次只引入一层新复杂度

## 9. 常见误判

### 9.1 “任务复杂，所以直接上 Team”

复杂不等于协作。很多复杂任务的真实需求只是：

- CodeAct
- Workflow

而不是多角色组织模型。

### 9.2 “有两个阶段，所以一定要 Workflow”

如果两个阶段只是一次顺序处理，而且没有明确状态分支、路由或失败恢复，CodeAct 或单 Agent 也可能更合适。

### 9.3 “需要委派，所以一定是 Team”

如果只有一个主调度者，且子任务只是工具化委派，SubAgent 通常更合适。

### 9.4 “ReAct 太简单，不够高级”

ReAct 不是 demo 层，而是默认 runtime 语义。很多生产任务真正需要改的是：

- prompt
- tool executor
- memory policy

而不是把系统整体升级到更高抽象层。

## 10. 推荐阅读顺序

1. [最小 ReAct Agent](/docs/agent/minimal-react-agent)
2. [Agent 架构总览](/docs/agent/architecture)
3. [Runtime 实现详解](/docs/agent/runtime-implementations)
4. [Workflow StateGraph](/docs/agent/workflow-stategraph)
5. [SubAgent 与 Handoff Policy](/docs/agent/subagent-handoff-policy)
6. [Agent Teams](/docs/agent/agent-teams)
