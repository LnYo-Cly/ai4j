# P4 CLI sandbox commands - 发现记录

本文件记录任务执行中形成的判断、事实和技术决策。它不是审查报告；阻塞性问题请写入 `review.md`。

## 研究发现

### F-001：P3 已提供 coding 层 sandbox route，CLI 需要做 host binding

- 背景：本任务要把 sandbox 体验暴露到 CLI，而不是重新实现 shell routing。
- 发现：`TARGET:ai4j-coding/src/main/java/io/github/lnyocly/ai4j/coding/CodingAgentBuilder.java` 已有 `.sandbox(SandboxSession)` / `.sandboxRuntime(CodingSandboxRuntime)`，并在 built-in bash tool 中创建 `SandboxShellCommandExecutor`。
- 影响：P4 不应修改 coding runtime 主逻辑；CLI 只需把当前 binding 传入 factory/builder。
- 后续：实现时优先改 `DefaultCodingCliAgentFactory` 的非破坏性 overload 和 `CodingCliSessionRunner` rebind。

### F-002：当前 sandbox SPI 不支持通用 attach/resume

- 背景：用户期望 `/sandbox attach <providerId> <sessionId>`，但真实 provider 后端不在本任务范围内。
- 发现：`SandboxProvider` 目前只有 `createSession(SandboxSpec)`，`SandboxSession` 代表 live session 并提供 `execute(...)`；没有 `attachSession(sessionId)` 或 provider registry/transport bridge。
- 影响：P4 不能声称“已连接真实远端 sandbox”。attach 只能记录 CLI 侧非敏感 binding，或通过后续 provider bridge 才能变成真实执行。
- 后续：本任务必须在输出和文档里明确边界；真实 attach/resume 应拆成后续 `SandboxProvider` / `Remote Agent Runner` 任务。

### F-003：`CodingCliSessionRunner` 是 slash dispatch 与 runtime rebind 的正确接点

- 背景：需要让 `/sandbox attach/disable` 不只显示状态，还能影响后续 agent runtime。
- 发现：`/provider`、`/model`、`/experimental`、`/mcp`、`/stream` 已在 `CodingCliSessionRunner` 中通过 `switchSessionRuntime(...)` 重建 runtime；`/help`、`/status`、palette 也在同一类附近维护。
- 影响：P4 应仿照 `/stream` 和 `/mcp` 的模式，在 attach/disable 后调用 runtime rebind，并保持 `activeSession` 与持久化逻辑一致。
- 后续：实现前先补小型状态模型，避免把大量 parsing 逻辑散落到多个类。

### F-004：`SlashCommandController` 需要同时更新 root、action 和 palette 体验

- 背景：用户关注 Codex/Claude Code/Pi 一类 TUI 体验，命令必须可发现。
- 发现：root commands、`EXECUTABLE_ROOT_COMMANDS`、action candidates、`suggest(...)` 分支和 command palette 都要同步；已有 `SlashCommandControllerTest` 可覆盖 root/action suggestions。
- 影响：只实现 dispatch 不够，必须把 `/sandbox` 加入 completion、palette、help/status。
- 后续：新增或扩展 `SlashCommandControllerTest`，至少验证 `/sandbox`、`/sandbox `、`/sandbox attach `。

### F-005：P4 采用 metadata-only `SandboxSession`，用显式失败防止 host fallback

- 背景：`/sandbox attach` 必须让后续 runtime 感知 attached 状态，但当前没有真实 provider bridge。
- 发现：如果只记录状态但不传入 `SandboxSession`，用户可能以为 runtime 已切换而实际仍走 direct-host；如果传入假成功 session，则会伪造 sandbox 执行成功。最终实现用 `CliAttachedSandboxSession` 包装非敏感 binding，并在 `execute(...)` 中抛出 `SandboxException`，提示 `Command was not executed locally`。
- 影响：P4 可以证明 `/sandbox attach` 会触发 `CodingAgentBuilder.sandbox(...)` 路由，同时不会在缺 provider bridge 时把命令静默落回宿主机。
- 后续：真实 attach/resume/create/list/destroy/logs 仍应由后续 provider bridge / Remote Agent Runner 任务实现。

## 技术决策

| 决策 | 选择 | 原因 | 替代方案 | 状态 |
| --- | --- | --- | --- | --- |
| P4 目标 | CLI 可见 binding + runtime rebind，不做真实 provider | 符合当前 P3 能力和本任务范围，避免伪造云端 sandbox | 直接实现 CubeSandbox/VM provider | accepted |
| attach 语义 | 记录非敏感 `providerId/sessionId/workspaceId` 并显式说明后端边界 | 当前 SPI 无通用 attach；不能误导用户 | 静默当作真实 live session | accepted |
| runtime 接线 | 给 `CodingCliAgentFactory` 增加默认 overload，`DefaultCodingCliAgentFactory` 调用 `builder.sandbox(...)` | 降低破坏面，兼容其他实现 | 把 sandbox 写进 `CodeCommandOptions` 或全局 config | accepted |
| 用户体验 | `/sandbox` 默认等价 status，`attach/disable` 是本轮唯一 mutating action | 和 `/stream`、`/mcp` 风格一致，简单可发现 | 一次性加入 create/list/destroy/logs | accepted |
| 真实后端 | 后续任务处理 provider bridge / remote runner | 需要新的 provider/transport/凭据/网络边界 | 在 CLI 里硬编码一个后端 | accepted |
| 无 provider bridge 的执行语义 | `CliAttachedSandboxSession.execute(...)` 显式失败并声明没有本地执行 | 防止用户误以为已进入 sandbox，同时防止 host fallback | 只做 status-only binding 或伪造成功 | accepted |

## 待确认问题

| 问题 | 当前判断 | Owner | 截止点 |
| --- | --- | --- | --- |
| `DefaultCodingCliAgentFactoryTest` 是否已有足够 seam 测 sandbox overload | 已新增 fake `SandboxSession` 验证 factory overload 会进入 sandbox routing，并保留正常 prepare path | coordinator | resolved |
| attach 后没有真实 provider 时，是否传入 metadata-only `SandboxSession` 还是只记录状态 | 已选择 metadata-only `CliAttachedSandboxSession`，`execute(...)` 明确失败且不执行本地命令 | coordinator | resolved |
| docs-site 是否新增页面还是更新现有 `sandbox-routing.md` / CLI command docs | 已更新现有 `sandbox-routing.md`、`command-reference.md`、`sdk-roadmap.md`，避免新增页面和 `.gitignore docs/` 风险 | coordinator | resolved |
