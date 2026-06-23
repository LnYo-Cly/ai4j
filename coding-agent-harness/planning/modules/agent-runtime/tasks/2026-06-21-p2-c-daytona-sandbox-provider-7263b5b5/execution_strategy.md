# 执行策略

## Subagent Authorization

任务开始时先读这一段，并向用户说明当前授权状态。这里是授权记录，不是执行沙箱。

| Role | Status | Permission | Authorized By | Authorized At | Scope | Worktree / Branch | Reuse |
| --- | --- | --- | --- | --- | --- | --- | --- |
| reviewer subagent | allowed by task policy | read-only | harness task policy / prior user approval for subagents when useful | task creation + conversation context | Daytona provider code/docs/governance review | n/a | allowed within this task |
| worker subagent | not authorized | write not used in this task | coordinator | 2026-06-21 | no worker write scope | current checkout only | not used in final pass |

## Subagent Delegation Decision

| Question | Decision | Reason | Next Action |
| --- | --- | --- | --- |
| Should a reviewer subagent be used? | attempted-but-unavailable | A read-only review would have been useful for the Daytona provider/security boundary, but current Codex subagent/thread concurrency limit was reached. | Use coordinator adversarial self-review, record limitation in `review.md`, keep evidence depth through tests/docs/harness. |
| Would a worker subagent materially help? | no | Remaining work after takeover was integration/docs/governance/final verification in shared files; parallel writes would increase conflict risk. | Coordinator owns final integration. |

## User Authorization Decision

| Gate | State | Decided By | Decided At | Scope | Worktree / Branch | Notes |
| --- | --- | --- | --- | --- | --- | --- |
| worker subagent | not-needed | coordinator | 2026-06-21 | final integration/docs/governance | current branch `docs/agent-final-roadmap-record` | User had generally allowed subagents, but no write worker was needed for this narrow final pass. |

## 决策表

| 决策 | 选择 | 说明 |
| --- | --- | --- |
| 主执行者 | coordinator | coordinator 负责编排实现整合、共享治理文件、最终验证和提交。 |
| Subagent 模式 | reviewer attempted, worker not used | reviewer 因并发额度不可用；worker 不适合接管共享收口文件。 |
| 审查模型 | adversarial self-review + executable evidence | 没有独立 reviewer artifact，因此通过 targeted/broad/docs/live-smoke evidence 加强。 |
| Worktree 策略 | same checkout | 当前任务在已有 agent-roadmap branch 上连续收口；无并行 worker 写入。 |
| 冲突控制 | coordinator owns shared files | Regression SSoT、Cadence Ledger、docs-site、task package 由 coordinator 统一更新。 |
| 证据深度 | L1 + L2 + L3 opt-in | RG-002 targeted/broad，RG-008 docs build，LV-004 prior Daytona live smoke。 |

## 子代理 / Worker 合同

| 角色 | 输入包 | 写入范围 | 交接要求 | 负责人 |
| --- | --- | --- | --- | --- |
| reviewer subagent | current Daytona diff and task context | read-only | no output; spawn failed due thread limit | coordinator |
| worker | n/a | n/a | n/a | coordinator |

## 证据计划

| 证据层级 | 计划命令或检查 | 记录位置 | 完成条件 |
| --- | --- | --- | --- |
| L0 | `git diff --check`; secret regex scan | `progress.md` / final summary | no whitespace errors; no actual Daytona/E2B token values |
| L1 | `mvn -pl ai4j-agent -am -DskipTests=false -Dtest=DaytonaSandboxProviderTest -DfailIfNoTests=false test` | `progress.md`; `artifacts/INDEX.md` | 5 Daytona deterministic tests pass |
| L1 | `mvn -pl ai4j-agent -am -DskipTests=false test` | `progress.md`; Regression SSoT | extension API/core/agent baseline pass |
| L2 | `npm --prefix docs-site run build` | `progress.md`; Cadence Ledger | docs-site static build pass |
| L3 | `DaytonaSandboxLiveSmokeTest` under `-P live-provider-tests` | `progress.md`; `artifacts/INDEX.md`; LV-004 | real Daytona create/execute/close smoke pass with sanitized evidence |
| Harness | `npx --yes coding-agent-harness status --json .` | `progress.md` | no validation failures; dirty-state warning acceptable before commit |

## 暂停 / 升级条件

- Daytona API/toolbox 字段与 local deterministic 或 live smoke evidence 冲突。
- 需要把 API key 写入 fixture、docs 或 command log 才能继续。
- `deleteOnClose` 行为无法用 local fake 或 live evidence 解释清楚。
- RG-002 broad baseline 失败且不是无关环境问题。
- docs-site build failure 不是本地 ignored dependency 缺失，而是内容/路由错误。

## Module Preset Strategy

| Field | Value |
| --- | --- |
| Module Key | agent-runtime |
| Module Plan | coding-agent-harness/planning/modules/agent-runtime/module_plan.md |

Keep shared module decisions in the module plan or module context files. Keep task-specific evidence in this task directory.
