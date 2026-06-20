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
| Should a reviewer subagent be used? | no | 这次变更边界很窄，self-check + 模块回归已经足够覆盖风险。 | coordinator 直接完成审查材料。 |
| Would a worker subagent materially help? | no | 只有一个模块的 TUI 投影和测试，不值得再拆一个写入 worker。 | 保持当前 checkout 直接实现。 |

## User Authorization Decision

| Gate | State | Decided By | Decided At | Scope | Worktree / Branch | Notes |
| --- | --- | --- | --- | --- | --- | --- |
| worker subagent | not-needed | coordinator | 2026-06-11 | n/a | n/a | 没有独立 worker 切片。 |

## 决策表

| 决策 | 选择 | 说明 |
| --- | --- | --- |
| 主执行者 | coordinator | coordinator 负责编排顺序、冲突判断和最终收口。 |
| Subagent 模式 | none | 变更范围窄，直接在当前 checkout 完成更稳。 |
| 审查模型 | self-check | 用 diff + 单元测试 + 模块回归覆盖此次变更。 |
| Worktree 策略 | same checkout | 没有 worker handoff，不需要额外 worktree。 |
| 冲突控制 | coordinator owns shared files | 共享任务包只由 coordinator 更新。 |
| 证据深度 | L1 | 代码变更 + 单测/模块回归足以证明这类 TUI 投影改动。 |

## 子代理 / Worker 合同

| 角色 | 输入包 | 写入范围 | 交接要求 | 负责人 |
| --- | --- | --- | --- | --- |
| n/a | n/a | n/a | n/a | coordinator |

## 证据计划

| 证据层级 | 计划命令或检查 | 记录位置 | 完成条件 |
| --- | --- | --- | --- |
| L0 | `git diff --check` | `progress.md` | 无格式问题 |
| L1 | `mvn -pl ai4j-cli -am -DskipTests=false test` | `progress.md` | `ai4j-cli` 及 reactor 回归通过 |
| L2 | n/a | n/a | n/a |
| L3 | n/a | n/a | n/a |

## 暂停 / 升级条件

- 所需工作超出已批准写入范围。
- 共享表需要更新，但没有 coordinator lock。
- 实际风险高于原计划，证据深度需要升级。
- reviewer 发现会改变范围或方案的 P0/P1/P2 问题。
- 环境无法提供关键证据，继续执行会变成猜测。
