# 执行策略

## Subagent Authorization

任务开始时先读这一段，并向用户说明当前授权状态。这里是授权记录，不是执行沙箱。

| Role | Status | Permission | Authorized By | Authorized At | Scope | Worktree / Branch | Reuse |
| --- | --- | --- | --- | --- | --- | --- | --- |
| reviewer subagent | allowed by default | read-only | harness task policy | task creation | current task review | n/a | allowed within this task |
| worker subagent | not authorized | write only after user approval | n/a | n/a | n/a | n/a | not used |

## Subagent Delegation Decision

任务开始时，coordinator 必须根据用户目标主动做这个判断，即使用户完全没有提到 subagent。
不要假设用户知道 subagent 或 worker 是什么。如果分工有帮助，用白话说明收益，并向用户申请一次授权。
可以直接对用户说 subagent 或 worker subagent；关键规则是 agent 不能等用户主动提出 subagent。
如果任务已经明显拆成互不重叠的独立切片，implementation 前就应判断为 `ask-user`。如果还不知道精确文件路径，先确认路径，然后立刻申请独立执行助手授权。

| Question | Decision | Reason | Next Action |
| --- | --- | --- | --- |
| Should a reviewer subagent be used? | no | 本轮范围集中在 `ai4j-agent` runtime + docs，已有 deterministic tests 和 self-review；不引入额外 read-only subagent 以避免重复上下文成本。 | 由 coordinator 完成 self-review 并写 `review.md`。 |
| Would a worker subagent materially help? | no | 变更横跨同一 runtime API、tests、docs 和 regression governance，拆 worker 会增加共享文件冲突；根 checkout 也有 unrelated dirty work，当前专用 worktree 已足够隔离。 | 不派 worker；coordinator 直接完成。 |

## User Authorization Decision

如果上方 worker 决策是 `ask-user`，implementation 必须暂停，直到这里记录用户答案。
已解决状态只能是 `authorized`、`denied` 或 `not-needed`。选择 `ask-user` 后不得继续保持 `pending`。

| Gate | State | Decided By | Decided At | Scope | Worktree / Branch | Notes |
| --- | --- | --- | --- | --- | --- | --- |
| worker subagent | not-needed | coordinator | 2026-07-06 | n/a | n/a | 单一 worktree 内完成，未拆 worker。 |

## 决策表

| 决策 | 选择 | 说明 |
| --- | --- | --- |
| 主执行者 | coordinator | coordinator 负责编排顺序、冲突判断和最终收口。 |
| Subagent 模式 | none | self-review 足够覆盖本轮 MVP；无 worker handoff。 |
| 审查模型 | self-check + deterministic regression | 安全敏感点通过 F-001 fix loop 和新增测试覆盖；如发布前需要可追加人工 review。 |
| Worktree 策略 | dedicated worktree | 使用 `.worktrees/feature/dynamic-workflow-executor`，根 checkout 保持 untouched。 |
| 冲突控制 | coordinator owns shared files | 只编辑本任务相关 runtime/docs/regression/task-local 文件。 |
| 证据深度 | L2 | Java targeted tests + docs-site type/build + diff check；live-provider 为范围外 opt-in。 |

## 子代理 / Worker 合同

如使用 subagent 或 worker，在这里写清楚输入包、写入范围、handoff 格式和最终集成 owner。

| 角色 | 输入包 | 写入范围 | 交接要求 | 负责人 |
| --- | --- | --- | --- | --- |
| n/a | task plan + current diff | n/a | n/a | coordinator |

## 证据计划

| 证据层级 | 计划命令或检查 | 记录位置 | 完成条件 |
| --- | --- | --- | --- |
| L0 | code/diff self-review，确认插件/host 边界、Java 8 兼容和安全默认 | `review.md` / `findings.md` | 无 open P0/P1/P2 blocking finding |
| L1 | `mvn -pl ai4j-agent -am -Dtest=DynamicWorkflow*Test -DfailIfNoTests=false -DskipTests=false test` | `progress.md` | BUILD SUCCESS，动态工作流测试全通过 |
| L2 | `npm run typecheck` + `npm run build` in `docs-site/`，`git diff --check` | `progress.md` / `walkthrough.md` | docs-site 通过，diff 无 whitespace error |
| L3 | live-provider/provider-key E2E | n/a | 范围外；如用户另行要求再开 opt-in 任务 |

## 暂停 / 升级条件

- 所需工作超出已批准写入范围。
- 共享表需要更新，但没有 coordinator lock。
- 实际风险高于原计划，证据深度需要升级。
- reviewer 发现会改变范围或方案的 P0/P1/P2 问题。
- 环境无法提供关键证据，继续执行会变成猜测。
