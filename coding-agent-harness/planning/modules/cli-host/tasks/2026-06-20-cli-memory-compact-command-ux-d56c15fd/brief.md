# CLI memory compact command UX

## Task ID

`2026-06-20-cli-memory-compact-command-ux-d56c15fd`

## 创建日期

2026-06-20

## 一句话结果

为 `ai4j-cli` 补齐一等 `/memory` 会话记忆诊断入口，并把它和已有 `/compact`、`/compacts`、`/checkpoint` 命令在帮助、补全、TUI palette、ACP 与 docs-site 中对齐。

## 完成后能得到什么

完成后，普通用户在 Coding Agent CLI/TUI 中不需要理解内部 `CodingSessionSnapshot` 或事件账本，也能通过 `/memory` 快速看到当前会话记忆状态：memory item 数、估算上下文 token、checkpoint goal、最近 compact 模式和 token 变化、auto-compact 失败/熔断状态，以及“不会打印原始敏感内容”的边界提示。开发者和下一轮 agent 也能在 docs-site 与测试中看到 `/memory`、`/compact`、`/compacts`、`/checkpoint` 的分工：一个看概览，一个主动压缩，一个看历史，一个看结构化 checkpoint。

## 交付物

- 可见产物：`references/cli-memory-compact-command-ux-plan.md` 中的执行规划。
- 修改位置：后续实现预计触及 `ai4j-cli/**`、`docs-site/docs/coding-agent/**`，如新增固定回归面则同步 `docs/05-TEST-QA/**`。
- 验证证据：本轮规划记录用 `git diff --check` 与 `npx --yes coding-agent-harness status --json .`；后续实现用 CLI targeted tests、CLI module tests、docs-site build。

## 第一眼应该看什么

1. `references/cli-memory-compact-command-ux-plan.md`：实现边界、文件清单、命令输出设计和验证矩阵。
2. `task_plan.md`：任务范围、验收标准、worktree 和 gate。
3. `findings.md`：现有 `/compact`、`/compacts`、`/checkpoint` 已存在，缺口是 `/memory` 一等诊断入口。
4. `visual_map.md`：实现路径、命令关系和验证链路。

## 边界

- 范围内：CLI/TUI/ACP 的 `/memory` 命令注册、dispatch、输出渲染、帮助、补全、palette、docs-site 命令参考和 deterministic tests。
- 范围外：不重写 compact 算法；不改变 `ai4j-coding` session storage 语义；不新增 provider 调用；不打印 raw memory 内容；不把 memory/compact API 改成 docs 里不存在的伪 API。
- 停止条件：如果实现发现缺少 snapshot 字段而必须改 `ai4j-coding` 或 public API，必须回到 coordinator 重新扩大范围并更新任务包。

## 完成判断

- [ ] `/memory` 成为 slash command root，可补全、可执行、可在 TUI palette 中出现。
- [ ] `/memory` 输出只展示诊断摘要，不泄露 raw user prompt、provider token 或工具输出全文。
- [ ] CLI/TUI/ACP 的 help/available command 与 docs-site 命令参考一致。
- [ ] 回归覆盖 `SlashCommandControllerTest`、`CodeCommandTest`、`AcpSlashCommandSupportTest`，必要时覆盖 `CodexStyleBlockFormatterTest`。
- [ ] `docs-site` 对 `/memory` 与 `/compact`、`/compacts`、`/checkpoint` 的分工讲清楚并通过 build。

## 执行合同

- Owner：coordinator
- 生命周期状态：进行中
- 必需文件：`INDEX.md`、`task_plan.md`、`execution_strategy.md`、`visual_map.md`、`progress.md`、`findings.md`、`review.md`
- 完成条件：后续实现 diff、测试、docs build、review 与 walkthrough 必须记录到 `progress.md`

## 当前下一步

创建 dedicated worktree：`.worktrees/feature/cli-memory-compact-ux`，基于最新 `origin/dev`，然后按 `references/cli-memory-compact-command-ux-plan.md` 实现 `/memory`。
