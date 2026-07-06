# 执行策略

## Subagent Authorization

| Role | Status | Permission | Authorized By | Authorized At | Scope | Worktree / Branch | Reuse |
| --- | --- | --- | --- | --- | --- | --- | --- |
| reviewer subagent | allowed by default | read-only | harness task policy | task creation | current task review | n/a | not used |
| worker subagent | not-needed | n/a | coordinator | 2026-07-06 | narrow single-class implementation | feature/rag-token-aware-context | not needed |

## Subagent Delegation Decision

| Question | Decision | Reason | Next Action |
| --- | --- | --- | --- |
| Should a reviewer subagent be used? | no | Scope is small and deterministic tests cover behavior. | Self-review in `review.md`. |
| Would a worker subagent materially help? | no | Code/docs/regression files are shared and better serialized. | Continue with coordinator. |

## User Authorization Decision

| Gate | State | Decided By | Decided At | Scope | Worktree / Branch | Notes |
| --- | --- | --- | --- | --- | --- | --- |
| worker subagent | not-needed | coordinator | 2026-07-06 | n/a | n/a | Single-owner implementation. |

## 决策表

| 决策 | 选择 | 说明 |
| --- | --- | --- |
| 主执行者 | coordinator | 最小协调成本。 |
| Subagent 模式 | none | 无并行收益。 |
| 审查模型 | self-check | 行为由单元测试和 core/docs/package gates 覆盖。 |
| Worktree 策略 | dedicated worktree | 当前主工作区 dirty，必须隔离。 |
| 冲突控制 | coordinator owns shared files | docs/regression/task 文件串行维护。 |
| 证据深度 | L1/L2 | core tests + docs build + package smoke。 |

## 子代理 / Worker 合同

| 角色 | 输入包 | 写入范围 | 交接要求 | 负责人 |
| --- | --- | --- | --- | --- |
| n/a | C-001..C-003 | n/a | n/a | coordinator |

## 证据计划

| 证据层级 | 计划命令或检查 | 记录位置 | 完成条件 |
| --- | --- | --- | --- |
| L0 | `git diff --check` | `progress.md` | no whitespace errors |
| L1 | `mvn -pl ai4j "-Dtest=TokenAwareRagContextAssemblerTest,DefaultRagServiceTest" -DskipTests=false test`; `mvn -pl ai4j -am -DskipTests=false test` | `progress.md` | pass |
| L2 | `npm run typecheck`; `npm run build`; `mvn -DskipTests package` | `progress.md` | pass |
| L3 | n/a | n/a | not needed |

## 暂停 / 升级条件

- 需要改变默认 assembler 行为。
- 需要 provider-specific tokenizer registry。
- docs-site 或 Maven gates 无法在本地稳定通过。
