---
sidebar_position: 9
---

# ACP 集成

`ACP` 是当前 `Coding Agent` 面向 IDE、桌面壳和其他宿主程序的标准接入面。

它的目标不是“远程控制一个终端窗口”，而是让宿主直接驱动：

- session 创建与加载
- prompt 执行
- 权限确认
- slash command 调用
- session 事件消费

如果只从高层概念看，会误以为 ACP 只是“把 CLI 输出换成 JSON”。

当前源码其实做得更重。

---

## 1. 先看 ACP 的真实入口链

把主链压成一条线：

```text
ai4j-cli acp ...
  -> AcpCommand.run(...)
  -> CodeCommandOptionsParser.parse(...)
  -> new AcpJsonRpcServer(...)
  -> initialize / session/new / session/load / session/prompt
  -> HeadlessCodingSessionRuntime
  -> session/update + session/request_permission
```

这里最关键的点有两个：

- `acp` 和 `code` 共用同一套命令行选项解析规则
- ACP 不是另一个 runtime，而是同一套 coding runtime 的 headless host

所以：

- provider / model / workspace 的解析规则，ACP 和 CLI 本质一致
- 差别主要在宿主协议和事件传输方式

---

## 2. 传输层约定比“stdio 模式”更具体

`AcpCommand` 的帮助文案和 `AcpJsonRpcServer.run()` 的读取循环决定了当前 ACP 的硬约定：

- `stdin`：换行分隔的 JSON-RPC 请求
- `stdout`：换行分隔的 JSON-RPC 响应与通知
- `stderr`：日志、告警、诊断

`AcpJsonRpcServer.run()` 当前会：

1. 按行读取 stdin
2. 忽略空行
3. 对每一行单独做 JSON parse
4. 解析失败时把错误写到 `stderr`
5. 继续处理后续消息

这意味着当前协议层假设的是：

- newline-delimited JSON-RPC

而不是：

- 带 `Content-Length` 头的 LSP 风格 framing

宿主如果 framing 假设错了，会在最开始就通信失败。

---

## 3. `initialize` 当前暴露的能力边界

当前 `buildInitializeResponse(...)` 返回的是一个很明确的能力集。

关键字段包括：

- `protocolVersion`
- `agentInfo`
- `agentCapabilities.loadSession=true`
- `agentCapabilities.mcpCapabilities.http=true`
- `agentCapabilities.mcpCapabilities.sse=true`
- `agentCapabilities.promptCapabilities.audio=false`
- `agentCapabilities.promptCapabilities.image=false`
- `agentCapabilities.promptCapabilities.embeddedContext=false`
- `agentCapabilities.sessionCapabilities.list`

这几项一起说明了当前 ACP 的真实边界：

- 可以列 session
- 可以 load session
- 可以从宿主注入 HTTP / SSE MCP
- 但 prompt 输入仍以文本为主
- 目前不应该假设图像、音频或嵌入上下文已经被 ACP 第一等支持

所以现在最稳的宿主心智模型仍然是：

- 文本 prompt + 结构化事件 + 审批回调

---

## 4. `session/new` 和 `session/load` 不是完全同一条路径

两者都会进入 `createSession(...)`，但随后动作不同。

### 4.1 `session/new`

执行顺序是：

1. 解析 `cwd` 和可选 `sessionId`
2. 解析会话级 `mcpServers`
3. 创建 permission gateway
4. 准备 `CodingCliAgentFactory`
5. 构建 `PreparedCodingAgent`
6. 创建 `CodingSessionManager`
7. 创建新的 `ManagedCodingSession`
8. 返回 `sessionId + configOptions + modes`
9. 发送 `available_commands_update`

### 4.2 `session/load`

执行顺序与 `session/new` 类似，但在响应前还会做一件额外的事：

- `handle.replayHistory()`

也就是说：

- `session/load` 会先重放历史 `session/update`
- 然后才发送本次 RPC 的成功响应
- 最后再发送 `available_commands_update`

这个顺序对宿主很重要，因为它决定了你是先收到历史内容，还是先收到“会话已打开”的确认。

---

## 5. `cwd` 为什么必须是绝对路径

`createSession(...)` 和 `listSessions(...)` 都会要求 `cwd` 走 `requireAbsolutePath(...)`。

所以当前 ACP 宿主不应该传：

- 相对路径
- IDE 内部工作区别名
- 逻辑 project id

而应该传：

- 真实绝对文件系统路径

