# 执行策略

## Subagent Authorization

| Role | Status | Permission | Authorized By | Authorized At | Scope | Worktree / Branch | Reuse |
| --- | --- | --- | --- | --- | --- | --- | --- |
| reviewer subagent | used | read-only | harness task policy | 2026-06-22 | current task review | n/a | allowed within this task |
| worker subagent | used | write only after user approval | user | 2026-06-22 | CLI / ACP observability slice | same checkout | allowed within approved scope |

## Subagent Delegation Decision

| Question | Decision | Reason | Next Action |
| --- | --- | --- | --- |
| Should a reviewer subagent be used? | yes | 本任务需要独立 review 来确认 correlation 链路和 Langfuse projection 没有漏字段。 | 已执行 reviewer-only 审查。 |
| Would a worker subagent materially help? | already-authorized | CLI / ACP 会话归一化和 worker 验证可并行完成，且用户已允许。 | worker 在限定写入范围内完成最小修复并回报。 |

## User Authorization Decision

| Gate | State | Decided By | Decided At | Scope | Worktree / Branch | Notes |
| --- | --- | --- | --- | --- | --- | --- |
| worker subagent | authorized | user | 2026-06-22 | CLI / ACP observability slice | same checkout | 允许并行修复与测试。 |

## 决策表

| 决策 | 选择 | 说明 |
| --- | --- | --- |
| 主执行者 | coordinator | 负责收口、集成和最终状态判断。 |
| Subagent 模式 | reviewer-only / worker-worktree | 本任务使用 reviewer 复核和 worker 切片并行。 |
| 审查模型 | adversarial review | 需要确认字段贯通、投影不丢失、真实回归通过。 |
| Worktree 策略 | same checkout | 任务切片较小，且存在既有 dirty state，不额外开分支。 |
| 冲突控制 | coordinator owns shared files | harness 共享文件由 coordinator 统一落盘。 |
| 证据深度 | L1 / L2 / L3 | 真实单测 + 真实 Maven 回归 + review 收口。 |

## 子代理 / Worker 合同

| 角色 | 输入包 | 写入范围 | 交接要求 | 负责人 |
| --- | --- | --- | --- | --- |
| reviewer | task diff + task package | read-only | review report | coordinator |
| worker | task-local CLI/ACP slice | `DefaultCodingSessionManager` / `CodingCliSessionRunner` / `AcpJsonRpcServer` | report + test result | coordinator |

## 证据计划

| 证据层级 | 计划命令或检查 | 记录位置 | 完成条件 |
| --- | --- | --- | --- |
| L0 | 静态检查 / diff review | `progress.md` | 断点已识别并修复 |
| L1 | 单元测试 / targeted check | `progress.md` | `LangfuseTraceExporterTest`、`DefaultCodingSessionManagerTest` 通过 |
| L2 | 真实 Maven 回归 | `progress.md` | `ai4j-agent` / `ai4j-coding` / `ai4j-cli` 通过 |
| L3 | review / walkthrough | `review.md` 与 `walkthrough.md` | 无阻塞发现并完成 closeout |

## 暂停 / 升级条件

- correlation 字段再次丢失。
- 真实 Maven 回归失败。
- 需要把 runId 持久化到 session store 才能继续。
- reviewer 提出 P0/P1/P2 阻塞发现。
- 任务范围开始溢出到 docs-site 或其他模块。

## Module Preset Strategy

| Field | Value |
| --- | --- |
| Module Key | agent-runtime |
| Module Plan | coding-agent-harness/planning/modules/agent-runtime/module_plan.md |

Keep shared module decisions in the module plan or module context files. Keep task-specific evidence in this task directory.
