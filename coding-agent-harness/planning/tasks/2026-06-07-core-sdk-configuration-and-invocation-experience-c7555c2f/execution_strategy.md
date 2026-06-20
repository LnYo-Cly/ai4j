# 执行策略

## Subagent Authorization

任务开始时先读这一段，并向用户说明当前授权状态。这里是授权记录，不是执行沙箱。

| Role | Status | Permission | Authorized By | Authorized At | Scope | Worktree / Branch | Reuse |
| --- | --- | --- | --- | --- | --- | --- | --- |
| reviewer subagent | allowed by default | read-only | harness task policy | task creation | current task review | n/a | allowed within this task |
| worker subagent | not-needed | n/a | coordinator | 2026-06-07 | task package only | same checkout | n/a |

## Subagent Delegation Decision

| Question | Decision | Reason | Next Action |
| --- | --- | --- | --- |
| Should a reviewer subagent be used? | no | 设计任务范围可由 coordinator 基于源码证据 self-review；不改业务代码。 | 使用 self-review。 |
| Would a worker subagent materially help? | no | 不做并行实现，只写设计合同。 | coordinator 直接执行。 |

## User Authorization Decision

如果上方 worker 决策是 `ask-user`，implementation 必须暂停，直到这里记录用户答案。
已解决状态只能是 `authorized`、`denied` 或 `not-needed`。选择 `ask-user` 后不得继续保持 `pending`。

| Gate | State | Decided By | Decided At | Scope | Worktree / Branch | Notes |
| --- | --- | --- | --- | --- | --- | --- |
| worker subagent | not-needed | coordinator | 2026-06-07 | task package only | same checkout | 设计任务不使用 worker。 |

## 决策表

| 决策 | 选择 | 说明 |
| --- | --- | --- |
| 主执行者 | coordinator | coordinator 负责编排顺序、冲突判断和最终收口。 |
| Subagent 模式 | none | 不需要 worker。 |
| 审查模型 | self-check | 本任务不改 Java API，风险集中在设计准确性。 |
| Worktree 策略 | same checkout | 只写当前 task package。 |
| 冲突控制 | coordinator owns shared files | 不触碰业务源码和 docs-site 正文。 |
| 证据深度 | L0 | 设计审计，使用源码/docs 扫描和 harness status。 |

## 子代理 / Worker 合同

如使用 subagent 或 worker，在这里写清楚输入包、写入范围、handoff 格式和最终集成 owner。

| 角色 | 输入包 | 写入范围 | 交接要求 | 负责人 |
| --- | --- | --- | --- | --- |
| n/a | C-001..C-008 | n/a | n/a | coordinator |

## 证据计划

| 证据层级 | 计划命令或检查 | 记录位置 | 完成条件 |
| --- | --- | --- | --- |
| L0 | source/docs targeted scan; `git diff --check`; `npx.cmd --yes coding-agent-harness status --json .` | `progress.md` | design package present, no template leftovers, harness status pass |

## 暂停 / 升级条件

- 设计要求新增公开 API 或修改 Java 实现。
- 发现现有源码不支持预期的 profile / OpenAI-compatible 配置事实。
- 需要改 docs-site 正文或 README。
- harness status 出现材料缺失或阻塞队列。
