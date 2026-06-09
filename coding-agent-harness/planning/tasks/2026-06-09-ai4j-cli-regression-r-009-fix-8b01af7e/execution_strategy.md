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
| Should a reviewer subagent be used? | no | 修复范围很窄，失败复现、代码 diff 和 owning module gate 能直接证明 R-009 是否关闭。 | 使用 self regression review，提交后等待 human review confirmation。 |
| Would a worker subagent materially help? | no | 只涉及 ACP 映射、一个 JLine 测试断言和治理记录；并行 worker 会增加协调成本。 | coordinator 在当前 checkout 内完成。 |

## User Authorization Decision

如果上方 worker 决策是 `ask-user`，implementation 必须暂停，直到这里记录用户答案。
已解决状态只能是 `authorized`、`denied` 或 `not-needed`。选择 `ask-user` 后不得继续保持 `pending`。

| Gate | State | Decided By | Decided At | Scope | Worktree / Branch | Notes |
| --- | --- | --- | --- | --- | --- | --- |
| worker subagent | not-needed | coordinator | 2026-06-09 | R-009 CLI regression fix | current `main` checkout | 用户已授权继续；本切片无需 worker。 |

## 决策表

| 决策 | 选择 | 说明 |
| --- | --- | --- |
| 主执行者 | coordinator | coordinator 负责编排顺序、冲突判断和最终收口。 |
| Subagent 模式 | none | 最小修复，单人当前 checkout 足够。 |
| 审查模型 | self-check + human review queue | 本轮是 regression closeout，不是架构或安全扩展；提交后由人工确认。 |
| Worktree 策略 | same checkout | 工作区开始时干净，变更范围集中，没有并行改动冲突。 |
| 冲突控制 | coordinator owns shared files | 回归治理表和 task materials 由 coordinator 串行更新。 |
| 证据深度 | L1 + L2 | L1 覆盖目标失败类和 CLI 直接套件；L2 覆盖 monorepo package 烟测。 |

## 子代理 / Worker 合同

如使用 subagent 或 worker，在这里写清楚输入包、写入范围、handoff 格式和最终集成 owner。

| 角色 | 输入包 | 写入范围 | 交接要求 | 负责人 |
| --- | --- | --- | --- | --- |
| n/a | C-001..C-005 | n/a | n/a | coordinator |

## 证据计划

| 证据层级 | 计划命令或检查 | 记录位置 | 完成条件 |
| --- | --- | --- | --- |
| L0 | `git diff --check` | `progress.md` / final verification | no whitespace errors |
| L1 | `mvn -pl ai4j-cli "-Dtest=JlineShellTerminalIOTest,AcpCommandTest" -DfailIfNoTests=false -DskipTests=false test`; `mvn -pl ai4j-cli -DfailIfNoTests=false -DskipTests=false test` | `progress.md` | target 30 tests pass; direct CLI 261 tests pass |
| L2 | `mvn -DskipTests package` | `progress.md` | 11 reactor projects package successfully |
| L1 residual probe | `mvn -pl ai4j-cli -am -DfailIfNoTests=false -DskipTests=false test` | `progress.md` / Regression SSoT | failure remains limited to known R-008 before CLI |

## 暂停 / 升级条件

- 所需工作超出已批准写入范围。
- 共享表需要更新，但没有 coordinator lock。
- 实际风险高于原计划，证据深度需要升级。
- reviewer 发现会改变范围或方案的 P0/P1/P2 问题。
- 环境无法提供关键证据，继续执行会变成猜测。
- ACP 需要新增或改变对外状态事件协议字段；本轮只允许修正文流污染，不设计新协议。
