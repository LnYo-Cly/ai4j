# MCP and ACP

`MCP` 和 `ACP` 经常一起出现，但它们解决的是两条完全不同的接入边界。

## 1. 先把两者摆回正确位置

### `MCP`

`MCP` 属于 AI 能力接入基座。

它回答的是：

- 外部工具或服务怎样暴露给模型
- tool schema 怎样进入 runtime
- 调用结果怎样回传

所以它首先是“能力来源层”，不是简单的某个本地工具子项。

### `ACP`

`ACP` 属于 `Coding Agent` 和宿主之间的协议层。

它回答的是：

- 会话怎么创建、加载、取消
- 事件怎样传给 IDE、桌面端或自定义宿主
- 审批怎样通过宿主交互完成

所以它是“产品壳 / host integration”这一侧。

## 2. 为什么在 Coding Agent 里两者会同时出现

因为 `Coding Agent` 同时面对两端：

- 向内，要组织模型和工具
- 向外，要把会话和事件交给宿主

可以先用这条链理解：

```text
MCP server
  -> Coding Agent tool surface
  -> session runtime
  -> ACP host / CLI / TUI
```

这里的含义是：

- `MCP` 决定“模型还能接什么能力”
- `ACP` 决定“宿主怎样消费这次会话”

## 3. MCP 在当前实现里怎么落

`Coding Agent` 里的 MCP 运行时不是静态配置，而是活的连接层。

当前主入口包括：

- `CliMcpConfigManager`
- `CliMcpRuntimeManager`
- `CliResolvedMcpConfig`
- `CliResolvedMcpServer`

配置面主要分成两层：

- 全局：`~/.ai4j/mcp.json`
- 工作区：`<workspace>/.ai4j/workspace.json`

工作区里通常通过 `enabledMcpServers` 声明当前仓库启用哪些 server。

## 4. ACP 在当前实现里怎么落

ACP 主入口在：

- `AcpJsonRpcServer`
- `AcpCodingCliAgentFactory`
- `AcpToolApprovalDecorator`

它负责把 coding session 包成结构化协议事件，而不是直接做终端交互。

典型职责包括：

- `session/new`
- `session/load`
- `session/prompt`
- `session/request_permission`
- reasoning / tool / approval / checkpoint 事件投递

所以 ACP 的重点不是“再造一个 CLI”，而是给宿主稳定协议。

## 5. 和 CLI / TUI 的关系

CLI / TUI 是本地交互壳。

ACP 是宿主协议壳。

三者共享的是底层 coding runtime 和 session 语义，但交互出口不同。

这也是为什么：

- CLI/TUI 审批会在终端里完成
- ACP 审批会通过 `session/request_permission` 走宿主交互

## 6. 推荐阅读顺序

1. [Core SDK / MCP](/docs/core-sdk/mcp/overview)
2. [Architecture](/docs/coding-agent/architecture)
3. [Session Runtime](/docs/coding-agent/session-runtime)
4. [Tools and Approvals](/docs/coding-agent/tools-and-approvals)

如果你准备从“节点图 + 前后端平台”这条线进入，而不是本地代码仓入口，可以转看 [Flowgram](/docs/flowgram/overview)。
