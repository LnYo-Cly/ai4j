# 执行策略

## Subagent Authorization

任务开始时先读这一段，并向用户说明当前授权状态。这里是授权记录，不是执行沙箱。

| Role | Status | Permission | Authorized By | Authorized At | Scope | Worktree / Branch | Reuse |
| --- | --- | --- | --- | --- | --- | --- | --- |
| reviewer subagent | allowed by default | read-only | harness task policy | task creation | current task review | n/a | allowed within this task |
| worker subagent | not-needed | no write delegation | coordinator | 2026-06-20 | planning-only task | n/a | 后续实现任务再申请 |

## Subagent Delegation Decision

| Question | Decision | Reason | Next Action |
| --- | --- | --- | --- |
| Should a reviewer subagent be used? | no | 本轮只做规划落盘，范围来自用户连续讨论和仓库标准；先由 coordinator self-review，后续实现前可开专项 reviewer。 | 在 `review.md` 写自审和残余风险。 |
| Would a worker subagent materially help? | no | 没有可并行写代码的独立切片；当前目标是生成单一规划 SSoT。 | 不派 worker；实现任务再使用 worktree。 |

## User Authorization Decision

| Gate | State | Decided By | Decided At | Scope | Worktree / Branch | Notes |
| --- | --- | --- | --- | --- | --- | --- |
| worker subagent | not-needed | coordinator | 2026-06-20 | planning-only | n/a | 后续代码实现任务再按范围授权。 |

## 决策表

| 决策 | 选择 | 说明 |
| --- | --- | --- |
| 主执行者 | coordinator | coordinator 负责编排规划、写 task package 和最终收口。 |
| Subagent 模式 | none | 当前没有并行 worker 价值。 |
| 审查模型 | self-check / architecture review | 本任务是规划；实现前再按风险升级 reviewer 或 human review。 |
| Worktree 策略 | same checkout for planning; dedicated worktree for implementation | 规划只写 Harness；实现必须隔离。 |
| 冲突控制 | coordinator owns shared files | 本任务不编辑生产代码和共享 regression docs。 |
| 证据深度 | L0 | 规划类任务用静态材料完整性、diff check、Harness status 验证。 |

## 子代理 / Worker 合同

| 角色 | 输入包 | 写入范围 | 交接要求 | 负责人 |
| --- | --- | --- | --- | --- |
| n/a | C-001..C-009 | n/a | n/a | coordinator |

## 证据计划

| 证据层级 | 计划命令或检查 | 记录位置 | 完成条件 |
| --- | --- | --- | --- |
| L0 | `git diff --check` | `progress.md` | 无 whitespace/error 输出，CRLF warning 可记录 |
| L0 | `npx --yes coding-agent-harness status --json .` | `progress.md` | failures=0；若有 warning 必须解释 |
| L1 | 不适用 | n/a | 本任务不改生产代码 |
| L2 | 不适用 | n/a | 本任务不改运行行为 |
| L3 | 不适用 | n/a | 本任务不做发布或真实 provider/sandbox 验证 |

## 暂停 / 升级条件

- 规划被要求直接进入代码实现。
- 需要新增 Maven 模块或 public API 顶层命名。
- 需要接入真实 sandbox、真实云 runner 或 provider token。
- 需要修改 docs-site 用户页面并保证示例可运行。
- reviewer 或用户认为路线图应改变优先级。

## Module Preset Strategy

| Field | Value |
| --- | --- |
| Module Key | agent-runtime |
| Module Plan | coding-agent-harness/planning/modules/agent-runtime/module_plan.md |

Keep shared module decisions in the module plan or module context files. Keep task-specific evidence in this task directory.
