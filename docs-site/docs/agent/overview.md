---
sidebar_position: 1
---

# Agent Overview

`ai4j-agent` 是 AI4J 在 Core SDK 之上的通用 Agent runtime 模块。

如果 `ai4j` Core SDK 解决的是“如何统一访问模型、工具、MCP、RAG 与基础 memory”，那么 `ai4j-agent` 解决的就是“这些能力如何在一个多步、可持续、可观测的运行时里协同工作”。

这一章的核心主题不是模型接入，而是 runtime 组织：

- 一轮结束后是否继续
- 工具调用如何闭环
- 状态怎样跨步保存
- 何时需要 workflow、subagent、team
- 事件如何被 trace、CLI、TUI 或 ACP 消费

如果你的目标是做本地代码仓助手，而不是通用业务 Agent，请优先阅读 [Coding Agent](/docs/coding-agent/overview)。

## 1. Agent 在整个仓库里的位置

仓库中的相邻模块大致可以这样理解：

| 模块 | 主要职责 |
| --- | --- |
| `ai4j/` | 模型访问、Function Tool、MCP、RAG、向量、Audio/Image、基础 `ChatMemory` |
| `ai4j-agent/` | Agent runtime、tool loop、state、workflow、subagent、team、trace |
| `ai4j-coding/` | 面向代码仓任务的 outer loop、workspace-aware tools、session/compact/checkpoint |
| `ai4j-cli/` | CLI、TUI、ACP host |
| `ai4j-flowgram-*` | 图式工作流、任务 API、可视化节点后端整合 |

因此 `Agent` 不是“聊天接口升级版”，而是上层运行时。

## 2. 什么时候需要进入 Agent

当问题仍然只是“一次模型请求如何发出去”时，Core SDK 已经足够。

当问题开始变成下面这些 runtime 级问题时，就应该进入 Agent：

- 需要多步推理，而不是单次问答
- 需要模型自己决定是否调用工具
- 需要把工具结果回灌到下一轮推理
- 需要显式的 session 和状态隔离
- 需要 trace、event、retry、step budget
- 需要 StateGraph、SubAgent、Team 这类更高层编排

一句话判断：

> 当问题从“如何请求模型”升级为“如何组织运行时”时，就该进入 `Agent`。

## 3. 这章最核心的五个设计目标

### 3.1 让模型协议与执行策略解耦

`AgentModelClient` 负责模型协议，`AgentRuntime` 负责执行策略。这样：

- 模型可以切换 `Chat` / `Responses`
- runtime 可以切换 `ReAct` / `CodeAct` / `DeepResearch`
- provider 变更不会污染主循环

### 3.2 让工具声明面与执行面分离

`AgentToolRegistry` 只决定模型看见哪些工具，`ToolExecutor` 才决定工具如何执行。

这样做的直接好处是：

- 工具白名单清晰
- 权限审批可落在执行面
- MCP、本地函数、subagent tool surface 可以统一暴露

### 3.3 让 memory 成为状态源

runtime 每一轮都从 `AgentMemory` 读取上下文，并把新输入、模型输出、工具结果回写进去。

这让：

- session 可恢复
- 压缩策略可替换
- workflow / subagent / team 能围绕同一状态源协同

### 3.4 让 runtime 策略可替换

Agent 不假设所有任务都适合一种执行方式。

- `ReActRuntime` 适合标准工具循环
- `CodeActRuntime` 适合代码驱动的工具编排
- `DeepResearchRuntime` 适合规划增强型流程

### 3.5 让可观测性内建，而不是事后拼接

`STEP_START`、`MODEL_REQUEST`、`MODEL_RESPONSE`、`TOOL_CALL`、`TOOL_RESULT`、`FINAL_OUTPUT` 等事件都来自 runtime 内部，而不是外围猜测。

## 4. 代码地图

主路径：

- `ai4j-agent/src/main/java/io/github/lnyocly/ai4j/agent`

建议按下面顺序阅读。

### 4.1 入口与装配

- `Agents`
- `AgentBuilder`
- `Agent`
- `AgentSession`
- `AgentContext`

### 4.2 模型适配

- `model/AgentModelClient`
- `model/ChatModelClient`
- `model/ResponsesModelClient`

### 4.3 运行时策略

- `runtime/BaseAgentRuntime`
- `runtime/ReActRuntime`
- `runtime/CodeActRuntime`
- `runtime/DeepResearchRuntime`

### 4.4 工具治理

- `tool/AgentToolRegistry`
- `tool/ToolExecutor`
- `tool/ToolUtilRegistry`
- `tool/ToolUtilExecutor`
- `tool/AgentToolCallSanitizer`

### 4.5 状态与压缩

- `memory/AgentMemory`
- `memory/InMemoryAgentMemory`
- `memory/JdbcAgentMemory`
- `memory/MemoryCompressor`

### 4.6 编排与协作

