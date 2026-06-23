# 执行策略

## Subagent Authorization

| Role | Status | Permission | Authorized By | Authorized At | Scope | Worktree / Branch | Reuse |
| --- | --- | --- | --- | --- | --- | --- | --- |
| reviewer subagent | allowed by default | read-only | harness task policy | task creation | current task review | n/a | allowed within this task |
| worker subagent | not used | n/a | n/a | n/a | n/a | n/a | n/a |

单 provider 增量，coordinator 直接实现，未拆 worker subagent。

## Subagent Delegation Decision

| Question | Decision | Reason | Next Action |
| --- | --- | --- | --- |
| Should a reviewer subagent be used? | yes | 外部协议实现，PR 评审时调用只读 reviewer | PR 评审时调用 |
| Would a worker subagent materially help? | no | 单 provider、依赖明确、无并行切片 | n/a |

## User Authorization Decision

| Gate | State | Decided By | Decided At | Scope | Worktree / Branch | Notes |
| --- | --- | --- | --- | --- | --- | --- |
| worker subagent | not-needed | coordinator | 2026-06-23 | n/a | n/a | 单切片无需 worker |

## 决策表

| 决策 | 选择 | 说明 |
| --- | --- | --- |
| 主执行者 | coordinator | 单 provider 增量，coordinator 全程实现与验证。 |
| Subagent 模式 | reviewer-only | PR 评审时用只读 reviewer。 |
| 审查模型 | self-check + PR reviewer | 协议已逐字节 live 实测，self-check 充分；PR 再过 reviewer。 |
| Worktree 策略 | same checkout | 单分支 feat/e2b-sandbox-provider，无并行写。 |
| 冲突控制 | n/a | 纯新增文件，无共享文件冲突。 |
| 证据深度 | L2 | 纯单测(L1) + live 端到端烟测(L2)。 |

## 子代理 / Worker 合同

| 角色 | 输入包 | 写入范围 | 交接要求 | 负责人 |
| --- | --- | --- | --- | --- |
| reviewer（PR 时） | C-001..C-003 | read-only | report | coordinator |

## 证据计划

| 证据层级 | 计划命令或检查 | 记录位置 | 完成条件 |
| --- | --- | --- | --- |
| L0 | `git diff --check` | progress.md | 无 whitespace error ✅ |
| L1 | `mvn -pl ai4j-agent -am -Dtest=E2B* test` | progress.md | 15 离线测试全绿 ✅ |
| L2 | `-Plive-provider-tests -Dtest=E2BSandboxLiveSmokeTest` | progress.md | live create/execute/delete 全绿 ✅ |
| L2 | `mvn -pl ai4j-agent -am test` | progress.md | 148 测试无回归 ✅ |

## 暂停 / 升级条件

- 协议/auth 不确定时 live 实测确认（已做）。
- 范围外能力（cancel/filesystem/labels）记为 deferred，不扩大本轮范围。

## Module Preset Strategy

| Field | Value |
| --- | --- |
| Module Key | agent-runtime |
| Module Plan | coding-agent-harness/planning/modules/agent-runtime/module_plan.md |
