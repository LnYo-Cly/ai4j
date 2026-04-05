# 2026-03-28 Coding Agent ACP 设计

- 状态：Approved
- 优先级：P0
- 目标模块：`ai4j-cli`、`ai4j-coding`
- 关联文档：
  - `docs/plans/2026-03-27-coding-agent-closure-plan.md`
  - `docs/plans/2026-03-23-codex-aligned-jline-cli-design.md`

## 1. 背景

当前 `ai4j` 已具备可用的 coding agent、session 持久化、MCP 接入、CLI/TUI 双入口，但真正的 turn 执行主链路仍主要内嵌在 CLI/TUI runner 中。

这导致它更像“一个终端应用”，而不是“一个可被多前端复用的 coding runtime”。如果直接把现有 CLI/TUI 壳包一层对接 ACP：

- `stdout` 会混入普通终端输出，污染 ACP `stdio` JSON-RPC
- 审批和取消依赖终端交互，无法由 IDE/宿主接管
- 后续 Web/IDE/ACP 会继续和 CLI/TUI 逻辑相互缠绕

## 2. 目标

本轮目标不是引入一个完整的新后端架构，而是在不破坏现有 CLI/TUI 行为的前提下，补出第一版 ACP 兼容能力：

- 新增 `ai4j-cli acp` 命令
- 支持 ACP `stdio` transport
- 支持 `initialize`
- 支持 `session/new`
- 支持 `session/load`
- 支持 `session/list`
- 支持 `session/prompt`
- 支持 `session/cancel`
- 支持 `session/request_permission`
- 支持 `session/update`

同时完成最小必要的 runtime 抽取，使 ACP 不直接依赖 TUI/CLI 渲染逻辑。

## 3. 非目标

本轮不做：

- WebSocket ACP transport
- ACP 下的 `/mcp` 面板
- session mode / session config option 的完整标准暴露
- 把 CLI/TUI 全量改造成对 headless runtime 的完全复用
- 通过 ACP 客户端代理本地文件系统和终端能力

第一版 ACP 优先复用 ai4j 本地工具与 MCP runtime，只把审批对接到 ACP 客户端。

## 4. 当前问题定位

当前耦合主要集中在 `ai4j-cli`：

- `CodingCliSessionRunner` 同时负责：
  - 接收用户输入
  - 执行 turn
  - 消费 `AgentEvent`
  - 刷新 TUI/CLI
  - 写 session ledger
  - 管理中断
- `CliAgentListener` 把事件处理、终端输出、TUI 状态维护、session event 记录揉在一起
- `CliToolApprovalDecorator` 默认把审批绑定到终端读输入

已经可以复用的部分：

- `DefaultCodingCliAgentFactory`
- `CodingSessionManager`
- `ManagedCodingSession`
- `CliMcpRuntimeManager`

## 5. 设计

### 5.1 总体分层

拆成三层：

1. `Coding Headless Runtime`
2. `ACP Adapter`
3. `CLI/TUI Frontend`

其中本轮只要求 ACP 使用 headless runtime，CLI/TUI 先保持现状并逐步迁移。

### 5.2 Headless Runtime

新增一个无 UI 的 session runtime，负责：

- 按输入启动一次 turn
- 记录 `USER_MESSAGE`
- 监听 `AgentEvent`
- 转换并写入 `SessionEvent`
- 向外发出结构化 turn 更新
- 处理中断和结束状态
- 在 turn 完成后执行 auto-compact 和 session save

Headless runtime 不直接调用：

- `terminal.println`
- TUI animation
- shell-specific interrupt watch

### 5.3 ACP 命令入口

新增顶层命令：

- `ai4j-cli acp [code options...]`

命令启动后：

- 使用 `stdin/stdout` 进行 newline-delimited JSON-RPC
- 所有普通日志改走 `stderr`
- 保持 provider/model/api-key 等基础运行参数仍沿用现有 `code` 命令解析规则

### 5.4 ACP Session 模型

