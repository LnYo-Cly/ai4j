---
sidebar_position: 1
---

# Agent Overview

`ai4j-agent` 是建立在 Core SDK 之上的 Java Agent runtime。它不替代模型调用层，而是解决“模型调用一次之后，系统如何继续推进任务”的问题。

如果你只是想发一条消息，先看 [Core SDK / Model Access](/docs/core-sdk/model-access/overview)。如果你需要多步推理、工具闭环、memory、workflow、trace 或多 agent 协作，再进入本章。

## 一句话定位

Agent 解决的是：

> 把模型、工具、memory、运行时策略和事件观测组织成一个可嵌入 Java 项目的多步执行单元。

它更适合做业务系统中的智能运行时，而不是本地代码仓助手。后者应该看 [Coding Agent](/docs/coding-agent/overview)。

## 什么时候需要 Agent

| 你遇到的问题 | 是否适合 Agent |
| --- | --- |
| 只需要一次 Chat 或 Responses 请求 | 不需要，先用 Core SDK |
| 需要模型根据中间结果继续调用工具 | 适合 |
| 需要把会话状态跨步骤保留 | 适合 |
| 需要显式 workflow、StateGraph、条件路由 | 适合 |
| 需要记录每一步模型和工具事件 | 适合 |
| 需要代码仓文件、shell、patch、审批、session store | 看 Coding Agent |
| 需要可视化流程图和 task API | 看 FlowGram |

## 最小心智模型

一次 Agent 运行可以理解成这条链：

```text
AgentRequest
  -> AgentRuntime
  -> AgentModelClient
  -> AgentToolRegistry + ToolExecutor
  -> AgentMemory
  -> AgentEventPublisher
  -> AgentResult
```

关键边界：

- `AgentModelClient` 负责把 Agent prompt 适配到 Chat 或 Responses。
- `AgentToolRegistry` 决定模型能看见哪些工具。
- `ToolExecutor` 决定工具调用时系统实际执行什么。
- `AgentMemory` 负责状态和历史。
- `AgentEventPublisher` 负责 trace 和宿主观测。

## 运行时选择

| Runtime | 适合什么 |
| --- | --- |
| ReAct | 通用多步推理、工具调用、文本任务 |
| CodeAct | 适合用代码作为中间表示的复杂任务 |
| DeepResearch | 更偏研究、规划和多轮资料整理 |
| StateGraph | 需要显式节点、分支、循环和状态流转 |

不要把 runtime 选型当成模型选型。模型决定“怎么生成”，runtime 决定“任务怎么推进”。

## 与相邻模块的边界

| 模块 | 负责什么 |
| --- | --- |
| `ai4j` | provider、Chat、Responses、Tool、MCP、RAG、基础 memory |
| `ai4j-agent` | runtime、tool loop、workflow、memory/state、trace、team |
| `ai4j-coding` | workspace-aware tools、审批、session、compact、代码仓任务 |
| `ai4j-cli` | CLI、TUI、ACP host 和本地会话入口 |
| `ai4j-flowgram-*` | 显式工作流图、task API、节点执行和前后端集成 |

Agent 是一层通用 runtime。它可以被 Coding Agent 和 FlowGram 复用，但它本身不负责宿主产品形态。

## 生产接入要注意什么

- 给 `maxSteps`、tool loop 和停止条件设置上限。
- 不要把所有 Tool / MCP 能力默认暴露给模型。
- memory 中不要保存真实密钥或未脱敏敏感数据。
- trace 可能包含 prompt、工具参数和模型输出，需要按场景脱敏。
- 多用户场景下，session 隔离不等于工具权限隔离。
- SubAgent 或 team orchestration 要有明确 handoff 和权限边界。

更多检查项见 [Security Overview](/docs/security/overview) 和 [Production Checklist](/docs/operations/production-checklist)。

## 推荐阅读顺序

### 想先判断是否需要 Agent

1. [Why Agent](/docs/agent/why-agent)
2. [Use Cases and Paths](/docs/agent/use-cases-and-paths)
3. [Architecture](/docs/agent/architecture)

### 想先跑通

1. [Quickstart](/docs/agent/quickstart)
2. [Model Client Selection](/docs/agent/model-client-selection)
3. [Minimal ReAct Agent](/docs/agent/minimal-react-agent)

### 想深入运行时

1. [Runtime Implementations](/docs/agent/runtime-implementations)
2. [Tools and Registry](/docs/agent/tools-and-registry)
3. [Memory and State](/docs/agent/memory-and-state)
4. [Trace Observability](/docs/agent/trace-observability)

### 想做编排和协作

1. [Workflow StateGraph](/docs/agent/workflow-stategraph)
2. [SubAgent Handoff Policy](/docs/agent/subagent-handoff-policy)
3. [Agent Teams](/docs/agent/agent-teams)

如果你想直接做本地代码仓任务，不要从这里硬扩展，直接看 [Coding Agent Overview](/docs/coding-agent/overview)。
