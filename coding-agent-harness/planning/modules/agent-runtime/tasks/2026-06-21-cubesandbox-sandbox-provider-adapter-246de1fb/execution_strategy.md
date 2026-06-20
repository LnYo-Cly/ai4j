# 执行策略

## Subagent Authorization

任务开始时先读这一段，并向用户说明当前授权状态。这里是授权记录，不是执行沙箱。

| Role | Status | Permission | Authorized By | Authorized At | Scope | Worktree / Branch | Reuse |
| --- | --- | --- | --- | --- | --- | --- | --- |
| reviewer subagent | used | read-only | user allowed suitable subagents; harness task policy | 2026-06-21 | CubeSandbox adapter protocol/material review | n/a | no longer needed after report |
| worker subagent | not-needed | no write delegation | coordinator | 2026-06-21 | single CubeSandbox adapter/docs/governance slice owned by coordinator | `.worktrees/feature/cubesandbox-provider` / `feature/cubesandbox-provider` | not needed |

## Subagent Delegation Decision

| Question | Decision | Reason | Next Action |
| --- | --- | --- | --- |
| Should a reviewer subagent be used? | yes | 协议适配 + Harness 材料风险适合独立只读挑战。 | 已派只读 reviewer，报告已回写到 `review.md`。 |
| Would a worker subagent materially help? | no | 代码、docs、governance 文件互相耦合；当前任务已经在单一 feature worktree 内推进，拆写入 worker 容易冲突。 | coordinator 自行实现并最终集成。 |

## User Authorization Decision

| Gate | State | Decided By | Decided At | Scope | Worktree / Branch | Notes |
| --- | --- | --- | --- | --- | --- | --- |
| worker subagent | not-needed | coordinator | 2026-06-21 | no worker write scope; coordinator owns CubeSandbox adapter/docs/governance slice | `.worktrees/feature/cubesandbox-provider` / `feature/cubesandbox-provider` | 本轮仅使用只读 reviewer，避免共享文件冲突。 |

## 决策表

| 决策 | 选择 | 说明 |
| --- | --- | --- |
| 主执行者 | coordinator | 统一维护 Java adapter、docs 和 governance，减少冲突。 |
| Subagent 模式 | reviewer-only | 子 agent 只读审查协议和材料缺口。 |
| 审查模型 | adversarial review | 协议适配、secret 边界和 live-provider 证据需要信心挑战。 |
| Worktree 策略 | dedicated worktree | `.worktrees/feature/cubesandbox-provider` / `feature/cubesandbox-provider`。 |
| 冲突控制 | coordinator owns shared files | `docs/05-TEST-QA/**`、`docs-site/**`、Harness generated/module plan 由 coordinator 写。 |
| 证据深度 | L1 + L2 + L3 opt-in | L1 Maven protocol tests；L2 docs build；L3 live CubeSandbox 仅在 env 存在时运行。 |

## 子代理 / Worker 合同

| 角色 | 输入包 | 写入范围 | 交接要求 | 负责人 |
| --- | --- | --- | --- | --- |
| reviewer | C-001..C-007 + current diff | read-only | protocol/material risk report | subagent `019ee64c-c0d0-7483-8822-92fb12c2576f` |
| worker | n/a | n/a | n/a | coordinator |

## 证据计划

| 证据层级 | 计划命令或检查 | 记录位置 | 完成条件 |
| --- | --- | --- | --- |
| L0 | `git diff --check`; sensitive-fragment scan on changed files | `progress.md` / `walkthrough.md` | no whitespace errors; no real secret fragments printed or committed |
| L1 | `mvn -pl ai4j-agent -am "-Dtest=CubeSandboxProviderTest,AgentSandboxSpiModelTest,AgentSessionSandboxBindingTest" -DskipTests=false -DfailIfNoTests=false test` | `progress.md` | 13 tests pass |
| L1 broad | `mvn -pl ai4j-agent -am -DskipTests=false test` | `progress.md` | agent runtime broad baseline passes |
| L2 | `npm --prefix docs-site run build` | `progress.md` | docs build passes |
| L3 opt-in | `mvn -pl ai4j-agent -am -P live-provider-tests "-Dtest=CubeSandboxLiveProviderTest" -DskipTests=false -DfailIfNoTests=false test` | `progress.md` / `walkthrough.md` | pass if required env exists; otherwise skipped/pending-env recorded |
| Governance | `npx --yes coding-agent-harness status --json .` | `progress.md` | no missing materials except expected dirty warning before commit |

## 暂停 / 升级条件

- 需要真实 CubeSandbox endpoint/API key/template，但当前 shell 没有 env vars。
- PR CI 发现本地无法复现的 Java/docs failure。
- CubeSandbox 官方协议与本 adapter 关键字段不一致且无法用兼容方式修复。
- 用户要求扩大到 `ai4j-coding` 全量 tool routing 或 CLI `/sandbox` live attach。

## Module Preset Strategy

| Field | Value |
| --- | --- |
| Module Key | agent-runtime |
| Module Plan | coding-agent-harness/planning/modules/agent-runtime/module_plan.md |

Keep shared module decisions in the module plan or module context files. Keep task-specific evidence in this task directory.
