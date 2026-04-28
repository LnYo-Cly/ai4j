---
sidebar_position: 1
---

# Agent Overview

`ai4j-agent` 不是“比 Chat/Responses 再高级一点的 API 包装”，而是 AI4J 在 Core SDK 之上的通用 Agent runtime。

如果 Core SDK 负责的是：

- 怎么访问模型
- 怎么定义工具
- 怎么接 MCP / RAG / 基础 memory

那么 Agent 负责的是：

- 一轮之后要不要继续
- 工具调用怎么闭环
- 状态怎么跨步保留
- 不同执行策略怎么切
- 运行过程怎么暴露给 trace / CLI / TUI / ACP

这章的核心不是 provider 接入，而是运行时组织。

## 1. 先抓住 6 个关键设计决策

### 1.1 Agent 是 runtime 层，不是协议层

`ai4j-agent` 本身不直接回答：

- OpenAI 怎么调
- Doubao 怎么调
- Responses 和 Chat 请求长什么样

这些属于：

- `ai4j/`
- `AgentModelClient`

Agent 回答的是：

- prompt 如何反复重建
- 工具结果何时进入下一轮
- 何时停止
- 怎么把事件吐给宿主

### 1.2 这个模块的核心对象不是一个 `Agent` 类，而是一组边界清晰的对象

如果只把注意力放在 `Agent.run(...)`，很容易低估这层真正的设计。

真正重要的是这几条边界：

- `AgentBuilder` 装配
- `AgentRuntime` 推进
- `AgentModelClient` 协议适配
- `AgentToolRegistry` / `ToolExecutor` 工具治理
- `AgentMemory` 状态源
- `AgentEventPublisher` 可观测面

### 1.3 Session 隔离默认只换 memory

`Agent.newSession()` 不会重新创建整套运行环境，只会替换：

- `memory`

它不会替换：

- runtime
- modelClient
- toolRegistry
- toolExecutor
- systemPrompt / instructions

所以当前默认 session 边界是“状态隔离”，不是“能力隔离”。

### 1.4 Runtime 是可替换的，但不是完全独立的世界

`ReActRuntime`、`CodeActRuntime`、`DeepResearchRuntime` 并不是三套互不相干的产品。

当前实现里：

- ReAct 主要复用 `BaseAgentRuntime`
- DeepResearch 是 planning-enhanced ReAct
- CodeAct 则换掉了中间表示

也就是说，大部分通用行为仍然围绕同一条 runtime 基础设施展开。

### 1.5 Tool surface 和 execution surface 从一开始就被拆开了

这层设计非常关键：

- `AgentToolRegistry` 决定模型能看到什么
- `ToolExecutor` 决定调用时系统怎么执行

如果不先接受这个分层，后面理解权限审批、subagent handoff、MCP tool 暴露都会越来越乱。

### 1.6 Agent 已经内建事件面，但还不是完整宿主系统

`ai4j-agent` 已经能发：

- `STEP_START`
- `MODEL_REQUEST`
- `MODEL_RESPONSE`
- `TOOL_CALL`
- `TOOL_RESULT`
- `FINAL_OUTPUT`

但它还不自动拥有：

- workspace-aware 审批
- 进程管理
- checkpoint / compact outer loop
- CLI / TUI / ACP host

这些属于更上层的 `ai4j-coding` 和 `ai4j-cli`。

## 2. 这层到底在仓库里处于什么位置

从 monorepo 角度看，最实用的理解方式是把相邻模块放在一条能力梯度上：

| 模块 | 主要职责 |
| --- | --- |
| `ai4j/` | provider 接入、Chat/Responses、Function Tool、MCP、RAG、基础 memory |
| `ai4j-agent/` | Agent runtime、tool loop、state、workflow、subagent、team、trace |
| `ai4j-coding/` | 面向代码仓任务的 outer loop、workspace-aware tools、checkpoint/compact |
| `ai4j-cli/` | CLI、TUI、ACP host |
| `ai4j-flowgram-*` | 图式工作流、任务 API、可视化运行整合 |

所以：

- Core SDK 解决“单次能力调用”
- Agent 解决“多步运行时”
- Coding Agent 解决“代码仓宿主”
- Flowgram 解决“显式图式平台化流程”

## 3. 最重要的对象关系

把 Agent 压缩成一张图，最容易看清主链：

```text
Agents.builder()/react()/codeAct()/deepResearch()
  -> AgentBuilder.build()
  -> AgentContext
  -> Agent
  -> AgentRuntime.run(...)
  -> AgentModelClient
  -> AgentToolRegistry + ToolExecutor
  -> AgentMemory
  -> AgentEventPublisher
```

这些对象最核心的职责是：

| 对象 | 真正职责 |
| --- | --- |
| `Agents` | 提供几条官方入口 |
| `AgentBuilder` | 解析默认值并组装依赖 |
| `AgentContext` | 运行时配置快照 |
| `Agent` | 暴露 `run` / `runStream` / `newSession` |
| `AgentRuntime` | 定义一次 run 的推进语义 |
| `AgentModelClient` | 适配模型协议 |
| `AgentToolRegistry` | 暴露工具面 |
| `ToolExecutor` | 执行工具调用 |
| `AgentMemory` | 保存会话状态 |
| `AgentEventPublisher` | 广播运行事件 |

如果你先把这张图里的职责混掉，后面读源码会很痛苦。

## 4. 一个最小运行路径到底经过什么

最小 ReAct Agent 的代码看起来很短，但背后经过的链路并不短：

```java
Agent agent = Agents.react()
        .modelClient(new ResponsesModelClient(responsesService))
        .model("gpt-4.1")
        .systemPrompt("You are a careful assistant.")
        .options(AgentOptions.builder().maxSteps(1).build())
        .build();

AgentResult result = agent.run(AgentRequest.builder()
        .input("用一句话介绍 AI4J Agent")
        .build());
```

