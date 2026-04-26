---
sidebar_position: 3
---

# Memory 与 Tool 分层边界

AI4J 里最容易混的两组概念就是：

- `ChatMemory`、`AgentMemory`、`CodingSession memory`
- `Function Tool / ToolUtil`、`AgentToolRegistry / ToolExecutor`、`MCP Tool`、`Coding 内置工具`

如果这两组概念没有分清，项目很容易出现两类问题：

- 架构上选错层，把简单场景做得过重；
- 文档和代码都能跑，但边界理解错误，后续扩展会越来越乱。

这一页就是把这些分层一次性讲清楚。

---

## 1. 三层 Memory，不是同一个东西

### 1.1 `ChatMemory`

源码根包：

`ai4j/src/main/java/io/github/lnyocly/ai4j/memory`

关键类：

- `ChatMemory`
- `InMemoryChatMemory`
- `JdbcChatMemory`
- `ChatMemoryPolicy`
- `MessageWindowChatMemoryPolicy`
- `ChatMemorySnapshot`

它的定位是：

- 面向基础 `Chat / Responses` 调用的会话上下文容器；
- 适合业务层自己管理 `sessionId -> memory`；
- 不自带推理循环。

也就是说，它只解决：

- 历史消息怎么存
- 怎么输出成 `ChatCompletion.messages`
- 怎么输出成 `ResponseRequest.input`

它不解决：

- step loop
- tool 推理策略
- agent 任务状态
- coding session compact

### 1.2 `AgentMemory`

源码根包：

`ai4j-agent/src/main/java/io/github/lnyocly/ai4j/agent/memory`

关键类：

- `AgentMemory`
- `InMemoryAgentMemory`
- `JdbcAgentMemory`
- `MemoryCompressor`
- `WindowedMemoryCompressor`
- `MemorySnapshot`

它的定位是：

- 面向 Agent runtime 的内部状态源；
- 不只是用户/助手文本，还包含工具输出写回；
- 直接参与 runtime 的每一轮循环。

`AgentMemory` 的接口语义也更强：

- `addUserInput(...)`
- `addOutputItems(...)`
- `addToolOutput(...)`
- `getItems()`
- `getSummary()`

这说明它不是简单消息列表，而是 Runtime 驱动状态。

### 1.3 `CodingSession memory`

源码入口：

- `ai4j-coding/src/main/java/io/github/lnyocly/ai4j/coding/CodingSession.java`
- `ai4j-coding/src/main/java/io/github/lnyocly/ai4j/coding/CodingSessionState.java`
- `ai4j-coding/src/main/java/io/github/lnyocly/ai4j/coding/CodingSessionSnapshot.java`

它不是新的基础 memory 实现，而是建立在 `AgentMemory` 之上的会话运行体系。

除了 memory 本身，它还额外管理：

- checkpoint
- compact result
- process snapshots
- auto-compact breaker
- outer loop 决策

所以它更接近：

- “长期任务会话状态机”

而不只是：

- “消息记忆容器”

---

## 2. 什么时候用哪种 Memory

| 场景 | 适合的层 |
| --- | --- |
| 普通聊天、多轮问答、摘要、改写 | `ChatMemory` |
| 需要 Agent runtime 自己驱动工具推理循环 | `AgentMemory` |
| 需要代码仓任务、compact、resume、fork、process 管理 | `CodingSession` |

一句话判断：

- 如果你自己控制每轮请求，优先 `ChatMemory`
- 如果 Runtime 要自己推进下一步，进入 `AgentMemory`
- 如果是代码仓交付和宿主交互，进入 `CodingSession`

---

## 3. 基础 Tool 层：`ToolUtil`

源码入口：

- `ai4j/src/main/java/io/github/lnyocly/ai4j/tool/ToolUtil.java`
- `ai4j/src/main/java/io/github/lnyocly/ai4j/tool/ResponseRequestToolResolver.java`

这层解决的是：

- 基础 `Chat / Responses` 请求怎么暴露工具
- Function 工具、本地 MCP 工具、远程 MCP 工具怎么统一

### 3.1 `ToolUtil` 做什么

`ToolUtil` 当前会：

- 扫描和注册传统 `@FunctionCall` 工具
- 扫描和注册本地 MCP 工具
- 在运行时统一执行工具
- 必要时通过 `McpGateway` 调远程 MCP 服务

所以它是“基础服务层的工具桥”。

### 3.2 `ResponseRequestToolResolver` 做什么

`Responses` 请求里经常会同时出现：

- `functions`
- `mcpServices`
- `tools`

`ResponseRequestToolResolver` 的职责是：

- 把 `functions / mcpServices` 转成标准 `tools`
- 合并回 `ResponseRequest`

也就是说，它主要解决的是：

- `Responses API` 的请求装配问题

而不是：

- Agent runtime 的工具治理

---

## 4. Agent Tool 层：`AgentToolRegistry + ToolExecutor`

源码根包：

`ai4j-agent/src/main/java/io/github/lnyocly/ai4j/agent/tool`

关键类：

