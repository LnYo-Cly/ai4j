---
sidebar_position: 4
---

# Runtime 架构

`Coding Agent` 不只是一个 `CodingAgent` 实例。

真正跑起来时，至少有四层运行时一起工作：

- Agent runtime：模型推理、工具调用、session state；
- Session runtime：一次 prompt 如何进入会话、如何流式回放、如何写事件账本；
- Host runtime：CLI、TUI、ACP 这些宿主如何驱动会话；
- MCP runtime：外部 MCP server 如何连进来并暴露成工具。

---

## 1. 先看整体结构

```text
CodeCommand / AcpCommand
    -> CodeCommandOptions
    -> CodingCliAgentFactory
        -> AgentModelClient
        -> CodingAgentBuilder
        -> CliMcpRuntimeManager
    -> CodingSessionManager
    -> Host runtime
        -> CodingCliSessionRunner   (CLI / TUI)
        -> HeadlessCodingSessionRuntime (ACP / headless)
```

对应代码中的主要入口：

- `DefaultCodingCliAgentFactory`
- `CodingCliSessionRunner`
- `HeadlessCodingSessionRuntime`
- `DefaultCodingSessionManager`
- `CliMcpRuntimeManager`
- `AcpJsonRpcServer`

---

## 2. Agent runtime 负责什么

最底层的核心仍然是 `CodingAgentBuilder`。

在 `DefaultCodingCliAgentFactory` 里，当前运行时会完成这些事情：

- 根据 `provider + protocol` 构造 `ChatModelClient` 或 `ResponsesModelClient`；
- 组装 `WorkspaceContext`；
- 注入 `systemPrompt`、`instructions`、采样参数和并行 tool 配置；
- 打开 auto compact、max steps、stream execution 等 agent 选项；
- 挂入 `toolExecutorDecorator` 做审批；
- 根据 workspace 配置决定是否注入实验性的 `subagent_background_worker` 与 `subagent_delivery_team`；
- 如果 MCP 已连接，再把 MCP tools 挂到 `toolRegistry` 和 `toolExecutor`。

也就是说，`CodingAgent` 本身更像“执行内核”，但它并不负责：

- CLI slash command；
- TUI 界面渲染；
- ACP JSON-RPC；
- session 持久化文件；
- MCP 配置解析。

这些都在外层 runtime。

这里还有一个容易混淆的边界：

- `BaseAgentRuntime` 仍然只负责单轮 tool-loop；
- `CodingSession` 在 `ai4j-coding` 层额外挂了 `CodingAgentLoopController`，做受控的 outer loop；
- `ChatModelClient` 在 agent/coding 路径下会自动打开 `passThroughToolCalls`，让 chat provider 把 `tool_calls` 交回 runtime，而不是在 provider 适配层直接执行。

### 2.1 `/experimental` 改变的是哪一层

`/experimental` 不是单独的 UI 开关，而是 `DefaultCodingCliAgentFactory` 的 runtime 组装输入。

当前实现里：

- `subagent` 打开时，会额外挂入 `subagent_background_worker`
- `agent-teams` 打开时，会额外挂入 `subagent_delivery_team`
- 这两个开关都持久化在 `<workspace>/.ai4j/workspace.json`
- CLI / TUI / ACP 切换开关后，都会立即重建当前 session runtime

这里要特别区分：

- 固定内置本地 Tool 仍然只有 `bash / read_file / write_file / apply_patch`
- `/experimental` 改变的是当前 session 额外可见的 agent delegation surface
- `subagent_delivery_team` 背后不是一个普通函数，而是一个真实 `AgentTeam` 包装后的 subagent

所以 `/experimental` 的影响范围是：

- 当前模型能看到哪些“可调用工具面”
- 当前会话是否会把某些复杂任务委派给 subagent / team runtime

而不是：

- provider/profile 配置
- TUI 渲染壳
- 底层 `BaseAgentRuntime` 的单轮 tool-loop 语义

