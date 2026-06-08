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
| Should a reviewer subagent be used? | no | 本轮为单线 coordinator 实现，测试覆盖明确；采用 self adversarial review 和 deterministic commands。 | 在 `review.md` 写入 confidence challenge。 |
| Would a worker subagent materially help? | no | 代码、docs 和治理表共享写入较多，拆 worker 会增加冲突，收益低。 | coordinator 直接执行并收口。 |

## User Authorization Decision

| Gate | State | Decided By | Decided At | Scope | Worktree / Branch | Notes |
| --- | --- | --- | --- | --- | --- | --- |
| worker subagent | not-needed | coordinator | 2026-06-09 | n/a | n/a | 本轮不拆 worker。 |

## 决策表

| 决策 | 选择 | 说明 |
| --- | --- | --- |
| 主执行者 | coordinator | coordinator 负责编排实现、文档、回归和最终收口。 |
| Subagent 模式 | none | 共享文件多于独立切片，不使用 worker。 |
| 审查模型 | self adversarial review | 对 tool execution 安全边界做架构 / security / regression challenge；人工确认仍走 review queue。 |
| Worktree 策略 | same checkout | 当前分支已有 harness lifecycle commits，且本轮由 coordinator 单线完成。 |
| 冲突控制 | coordinator owns shared files | `docs-site`、README、Regression SSoT、Cadence Ledger 和 task package 均由 coordinator 修改。 |
| 证据深度 | L1 + L2 | targeted JUnit 覆盖 Agent / Coding Agent 行为，package/docs/diff/harness 做本地 smoke。 |

## 子代理 / Worker 合同

| 角色 | 输入包 | 写入范围 | 交接要求 | 负责人 |
| --- | --- | --- | --- | --- |
| n/a | n/a | n/a | n/a | coordinator |

## 证据计划

| 证据层级 | 计划命令或检查 | 记录位置 | 完成条件 |
| --- | --- | --- | --- |
| L0 | diff review、`git diff --check` | `progress.md` / `review.md` | 无格式错误，无未记录的共享表漂移 |
| L1 | `mvn -pl ai4j-agent -am -Dtest=ExtensionAgentToolsTest -DfailIfNoTests=false -DskipTests=false test`; `mvn -pl ai4j-coding -am -Dtest=CodingAgentBuilderTest -DfailIfNoTests=false -DskipTests=false test` | `progress.md` | Agent / Coding Agent targeted tests pass |
| L2 | `mvn -DskipTests package`; docs-site `npm run typecheck` / `npm run build`; harness status | `progress.md` / `walkthrough.md` | package/docs pass；harness status 无 validation failure |
| L3 | 不适用 | n/a | 本轮不需要 live provider |

## 暂停 / 升级条件

- 需要改变 CLI command/resource Guardrail 拦截语义。
- 需要改变 Coding Agent approval、workspace 写边界或 tool policy 顺序。
- targeted tests 无法证明工具未执行。
- full agent/coding suite 的既有 R-008 之外出现新失败。
