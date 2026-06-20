# 执行策略

## Subagent Authorization

| Role | Status | Permission | Authorized By | Authorized At | Scope | Worktree / Branch | Reuse |
| --- | --- | --- | --- | --- | --- | --- | --- |
| reviewer subagent | allowed by default | read-only | harness task policy | task creation | current task review | n/a | allowed within this task |
| worker subagent | not-needed | none | coordinator | 2026-06-20 | planning-only task | n/a | not needed |

## Subagent Delegation Decision

| Question | Decision | Reason | Next Action |
| --- | --- | --- | --- |
| Should a reviewer subagent be used? | no | 本轮只记录对话形成的架构规划，不改生产代码；self-review 足够覆盖材料完整性。 | 由 coordinator 自检 review.md。 |
| Would a worker subagent materially help? | no | 没有并行实现切片；worker 会增加协调成本。 | 不使用 worker。 |

## User Authorization Decision

| Gate | State | Decided By | Decided At | Scope | Worktree / Branch | Notes |
| --- | --- | --- | --- | --- | --- | --- |
| worker subagent | not-needed | coordinator | 2026-06-20 | planning-only task | n/a | 不涉及代码实现。 |

## 决策表

| 决策 | 选择 | 说明 |
| --- | --- | --- |
| 主执行者 | coordinator | coordinator 负责任务材料和最终收口。 |
| Subagent 模式 | none | 规划记录任务不需要并行 worker。 |
| 审查模型 | self-check | 不改生产代码，L0/L1 harness 检查足够。 |
| Worktree 策略 | same checkout | 只改当前任务包，不需要独立 worktree。 |
| 冲突控制 | coordinator owns task package | 只修改本任务目录和 Harness generated ledger。 |
| 证据深度 | L1 | 文档规划 + Harness 状态检查。 |

## 子代理 / Worker 合同

| 角色 | 输入包 | 写入范围 | 交接要求 | 负责人 |
| --- | --- | --- | --- | --- |
| n/a | C-001..C-005 | n/a | n/a | coordinator |

## 证据计划

| 证据层级 | 计划命令或检查 | 记录位置 | 完成条件 |
| --- | --- | --- | --- |
| L0 | `git status --short` | `progress.md` | 只出现本任务材料改动。 |
| L1 | `npx --yes coding-agent-harness status --json .` | `progress.md` | status pass，无 failure。 |
| L1 | `npx --yes coding-agent-harness task-review ...` | `progress.md` / `review.md` | 任务进入 review queue 或明确留在 active。 |
| L2 | 不适用 | n/a | 本任务不改运行行为。 |
| L3 | 不适用 | n/a | 本任务不是发布门禁。 |

## 暂停 / 升级条件

- 需要修改 Java 生产代码。
- 需要新增 Maven 模块或插件合同。
- 需要把规划投影到 docs-site 或 README。
- Harness 状态检查出现 failure。
