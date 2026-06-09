# 执行策略

## Subagent Authorization

任务开始时先读这一段，并向用户说明当前授权状态。这里是授权记录，不是执行沙箱。

| Role | Status | Permission | Authorized By | Authorized At | Scope | Worktree / Branch | Reuse |
| --- | --- | --- | --- | --- | --- | --- | --- |
| reviewer subagent | allowed by default | read-only | harness task policy | task creation | current task review | n/a | allowed within this task |
| worker subagent | not-needed | no worker execution | coordinator | 2026-06-09 | n/a | n/a | n/a |

## Subagent Delegation Decision

任务开始时，coordinator 必须根据用户目标主动做这个判断，即使用户完全没有提到 subagent。
不要假设用户知道 subagent 或 worker 是什么。如果分工有帮助，用白话说明收益，并向用户申请一次授权。
可以直接对用户说 subagent 或 worker subagent；关键规则是 agent 不能等用户主动提出 subagent。
如果任务已经明显拆成互不重叠的独立切片，implementation 前就应判断为 `ask-user`。如果还不知道精确文件路径，先确认路径，然后立刻申请独立执行助手授权。

| Question | Decision | Reason | Next Action |
| --- | --- | --- | --- |
| Should a reviewer subagent be used? | no | R-008 是已复现的窄回归；使用 targeted tests、full module tests 和 harness review packet 足够。 | coordinator 自检并提交 review packet。 |
| Would a worker subagent materially help? | no | 修复集中在单一异常传播路径，并行 worker 会增加共享文件协调成本。 | coordinator 顺序执行。 |

## User Authorization Decision

如果上方 worker 决策是 `ask-user`，implementation 必须暂停，直到这里记录用户答案。
已解决状态只能是 `authorized`、`denied` 或 `not-needed`。选择 `ask-user` 后不得继续保持 `pending`。

| Gate | State | Decided By | Decided At | Scope | Worktree / Branch | Notes |
| --- | --- | --- | --- | --- | --- | --- |
| worker subagent | not-needed | coordinator | 2026-06-09 | n/a | n/a | 任务不拆 worker。 |

## 决策表

| 决策 | 选择 | 说明 |
| --- | --- | --- |
| 主执行者 | coordinator | coordinator 负责编排顺序、冲突判断和最终收口。 |
| Subagent 模式 | none | 当前回归面窄，不启用 worker。 |
| 审查模型 | predefined verifier | 以 `HandoffPolicyTest`、agent full gate、CLI `-am` gate 和 harness status 作为审查证据。 |
| Worktree 策略 | same checkout | 无并行 worker，当前 checkout 可控。 |
| 冲突控制 | coordinator owns shared files | subagent 不得直接编辑 coordinator 管理的全局表或共享文件，除非获得明确锁。 |
| 证据深度 | L1 | 本地单元/模块测试覆盖行为修复；不需要 live-provider。 |

## 子代理 / Worker 合同

如使用 subagent 或 worker，在这里写清楚输入包、写入范围、handoff 格式和最终集成 owner。

| 角色 | 输入包 | 写入范围 | 交接要求 | 负责人 |
| --- | --- | --- | --- | --- |
| n/a | C-001..C-004 | n/a | n/a | coordinator |

## 证据计划

| 证据层级 | 计划命令或检查 | 记录位置 | 完成条件 |
| --- | --- | --- | --- |
| L0 | `git diff --check` | `progress.md` | 无 whitespace error。 |
| L1 | `mvn -pl ai4j-agent -Dtest=HandoffPolicyTest -DfailIfNoTests=false -DskipTests=false test` | `progress.md` | targeted 回归通过。 |
| L1 | `mvn -pl ai4j-agent -am -DfailIfNoTests=false -DskipTests=false test` | `progress.md` | RG-002 通过。 |
| L1 | `mvn -pl ai4j-cli -am -DfailIfNoTests=false -DskipTests=false test` | `progress.md` | RG-004 broad gate 不再被 R-008 阻塞。 |
| L1 | `mvn -DskipTests package` 与 `npx --yes coding-agent-harness status --json .` | `progress.md` | smoke 与 harness 校验通过。 |

## 暂停 / 升级条件

- 所需工作超出已批准写入范围。
- 共享表需要更新，但没有 coordinator lock。
- 实际风险高于原计划，证据深度需要升级。
- reviewer 发现会改变范围或方案的 P0/P1/P2 问题。
- 环境无法提供关键证据，继续执行会变成猜测。
