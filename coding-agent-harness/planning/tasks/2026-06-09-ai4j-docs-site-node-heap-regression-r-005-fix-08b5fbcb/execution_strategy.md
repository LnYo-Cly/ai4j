# 执行策略

## Subagent Authorization

任务开始时先读这一段，并向用户说明当前授权状态。这里是授权记录，不是执行沙箱。

| Role | Status | Permission | Authorized By | Authorized At | Scope | Worktree / Branch | Reuse |
| --- | --- | --- | --- | --- | --- | --- | --- |
| reviewer subagent | allowed by default | read-only | harness task policy | task creation | current task review | n/a | allowed within this task |
| worker subagent | not authorized | write only after user approval | pending | pending | pending | pending | allowed only within approved task/scope |

## Subagent Delegation Decision

任务开始时，coordinator 必须根据用户目标主动做这个判断，即使用户完全没有提到 subagent。
不要假设用户知道 subagent 或 worker 是什么。如果分工有帮助，用白话说明收益，并向用户申请一次授权。
可以直接对用户说 subagent 或 worker subagent；关键规则是 agent 不能等用户主动提出 subagent。
如果任务已经明显拆成互不重叠的独立切片，implementation 前就应判断为 `ask-user`。如果还不知道精确文件路径，先确认路径，然后立刻申请独立执行助手授权。

| Question | Decision | Reason | Next Action |
| --- | --- | --- | --- |
| Should a reviewer subagent be used? | no | Scope is narrow and deterministic; self review plus human confirmation queue is sufficient. | Record self-review findings in `review.md`. |
| Would a worker subagent materially help? | no | Changes are a small connected set across package scripts, workflow YAML, and governance docs; splitting would add coordination overhead. | Coordinator implements and verifies in the main checkout. |

## User Authorization Decision

如果上方 worker 决策是 `ask-user`，implementation 必须暂停，直到这里记录用户答案。
已解决状态只能是 `authorized`、`denied` 或 `not-needed`。选择 `ask-user` 后不得继续保持 `pending`。

| Gate | State | Decided By | Decided At | Scope | Worktree / Branch | Notes |
| --- | --- | --- | --- | --- | --- | --- |
| worker subagent | not-needed | coordinator | 2026-06-10 | n/a | main | Narrow single-checkout task; no worker authorization needed. |

## 决策表

| 决策 | 选择 | 说明 |
| --- | --- | --- |
| 主执行者 | coordinator | coordinator 负责编排顺序、冲突判断和最终收口。 |
| Subagent 模式 | none | Narrow deterministic change; no write worker. |
| 审查模型 | self-check + human confirmation queue | R-005 fix is proven by local commands and SSoT diff; human confirmation remains separate. |
| Worktree 策略 | same checkout | Current worktree was clean before task and no parallel worker is writing. |
| 冲突控制 | coordinator owns shared files | subagent 不得直接编辑 coordinator 管理的全局表或共享文件，除非获得明确锁。 |
| 证据深度 | L2 | docs-site RG-008 is a local smoke gate: typecheck plus build. |

## 子代理 / Worker 合同

如使用 subagent 或 worker，在这里写清楚输入包、写入范围、handoff 格式和最终集成 owner。

| 角色 | 输入包 | 写入范围 | 交接要求 | 负责人 |
| --- | --- | --- | --- | --- |
| n/a | task plan + diff + RG-008 evidence | n/a | n/a | coordinator |

## 证据计划

| 证据层级 | 计划命令或检查 | 记录位置 | 完成条件 |
| --- | --- | --- | --- |
| L0 | workflow YAML lint; `git diff --check`; harness status | `progress.md` | no syntax/checker failures |
| L1 | n/a | n/a | no unit-test surface touched |
| L2 | `npm run typecheck`; `npm run build` in `docs-site/` | `progress.md` | both pass without external `NODE_OPTIONS` |
| L3 | post-push GitHub Actions observation, if needed | `review.md` 与 walkthrough | docs workflows do not fail after push |

## 暂停 / 升级条件

- 所需工作超出已批准写入范围。
- 共享表需要更新，但没有 coordinator lock。
- 实际风险高于原计划，证据深度需要升级。
- reviewer 发现会改变范围或方案的 P0/P1/P2 问题。
- 环境无法提供关键证据，继续执行会变成猜测。
