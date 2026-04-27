---
sidebar_position: 6
---

# Tools 与审批机制

Coding Agent 的核心不是只会聊天，而是能通过工具读文件、改文件、跑命令和接外部系统。

这页聚焦两个问题：

1. 当前内置了哪些工具？
2. 如果我要扩展 Tool，应该从哪里接？

---

## 1. 固定内置 Tool

当前固定内置的本地 Tool 只有四个：

- `bash`
- `read_file`
- `write_file`
- `apply_patch`

它们是由 `CodingAgentBuilder` 默认装配进去的。

---

## 2. 当前 session 可见工具集合不一定只有四个

这是最容易混淆的地方。

上面那四个，是固定内置的“本地执行 Tool”。但模型在当前 session 里实际能看到的工具集合，还可能额外包含：

- `delegate_*`：来自内建或自定义 `CodingAgentDefinitionRegistry` 的委派型 agent tools
- `subagent_*`：通过 `subAgentRegistry` 或 `subAgent(...)` 挂进去的 subagent tools
- `subagent_background_worker` / `subagent_delivery_team`：由 `/experimental` 控制注入的实验性 agent tools
- MCP tools：来自 `CliMcpRuntimeManager`

这里再补一个边界：

- `team_*` 工具不是顶层 `coding-agent` 默认暴露的工具
- 它们只会出现在 `AgentTeam` 成员自己的运行时里，用来做成员协作

所以更准确的说法是：

- 固定内置本地 Tool = 4 个
- 当前 session 可见工具集合 = 固定内置本地 Tool + 运行时注入的 agent tools + MCP tools

---

## 3. 每个固定内置 Tool 的职责

### 3.1 `read_file`

适合：

- 读取工作区文件；
- 限定行范围读取；
- 读取技能目录里的只读 `SKILL.md`。

### 3.2 `write_file`

适合：

- 创建文件；
- 覆盖文件；
- 追加文件内容。

### 3.3 `apply_patch`

适合：

- 基于结构化 patch 修改文件；
- 精准编辑已有内容；
- 保留更清晰的差异语义。

### 3.4 `bash`

适合：

- 执行非交互命令；
- 启动长期运行进程；
- 读取日志、写 stdin、停止进程。

---

## 4. `/experimental` 与 agent tools 的关系

`/experimental` 不是在增加新的“本地执行器”，而是在切换是否向当前 session 注入两类实验性 agent surface：

- `subagent_background_worker`
  适合长任务、后台进程、仓库扫描、构建和测试
- `subagent_delivery_team`
  背后是一个 `architect / backend / frontend / qa` 的 `AgentTeam`，再包装成一个可调用的 subagent

它们的特点是：

- 从父 agent 视角看，是普通 tool call
- 真正执行时，会进入 subagent 或 team runtime，而不是直接执行一个本地 shell/文件动作
- 开关状态来自 workspace 配置，CLI / TUI / ACP 改动后都会立即重建当前 session runtime

---

## 5. Tool 如何扩展

如果只是使用 Coding Agent，默认内置 Tool 足够。

如果要扩展，有三层入口：

### 5.1 自定义 `toolRegistry`

用来决定“暴露给模型哪些工具”。

适合：

- 接企业内部 Tool 总线；
- 只暴露特定白名单；
- 给不同工作区装不同工具集。

### 5.2 自定义 `toolExecutor`

用来决定“工具如何执行”。

适合：

- 做多租户鉴权；
- 工具调用前后统一审计；
- 对接公司内部执行网关。

注意：

- 如果你传了自定义 `toolRegistry`，也必须同时提供对应的 `toolExecutor`。

### 5.3 `ToolExecutorDecorator`

这是 Coding Agent 很实用的一层。

它允许你在不改原始 Tool 实现的前提下，为某些工具统一包一层行为，例如：

- 审批；
- 限流；
- 日志；
- 统一错误包装。

CLI/TUI 的审批装饰器和 ACP 的审批装饰器，都是这一层在工作。

---

## 6. 审批不是 hook，而是 ToolExecutor 包装

这里最容易误解。

当前审批拦截不是：

- OS hook；
- syscall hook；
- JVM instrumentation；
- “命令已经跑起来了再去拦”。

它的实际做法是：

1. `--approval` 先在 CLI 参数阶段解析成 `auto / safe / manual`
2. `DefaultCodingCliAgentFactory` 把审批实现挂进 `CodingAgentOptions.toolExecutorDecorator`
3. `CodingAgentBuilder` 在组装内置 Tool 时，把 `read_file / write_file / apply_patch / bash` 用 decorator 包起来
4. 真正执行 Tool 时，decorator 先判断是否要审批，再决定是否继续调用底层 executor

