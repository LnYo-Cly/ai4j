---
sidebar_position: 3
---

# Agent 使用路径与场景选择

如果你已经知道自己要做的是“智能体”，但还不确定应该从 `ReAct`、`CodeAct`、`Workflow`、`SubAgent` 还是 `Teams` 开始，这一页就是入口。

目标很简单：

- 不让第一次接 Agent 的人一上来就选错抽象层
- 让你先按任务形态选路线，再进入具体 API

---

## 1. 最常见的五条路径

### 路径 1：单 Agent + 少量工具

适合：

- 普通问答
- 简单工具调用
- 先验证模型和工具白名单

先看：

- [最小 ReAct Agent](/docs/agent/minimal-react-agent)

这是默认起点，也是绝大多数业务 Agent 的第一步。

### 路径 2：代码驱动、多工具批处理

适合：

- 让模型先产出代码
- 再由代码内部多次调工具
- 做复杂结构化加工

先看：

- [CodeAct Runtime](/docs/agent/codeact-runtime)
- [CodeAct 自定义沙箱](/docs/agent/codeact-custom-sandbox)

### 路径 3：有显式分支、循环、条件路由

适合：

- 审批流
- 多阶段工作流
- 有固定节点与条件判断的任务

先看：

- [Workflow StateGraph](/docs/agent/workflow-stategraph)
- [天气工作流 Cookbook](/docs/agent/weather-workflow-cookbook)

### 路径 4：主 Agent 委派子 Agent

适合：

- 需要主从分工
- 需要 handoff 治理
- 需要限制子任务深度与回退策略

先看：

- [SubAgent 与 Handoff Policy](/docs/agent/subagent-handoff-policy)

### 路径 5：多角色协作

适合：

- Lead / Member 多成员协同
- 共享任务板
- 成员间消息通信与任务认领

先看：

- [Agent Teams](/docs/agent/agent-teams)
- [Agent Teams API Reference](/docs/agent/agent-teams-api-reference)

---

## 2. 一个更直接的判断方法

可以先用下面这张表快速判断。

| 任务形态 | 推荐入口 | 不建议一开始就用 |
| --- | --- | --- |
| 一次任务 + 少量工具 | ReAct | Teams |
| 模型要产代码再执行 | CodeAct | Teams |
| 需要显式节点编排 | Workflow / StateGraph | Teams |
| 主从委派与受控 handoff | SubAgent | Teams |
| 多成员长期协作 | Agent Teams | 先别从最小 ReAct 反复硬撑 |

核心原则只有一条：

- 先选满足当前需求的最小抽象层

不要把“以后可能会复杂”当成今天直接上 `Teams` 的理由。

---

## 3. 典型演进顺序

AI4J Agent 更适合按下面顺序演进：

1. `ReAct`
2. `CodeAct` 或 `Workflow`
3. `SubAgent`
4. `Teams`
5. `Trace`、治理、在线观测全量接入

这个顺序的好处是：

- 模型与工具白名单先稳定
- 编排复杂度逐步增加
- 出问题时更容易定位在哪一层

---

## 4. 什么时候该停在 ReAct

满足下面条件时，先不要升级抽象层：

- 工具数量很少
- 没有显式分支/循环
- 不需要代码执行环境
- 不需要多成员协作

这类任务里，ReAct 往往已经是最优解。

---

## 5. 什么时候该上 CodeAct

下面几类任务，CodeAct 更合适：

- 需要生成一段中间代码
- 需要在代码里多次调用工具
- 需要把复杂处理逻辑交给代码完成，而不是靠多轮文本推理硬撑

换句话说，CodeAct 更像“让模型写一个临时执行程序来完成任务”。

---

## 6. 什么时候该上 Workflow

当任务已经天然是“节点流”时，优先考虑 Workflow，而不是让一个大 Agent 在提示词里模拟流程图。

典型场景：

- 审批
- 路由
- 条件分支
- 失败回退
- 可视化流程编排

---

## 7. 什么时候该上 SubAgent / Teams

### SubAgent

适合：

- 主 Agent 仍然是核心调度者
- 只是把特定任务委派给专门子 Agent

### Teams

适合：

- 多成员都是长期存在的角色
- 需要任务板、认领、转派、广播

不要把 `SubAgent` 和 `Teams` 混成一个概念。

前者偏“受控委派”，后者偏“协作组织”。

---

## 8. 开发顺序建议

无论最终想做到哪一层，开发顺序都建议是：

1. 先跑一个最小可观测单 Agent
2. 明确工具白名单
3. 跑一套 session / trace / 回归测试
4. 再升级运行时或编排层

这会比一开始就上复杂编排稳得多。

---

## 9. 推荐阅读

1. [最小 ReAct Agent](/docs/agent/minimal-react-agent)
2. [自定义 Agent 开发指南](/docs/agent/custom-agent-development)
3. [Runtime 实现详解](/docs/agent/runtime-implementations)
4. [Workflow StateGraph](/docs/agent/workflow-stategraph)
5. [SubAgent 与 Handoff Policy](/docs/agent/subagent-handoff-policy)
6. [Agent Teams](/docs/agent/agent-teams)