### 2.2 审批拦截发生在哪一层

审批不是一个独立的 shell hook 层，而是 runtime 在组装 Tool executor 时挂进去的一层 decorator。

当前链路可以直接理解为：

```text
CodeCommandOptionsParser
    -> ApprovalMode
    -> DefaultCodingCliAgentFactory
        -> CodingAgentOptions.toolExecutorDecorator
        -> CodingAgentBuilder.createBuiltInToolExecutor(...)
            -> decorate(read_file / write_file / apply_patch / bash)
                -> CliToolApprovalDecorator or AcpToolApprovalDecorator
```

这段语义很重要，因为它决定了两件事：

- 审批发生在 Tool 执行入口，而不是操作系统层
- CLI/TUI 和 ACP 可以共享同一套审批策略，但走不同的宿主交互通道

所以如果你要改审批规则，优先看的不是 shell executor，而是：

- `ToolExecutorDecorator`
- `CliToolApprovalDecorator`
- `AcpToolApprovalDecorator`

---

## 3. Session runtime 负责什么

### 3.1 CLI / TUI 模式

CLI 和 TUI 的主运行时是 `CodingCliSessionRunner`。

它负责：

- 接收用户输入和 slash command；
- 持有当前 `ManagedCodingSession`；
- 驱动 `agent.newSession()` / resume / fork 后的会话切换；
- 驱动一次用户 prompt 下的多轮 outer loop continuation；
- 把模型事件渲染到终端或 TUI；
- 管理 `/processes`、`/events`、`/replay`、`/history` 这类会话级命令；
- 在 provider、profile、model、MCP 状态变化时重建当前 session runtime。

因此它不是“界面层小工具”，而是 CLI/TUI 的主控 runtime。

### 3.2 ACP / Headless 模式

ACP 不走 `CodingCliSessionRunner`，而是走 `HeadlessCodingSessionRuntime`。

它负责：

- 接收 `session/prompt`；
- 生成 turnId；
- 将用户输入写入 session event；
- 调用 `session.getSession().runStream(...)`；
- 把 reasoning、assistant text、tool call、tool result 转成宿主可消费的事件；
- 追加 `AUTO_CONTINUE`、`AUTO_STOP`、`BLOCKED` 这类 outer loop 决策事件；
- 处理 cancel；
- 在结束后写入 compact / error / save。

所以 ACP 的 runtime 重点不是终端交互，而是“结构化事件流”。

---

## 4. Session manager 负责什么

`DefaultCodingSessionManager` 负责会话生命周期和持久化，不负责模型执行。

它的职责包括：

- 创建新 session；
- 从存储恢复 session state；
- fork 出新的 session 分支；
- 保存 `CodingSessionSnapshot`；
- 追加 `SessionEvent`；
- 列出 session descriptors 和事件账本。

可以把它理解成：

- `CodingAgent` 负责“跑”
- `CodingSessionManager` 负责“存、取、分支、记账”

这也是为什么 `session`、`resume`、`fork`、`events`、`replay` 这些能力可以跨 CLI、TUI、ACP 共享。

---

## 4.1 Team snapshot manager 负责什么

`CliTeamStateManager` 负责的是“工作区 team 持久化快照的读取与渲染”，不是 session manager 的一部分。

它当前做两件事：

- 从 `<workspace>/.ai4j/teams/state/<teamId>.json` 读取 `AgentTeamState`
- 从 `<workspace>/.ai4j/teams/mailbox/<teamId>.jsonl` 读取 mailbox 消息

这层能力专门服务：

- `/team list`
- `/team status [team-id]`
- `/team messages [team-id] [limit]`
- `/team resume [team-id]`

而 `/team` 本身走的是另一条路径：

- 当前 session event ledger
- `TeamBoardRenderSupport.renderBoardLines(events)`

这样拆开的原因是：

- session ledger 记录的是“这次 coding session 观察到的 team 事件”
- team snapshot 记录的是“某个 team runtime 最近一次持久化状态”