可以把它理解成：

```text
CodeCommandOptionsParser
    -> DefaultCodingCliAgentFactory
        -> CodingAgentOptions.toolExecutorDecorator
            -> CodingAgentBuilder.decorate(...)
                -> CliToolApprovalDecorator / AcpToolApprovalDecorator
                    -> delegate.execute(...)
```

所以审批的拦截点是：

- Tool executor 的组装阶段
- Tool executor 的调用入口

而不是 shell、文件系统或操作系统层面的 hook。

---

## 7. 审批模式

启动参数：

```text
--approval <auto|safe|manual>
```

### `auto`

默认自动处理，适合本地开发和低风险场景。

### `safe`

更保守，但这里要按“当前实现”理解，而不是按字面想象。

当前 `safe` 模式下，会拦的主要是：

- `apply_patch`
- `bash` 的 `exec`
- `bash` 的 `start`
- `bash` 的 `stop`
- `bash` 的 `write`

也就是说，当前实现里：

- `read_file` 不会因为 `safe` 自动要求审批
- `write_file` 也不会因为 `safe` 自动要求审批

如果你想把更多工具也纳入审批，不是改 prompt，而是扩展或替换 decorator 规则。

### `manual`

当前实现里，`manual` 不是“只拦敏感工具”，而是每次 Tool 调用都要求确认。

适合：

- 宿主侧要审计；
- 工具调用代价高；
- 需要人工兜底。

---

## 8. CLI/TUI 和 ACP 的审批路径不一样

审批策略是一套，但宿主交互路径有两条。

### 8.1 CLI / TUI

CLI / TUI 使用 `CliToolApprovalDecorator`。

它会：

- 先根据 tool name 和 arguments 判断是否需要审批
- 在终端打印 approval block
- 通过终端 `readLine(...)` 读取 `y/yes`
- 拒绝时返回一个带 `[approval-rejected]` 前缀的错误

所以 CLI/TUI 的审批是本地终端里的同步交互。

### 8.2 ACP

ACP 使用 `AcpToolApprovalDecorator`。

它不会直接在本地终端问用户，而是：

1. 调用 `PermissionGateway`
2. 由 `AcpJsonRpcServer` 发出 `session/request_permission`
3. 由宿主返回审批结果
4. 再决定是否继续执行底层 Tool

当前 ACP 宿主可返回的选项包括：

- `allow_once`
- `allow_always`
- `reject_once`
- `reject_always`

所以 ACP 的审批本质上是一次 JSON-RPC permission round-trip。

---

## 9. 审批不是工作区沙箱

审批和工作区边界是两层不同的控制。

工作区边界主要依赖 `WorkspaceContext` 的路径解析，例如：

- `resolveWorkspacePath(...)`
- `resolveReadablePath(...)`

大多数内置 Tool 的工作区访问都依赖这层语义，而不是靠审批本身来隔离。

这意味着：

- 审批负责“要不要执行”
- 工作区路径约束负责“允许访问哪里”

这里还有一个当前实现需要明确说明的 caveat：

- `write_file` 目前有自己的路径解析逻辑
- 当传入绝对路径时，它可以落到工作区之外
- 所以当前版本不能把“开启审批”理解成“已经获得严格文件沙箱”

如果你需要更严格的企业级控制，正确入口通常是：

- 自定义 `toolExecutor`
- 自定义 `ToolExecutorDecorator`
- 收紧工作区配置和路径策略

---

## 10. Tool 来源边界

要区分三类来源：

- 内置 Tool：`bash/read_file/write_file/apply_patch`
- agent delegation tools：`delegate_*`、`subagent_*`、`/experimental` 注入的 agent tools
- MCP Tool：来自外部 MCP server

它们在模型看来都能成为工具，但接入路径不同：

- 内置 Tool 由 Coding Agent 自己装配；
- agent delegation tools 由 definition registry、subagent registry 或 runtime factory 在组装阶段注入；
- MCP Tool 由 `CliMcpRuntimeManager` 接入后再挂到 tool registry / executor。

---

## 11. 推荐做法

- 内置 Tool 负责本地工作区操作；
- agent delegation tools 负责把复杂任务拆出去；
- MCP Tool 负责外部系统访问；
- 审批逻辑统一放在 decorator 层；
- 不要把审批误当成文件系统 hook 或工作区沙箱；
- 复杂工具接入时，把 registry 和 executor 一起设计。
