# 执行策略

## Subagent Authorization

任务开始时先读这一段，并向用户说明当前授权状态。这里是授权记录，不是执行沙箱。

| Role | Status | Permission | Authorized By | Authorized At | Scope | Worktree / Branch | Reuse |
| --- | --- | --- | --- | --- | --- | --- | --- |
| reviewer subagent | used | read-only | harness task policy | 2026-06-10 | current task review | n/a | completed as Hypatia read-only review |
| worker subagent | not-needed | n/a | coordinator | 2026-06-10 | n/a | n/a | implementation stayed in coordinator checkout |

## Subagent Delegation Decision

任务开始时，coordinator 必须根据用户目标主动做这个判断，即使用户完全没有提到 subagent。
不要假设用户知道 subagent 或 worker 是什么。如果分工有帮助，用白话说明收益，并向用户申请一次授权。
可以直接对用户说 subagent 或 worker subagent；关键规则是 agent 不能等用户主动提出 subagent。
如果任务已经明显拆成互不重叠的独立切片，implementation 前就应判断为 `ask-user`。如果还不知道精确文件路径，先确认路径，然后立刻申请独立执行助手授权。

| Question | Decision | Reason | Next Action |
| --- | --- | --- | --- |
| Should a reviewer subagent be used? | yes | 本任务跨 API、CLI、Spring、docs 和治理材料，适合只读 adversarial review。 | 已完成 Hypatia read-only review，findings 写入 `review.md`。 |
| Would a worker subagent materially help? | no | 实现切片共享 extension API / CLI / docs 语义，且全局治理文件由 coordinator 负责，拆 worker 容易增加冲突。 | 不派写入型 worker。 |

## User Authorization Decision

如果上方 worker 决策是 `ask-user`，implementation 必须暂停，直到这里记录用户答案。
已解决状态只能是 `authorized`、`denied` 或 `not-needed`。选择 `ask-user` 后不得继续保持 `pending`。

| Gate | State | Decided By | Decided At | Scope | Worktree / Branch | Notes |
| --- | --- | --- | --- | --- | --- | --- |
| worker subagent | not-needed | coordinator | 2026-06-10 | n/a | n/a | 本任务未拆写入型 worker；只使用只读 reviewer subagent。 |

## 决策表

| 决策 | 选择 | 说明 |
| --- | --- | --- |
| 主执行者 | coordinator | coordinator 负责编排顺序、冲突判断和最终收口。 |
| Subagent 模式 | reviewer-only | 使用只读 reviewer 检查 API/CLI/Spring/docs/governance 风险；不派写入型 worker。 |
| 审查模型 | adversarial review | 变更涉及插件权限和安装体验，需要挑战安全边界、兼容性和证据缺口。 |
| Worktree 策略 | same checkout | coordinator 单线实现；没有写入型 subagent 或并行 worker handoff。 |
| 冲突控制 | coordinator owns shared files | subagent 不得直接编辑 coordinator 管理的全局表或共享文件，除非获得明确锁。 |
| 证据深度 | L1 / L2 | API/CLI/Spring/Ask User plugin 使用 L1 tests；package/docs-site 使用 L2 local_smoke。 |

## 子代理 / Worker 合同

如使用 subagent 或 worker，在这里写清楚输入包、写入范围、handoff 格式和最终集成 owner。

| 角色 | 输入包 | 写入范围 | 交接要求 | 负责人 |
| --- | --- | --- | --- | --- |
| reviewer subagent | task plan, current diff, extension docs, regression docs | read-only | findings report in conversation and `review.md` | coordinator |

## 证据计划

| 证据层级 | 计划命令或检查 | 记录位置 | 完成条件 |
| --- | --- | --- | --- |
| L0 | diff review, stale wording scan | `progress.md`, `review.md` | no known misleading marketplace/install wording |
| L1 | extension API, CLI targeted, Spring targeted, Ask User plugin tests | `progress.md` | all commands pass |
| L2 | monorepo package smoke, docs-site typecheck/build, git diff check, harness status | `progress.md`, `walkthrough.md` | package/docs/harness gates pass or have only dirty-state warning before commit |
| L3 | not required | `review.md` 与 walkthrough | live/provider or real third-party plugin validation is out of scope |

## 暂停 / 升级条件

- 所需工作超出已批准写入范围。
- 共享表需要更新，但没有 coordinator lock。
- 实际风险高于原计划，证据深度需要升级。
- reviewer 发现会改变范围或方案的 P0/P1/P2 问题。
- 环境无法提供关键证据，继续执行会变成猜测。
