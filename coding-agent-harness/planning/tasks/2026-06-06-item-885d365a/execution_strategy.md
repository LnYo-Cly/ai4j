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
| Should a reviewer subagent be used? | no | 范围是确定性测试、文档同步和回归台账；自审加固定门禁足够，人工确认仍保留。 | self-review 后提交 human review。 |
| Would a worker subagent materially help? | no | 文件数量不多，且共享回归台账需要 coordinator 统一更新；拆 worker 会增加交接成本。 | coordinator 单线程完成。 |

## User Authorization Decision

如果上方 worker 决策是 `ask-user`，implementation 必须暂停，直到这里记录用户答案。
已解决状态只能是 `authorized`、`denied` 或 `not-needed`。选择 `ask-user` 后不得继续保持 `pending`。

| Gate | State | Decided By | Decided At | Scope | Worktree / Branch | Notes |
| --- | --- | --- | --- | --- | --- | --- |
| worker subagent | not-needed | coordinator | 2026-06-06 | 当前首聊合同任务 | same checkout | 范围集中，不需要 worker。 |

## 决策表

| 决策 | 选择 | 说明 |
| --- | --- | --- |
| 主执行者 | coordinator | coordinator 负责编排顺序、冲突判断和最终收口。 |
| Subagent 模式 | none | 没有并行 worker，review 由 self-check + human confirmation 完成。 |
| 审查模型 | predefined verifier | RG-001/RG-005/RG-007/RG-008 覆盖本轮 touched surface。 |
| Worktree 策略 | same checkout | 工作区开始时干净，变更范围集中，无并行写入。 |
| 冲突控制 | coordinator owns shared files | subagent 不得直接编辑 coordinator 管理的全局表或共享文件，除非获得明确锁。 |
| 证据深度 | L1/L2 | Java 模块用 L1 tests；docs-site 和 package smoke 用 L2 local_smoke。 |

## 子代理 / Worker 合同

如使用 subagent 或 worker，在这里写清楚输入包、写入范围、handoff 格式和最终集成 owner。

| 角色 | 输入包 | 写入范围 | 交接要求 | 负责人 |
| --- | --- | --- | --- | --- |
| n/a | C-001..C-005 | n/a | n/a | coordinator |

## 证据计划

| 证据层级 | 计划命令或检查 | 记录位置 | 完成条件 |
| --- | --- | --- | --- |
| L0 | `git diff --check`、示例/文档自检 | `progress.md` | 无 whitespace error，示例命令和测试名一致 |
| L1 | `mvn -pl ai4j ... test`、`mvn -pl ai4j-spring-boot-starter ... test` | `progress.md` | core 和 starter tests 均 pass |
| L2 | `npm run typecheck`、`npm run build`、`mvn -DskipTests package` | `progress.md` | docs-site build/typecheck 和 9-module package pass |
| L3 | 不适用 | `review.md` 与 walkthrough | 本任务不需要 live provider 或 release validation |

## 暂停 / 升级条件

- 所需工作超出已批准写入范围。
- 共享表需要更新，但没有 coordinator lock。
- 实际风险高于原计划，证据深度需要升级。
- reviewer 发现会改变范围或方案的 P0/P1/P2 问题。
- 环境无法提供关键证据，继续执行会变成猜测。