因为后续所有 session store、workspace config、skills、MCP、文件工具边界，最终都依赖这个真实 workspace root。

---

## 6. `session/prompt` 其实有两条执行分支

`promptSession(...)` 先把 `prompt` 数组 flatten 成输入字符串，然后判断：

- `AcpSlashCommandSupport.supports(input)` 是否为真

于是当前 prompt 有两条路：

### 6.1 普通 prompt

走：

- `HeadlessCodingSessionRuntime.runPrompt(...)`

这条路会产生真正的模型调用、工具调用、loop decision、auto-compact、event ledger 等一整套运行事件。

### 6.2 ACP 已知 slash command

走：

- `SessionHandle.runSlashCommand(...)`

这条路不会把输入再交给模型，而是在本地执行命令逻辑，然后依然用标准 `session/update` 事件把文本结果发回宿主。

所以：

- ACP 里的 slash command 发现是协议能力
- 但执行仍然通过普通 `session/prompt` 输入触发

这和“单独再发一套 command RPC”是不同设计。

---

## 7. `available_commands_update` 是元数据事件，不是模型事件

当前 ACP 会在以下时机主动发送：

- `session/new` 之后
- `session/load` 之后

事件类型是：

- `session/update`
- `update.sessionUpdate = "available_commands_update"`

它的作用不是回复某次 prompt，而是告诉宿主：

- 这个 ACP session 当前支持哪些 slash commands

宿主最稳的做法应该是：

1. 缓存这份命令列表
2. 把它映射到 slash menu / command palette
3. 不要在客户端写死另一套命令清单

---

## 8. ACP 当前默认暴露的是“headless 友好”命令子集

`AcpSlashCommandSupport.COMMANDS` 当前暴露的重点命令包括：

- `help`
- `status`
- `session`
- `save`
- `providers`
- `provider`
- `model`
- `experimental`
- `skills`
- `agents`
- `mcp`
- `sessions`
- `history`
- `tree`
- `events`
- `team`
- `compacts`
- `checkpoint`
- `processes`
- `process`

这和 CLI/TUI 的完整命令面不完全一样。

原因不是功能缺失，而是 ACP 当前更强调：

- 结构化宿主集成
- 不依赖终端特有交互
- 命令结果能够稳定退化为纯文本

所以像 `team` 这类命令虽然复杂，但仍然适合 ACP，因为它的输出可以先作为文本展示，再由宿主按需做 richer UI。

---

## 9. 文本事件模型的关键不是“分几类”，而是“顺序可重放”

ACP 最常见的通知仍然是：

- `session/update`

其中常见 `sessionUpdate` 包括：

- `available_commands_update`
- `user_message_chunk`
- `agent_thought_chunk`
- `agent_message_chunk`
- `tool_call`
- `tool_call_update`

当前设计最重要的特点不是事件名本身，而是：

- live turn 和 replay history 走的是同一类更新模型

也就是说：

- 你不需要为“实时渲染”和“历史回放”写两套完全不同的渲染器
- 只要按顺序消费 `session/update`，大多数宿主就能统一处理

---

## 10. slash command 的执行结果为什么也走 `agent_message_chunk`

`runSlashCommand(...)` 会：

1. 先追加一个 `USER_MESSAGE` ledger event
2. 发送 `user_message_chunk`
3. 本地执行 ACP slash command
4. 追加一个 `ASSISTANT_MESSAGE` ledger event，标记 `kind=command`
5. 再把结果通过 `agent_message_chunk` 发给宿主

这意味着对宿主来说：

- slash command 结果和普通 assistant 文本结果在消费层可以共用 UI

如果宿主想做更细区分，可以去读 ledger payload 里的：

- `kind = command`

但不是必须。

---

## 11. `session/load` 的 replay 不是“再问模型一次”

`SessionHandle.replayHistory()` 只是从 event store 读取历史 `SessionEvent`，然后转成 ACP `session/update`。

它不会：

- 重新跑模型
- 重新执行工具
- 重新拉取外部状态

所以 ACP 宿主必须把 replay 理解为：

- 事件账本回放

而不是：

- 重新构造真实运行现场

如果某些实时外部资源已经变化，replay 看到的仍然只是当时写下来的会话事件。

---

## 12. `session/cancel` 不只中断当前 turn，还会统一结束待审批状态

`cancelSession(...)` 当前会做两件事：

1. 取消该 session 上的 active prompt
2. 把所有 pending permission futures 统一完成为 cancelled

这说明 ACP 的“停止”语义不只是文本生成中断，还包括：

