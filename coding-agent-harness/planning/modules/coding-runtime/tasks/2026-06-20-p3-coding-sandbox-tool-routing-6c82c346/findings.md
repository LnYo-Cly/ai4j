# P3 Coding sandbox tool routing - 发现记录

本文件记录 P3 首切片中形成的事实、决策和边界。

## 研究发现

### `bash exec` 是最小可验证切片

- 背景：P3 目标最终包括 file / shell / git / browser / project run / test runner，但一次性远端化所有执行面风险过高。
- 发现：当前 `BashToolExecutor` 已经通过 `ShellCommandExecutor` 承载 foreground `exec`，而后台进程由 `SessionProcessRegistry` 单独管理。
- 影响：首切片只替换 foreground shell executor，不触碰后台进程生命周期。
- 后续：`bash start/status/logs/write/stop/list` 需要单独设计 provider-side process 映射。

### live sandbox handle 不能写入 AgentSession snapshot

- 背景：P2-B 已经设计了 `AgentSessionSandboxBinding`，只保存非敏感摘要。
- 发现：`SandboxSession` 是 live provider handle，不应该进入 session snapshot 或 store。
- 影响：本任务新增 `CodingSandboxRuntime` 只在运行期持有 `SandboxSession`；持久化仍使用 `AgentSessionSandboxBinding`。
- 后续：远端 runner / sandbox attach/resume 需要 provider-side restore 语义，不应依赖 Java 对象序列化。

### approval 与 sandbox routing 必须保持分层

- 背景：用户关心类似 Codex `/sandbox` 的体验，但 sandbox 不是权限批准。
- 发现：当前 coding approval 是 `ToolExecutorDecorator`；sandbox routing 是 execution destination。
- 影响：本任务只替换 executor destination，不改变 approval decorator 顺序，也不默认放开危险工具。
- 后续：CLI `/sandbox` 可以展示状态，但仍需 `/permissions` / approval 机制。

### docs-site 必须写清未实现边界

- 背景：此前用户明确要求不要再写不存在或夸大的 API。
- 发现：当前首切片只实现 `bash exec` routing，文件/patch/browser/git 未实现。
- 影响：`coding-agent/sandbox-routing.md` 明确列出“当前没有做什么”。
- 后续：每个后续路由切片都必须同步更新该表。

## 技术决策

| 决策 | 选择 | 原因 | 替代方案 | 状态 |
| --- | --- | --- | --- | --- |
| sandbox 注入入口 | `CodingAgentBuilder.sandbox(SandboxSession)` / `sandboxRuntime(CodingSandboxRuntime)` | 宿主显式提供 live session，SDK 不创建真实 provider | Blueprint/CLI 自动创建 sandbox | accepted |
| 首切片工具范围 | 只远端化 `bash action=exec` | 最小风险、最易验证、不破坏后台进程语义 | 一次性远端化所有 built-in tools | accepted |
| 结果标识 | `ShellCommandResult.executionEnvironment`、`sandboxSessionId`、`sandboxProviderId` | CLI/TUI 和日志能展示执行位置 | 只靠 stdout/stderr 推断 | accepted |
| session 记录 | `AgentSession.bindSandbox(sandboxSession)` | 复用 P2-B 非敏感 binding | 把 live sandbox handle 存进 snapshot | accepted |
| docs 边界 | 新增 canonical `coding-agent/sandbox-routing.md` | 避免 roadmap 文档承载过多实现细节 | 只改 roadmap | accepted |

## 待确认问题

| 问题 | 当前判断 | Owner | 截止点 |
| --- | --- | --- | --- |
| `read_file` / `write_file` / `apply_patch` 如何远端化 | 后续需要 sandbox workspace file service 或 provider file API | coordinator | P3 后续切片 |
| 后台 process lifecycle 是否映射到 provider | 需要 provider-side process id、logs、write/stop/cancel 语义 | coordinator | bash process routing task |
| CLI `/sandbox` 如何展示状态 | P4 负责；本任务只提供 execution result metadata | coordinator | P4 CLI sandbox commands |
