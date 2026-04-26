---
sidebar_position: 1
---

# Agent 架构总览

`Agent` 对应仓库里的 `ai4j-agent/` 模块。

如果 `Core SDK` 解决的是“模型和能力怎么接”，那么 `Agent` 解决的就是“推理循环、工具调用、记忆、编排和观测怎么组织成一个可持续运行的智能体 runtime”。

如果你的目标是直接把本地代码仓跑成一个可交互的编码助手，请先看 [Coding Agent 专题](/docs/coding-agent/overview)。

## 1. 三分钟理解 Agent

先记住这四句话：

- `Agent` 是通用智能体 runtime，不是聊天接口的别名
- 它建立在 `Core SDK` 之上，但不替代 `Core SDK`
- 它重点解决 `runtime + tool loop + memory + orchestration + trace`
- 它适合业务智能体、任务编排和多步骤推理，不等于本地 coding assistant 产品

如果你要向别人解释 AI4J 的上层模块，`Agent` 的一句话定义应该是：

> 一个建立在 `ai4j` 基座之上的通用智能体运行时层。

## 2. 它到底解决什么问题

当你已经能调用模型和工具后，真正的难点通常会转成这些问题：

- 多轮推理什么时候继续、什么时候停止
- 工具该暴露哪些、怎么执行、怎么治理
- 会话记忆如何保存、压缩和恢复
- 单 Agent、SubAgent、Team、StateGraph 什么时候该选哪种编排方式
- 过程怎么 trace、怎么审计、怎么调试

`Agent` 这一层就是为了把这些 runtime 级问题收敛起来，而不是让每个业务项目自己再写一套 step loop。

## 3. 模块路径和能力地图

源码主模块：

- `ai4j-agent/src/main/java/io/github/lnyocly/ai4j/agent`

阅读源码时，最值得先记住的是这几块：

- `runtime`：`ReAct`、`CodeAct`、`DeepResearch` 等运行时策略
- `tool`：tool registry、executor、sanitizer、Function/MCP bridge
- `memory`：agent memory 与压缩
- `workflow`：顺序流与状态图编排
- `subagent`：handoff 与代理委派
- `team`：多成员协作
- `trace`：事件与 span 观测
- `model`：`AgentModelClient`、`ChatModelClient`、`ResponsesModelClient`

核心入口类包括：

- `Agent`
- `AgentBuilder`
- `AgentSession`
- `AgentContext`
- `Agents`

## 4. 和相邻模块的边界

### 4.1 和 Core SDK 的边界

`Core SDK` 负责：

- 模型访问
- `Function Call`
- `Skill`
- `MCP`
- `ChatMemory`
- RAG 与扩展点

`Agent` 在这之上增加的是：

- step loop
- tool decision/runtime policy
- agent memory
- orchestration
- trace

所以 `Agent` 不是“更高级的 SDK 页面”，而是上层 runtime。

### 4.2 和 Coding Agent 的边界

`Agent` 是通用智能体 runtime。

`Coding Agent` 则是在 `Agent` 之上继续针对本地代码仓任务增加：

- workspace-aware tools
- approvals
- session/process 管理
- CLI / TUI / ACP 宿主

如果你要做业务智能体、workflow agent 或服务端 runtime，先看 `Agent`。如果你要直接做本地代码仓交互产品，先看 `Coding Agent`。

### 4.3 和 Flowgram 的边界

`Agent` 偏自由推理与 runtime 组织。

`Flowgram` 偏可视化节点图、稳定输入输出 schema、后端任务 API 与平台接入。

如果你的任务天然更适合“节点图 + 明确流转”，优先看 `Flowgram`。如果更适合“模型在 runtime 中按需决定下一步”，优先看 `Agent`。

## 5. 运行时怎么选

### ReActRuntime

适合大多数通用业务 Agent：

- 文本任务 + 工具调用
- 多轮推理但不需要代码执行环境
- 接入成本最低

### CodeActRuntime

适合复杂工具链和结构化任务：

- 一次生成代码，代码内部多次调用工具
- 批量工具调用和数据加工更稳定
- 可以配合自定义沙箱

### DeepResearchRuntime

适合研究型或规划优先任务：

- 先规划
- 再分阶段收集证据
- 最后结构化汇总

## 6. 这层最容易混的三道边界

读 `Agent` 时，最容易混掉的是三层 memory：

- `ChatMemory`：`Core SDK` 的基础会话上下文
- `AgentMemory`：`Agent` runtime 的记忆层
- `CodingSession`：`Coding Agent` 的长期任务会话状态

如果这三层经常混掉，建议连读：

- [Core SDK / Memory Boundaries](/docs/core-sdk/memory/memory-and-tool-boundaries)
- [Agent / Memory and State](/docs/agent/memory-and-state)

## 7. 最小示例（可运行）

```java
Agent agent = Agents.react()
        .modelClient(new ResponsesModelClient(responsesService))
        .model("doubao-seed-1-8-251228")
        .systemPrompt("你是一个严谨的助手")
        .instructions("需要时再调用工具")
        .toolRegistry(java.util.Arrays.asList("queryWeather"), null)
        .build();

AgentResult result = agent.run(AgentRequest.builder()
        .input("请给出北京今天的天气摘要")
        .build());

System.out.println(result.getOutputText());
```

## 8. 推荐阅读顺序

建议按这个顺序进入：

1. [Why Agent](/docs/agent/why-agent)
2. [Agent Quickstart](/docs/agent/quickstart)
3. [Architecture](/docs/agent/architecture)
4. [Model Client Selection](/docs/agent/model-client-selection)
5. [Memory and State](/docs/agent/memory-and-state)
6. [Tools and Registry](/docs/agent/tools-and-registry)
7. [Minimal ReAct Agent](/docs/agent/runtimes/minimal-react-agent)
8. [Runtime Implementations](/docs/agent/runtimes/runtime-implementations)
9. [CodeAct Runtime](/docs/agent/runtimes/codeact-runtime)
10. [CodeAct Custom Sandbox](/docs/agent/runtimes/codeact-custom-sandbox)
11. [StateGraph](/docs/agent/orchestration/stategraph)
12. [SubAgent Handoff](/docs/agent/orchestration/subagent-handoff)
13. [Teams](/docs/agent/orchestration/teams)
14. [Trace](/docs/agent/observability/trace)
15. [Reference Core Classes](/docs/agent/reference-core-classes)

## 9. 如果你要拿它做生产能力

建议至少能回答这几个问题：

- 你的任务更适合 `ReAct`、`CodeAct` 还是 `DeepResearch`
- 哪些工具应该暴露，哪些不应该暴露
- 记忆层在哪里压缩、在哪里持久化
- 任务是单 Agent、StateGraph、SubAgent 还是 Team
- trace 和回归验证准备怎么做

如果你是第一次进入这一章，下一页建议直接看 [Why Agent](/docs/agent/why-agent)。