两者不是同一份存储，也不应该互相覆盖。

---

## 5. MCP runtime 负责什么

`CliMcpRuntimeManager` 是 Coding Agent 的 MCP 运行时桥接层。

它会：

- 解析全局和 workspace MCP 配置；
- 建立 MCP client session；
- 拉取每个 server 的 tool definitions；
- 校验工具名是否和内置工具冲突；
- 生成 `toolRegistry` 和 `toolExecutor`；
- 维护 `connected / paused / disabled / error / missing` 状态。

它不是简单的“配置对象”，而是一个活的 runtime。

所以当你执行：

- `/mcp`
- `/mcp pause`
- `/mcp resume`
- `/mcp reconnect`

背后影响的是当前 session runtime 可见的工具集合。

---

## 6. TUI runtime 和 session runtime 不一样

这个地方最容易混淆。

`TuiRuntime` 讲的是终端渲染机制，比如：

- `AnsiTuiRuntime`
- `AppendOnlyTuiRuntime`

它解决的是：

- 是否使用 alternate screen；
- 如何刷新主缓冲区；
- 如何控制绘制频率；
- 如何把 `TuiRenderer` 输出到终端。

但它不负责模型调用、session state、MCP 生命周期。

所以：

- `TuiRuntime` = UI runtime
- `CodingCliSessionRunner` = 会话主 runtime

两者不是一层东西。

---

## 7. ACP runtime 和协议边界

`AcpJsonRpcServer` 是 ACP 宿主层，不是 agent 内核。

它主要负责：

- 处理 `initialize`、`session/new`、`session/load`、`session/list`、`session/prompt`、`session/cancel`；
- 为每个 ACP session 创建 `SessionHandle`；
- 把 `HeadlessCodingSessionRuntime` 的事件转成 `session/update`；
- 在需要审批时，通过 `session/request_permission` 向宿主发起权限请求。

因此 ACP 集成时要区分三层：

- ACP 协议层：JSON-RPC 收发；
- Headless session runtime：一轮 prompt 如何执行；
- CodingAgent：真正做推理和工具调用。

---

## 8. 什么时候会重建 runtime

下面这些操作通常不只是改一个变量，而是会触发当前 session runtime 重建：

- 切换 provider profile；
- 切换 model；
- 切换 protocol；
- 修改会影响 effective profile 的配置；
- 切换 MCP 可用集合。

原因很简单：

- model client 变了；
- tool registry / executor 变了；
- stream / approval / sampling 参数可能也变了。

所以文档里经常写“立即重建当前 session runtime”，不是修辞，而是当前实现语义。

---

## 9. 扩展时优先改哪一层

### 9.1 想换模型接入方式

优先看：

- `DefaultCodingCliAgentFactory`
- provider profile / configuration 相关文档

### 9.2 想加新的工具审批策略

优先看：

- `ToolExecutorDecorator`
- `CliToolApprovalDecorator`
- `AcpToolApprovalDecorator`

### 9.3 想改宿主体验

优先看：

- `CodingCliSessionRunner`
- `CodingCliTuiFactory`
- `TuiRuntime`
- `TuiRenderer`

### 9.4 想改 ACP 接入行为

优先看：

- `AcpJsonRpcServer`
- `HeadlessCodingSessionRuntime`

### 9.5 想扩 MCP 工具来源

优先看：

- `CliMcpRuntimeManager`

---

## 10. 推荐继续阅读

1. [会话、流式与进程](/docs/coding-agent/session-runtime)
2. [Coding Agent Architecture](/docs/coding-agent/architecture)
3. [Tools 与审批机制](/docs/coding-agent/tools-and-approvals)
4. [MCP 与 ACP](/docs/coding-agent/mcp-and-acp)
5. [CLI / TUI 使用指南](/docs/coding-agent/cli-and-tui)
6. [命令参考](/docs/coding-agent/command-reference)