ACP server 内部维护 `sessionId -> session handle` 注册表。

每个 session handle 包含：

- `CodeCommandOptions`
- `PreparedCodingAgent`
- `ManagedCodingSession`
- `HeadlessCodingSessionRuntime`
- 当前活动 prompt future
- 当前活动 prompt thread
- 审批网关

### 5.5 MCP 对接

ACP `session/new` / `session/load` 提供 `mcpServers`。

本轮支持：

- `stdio`
- `sse`
- `http`

实现方式：

- 沿用 `CliMcpRuntimeManager`
- 补一个“从 ACP `mcpServers` 直接构建 resolved config”的入口
- 不依赖工作区现有 `.ai4j` MCP 配置也能运行

### 5.6 审批

CLI/TUI 继续走 `CliToolApprovalDecorator`。

ACP 新增 `AcpToolApprovalDecorator`：

- 在需要审批时，向客户端发送 `session/request_permission`
- 等待客户端返回 `selected` 或 `cancelled`
- 把 `allow_once` / `allow_always` 视为批准
- 把 `reject_once` / `reject_always` 视为拒绝
- 如果 prompt 被客户端 `session/cancel`，则未完成审批一律视为取消

### 5.7 事件映射

从 `AgentEvent` / `SessionEvent` 映射到 ACP `session/update`：

- 用户输入 -> `user_message_chunk`
- 模型输出 -> `agent_message_chunk`
- reasoning -> `agent_thought_chunk`
- 工具开始 -> `tool_call`
- 工具完成/失败 -> `tool_call_update`
- session 标题/时间 -> `session_info_update`

第一版暂不发：

- `plan`
- `available_commands_update`
- `current_mode_update`
- `config_option_update`

### 5.8 停止原因

第一版返回：

- 正常完成 -> `end_turn`
- 收到 `session/cancel` -> `cancelled`
- 其他异常 -> JSON-RPC error

本轮先不细分 `max_tokens` / `refusal`。

## 6. 预期修改文件

### 新增

- `ai4j-cli/src/main/java/io/github/lnyocly/ai4j/cli/acp/...`
- `ai4j-cli/src/main/java/io/github/lnyocly/ai4j/cli/runtime/HeadlessCodingSessionRuntime.java`
- `ai4j-cli/src/main/java/io/github/lnyocly/ai4j/cli/runtime/HeadlessTurnObserver.java`
- `ai4j-cli/src/test/java/io/github/lnyocly/ai4j/cli/AcpCommandTest.java`

### 修改

- `ai4j-cli/src/main/java/io/github/lnyocly/ai4j/cli/Ai4jCli.java`
- `ai4j-cli/src/main/java/io/github/lnyocly/ai4j/cli/factory/DefaultCodingCliAgentFactory.java`
- `ai4j-cli/src/main/java/io/github/lnyocly/ai4j/cli/mcp/CliMcpRuntimeManager.java`
- `ai4j-cli/src/main/java/io/github/lnyocly/ai4j/cli/CodeCommandOptions.java`

## 7. 测试策略

### 单元测试

- `initialize` 返回 capability
- `session/new` 创建 session
- `session/list` 列出 session
- `session/load` 能恢复并回放历史
- `session/prompt` 产生流式 `session/update`
- `session/cancel` 返回 `cancelled`
- `session/request_permission` 能驱动审批分支

### 回归验证

- 现有 `code` / `tui` 命令不回归
- ACP 模式下 `stdout` 不混入普通日志
- MCP 启动失败只作为 warning，不中断 ACP 运行

## 8. 分阶段

### Phase 1

- 建立 `acp` 入口
- 建立 JSON-RPC transport
- 建立 session registry
- 跑通 `initialize/new/load/list/prompt/cancel`

### Phase 2

- 接入 ACP 审批桥接
- 完善 MCP request-based config
- 完善 session history replay

### Phase 3

- 把 CLI/TUI turn 执行逐步迁移到 headless runtime
- 再考虑 WebSocket ACP 与更多标准能力
