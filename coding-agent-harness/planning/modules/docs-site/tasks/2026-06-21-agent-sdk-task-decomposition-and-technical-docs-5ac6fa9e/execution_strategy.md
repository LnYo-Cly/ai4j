# Agent SDK task decomposition and technical docs - 执行策略

## Subagent Authorization

| Role | Status | Permission | Authorized By | Authorized At | Scope | Worktree / Branch | Reuse |
| --- | --- | --- | --- | --- | --- | --- | --- |
| reviewer subagent | allowed by default | read-only | harness task policy | task creation | current task review | n/a | allowed within this task |
| worker subagent | not-needed | n/a | coordinator decision | 2026-06-21 | docs-only task, narrow scope | `.worktrees/docs/agent-sdk-task-decomposition` / `docs/agent-sdk-task-decomposition` | n/a |

## Subagent Delegation Decision

| Question | Decision | Reason | Next Action |
| --- | --- | --- | --- |
| Should a reviewer subagent be used? | no | 本任务是 docs/Harness 拆解切片，self adversarial review + docs build + Harness status 足够；后续实现切片再考虑 reviewer subagent。 | 在 `review.md` 记录信心挑战。 |
| Would a worker subagent materially help? | no | 改动集中在一个 docs-site 页面和一个任务包，拆 worker 会增加同步成本。 | coordinator 独立完成。 |

## User Authorization Decision

| Gate | State | Decided By | Decided At | Scope | Worktree / Branch | Notes |
| --- | --- | --- | --- | --- | --- | --- |
| worker subagent | not-needed | coordinator | 2026-06-21 | docs/Harness task package | `.worktrees/docs/agent-sdk-task-decomposition` | 用户已授权继续整体 program，但本切片无需 worker。 |

## 决策表

| 决策 | 选择 | 说明 |
| --- | --- | --- |
| 主执行者 | coordinator | 负责状态校准、文档编写、验证、PR。 |
| Subagent 模式 | self-check | 当前切片不需要 worker。 |
| 审查模型 | self adversarial review + CI | 本地 review、docs build、Harness status、PR checks。 |
| Worktree 策略 | dedicated worktree | 从 `origin/dev` 创建 `.worktrees/docs/agent-sdk-task-decomposition`。 |
| 冲突控制 | coordinator owns shared files | 只改 docs-site Agent 页面、sidebar 和本任务包。 |
| 证据深度 | L1 | docs build + static checks + Harness status；无 Java 行为改动。 |

## 子代理 / Worker 合同

| 角色 | 输入包 | 写入范围 | 交接要求 | 负责人 |
| --- | --- | --- | --- | --- |
| n/a | n/a | n/a | n/a | coordinator |

## 证据计划

| 证据层级 | 计划命令或检查 | 记录位置 | 完成条件 |
| --- | --- | --- | --- |
| L0 | `git diff --check` | `progress.md` | 无 whitespace/error 输出。 |
| L0 | token fragment scan | `progress.md` | 无命中。 |
| L1 | `npm --prefix docs-site run build` | `progress.md` / `review.md` | Docusaurus build 成功。 |
| L1 | `npx --yes coding-agent-harness status --json .` | `progress.md` / `review.md` | check=pass 或 residual 清楚。 |
| L2 | GitHub PR checks | PR / closeout | PR 后 watch checks。 |

## 暂停 / 升级条件

- 需要实现 Java/CLI 行为。
- 需要使用真实 provider token 或 sandbox secret。
- docs build 暴露非本任务可修的全站错误。
- PR 与 `dev` 冲突，需要 rebase/重新验证。

## Module Preset Strategy

| Field | Value |
| --- | --- |
| Module Key | docs-site |
| Module Plan | coding-agent-harness/planning/modules/docs-site/module_plan.md |

本任务只把任务拆解与技术文档入口落盘。后续实现切片继续按模块创建自己的 task/worktree/PR。
