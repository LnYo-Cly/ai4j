# Agent SDK task decomposition and technical docs - 发现记录

## 研究发现

### 当前 `review` 队列主要是 lifecycle 状态，不等于代码缺失

- 背景：`origin/dev` Harness status 显示大量 Agent/CLI/Sandbox 相关任务处于 `review`。
- 发现：源码和 docs-site 已经包含 P0/P1/P2/P3/P4/P5 多个切片的实现或文档，例如 `AgentSession`、Blueprint、Sandbox SPI、Remote Runner SPI、CLI sandbox command、TUI status context bar。
- 影响：下一步必须先做 backlog/review reconciliation，不能看到 `review` 就重复实现。
- 后续：每个实现任务开始前运行 `harness status --json`、检查源码路径、检查 PR 状态。

### docs-site 需要一个“任务拆解”入口，而不只是 roadmap

- 背景：roadmap 描述方向，但不能替代后续 worker 的任务选择、依赖关系和验证命令。
- 发现：新增 `agent/sdk-task-decomposition.md` 能把 R0 digest、real API matrix、P0-P5 任务和 CLI/TUI/docs 后续工作连接起来。
- 影响：后续用户或 agent 接手时可以直接从 docs-site 看到“接下来怎么做”。
- 后续：docs-site completeness pass 继续按能力页补真实示例和排障。

### CLI `/memory` + `/compact` 是最值得优先执行的实现切片

- 背景：用户想要接近 Codex/Claude Code 的 coding agent 体验，且已多次关注 memory/compact/session/sandbox。
- 发现：Agent runtime 基座已有，CLI/TUI 将这些状态一等展示，能最快验证 SDK 设计是否真的好用。
- 影响：本任务推荐后续首个实现切片优先处理 `cli-memory-compact-command-ux` 或其 polish。
- 后续：实现前先检查该任务当前代码状态，避免重复。

## 技术决策

| 决策 | 选择 | 原因 | 替代方案 | 状态 |
| --- | --- | --- | --- | --- |
| 本任务范围 | docs/Harness task decomposition only | 避免在规划 worktree 混入生产代码 | 同时实现 CLI/Java 行为 | accepted |
| docs-site 页面位置 | `docs-site/docs/agent/sdk-task-decomposition.md` | Agent SDK 后续任务的自然入口 | 放到 coding-agent 或 reference | accepted |
| sidebar 位置 | Agent category 中紧跟 R0 digest | 先看 source-backed digest，再看任务拆解 | 放到 roadmap 之前 | accepted |
| 后续首选实现 | CLI memory/compact UX | 最快提升使用体验并验证 Session/Compact | 先做 install 或 runner | accepted |
| token 处理 | 完全不写入任何文件/日志/命令 | 用户提供 token 只作为可用计划信息，本任务不需要使用 | 写入本地 config 测试 | rejected |

## 待确认问题

| 问题 | 当前判断 | Owner | 截止点 |
| --- | --- | --- | --- |
| one-command install 首选方案 | 需要 ADR 比较 zip/JBang/npm/native/Scoop/Homebrew | cli-host owner | launcher distribution task |
| tmux smoke 是否纳入固定回归 | 建议单独 task，先作为 CLI/TUI polish 的证据 | cli-host owner | TUI polish task |
| 真实 sandbox provider 是否官方提供 | 先做 SPI/fake provider，真实 provider 作为插件或示例 | agent-runtime owner | sandbox provider task |
