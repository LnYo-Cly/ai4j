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
| Should a reviewer subagent be used? | no | 范围是单一 webapp gate 和治理同步；本轮使用 self adversarial review，人工确认仍由 human gate 处理。 | 在 `review.md` 记录 no-finding / residual。 |
| Would a worker subagent materially help? | no | 实现、CI 和台账文件高度耦合，并行 worker 反而会增加共享文件冲突。 | coordinator 在 same checkout 内完成。 |

## User Authorization Decision

如果上方 worker 决策是 `ask-user`，implementation 必须暂停，直到这里记录用户答案。
已解决状态只能是 `authorized`、`denied` 或 `not-needed`。选择 `ask-user` 后不得继续保持 `pending`。

| Gate | State | Decided By | Decided At | Scope | Worktree / Branch | Notes |
| --- | --- | --- | --- | --- | --- | --- |
| worker subagent | not-needed | coordinator | 2026-06-10 12:26 | n/a | same checkout | 单一窄范围切片，不拆 worker。 |

## 决策表

| 决策 | 选择 | 说明 |
| --- | --- | --- |
| 主执行者 | coordinator | coordinator 负责编排顺序、冲突判断和最终收口。 |
| Subagent 模式 | none | 不使用 worker；self review 加人工确认门禁足够覆盖本轮。 |
| 审查模型 | adversarial review | review 重点检查 test gate 是否真实、CI 是否接入、R-003 是否没有被夸大成 E2E。 |
| Worktree 策略 | same checkout | 当前分支已承载 R-003 harness commits，改动集中且无并行写入。 |
| 冲突控制 | coordinator owns shared files | Regression SSoT、Cadence、Feature SSoT 和模块计划由 coordinator 单点写入。 |
| 证据深度 | L2 | Webapp test/lint/type/build 是 deterministic local smoke；live backend/browser 保持 LV-003 opt-in。 |

## 子代理 / Worker 合同

如使用 subagent 或 worker，在这里写清楚输入包、写入范围、handoff 格式和最终集成 owner。

| 角色 | 输入包 | 写入范围 | 交接要求 | 负责人 |
| --- | --- | --- | --- | --- |
| n/a | C-001..C-004 | n/a | n/a | coordinator |

## 证据计划

| 证据层级 | 计划命令或检查 | 记录位置 | 完成条件 |
| --- | --- | --- | --- |
| L0 | diff review and generated `dist` negative scan | `progress.md` | CI/test files not bundled into generated output. |
| L1 | `npm run test` | `progress.md` | backend workflow contract tests pass. |
| L2 | `npm run lint`; `npm run ts-check`; `npm run build` | `progress.md` | webapp baseline passes with only known warnings. |
| L3 | n/a | n/a | LV-003 browser/backend validation remains opt-in and out of scope. |

## 暂停 / 升级条件

- 所需工作超出已批准写入范围。
- 共享表需要更新，但没有 coordinator lock。
- 实际风险高于原计划，证据深度需要升级。
- reviewer 发现会改变范围或方案的 P0/P1/P2 问题。
- 环境无法提供关键证据，继续执行会变成猜测。