- `workflow/*`
- `subagent/*`
- `team/*`

### 4.7 观测

- `event/*`
- `trace/*`

## 5. 最重要的对象关系

可以把 Agent 看成下面这条主链：

```text
Agents.builder()/react()/codeAct()/deepResearch()
  -> AgentBuilder.build()
  -> AgentContext
  -> AgentRuntime.run(...)
  -> AgentModelClient
  -> AgentToolRegistry / ToolExecutor
  -> AgentMemory
  -> AgentEventPublisher
```

其中三条最关键的边界是：

- `AgentBuilder` 负责装配，不负责执行循环
- `AgentToolRegistry` 负责声明面，不负责权限执行
- `AgentMemory` 负责上下文状态，不等于全部运行时状态

这三条边界是理解后续页面的基础。

## 6. 当前支持的主要能力面

### 6.1 单 Agent

最小可运行形态。适合：

- 业务问答
- 工具辅助任务
- 标准多轮推理

### 6.2 Session

通过 `Agent.newSession()` 派生独立 memory。适合：

- 用户会话隔离
- 长任务分叉
- 同一 Agent 配置复用

### 6.3 Runtime 策略切换

通过 `Agents.react()`、`Agents.codeAct()`、`Agents.deepResearch()` 或自定义 runtime 完成。

### 6.4 Workflow / StateGraph

适合“显式状态流转比自由推理更重要”的任务。

### 6.5 SubAgent / Team

适合把任务拆给其他 Agent 或多个成员协作执行的场景。

### 6.6 Trace

适合：

- 调试
- 回放
- 审计
- 运行时可视化

## 7. 与相邻模块的边界

### 7.1 与 Core SDK 的边界

Core SDK 负责：

- provider 接入
- Function Tool
- MCP
- `ChatMemory`
- RAG / Search / Vector

Agent 负责：

- step loop
- tool governance
- state continuation
- orchestration
- trace

因此 Agent 不是对 Core SDK 的替代，而是建立在 Core SDK 之上的运行时层。

### 7.2 与 Coding Agent 的边界

`Agent` 是通用智能体 runtime。

`Coding Agent` 在此之上继续增加：

- workspace-aware tools
- approval 模型
- session persistence / compact / checkpoint
- CLI / TUI / ACP host

如果你要做的是业务 Agent、平台 Agent 或服务端智能流程，先看 `Agent`；如果你要做的是本地代码仓助手，先看 `Coding Agent`。

### 7.3 与 Flowgram 的边界

`Agent` 更适合模型在运行时自由决定下一步。

`Flowgram` 更适合：

- 显式节点图
- 稳定输入输出 schema
- 平台化任务 API
- 可视化编辑与后端执行分层

## 8. 一个最小可运行路径

```java
Agent agent = Agents.react()
        .modelClient(new ResponsesModelClient(responsesService))
        .model("gpt-4.1")
        .systemPrompt("You are a careful assistant.")
        .instructions("Use tools only when necessary.")
        .toolRegistry(java.util.Arrays.asList("queryWeather"), null)
        .build();

AgentResult result = agent.run(AgentRequest.builder()
        .input("请给出北京今天的天气摘要")
        .build());
```

这段代码对应的真实语义是：

- `ResponsesModelClient` 负责模型协议
- `ReActRuntime` 负责主循环
- `toolRegistry(...)` 控制可见工具
- 默认 memory 为 `InMemoryAgentMemory`

## 9. 阅读路径

### 9.1 想先搞清楚 Agent 为什么存在

1. [Why Agent](/docs/agent/why-agent)
2. [Architecture](/docs/agent/architecture)

### 9.2 想先跑通一个最小可用 Agent

1. [Quickstart](/docs/agent/quickstart)
2. [Model Client Selection](/docs/agent/model-client-selection)
3. [Minimal ReAct Agent](/docs/agent/minimal-react-agent)

### 9.3 想理解 runtime 内部怎么工作

1. [Architecture](/docs/agent/architecture)
2. [Runtime Implementations](/docs/agent/runtime-implementations)
3. [Tools and Registry](/docs/agent/tools-and-registry)
4. [Memory and State](/docs/agent/memory-and-state)

### 9.4 想看更高层编排

1. [StateGraph](/docs/agent/orchestration/stategraph)
2. [Subagent Handoff Policy](/docs/agent/subagent-handoff-policy)
3. [Agent Teams](/docs/agent/agent-teams)

### 9.5 想看观测和调试

1. [Trace Observability](/docs/agent/trace-observability)
2. [Reference Core Classes](/docs/agent/reference-core-classes)

## 10. 这一章不回答什么

这一章不直接回答：

- 本地代码仓审批怎么做
- CLI / TUI / ACP 宿主细节
- Coding Agent 的 outer loop、checkpoint、compact
- Flowgram 平台后端如何建模

这些内容应分别到 `coding-agent` 或 `flowgram` 章节中理解。
