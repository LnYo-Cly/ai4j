# Coding Agent Architecture

`Coding Agent` 的架构重点不是“再包一层 builder”，而是如何把通用 agent 能力变成一个可以在本地代码仓里稳定交付任务的系统。

## 1. 先看总链路

可以先把当前主链压成下面这条：

```text
CLI / TUI / ACP host
  -> session runtime
  -> coding runtime
  -> workspace-aware tools + approvals
  -> MCP runtime
  -> Agent runtime
```

它表达的意思是：

- 最底层仍然是 `Agent`
- 但真正可交付的 Coding Agent，一定还有 session、tool、approval、host 这些外层

## 2. 四层结构怎么读

### `Agent runtime`

最底层仍然沿用 `Agent` 的模型调用和工具循环能力。

它负责：

- model client
- step loop
- tool execution
- base memory

### `coding runtime`

主要落在 `ai4j-coding/`。

它负责：

- workspace 语义
- outer loop
- checkpoint / compact
- 代码仓任务的长期运行策略

关键类和包包括：

- `CodingSession`
- `CodingSessionSnapshot`
- `CodingSessionState`
- `coding.workspace.*`
- `coding.compact.CodingSessionCompactor`
- `SessionProcessRegistry`

### `session runtime`

这一层负责“任务如何作为会话持续运行”。

它解决：

- save / resume / fork / replay
- event ledger
- 进程、checkpoint、compact 结果如何纳入会话

这里已经不再只是模型执行，而是任务生命周期管理。

### `host runtime`

主要落在 `ai4j-cli/`。

它负责：

- CLI slash commands
- TUI 渲染与交互
- ACP JSON-RPC
- approval UI / host callbacks
- provider / MCP / workspace 配置入口

关键入口包括：

- `CodingCliSessionRunner`
- `AcpJsonRpcServer`
- `CliToolApprovalDecorator`
- `AcpToolApprovalDecorator`
- `CliMcpRuntimeManager`

## 3. 模块边界

当前最重要的两块模块是：

- `ai4j-coding/`
- `ai4j-cli/`

可以用一句话记：

- `ai4j-coding` 解决“任务怎么跑”
- `ai4j-cli` 解决“人或宿主怎么用”

这也是为什么 approval、ACP、TUI 这些能力不应该反向侵入更底层的 `ai4j-agent`。

## 4. 审批拦截发生在哪一层

这一点非常重要。

审批不是操作系统 hook，也不是 JVM agent。

当前实现是 runtime 在组装 `ToolExecutor` 时挂上的 decorator：

- `CliToolApprovalDecorator`
- `AcpToolApprovalDecorator`

所以审批的本质是：

- 拦截工具执行入口
- 向 CLI/TUI 或 ACP 宿主请求授权
- 再决定是否继续调用底层 tool executor

这也是为什么 CLI/TUI 和 ACP 能共享同一套审批语义，但走不同宿主通道。

## 5. MCP 为什么单独成层

`MCP` 不是简单的“再多几个工具”。

在 `Coding Agent` 里，它是外部能力接入面：

- 全局配置
- 工作区启用状态
- 运行时连接状态
- tool definitions 拉取与冲突校验

这部分由 `CliMcpRuntimeManager` 负责，因此它更像一个活的 runtime，而不是静态配置对象。

## 6. 这页和相邻页面怎么分工

- `why-coding-agent`：定位和价值
- `architecture`：层次、边界、主执行链
- `session-runtime`：长期任务和会话生命周期
- `tools-and-approvals`：工作区工具面和审批拦截
- `mcp-and-acp`：协议边界和宿主集成
- `install-and-release`：可分发入口怎样落地

## 7. 推荐阅读顺序

1. [Session Runtime](/docs/coding-agent/session-runtime)
2. [Tools and Approvals](/docs/coding-agent/tools-and-approvals)
3. [MCP and ACP](/docs/coding-agent/mcp-and-acp)
4. [Command Reference](/docs/coding-agent/command-reference)

如果你想继续追“长任务为什么能持续运行”，下一页看 [Session Runtime](/docs/coding-agent/session-runtime)。
