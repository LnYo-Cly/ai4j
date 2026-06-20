# 执行策略

## Subagent Authorization

任务开始时先读这一段，并向用户说明当前授权状态。这里是授权记录，不是执行沙箱。

| Role | Status | Permission | Authorized By | Authorized At | Scope | Worktree / Branch | Reuse |
| --- | --- | --- | --- | --- | --- | --- | --- |
| reviewer subagent | allowed by default | read-only | harness task policy | task creation | current task review | n/a | allowed within this task |
| worker subagent | not authorized | write only after user approval | n/a | n/a | n/a | n/a | not used |

## Subagent Delegation Decision

任务开始时，coordinator 必须根据用户目标主动做这个判断，即使用户完全没有提到 subagent。
不要假设用户知道 subagent 或 worker 是什么。如果分工有帮助，用白话说明收益，并向用户申请一次授权。

| Question | Decision | Reason | Next Action |
| --- | --- | --- | --- |
| Should a reviewer subagent be used? | no | 本任务为 docs-site recipe 层和任务材料更新，验证面清晰，self review + docs-site build 足够；最终仍保留人工确认门禁。 | 提交 agent review packet，等待用户确认。 |
| Would a worker subagent materially help? | no | 改动集中在单个文档页、少量链接入口和 harness 材料，拆分 worker 会增加协调成本。 | coordinator 串行完成。 |

## User Authorization Decision

如果上方 worker 决策是 `ask-user`，implementation 必须暂停，直到这里记录用户答案。
已解决状态只能是 `authorized`、`denied` 或 `not-needed`。选择 `ask-user` 后不得继续保持 `pending`。

| Gate | State | Decided By | Decided At | Scope | Worktree / Branch | Notes |
| --- | --- | --- | --- | --- | --- | --- |
| worker subagent | not-needed | coordinator | 2026-06-10 | docs-site recipe documentation | main | 不使用 worker。 |

## 决策表

| 决策 | 选择 | 说明 |
| --- | --- | --- |
| 主执行者 | coordinator | coordinator 负责编排顺序、冲突判断和最终收口。 |
| Subagent 模式 | self-check | 文档层改动，使用 self review、构建验证和人工确认门禁。 |
| 审查模型 | self-check + human gate | 当前无 Java 行为变更，docs-site build/typecheck 是主要回归证据。 |
| Worktree 策略 | same checkout | 当前 `main` 干净，任务范围窄，不需要独立 worktree。 |
| 冲突控制 | coordinator owns shared files | Feature SSoT、sidebar 和 task materials 由 coordinator 串行更新。 |
| 证据深度 | L1 | docs-site typecheck/build + diff check。 |

## 子代理 / Worker 合同

如使用 subagent 或 worker，在这里写清楚输入包、写入范围、handoff 格式和最终集成 owner。

| 角色 | 输入包 | 写入范围 | 交接要求 | 负责人 |
| --- | --- | --- | --- | --- |
| n/a | n/a | n/a | n/a | coordinator |

## 证据计划

| 证据层级 | 计划命令或检查 | 记录位置 | 完成条件 |
| --- | --- | --- | --- |
| L0 | `git diff --check` | `progress.md` | 无 whitespace error |
| L1 | `npm run typecheck`; `npm run build` | `progress.md` | docs-site typecheck/build 通过 |
| L2 | 不适用 | n/a | 本任务不改运行时或浏览器交互 |
| L3 | `review.md` self review + human confirmation gate | `review.md` 与 `walkthrough.md` | 无 open material finding，等待人工确认 |

## 暂停 / 升级条件

- 所需工作超出已批准写入范围。
- 实际风险高于原计划，证据深度需要升级。
- review 发现会改变范围或方案的 P0/P1/P2 问题。
- 环境无法提供关键证据，继续执行会变成猜测。