- 宿主侧如果正卡在一次工具审批上，也应该一起退出等待

这比简单中断线程更接近用户真正需要的“停止当前工作单元”。

---

## 13. 权限确认是服务端主动发起的反向 RPC

如果当前审批模式不是 `auto`，并且工具调用命中审批规则，ACP 不会只在本地等待。

它会主动向宿主发送一个 JSON-RPC request：

- `method = "session/request_permission"`

当前选项集合固定为：

- `allow_once`
- `allow_always`
- `reject_once`
- `reject_always`

服务端收到宿主响应后，只把：

- `allow_once`
- `allow_always`

视为批准。

其他结果都会走拒绝或取消。

因此宿主需要支持的不是“显示一段文本”，而是：

- 接收服务端主动请求
- 暂停本次工具调用
- 回传最终选择结果

这条链如果没实现，`manual` / `safe` 模式下的 ACP 集成会卡死在权限等待点。

---

## 14. `modes` 和 `configOptions` 当前能力边界很窄

`buildSessionOpenResult()` 会把两组东西发回给宿主：

- `modes`
- `configOptions`

但当前真正支持的内容其实很有限。

### `modes`

当前本质上是审批模式集合，例如：

- `auto`
- `safe`
- `manual`

### `configOptions`

当前只有：

- `mode`
- `model`

也就是说 ACP 当前还不是完整“设置中心”。

它只支持：

- 切审批模式
- 切模型

如果宿主想改 provider、MCP store、workspace binding，当前仍然要通过其他路径，而不是期待 ACP 已经提供了全量配置 API。

---

## 15. ACP 下的 MCP 注入是一条独立于本地 store 的链

在 `session/new` / `session/load` 里，宿主可以直接传 `mcpServers`。

`AcpJsonRpcServer.resolveMcpConfig(...)` 会把它们直接组装成 `CliResolvedMcpConfig`，随后交给 ACP agent factory。

这条链的特点是：

- 默认视为 workspace enabled
- 不依赖 `~/.ai4j/mcp.json`
- 不依赖 `workspace.json.enabledMcpServers`
- 更像宿主临时会话注入

因此 ACP 下的 MCP 最适合：

- IDE 按项目动态挂载工具
- 桌面壳按会话临时分配 MCP
- 多租户宿主不想依赖用户本地全局配置

---

## 16. 当前最常见的接入误区

### 16.1 用 LSP framing 发消息

当前 ACP 读的是逐行 JSON，不是 `Content-Length` framing。

### 16.2 把 `cwd` 传成相对路径

当前要求绝对路径，否则建 session 就会失败。

### 16.3 以为 slash command 需要另一套 RPC

当前 slash command 仍然通过 `session/prompt` 文本触发。

### 16.4 以为 `session/load` 会重跑历史工具

它只 replay event ledger，不会重演真实执行。

### 16.5 忽略服务端反向 `session/request_permission`

如果宿主只会发请求、不会处理服务端发回来的审批请求，ACP 在非 `auto` 模式下就不完整。

---

## 17. 宿主实现建议

- `stdout` 只作为协议通道，不要混入日志
- `stderr` 单独接日志与告警
- 所有请求按换行分隔 JSON-RPC 发送
- `cwd` 始终传真实绝对路径
- 统一按收到顺序消费 `session/update`
- slash menu 以 `available_commands_update` 为准，不要本地硬编码
- 权限对话框要支持处理服务端主动发起的 `session/request_permission`
- 当前 prompt 输入以文本为主，不要假设 image/audio/embeddedContext 已可用

---

## 18. 这页最该记住的结论

- ACP 是 headless host，不是“远程终端镜像”
- `acp` 和 `code` 共用同一套基础配置解析规则
- `session/new`、`session/load`、`session/prompt` 背后都有明确的本地运行链，不只是 JSON 转发
- slash command 发现靠 `available_commands_update`，执行靠普通 `session/prompt`
- 权限确认是服务端主动发起的反向 RPC
- `session/load` replay 的是事件账本，不是重新执行历史运行

---

## 19. 继续阅读

1. [CLI / TUI 使用指南](/docs/coding-agent/cli-and-tui)
2. [会话、流式与进程](/docs/coding-agent/session-runtime)
3. [MCP 与 ACP](/docs/coding-agent/mcp-and-acp)
4. [MCP 对接](/docs/coding-agent/mcp-integration)
5. [命令参考](/docs/coding-agent/command-reference)