对应的真实执行链大致是：

1. `Agents.react()` 进入 `AgentBuilder`
2. `build()` 默认装配 `ReActRuntime`
3. 默认创建 `InMemoryAgentMemory`
4. 生成 `AgentContext`
5. `Agent.run(...)`
6. `ReActRuntime.run(...)`
7. `BaseAgentRuntime.runInternal(...)`
8. 用户输入写入 memory
9. 构建 `AgentPrompt`
10. `AgentModelClient` 请求模型
11. 若有工具调用则执行并回写 memory
12. 无工具调用则收口成 `AgentResult`

这就是为什么 Agent 不是简单的“模型 API 再包一层”。

## 5. 当前这章真正覆盖哪些能力面

### 5.1 单 Agent loop

这是主线能力，包括：

- 多步推进
- 工具回灌
- 最终结果收口

### 5.2 Session

默认通过替换 memory 做状态隔离，适合：

- 多用户会话
- 同配置多线程任务分叉
- 一份 Agent 配置复用到多份状态空间

### 5.3 Runtime 切换

当前最重要的 3 条线是：

- ReAct
- CodeAct
- DeepResearch

选的不是“模型强弱”，而是中间表示和推进语义。

### 5.4 Workflow / StateGraph

当“自由工具循环”已经不够表达业务结构时，就进入显式节点编排。

### 5.5 SubAgent / Team

分别对应：

- 受治理的主从委派
- 显式任务板和成员协作

### 5.6 Trace / Event

这是当前宿主观测这层 runtime 的主入口，不是事后补日志。

## 6. 与相邻模块的边界

### 6.1 与 Core SDK 的边界

Core SDK 仍然负责：

- provider 接入
- tool schema 基础设施
- MCP
- RAG / Search / Vector
- 更基础的 memory 能力

Agent 负责的是：

- step loop
- tool governance
- memory 驱动的持续运行
- higher-order orchestration
- event / trace

因此 Agent 不是 Core SDK 的替代品，而是建立在 Core SDK 之上的运行时层。

### 6.2 与 Coding Agent 的边界

Agent 是通用业务智能体 runtime。

Coding Agent 在此之上继续增加：

- workspace-aware tools
- approval / blocked 模型
- session persistence
- compact / checkpoint
- 终端宿主集成

如果你要做本地代码仓助手，通用 Agent 只是底座，不是最终形态。

### 6.3 与 Flowgram 的边界

Agent 更适合：

- 运行时自由决策
- 工具循环
- 主从委派

Flowgram 更适合：

- 显式节点图
- 稳定输入输出 schema
- 平台化任务 API
- 可视化后端执行

这两者不是互斥关系，但边界要分清。

## 7. 这章最适合怎么读

### 如果你刚判断“我可能需要 Agent”

先读：

1. [Why Agent](/docs/agent/why-agent)
2. [Architecture](/docs/agent/architecture)

### 如果你想先跑一个最小闭环

先读：

1. [Quickstart](/docs/agent/quickstart)
2. [Model Client Selection](/docs/agent/model-client-selection)
3. [Minimal ReAct Agent](/docs/agent/minimal-react-agent)

### 如果你已经开始排查 runtime 内部行为

先读：

1. [Architecture](/docs/agent/architecture)
2. [Runtime Implementations](/docs/agent/runtime-implementations)
3. [Tools and Registry](/docs/agent/tools-and-registry)
4. [Memory and State](/docs/agent/memory-and-state)

### 如果你已经进入显式编排和协作

先读：

1. [Workflow 与 StateGraph](/docs/agent/workflow-stategraph)
2. [SubAgent 与 Handoff Policy](/docs/agent/subagent-handoff-policy)
3. [Agent Teams](/docs/agent/agent-teams)

### 如果你要看调试和观测

先读：

1. [Trace 与可观测性](/docs/agent/trace-observability)
2. [Agent 核心类参考](/docs/agent/reference-core-classes)

## 8. 这一章不回答什么

这一章不直接回答：

- 本地代码仓审批怎么做
- CLI / TUI / ACP 宿主细节
- Coding Agent 的 compact / checkpoint / outer loop
- Flowgram 平台后端如何建模

这些问题都已经超出通用 Agent runtime 的边界。

## 9. 推荐阅读源码顺序

- `ai4j-agent/src/main/java/io/github/lnyocly/ai4j/agent/Agents.java`
- `ai4j-agent/src/main/java/io/github/lnyocly/ai4j/agent/AgentBuilder.java`
- `ai4j-agent/src/main/java/io/github/lnyocly/ai4j/agent/Agent.java`
- `ai4j-agent/src/main/java/io/github/lnyocly/ai4j/agent/AgentContext.java`
- `ai4j-agent/src/main/java/io/github/lnyocly/ai4j/agent/runtime/BaseAgentRuntime.java`
- `ai4j-agent/src/main/java/io/github/lnyocly/ai4j/agent/model/AgentModelClient.java`
- `ai4j-agent/src/main/java/io/github/lnyocly/ai4j/agent/tool/AgentToolRegistry.java`
- `ai4j-agent/src/main/java/io/github/lnyocly/ai4j/agent/memory/AgentMemory.java`
- `ai4j-agent/src/main/java/io/github/lnyocly/ai4j/agent/event/AgentEventPublisher.java`

## 10. 继续阅读

1. [Why Agent](/docs/agent/why-agent)
2. [Architecture](/docs/agent/architecture)
3. [Quickstart](/docs/agent/quickstart)
4. [Runtime Implementations](/docs/agent/runtime-implementations)