- `AgentToolRegistry`
- `ToolExecutor`
- `ToolUtilRegistry`
- `ToolUtilExecutor`
- `StaticToolRegistry`
- `CompositeToolRegistry`

这里有一个非常重要的边界：

- `AgentToolRegistry` 决定“模型能看到哪些工具”
- `ToolExecutor` 决定“工具被调用后如何执行”

基础层的 `ToolUtil` 只是其中一种执行后端，不是唯一执行模型。

### 4.1 `ToolUtilRegistry / ToolUtilExecutor`

这两个类是 Agent 层和基础 `ToolUtil` 之间的桥：

- `ToolUtilRegistry`：把 `ToolUtil.getAllTools(...)` 包装成 `AgentToolRegistry`
- `ToolUtilExecutor`：把 `ToolUtil.invoke(...)` 包装成 `ToolExecutor`

所以：

- 如果你要在 Agent 里继续复用基础 Function/MCP 工具，这两类桥最省事；
- 如果你要做更严格的治理、审批、沙箱、路由，就不应该只停留在 `ToolUtil`。

---

## 5. Coding Tool 层：workspace-aware 工具

源码入口：

- `ai4j-coding/src/main/java/io/github/lnyocly/ai4j/coding/tool/CodingToolRegistryFactory.java`
- `ai4j-coding/src/main/java/io/github/lnyocly/ai4j/coding/tool/CodingToolNames.java`
- `ai4j-coding/src/main/java/io/github/lnyocly/ai4j/coding/CodingAgentBuilder.java`

`Coding Agent` 不直接复用基础 `ToolUtil` 作为主工具层。

原因很简单：

- 它需要 workspace-aware 工具
- 它需要审批、路径约束、进程管理、patch 语法约束
- 它需要 delegate / subagent / runtime 级路由

当前内置工具包括：

- `bash`
- `read_file`
- `write_file`
- `apply_patch`

它们通过 `CodingToolRegistryFactory` 暴露 schema，通过 `CodingAgentBuilder.createBuiltInToolExecutor(...)` 挂执行器，再与 delegate tools、subagent tools 和 MCP tools 合并。

所以这层的关键词不是“通用工具调用”，而是：

- workspace constraints
- runtime routing
- approval decoration

---

## 6. MCP 在三层里分别扮演什么角色

MCP 不是单独的一套 Agent runtime，它在不同层的角色不同。

### 6.1 在基础服务层

MCP 是 `ToolUtil` 可以消费的一类工具来源。

也就是：

- 本地 MCP 工具
- 远程 MCP server 暴露的工具

### 6.2 在 Agent 层

MCP 只是 `AgentToolRegistry` / `ToolExecutor` 的一种工具来源。

你可以：

- 继续用 `ToolUtilRegistry / ToolUtilExecutor`
- 或者把 MCP 工具自行封装进自定义 registry / executor

### 6.3 在 Coding Agent 层

MCP 是宿主 runtime 可以动态挂载的一组外部工具。

这里还会涉及：

- MCP runtime manager
- 当前 session runtime 的工具可见性
- CLI / ACP 的 pause / resume / reconnect

所以 Coding Agent 里的 MCP，已经不只是“某个工具列表”，而是“会影响当前宿主会话”的活 runtime。

---

## 7. 一张分层图

```text
基础服务层
  Chat / Responses / ToolUtil / ChatMemory / MCP gateway

Agent 层
  BaseAgentRuntime / AgentMemory / AgentToolRegistry / ToolExecutor

Coding Agent 层
  Workspace tools / CodingSession / checkpoint / compact / outer loop / host runtime
```

这三层关系是：

- 下层可被上层复用
- 上层不是下层的简单别名
- 同名概念在不同层里语义强度不同

---

## 8. 常见误区

### 8.1 “我已经有 `ChatMemory`，是不是就等于 Agent memory 了？”

不是。

`ChatMemory` 更像基础会话容器，`AgentMemory` 是 runtime 状态源。

### 8.2 “我有 `ToolUtil`，是不是就不需要 `AgentToolRegistry` 了？”

不是。

`ToolUtil` 是基础桥，`AgentToolRegistry / ToolExecutor` 才是 Agent 层的治理抽象。

### 8.3 “MCP 就是 Agent 的工具系统吗？”

不是。

MCP 是工具来源或传输协议，不等于整个工具治理模型。

### 8.4 “Coding Agent 只是 Agent + 几个文件工具吗？”

不是。

它额外解决了：

- workspace prompt
- session tree
- compact / checkpoint
- host runtime
- CLI / TUI / ACP 交互

---

## 9. 推荐连读

1. [模块架构与包地图](/docs/ai-basics/architecture-and-package-map)
2. [统一服务入口与调用方式](/docs/ai-basics/unified-service-entry)
3. [ChatMemory：基础会话上下文](/docs/ai-basics/chat/chat-memory)
4. [Memory 记忆管理与压缩策略](/docs/agent/memory-management)
5. [Compact 与 Checkpoint 机制](/docs/coding-agent/compact-and-checkpoint)
6. [MCP 总览](/docs/mcp/overview)
