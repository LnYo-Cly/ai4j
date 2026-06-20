# 执行策略

## Subagent Authorization

任务开始时先读这一段，并向用户说明当前授权状态。这里是授权记录，不是执行沙箱。

| Role | Status | Permission | Authorized By | Authorized At | Scope | Worktree / Branch | Reuse |
| --- | --- | --- | --- | --- | --- | --- | --- |
| reviewer subagent | allowed by default | read-only | harness task policy | task creation | current task review | n/a | allowed within this task |
| worker subagent | not authorized | write only after user approval | pending | pending | CLI `/memory` implementation slice | `.worktrees/feature/cli-memory-compact-ux` / `feature/cli-memory-compact-ux` | allowed only after explicit approval |

## Subagent Delegation Decision

| Question | Decision | Reason | Next Action |
| --- | --- | --- | --- |
| Should a reviewer subagent be used? | yes | 命令面涉及 CLI、TUI、ACP、docs-site，一轮只读 reviewer 可以检查是否遗漏命令入口或泄露 raw memory。 | 实现完成后调用只读 reviewer；规划阶段先 self-review。 |
| Would a worker subagent materially help? | no | 当前切片集中在 `ai4j-cli` 命令注册和 docs-site，小到可以由 coordinator 顺序实现；并行 worker 反而容易和共享 docs/command registry 冲突。 | 暂不派 worker；如后续同时做 ACP 与 docs 可再申请。 |

## User Authorization Decision

| Gate | State | Decided By | Decided At | Scope | Worktree / Branch | Notes |
| --- | --- | --- | --- | --- | --- | --- |
| worker subagent | not-needed | coordinator | 2026-06-20 | CLI `/memory` command UX | `.worktrees/feature/cli-memory-compact-ux` / `feature/cli-memory-compact-ux` | 本轮规划不需要 worker；实现阶段可由 coordinator 直接做。 |

## 决策表

| 决策 | 选择 | 说明 |
| --- | --- | --- |
| 主执行者 | coordinator | coordinator 负责范围控制、实现、验证和最终收口。 |
| Subagent 模式 | reviewer-only | 实现完成后适合只读 reviewer 检查命令面一致性；不需要 worker 并行。 |
| 审查模型 | self-check + reviewer-ready | 规划阶段 self-check；实现后至少 self adversarial review，可加 read-only reviewer。 |
| Worktree 策略 | dedicated worktree | 当前根 checkout 是 roadmap 分支；实现必须从最新 `origin/dev` 切 dedicated worktree。 |
| 冲突控制 | coordinator owns shared files | `docs-site`、Regression SSoT、module_plan 由 coordinator 串行维护。 |
| 证据深度 | L1 + docs build | CLI command UX 需要 deterministic targeted tests；docs 更新需要 docs-site build。 |

## 子代理 / Worker 合同

| 角色 | 输入包 | 写入范围 | 交接要求 | 负责人 |
| --- | --- | --- | --- | --- |
| reviewer | `task_plan.md`、`references/cli-memory-compact-command-ux-plan.md`、最终 diff、测试输出 | read-only | review report with P0/P1/P2 findings or no-finding statement | coordinator |
| worker | not used | not applicable | not applicable | coordinator |

## 证据计划

| 证据层级 | 计划命令或检查 | 记录位置 | 完成条件 |
| --- | --- | --- | --- |
| L0 | `git diff --check` | `progress.md` | 无 whitespace / conflict marker 问题 |
| L1 | `mvn -pl ai4j-cli -am "-Dtest=SlashCommandControllerTest,CodeCommandTest,AcpSlashCommandSupportTest,CodexStyleBlockFormatterTest" -DskipTests=false -DfailIfNoTests=false test` | `progress.md` | `/memory` command surface targeted tests 通过 |
| L1 | `mvn -pl ai4j-cli -am -DskipTests=false test` | `progress.md` | CLI module 全量本地测试通过 |
| L1 | `npm --prefix docs-site run build` | `progress.md` | docs-site build 通过 |
| L0 | `npx --yes coding-agent-harness status --json .` | `progress.md` | failures=0，任务材料齐全或 residual 明确 |

## 暂停 / 升级条件

- `/memory` 需要字段不在现有 `CodingSessionSnapshot` / export state 中，且无法用 `unknown` 合理降级。
- 需要读取 raw memory item 才能满足输出；这违反本任务安全边界，必须暂停。
- 改动扩展到 `ai4j-coding` public API、session persistence schema 或 provider runtime。
- docs-site 需要展示尚不存在 API 示例。
- reviewer 发现 CLI/TUI/ACP 三个命令面不一致或存在 raw context 泄露。

## Module Preset Strategy

| Field | Value |
| --- | --- |
| Module Key | cli-host |
| Module Plan | coding-agent-harness/planning/modules/cli-host/module_plan.md |

Keep shared module decisions in the module plan or module context files. Keep task-specific evidence in this task directory.
