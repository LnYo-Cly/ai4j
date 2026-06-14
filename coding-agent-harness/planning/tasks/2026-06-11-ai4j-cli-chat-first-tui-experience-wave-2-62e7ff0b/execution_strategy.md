# 执行策略

## Subagent Authorization

任务开始时先读这一段，并向用户说明当前授权状态。这里是授权记录，不是执行沙箱。

| Role | Status | Permission | Authorized By | Authorized At | Scope | Worktree / Branch | Reuse |
| --- | --- | --- | --- | --- | --- | --- | --- |
| reviewer subagent | allowed by default | read-only | harness task policy | task creation | current task review | n/a | allowed within this task |
| worker subagent | not authorized | write only after user approval | n/a | n/a | n/a | n/a | allowed only within approved task/scope |

## Subagent Delegation Decision

任务开始时，coordinator 必须根据用户目标主动做这个判断，即使用户完全没有提到 subagent。
不要假设用户知道 subagent 或 worker 是什么。如果分工有帮助，用白话说明收益，并向用户申请一次授权。
可以直接对用户说 subagent 或 worker subagent；关键规则是 agent 不能等用户主动提出 subagent。
如果任务已经明显拆成互不重叠的独立切片，implementation 前就应判断为 `ask-user`。如果还不知道精确文件路径，先确认路径，然后立刻申请独立执行助手授权。

| Question | Decision | Reason | Next Action |
| --- | --- | --- | --- |
| Should a reviewer subagent be used? | no | 本轮实现范围集中，先由 coordinator 自审并保留人工确认门禁；若测试或 diff 暴露风险再升级。 | 任务完成后填写 `review.md`。 |
| Would a worker subagent materially help? | no | 改动会触碰已有未提交 CLI diff，拆分 worker 容易制造冲突，收益低于协调成本。 | coordinator 原地实现并保护现有改动。 |

## User Authorization Decision

如果上方 worker 决策是 `ask-user`，implementation 必须暂停，直到这里记录用户答案。
已解决状态只能是 `authorized`、`denied` 或 `not-needed`。选择 `ask-user` 后不得继续保持 `pending`。

| Gate | State | Decided By | Decided At | Scope | Worktree / Branch | Notes |
| --- | --- | --- | --- | --- | --- | --- |
| worker subagent | not-needed | coordinator | 2026-06-11 | `ai4j-cli` TUI/status/tests | same checkout | 小范围串行实现更安全。 |

## 决策表

| 决策 | 选择 | 说明 |
| --- | --- | --- |
| 主执行者 | coordinator | coordinator 负责编排顺序、冲突判断和最终收口。 |
| Subagent 模式 | none | 本轮不拆 worker，避免覆盖已有未提交 CLI diff。 |
| 审查模型 | self-check + human confirmation gate | 本地 targeted regression 后提交审查材料，人工确认另行完成。 |
| Worktree 策略 | same checkout | 现有改动已在当前 checkout，继续原地保护。 |
| 冲突控制 | coordinator owns shared files | subagent 不得直接编辑 coordinator 管理的全局表或共享文件，除非获得明确锁。 |
| 证据深度 | L1 | CLI/TUI 变更用 targeted JUnit 覆盖，必要时记录未做人工交互 smoke 的 residual。 |

## 子代理 / Worker 合同

如使用 subagent 或 worker，在这里写清楚输入包、写入范围、handoff 格式和最终集成 owner。

| 角色 | 输入包 | 写入范围 | 交接要求 | 负责人 |
| --- | --- | --- | --- | --- |
| n/a | C-001..C-005 | n/a | n/a | coordinator |

## 证据计划

| 证据层级 | 计划命令或检查 | 记录位置 | 完成条件 |
| --- | --- | --- | --- |
| L0 | `git diff -- ai4j-cli/...` 自审 | `progress.md` | diff 只包含本轮 TUI/status/test 范围和上一轮 extension 接入延续。 |
| L1 | `mvn -pl ai4j-cli -am "-Dtest=TuiSessionViewTest,JlineShellTerminalIOTest,CliThemeStylerTest,SlashCommandControllerTest" -DskipTests=false -DfailIfNoTests=false test` | `progress.md` | 已通过，91 tests，0 failures，0 errors，0 skipped。 |
| L2 | 不计划 | `walkthrough.md` | 本轮不需要真实终端人工冒烟；如未做，记录 residual。 |
| L3 | 人工审查确认 | `review.md` 与 walkthrough | Agent 只提交材料，不代办人工确认。 |

## 暂停 / 升级条件

- 所需工作超出已批准写入范围。
- 共享表需要更新，但没有 coordinator lock。
- 实际风险高于原计划，证据深度需要升级。
- reviewer 发现会改变范围或方案的 P0/P1/P2 问题。
- 环境无法提供关键证据，继续执行会变成猜测。
