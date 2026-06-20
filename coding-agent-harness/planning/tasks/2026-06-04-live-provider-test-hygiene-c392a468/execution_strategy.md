# 执行策略

## Subagent Authorization

| Role | Status | Permission | Authorized By | Authorized At | Scope | Worktree / Branch | Reuse |
| --- | --- | --- | --- | --- | --- | --- | --- |
| reviewer subagent | allowed by default | read-only | harness task policy | task creation | current task review | n/a | allowed within this task |
| worker subagent | not authorized | write only after user approval | pending | pending | pending | pending | allowed only within approved task/scope |

## Subagent Delegation Decision

| Question | Decision | Reason | Next Action |
| --- | --- | --- | --- |
| Should a reviewer subagent be used? | no | 变更是机械隔离、凭据读取和文档同步；证据来自编译、测试、profile smoke 和扫描。 | self review with command evidence |
| Would a worker subagent materially help? | no | 共享 POM、测试与回归文档需要同一 coordinator 控制，避免跨 worker 冲突。 | not-needed |

## User Authorization Decision

| Gate | State | Decided By | Decided At | Scope | Worktree / Branch | Notes |
| --- | --- | --- | --- | --- | --- | --- |
| worker subagent | not-needed | coordinator | 2026-06-04 | n/a | n/a | 单 checkout 串行处理。 |

## 决策表

| 决策 | 选择 | 说明 |
| --- | --- | --- |
| 主执行者 | coordinator | coordinator 负责编排顺序、冲突判断和最终收口。 |
| Subagent 模式 | none | 无独立可写 worker；review 由 self-check 加命令证据支撑。 |
| 审查模型 | regression/self-check | 风险主要是测试选择器和凭据处理，使用 targeted Maven 与扫描验证。 |
| Worktree 策略 | same checkout | 没有并行 worker；共享 POM/文档在当前 checkout 直接维护。 |
| 冲突控制 | coordinator owns shared files | POM、Regression SSoT、Cadence 和 task materials 均由 coordinator 修改。 |
| 证据深度 | L1 plus LV profile smoke | default local tests 和 testCompile/package 为 L1/L2；live profile 在无凭据时验证 skip contract。 |

## 子代理 / Worker 合同

| 角色 | 输入包 | 写入范围 | 交接要求 | 负责人 |
| --- | --- | --- | --- | --- |
| n/a | C-001..C-004 | n/a | n/a | coordinator |

## 证据计划

| 证据层级 | 计划命令或检查 | 记录位置 | 完成条件 |
| --- | --- | --- | --- |
| L0 | `rg` credential/local path/category scan | `progress.md` / `artifacts/INDEX.md` | live tests 无默认 key、无 API key property fallback、无本机路径 |
| L1 | `mvn -pl ai4j -DskipTests=false test`; `mvn -pl ai4j-coding -DskipTests=false test` | `progress.md` | 默认 local tests 通过且 live tests 排除 |
| L1/L2 | `mvn -pl ai4j-coding -am -DskipTests package` | `progress.md` | agent/coding 测试源码编译，打包通过 |
| L3-smoke | targeted `-P live-provider-tests -Dtest=<LiveTest>` | `progress.md` | 无凭据时 JUnit Assume clean skip |

## 暂停 / 升级条件

- 需要真实 provider API key 或 release credential。
- 需要修复 `HandoffPolicyTest` 行为。
- 发现 live provider hygiene 之外的 runtime API breakage。
- reviewer 或用户要求将 R-008 纳入本任务。
