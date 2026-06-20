# 执行策略

## Subagent Authorization

任务开始时先读这一段，并向用户说明当前授权状态。这里是授权记录，不是执行沙箱。

| Role | Status | Permission | Authorized By | Authorized At | Scope | Worktree / Branch | Reuse |
| --- | --- | --- | --- | --- | --- | --- | --- |
| reviewer subagent | allowed by default | read-only | harness task policy | task creation | current task review | n/a | allowed within this task |
| worker subagent | not authorized | write only after user approval | pending | pending | pending | pending | allowed only within approved task/scope |

## Subagent Delegation Decision

| Question | Decision | Reason | Next Action |
| --- | --- | --- | --- |
| Should a reviewer subagent be used? | no | 本轮已经处于用户要求继续做完和推送的收口阶段；可用证据来自本地回归与 self adversarial review，人工确认仍由用户侧完成。 | 在 `review.md` 记录 self review，不运行 `review-confirm`。 |
| Would a worker subagent materially help? | no | 写入范围包含共享 POM/BOM、docs-site、Regression SSoT 和 harness registry，拆 worker 会增加共享表协调成本。 | coordinator 串行执行并提交。 |

## User Authorization Decision

| Gate | State | Decided By | Decided At | Scope | Worktree / Branch | Notes |
| --- | --- | --- | --- | --- | --- | --- |
| worker subagent | not-needed | coordinator | 2026-06-09 | ask-user plugin wave 10 | current checkout / main | 不拆 worker，避免共享治理表冲突。 |

## 决策表

| 决策 | 选择 | 说明 |
| --- | --- | --- |
| 主执行者 | coordinator | coordinator 负责编排顺序、冲突判断和最终收口。 |
| Subagent 模式 | none | 当前阶段集中收口，使用 coordinator 串行更新共享文件。 |
| 审查模型 | self adversarial review + user-side human review pending | agent 可以提交审查材料，但不能代替人工确认。 |
| Worktree 策略 | same checkout | 用户要求在当前项目继续并推送，且无 worker handoff。 |
| 冲突控制 | coordinator owns shared files | subagent 不得直接编辑 coordinator 管理的全局表或共享文件，除非获得明确锁。 |
| 证据深度 | L1 + L2 | 插件模块单测、全仓 packaging smoke、docs-site typecheck/build、diff check、harness status。 |

## 子代理 / Worker 合同

| 角色 | 输入包 | 写入范围 | 交接要求 | 负责人 |
| --- | --- | --- | --- | --- |
| n/a | C-001..C-005 | n/a | n/a | coordinator |

## 证据计划

| 证据层级 | 计划命令或检查 | 记录位置 | 完成条件 |
| --- | --- | --- | --- |
| L0 | `git diff --check` | `progress.md` | 无 whitespace error |
| L1 | `mvn -pl ai4j-plugin-ask-user -am -DskipTests=false test` | `progress.md` | extension API dependency tests 和插件 tests 通过 |
| L2 | `mvn -DskipTests package`; docs-site typecheck/build; harness status | `progress.md` | 共享 packaging、docs-site 和 harness 状态通过 |
| L3 | 不执行 live/provider/release gate | `review.md` 与 walkthrough | 本任务不触发真实 provider、发布或人工确认 gate |

## 暂停 / 升级条件

- 所需工作超出已批准写入范围。
- 共享表需要更新，但没有 coordinator lock。
- 实际风险高于原计划，证据深度需要升级。
- review 发现会改变范围或方案的 P0/P1/P2 问题。
- 环境无法提供关键证据，继续执行会变成猜测。
