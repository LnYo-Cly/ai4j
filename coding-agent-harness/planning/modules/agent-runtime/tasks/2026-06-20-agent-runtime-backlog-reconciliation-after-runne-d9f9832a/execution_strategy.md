# 执行策略

## Subagent Authorization

任务开始时先读这一段，并向用户说明当前授权状态。这里是授权记录，不是执行沙箱。

| Role | Status | Permission | Authorized By | Authorized At | Scope | Worktree / Branch | Reuse |
| --- | --- | --- | --- | --- | --- | --- | --- |
| reviewer subagent | allowed by default | read-only | harness task policy | task creation | current task review | n/a | allowed within this task |
| worker subagent | not needed | no write delegation | coordinator decision | 2026-06-20 | planning-only reconciliation | n/a | n/a |

## Subagent Delegation Decision

| Question | Decision | Reason | Next Action |
| --- | --- | --- | --- |
| Should a reviewer subagent be used? | no | 当前只做事实核查和 Harness 规划落盘，证据来自 Git/PR/路径/Harness status；self adversarial review 足够。 | 如后续改 public API 或实现 Memory/Compact polish，再开 reviewer pass。 |
| Would a worker subagent materially help? | no | 无可并行代码切片；共享文件只有 module plan 和 task package，拆 worker 会增加冲突。 | coordinator 直接完成。 |

## User Authorization Decision

| Gate | State | Decided By | Decided At | Scope | Worktree / Branch | Notes |
| --- | --- | --- | --- | --- | --- | --- |
| worker subagent | not-needed | coordinator | 2026-06-20 | planning-only reconciliation | `docs/agent-runtime-backlog-reconciliation` | 不需要额外写入 worker。 |

## 决策表

| 决策 | 选择 | 说明 |
| --- | --- | --- |
| 主执行者 | coordinator | coordinator 负责编排事实核查、module plan 更新和 final review。 |
| Subagent 模式 | none | 本轮不改生产代码，不拆并行 worker。 |
| 审查模型 | self-check + adversarial review | 通过 PR、路径、Git、Harness status 交叉验证；后续实现再升级审查。 |
| Worktree 策略 | dedicated worktree | 使用现有 `.worktrees/docs/agent-runtime-backlog-reconciliation`，避免污染 root checkout。 |
| 冲突控制 | coordinator owns shared files | 只改本任务包和 `agent-runtime/module_plan.md`。 |
| 证据深度 | L1 | 文档/规划 reconciliation：静态检查 + Harness status + GitHub PR/路径核查。 |

## 子代理 / Worker 合同

| 角色 | 输入包 | 写入范围 | 交接要求 | 负责人 |
| --- | --- | --- | --- | --- |
| n/a | n/a | n/a | n/a | coordinator |

## 证据计划

| 证据层级 | 计划命令或检查 | 记录位置 | 完成条件 |
| --- | --- | --- | --- |
| L0 | `git status --short --branch`; key path existence checks; `gh pr view 118`; `gh pr list --base dev --state open` | `progress.md` / `findings.md` | 当前事实足以支撑 module plan 更新。 |
| L1 | `git diff --check`; `npx --yes coding-agent-harness status --json .` | `progress.md` / `review.md` | 无 diff whitespace 问题；Harness failures=0。 |
| L2 | 不适用 | n/a | 本轮不改 docs-site 页面或生产代码。 |
| L3 | 不适用 | n/a | 人工确认通过 dashboard/review queue 单独完成。 |

## 暂停 / 升级条件

- 核查发现关键代码或 docs 不存在。
- PR #118 未合并或 `dev` 与本 worktree 出现分叉冲突。
- Harness status 出现 failures、missing materials 或 blocking finding。
- 需要修改 Java public API、docs-site 用户文档或 Regression SSoT。

## Module Preset Strategy

| Field | Value |
| --- | --- |
| Module Key | agent-runtime |
| Module Plan | coding-agent-harness/planning/modules/agent-runtime/module_plan.md |

Keep shared module decisions in the module plan or module context files. Keep task-specific evidence in this task directory.
