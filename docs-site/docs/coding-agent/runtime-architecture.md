---
sidebar_position: 4
---

# Runtime 架构

`Coding Agent` 的 runtime，如果只画成“CLI 调 CodingAgent，再调模型”，会漏掉太多真正影响行为的层。  
从当前源码看，一次完整运行至少要经过 5 层：

1. factory 装配层
2. coding agent 构建层
3. session 执行层
4. host/runtime 交互层
5. MCP 外部工具运行层

这页的目标不是列模块名，而是讲清楚：**每层真正持有什么状态，决定什么行为，以及它和相邻层的边界是什么。**

## 1. 先看最外层总装配入口

CLI / TUI / ACP 当前真正的准备入口是：

- `ai4j-cli/.../DefaultCodingCliAgentFactory.java`

`prepare(...)` 的主线非常清楚：

1. `resolveProtocol(options)`
2. `createModelClient(options, protocol)`
3. `prepareMcpRuntime(options, pausedServers, terminal)`
4. `buildAgent(options, terminal, interactionState, modelClient, mcpRuntimeManager)`
5. 返回 `PreparedCodingAgent`

这意味着 runtime 不是在 `CodingAgent` 内部“自动长出来”的，而是由 CLI factory 把：

- 协议
- provider
- workspace
- MCP
- approval
- stream options

先装成一个可运行的环境。

## 2. factory 层到底决定了什么

`DefaultCodingCliAgentFactory` 当前至少决定 6 件事：

- 用 `ChatModelClient` 还是 `ResponsesModelClient`
- 当前 provider / baseUrl / apiKey 如何写入 `Configuration`
- 当前 workspace 对应的 `CliWorkspaceConfig`
- 是否初始化 `CliMcpRuntimeManager`
- approval decorator 怎么挂
- 是否注入 experimental subagent / delivery team

所以 factory 层不是“小小的对象创建器”，而是 runtime policy 的第一层入口。

一旦你切：

- provider
- protocol
- model
- MCP 可见集合
- approval mode

很多时候都不是改一个字段，而是重新组装整套 runtime。

## 3. `CodingAgentBuilder` 是真正把通用 Agent 变成 Coding Agent 的分叉点

factory 往里走，最关键的下一层是：

- `ai4j-coding/.../CodingAgentBuilder.java`

这个 builder 当前会额外完成：

- `WorkspaceContext` 解析
- `CodingSkillDiscovery.enrich(...)`
- `CodingAgentOptions` / `AgentOptions` 归一化
- built-in coding tool registry 创建
- built-in tool executor 创建
- `DefaultCodingRuntime` 创建
- custom tool 合并
- subagent / handoff 合并
- workspace system prompt 注入

最后才把这些东西交给：

- `AgentBuilder`

所以架构上更准确的描述是：

**Coding Agent = 通用 Agent 内核 + coding runtime 装配层。**

## 4. Coding Agent 内核层到底持有什么

如果只看 `CodingAgentBuilder.build()`，你能看到这层真正汇聚的对象是：

- `AgentModelClient`
- `WorkspaceContext`
- `CodingAgentOptions`
- `AgentToolRegistry`
- `ToolExecutor`
- `CodingRuntime`
- `SubAgentRegistry`
- `HandoffPolicy`

这比普通 Agent 多出来的关键状态是：

- workspace 语义
- coding-specific tools
- coding-specific runtime
- 子工作会话/委派能力

也就是说，Coding Agent 本身已经不是“单模型 + 单工具表”的简单执行器。

## 5. `DefaultCodingRuntime` 为什么是独立一层

很多人第一次看会把 `CodingRuntime` 误会成“宿主界面层”。  
其实不是。

- `ai4j-coding/.../runtime/DefaultCodingRuntime.java`

当前更像一个 coding 工作编排层。它负责：

- `delegate(...)`
- background task 调度
- child session 创建
- `CodingTask` 生命周期
- `CodingSessionLink` 持久化
- `CodingToolPolicyResolver` 应用
- runtime listeners

也就是说，这层关心的是：

- 任务怎样拆出去
- 子 session 怎样继承父上下文
- 子 agent 允许用哪些工具
- 前后台任务怎样跟踪

它不是 UI runtime，也不是模型 runtime，而是 **coding 工作运行时**。

## 6. session 执行层和 host runtime 不是同一层

这也是当前文档最容易混淆的地方。

### session 执行层

核心对象是：

- `CodingSession`
- `CodingAgentLoopController`

它们负责：

- 一次 prompt 如何变成多轮 outer loop
- 自动继续与停止
- compact / checkpoint
- process snapshots
- state export / restore

### host runtime

核心对象是：

- `CodingCliSessionRunner`
- `HeadlessCodingSessionRuntime`

它们负责：

- 用户输入如何进入 session
- 事件怎样呈现给 CLI/TUI/ACP
- 如何持久化与回放
- 审批交互如何和宿主通信

所以：

- `CodingSession` 是执行会话内核
- `CodingCliSessionRunner` / `HeadlessCodingSessionRuntime` 是宿主驱动器

两者不是一层。

## 7. `DefaultCodingSessionManager` 在架构里的位置是什么

`DefaultCodingSessionManager` 也经常被误归到 host UI 层。  
其实它更像是 session 生命周期服务。

它负责：

- `create`
- `resume`
- `fork`
- `save`
- `load`
- `list`
- `appendEvent`
- `listEvents`

并且会处理：

