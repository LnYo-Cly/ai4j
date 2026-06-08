# 执行策略：AI4J extension system wave 1

## Subagent Authorization

| Role | Status | Permission | Authorized By | Authorized At | Scope | Worktree / Branch | Reuse |
| --- | --- | --- | --- | --- | --- | --- | --- |
| reviewer subagent | allowed by default | read-only | harness task policy | task creation | current task review | n/a | not used |
| worker subagent | not authorized | n/a | coordinator decision | 2026-06-08 | extension-api-wave-1 | main | not used |

## Subagent Delegation Decision

| Question | Decision | Reason | Next Action |
| --- | --- | --- | --- |
| Should a reviewer subagent be used? | no | Wave 1 的实现边界小且已有人工确认要求；使用 self-review + Confidence Challenge 足够覆盖公共合同风险。 | 在 `review.md` 记录 self-review 与残余风险。 |
| Would a worker subagent materially help? | no | 变更集中在新增 API 模块和共享治理表，拆分 worker 会增加全局表冲突，不提升交付质量。 | coordinator 在当前 checkout 内完成实现、验证和收口。 |

## User Authorization Decision

| Gate | State | Decided By | Decided At | Scope | Worktree / Branch | Notes |
| --- | --- | --- | --- | --- | --- | --- |
| worker subagent | not-needed | coordinator | 2026-06-08 | extension-api-wave-1 | main | 单 coordinator 执行；不创建 worker worktree。 |

## 决策表

| 决策 | 选择 | 说明 |
| --- | --- | --- |
| 主执行者 | coordinator | coordinator 负责实现、共享表同步、验证和最终收口。 |
| Subagent 模式 | none | 不派 worker；review 使用本任务 `review.md` 的 self-review。 |
| 审查模型 | self-check + Confidence Challenge + human confirmation | 公共合同通过 deterministic tests 和全仓 package smoke 证明；最终仍等待人工确认。 |
| Worktree 策略 | same checkout | 当前任务开始时在主 checkout 执行，未启用并行 worker。 |
| 冲突控制 | coordinator owns shared files | root POM、BOM、CI、Regression、Cadence、harness context 和 Module Registry 均由 coordinator 一次性同步。 |
| 证据深度 | L2 | 新增 Maven 模块和公共 API 需要模块测试、全仓 package smoke、diff hygiene、harness status。 |

## 子代理 / Worker 合同

| 角色 | 输入包 | 写入范围 | 交接要求 | 负责人 |
| --- | --- | --- | --- | --- |
| n/a | C-001, C-002, C-003, C-005 | n/a | n/a | coordinator |

## 证据计划

| 证据层级 | 计划命令或检查 | 记录位置 | 完成条件 |
| --- | --- | --- | --- |
| L0 | `git diff --check` | `progress.md`、`walkthrough.md` | 无 whitespace error；CRLF warning 可接受。 |
| L1 | `mvn -pl ai4j-extension-api -DskipTests=false test` | `progress.md`、`walkthrough.md` | RG-010 通过，extension API tests 全绿。 |
| L2 | `mvn -DskipTests package` | `progress.md`、`walkthrough.md` | root reactor package smoke 通过，新增模块进入全仓构建。 |
| L2 | `npx.cmd --yes coding-agent-harness status --json .` | `progress.md`、`walkthrough.md` | task package 无模板残留；若仍需人工确认，明确记录状态。 |
| L3 | 不适用 | `review.md` | Wave 1 不触达 live provider、Marketplace 或外部第三方 jar。 |

## 暂停 / 升级条件

- 需要把 Wave 1 扩大到 CLI/Spring/Agent/Coding runtime adapter。
- 发现新增公共 API 需要依赖 `ai4j` core 或 agent runtime 内部类型。
- L1/L2 回归失败且不能在当前范围内修复。
- harness status 出现材料缺失或任务生命周期矛盾，不能进入人工确认。
