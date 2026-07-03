# 执行策略

## Subagent Authorization

任务开始时先读这一段，并向用户说明当前授权状态。这里是授权记录，不是执行沙箱。

| Role | Status | Permission | Authorized By | Authorized At | Scope | Worktree / Branch | Reuse |
| --- | --- | --- | --- | --- | --- | --- | --- |
| reviewer subagent | allowed by default | read-only | harness task policy | task creation | current task review | n/a | not used; self-review was sufficient for the narrow provider surface |
| worker subagent | not authorized | write only after user approval | n/a | n/a | n/a | n/a | not needed; implementation was single-owner and conflict-prone shared docs stayed with coordinator |

## Subagent Delegation Decision

任务开始时，coordinator 必须根据用户目标主动做这个判断，即使用户完全没有提到 subagent。
不要假设用户知道 subagent 或 worker 是什么。如果分工有帮助，用白话说明收益，并向用户申请一次授权。
可以直接对用户说 subagent 或 worker subagent；关键规则是 agent 不能等用户主动提出 subagent。
如果任务已经明显拆成互不重叠的独立切片，implementation 前就应判断为 `ask-user`。如果还不知道精确文件路径，先确认路径，然后立刻申请独立执行助手授权。

| Question | Decision | Reason | Next Action |
| --- | --- | --- | --- |
| Should a reviewer subagent be used? | no | Scope is narrow, deterministic tests cover the provider contract, and no adversarial-review trigger applies. | Self-review in `review.md`. |
| Would a worker subagent materially help? | no | Code, tests, starter binding, and regression ledgers touch shared files that should remain serialized under coordinator ownership. | Continue in one dedicated worktree. |

## User Authorization Decision

如果上方 worker 决策是 `ask-user`，implementation 必须暂停，直到这里记录用户答案。
已解决状态只能是 `authorized`、`denied` 或 `not-needed`。选择 `ask-user` 后不得继续保持 `pending`。

| Gate | State | Decided By | Decided At | Scope | Worktree / Branch | Notes |
| --- | --- | --- | --- | --- | --- | --- |
| worker subagent | not-needed | coordinator | 2026-07-03 | n/a | n/a | Single-owner implementation in the approved feature worktree. |

## 决策表

| 决策 | 选择 | 说明 |
| --- | --- | --- |
| 主执行者 | coordinator | coordinator 负责编排顺序、冲突判断和最终收口。 |
| Subagent 模式 | none | 最小协作模式足够，避免多人同时编辑 regression/task shared files。 |
| 审查模型 | self-check | OpenAI-compatible `/v1/videos` contract 由 MockWebServer 和 module/starter gates 覆盖；live provider 是 opt-in residual。 |
| Worktree 策略 | dedicated worktree | 使用 `G:\My_Project\java\ai4j-sdk\.worktrees\feature\chatfire-openai-videos` / `feature/chatfire-openai-videos`。 |
| 冲突控制 | coordinator owns shared files | subagent 不得直接编辑 coordinator 管理的全局表或共享文件，除非获得明确锁。 |
| 证据深度 | L1 | 目标是 deterministic local SDK/starter contract；不触发 live-provider gate。 |

## 子代理 / Worker 合同

如使用 subagent 或 worker，在这里写清楚输入包、写入范围、handoff 格式和最终集成 owner。

| 角色 | 输入包 | 写入范围 | 交接要求 | 负责人 |
| --- | --- | --- | --- | --- |
| n/a | C-001; task diff | n/a | coordinator self-review in `review.md` | coordinator |

## 证据计划

| 证据层级 | 计划命令或检查 | 记录位置 | 完成条件 |
| --- | --- | --- | --- |
| L0 | `git diff --check` and task material self-check | `progress.md` | no whitespace errors and no placeholder closeout fields |
| L1 | `mvn -pl ai4j "-Dtest=OpenAiVideoServiceTest,AiServiceRegistryTest" -DskipTests=false test`; `mvn -pl ai4j -am -DskipTests=false test`; `mvn -pl ai4j-spring-boot-starter -am -DskipTests=false test` | `progress.md` and `review.md` | all commands pass |
| L2 | n/a | n/a | not needed for deterministic provider contract |
| L3 | live ChatFire smoke with `CHATFIRE_API_KEY` | `review.md` and walkthrough if later approved | opt-in only; not a blocker for this task |

## 暂停 / 升级条件

- 所需工作超出已批准写入范围。
- 共享表需要更新，但没有 coordinator lock。
- 实际风险高于原计划，证据深度需要升级。
- reviewer 发现会改变范围或方案的 P0/P1/P2 问题。
- 环境无法提供关键证据，继续执行会变成猜测。

## Module Preset Strategy

| Field | Value |
| --- | --- |
| Module Key | core-sdk |
| Module Plan | coding-agent-harness/planning/modules/core-sdk/module_plan.md |

Keep shared module decisions in the module plan or module context files. Keep task-specific evidence in this task directory.