- workspace 匹配校验
- `rootSessionId / parentSessionId`
- `SESSION_CREATED / RESUMED / FORKED / SAVED` 事件

所以这层的正确定位是：

- 不负责推理
- 不负责 UI
- 负责 session 生命周期与账本

## 8. CLI/TUI 路径和 ACP/headless 路径为什么要分开

### CLI / TUI

主要由：

- `CodingCliSessionRunner`

来驱动。

它更偏交互式主控，职责包括：

- 接收用户输入和 slash command
- 管理当前 `ManagedCodingSession`
- 处理会话切换与 runtime 重建
- 驱动终端/TUI 渲染

### ACP / headless

主要由：

- `HeadlessCodingSessionRuntime`
- `AcpJsonRpcServer`

来驱动。

`HeadlessCodingSessionRuntime` 会把一次 prompt 转成：

- `USER_MESSAGE`
- `ASSISTANT_MESSAGE`
- `TOOL_CALL`
- `TOOL_RESULT`
- `AUTO_CONTINUE / AUTO_STOP / BLOCKED`
- `COMPACT`
- `ERROR`

等结构化事件。  
`AcpJsonRpcServer` 再把这些事件通过协议发给宿主。

所以 ACP 路径的重点不是终端体验，而是协议化事件流。

## 9. MCP runtime 为什么要独立成活的运行层

MCP 这层当前不是“读取配置文件然后拼几个工具定义”。

- `CliMcpRuntimeManager`

启动时会：

- 解析 resolved config
- 为每个 server 建 `CliMcpConnectionHandle`
- connect
- `listTools()`
- 校验工具名冲突
- 转成 OpenAI-style `Tool`
- 生成 `toolRegistry` 和 `toolExecutor`
- 维护 `connected / disabled / paused / error / missing` 状态

尤其它还显式防止与内置工具冲突：

- `bash`
- `read_file`
- `write_file`
- `apply_patch`

这说明 MCP 在 Coding Agent 里不是静态扩展点，而是一个 **可连接、可暂停、可失败、可重建** 的运行层。

## 10. `/experimental` 影响的是哪一层

这也是很容易写错的一个点。

`/experimental` 当前影响的不是：

- model client
- session manager
- TUI renderer

它影响的是 `DefaultCodingCliAgentFactory` 在构建 agent 时：

- 是否附加 experimental subagent
- 是否附加 delivery team surface

也就是说，它改变的是当前 session 对模型暴露的可调用 agent surface，而不是整个底层 runtime 语义。

## 11. 审批拦截在哪一层最合理

当前审批链是：

- `DefaultCodingCliAgentFactory`
  -> `CodingAgentOptions.toolExecutorDecorator`
  -> `CodingAgentBuilder.createBuiltInToolExecutor(...)`
  -> `CliToolApprovalDecorator` / `AcpToolApprovalDecorator`

这说明审批属于：

- 工具执行入口层

而不是：

- shell 层
- session manager 层
- UI renderer 层

然后 outer loop 再把审批拒绝转成 `BLOCKED_BY_APPROVAL`。  
这是一种很干净的分层：

- decorator 决定“能不能执行”
- loop 决定“被拒后会话怎么停”

## 12. runtime 为什么经常需要“重建”

在 Coding Agent 里，下面这些变化往往会触发 runtime rebuild：

- provider/profile 变化
- protocol 变化
- model 变化
- MCP server 状态变化
- workspace experimental 设置变化

原因不是 UI 任性，而是这些变化会直接影响：

- `AgentModelClient`
- `toolRegistry`
- `toolExecutor`
- approval decorator
- 可用 subagent surface

所以很多配置变更，本质上都是运行环境变更。

## 13. 从扩展角度看，优先改哪一层

如果你的需求是：

- 改 provider / protocol 装配
  先看 `DefaultCodingCliAgentFactory`

- 改 delegation / child session / background task 语义
  先看 `DefaultCodingRuntime`

- 改会话持久化与 replay
  先看 `DefaultCodingSessionManager` / `SessionEventStore`

- 改宿主流式事件行为
  先看 `HeadlessCodingSessionRuntime` 或 `CodingCliSessionRunner`

- 改 MCP 接入与冲突校验
  先看 `CliMcpRuntimeManager`

- 改审批策略
  先看 `ToolExecutorDecorator` 与 `CliToolApprovalDecorator`

这种按层修改，会比直接进 `CodingAgent` 主类乱改稳定得多。

## 14. 这页最该记住的结论

AI4J 当前的 Coding Agent runtime 不是单层系统，而是一条清晰的分层装配链：

- factory 层决定 provider、protocol、workspace、MCP、approval、experimental surface
- builder 层把这些装成 coding-specific agent 内核
- runtime 层负责 delegation 与子工作会话
- session 层负责 outer loop、compact、进程、state
- host 层负责 CLI/TUI/ACP 交互与事件呈现
- MCP runtime 层负责外部工具连接与冲突治理

把这条链看清楚后，你再去改功能，就知道该改哪一层，而不是把所有东西都往 `CodingAgent` 一个类里堆。

## 15. 推荐继续阅读

1. [会话、流式与进程](/docs/coding-agent/session-runtime)
2. [Tools 与审批机制](/docs/coding-agent/tools-and-approvals)
3. [Compact 与 Checkpoint 机制](/docs/coding-agent/compact-and-checkpoint)
4. [MCP 与 ACP](/docs/coding-agent/mcp-and-acp)
