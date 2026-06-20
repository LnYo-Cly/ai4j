# 执行策略

## Subagent Authorization

任务开始时先读这一段，并向用户说明当前授权状态。这里是授权记录，不是执行沙箱。

| Role | Status | Permission | Authorized By | Authorized At | Scope | Worktree / Branch | Reuse |
| --- | --- | --- | --- | --- | --- | --- | --- |
| reviewer subagent | allowed by default | read-only | harness task policy | task creation | current task review | n/a | allowed within this task |
| worker subagent | not-needed | no write delegation | coordinator | 2026-06-20 | single bounded API/docs slice | `feature/memory-compact-session-api-polish` | 后续独立切片再申请 |

## Subagent Delegation Decision

| Question | Decision | Reason | Next Action |
| --- | --- | --- | --- |
| Should a reviewer subagent be used? | no | 本切片较小，证据可由 deterministic tests + docs build + Harness status 覆盖；PR 后仍走人工/CI review。 | 在 `review.md` 完成 self architecture/regression review。 |
| Would a worker subagent materially help? | not-needed | 写入范围集中在 `AgentSession`、compact 小 API、单个测试类和两页 docs；拆 worker 会增加冲突成本。 | coordinator 直接实现并提交。 |

## User Authorization Decision

| Gate | State | Decided By | Decided At | Scope | Worktree / Branch | Notes |
| --- | --- | --- | --- | --- | --- | --- |
| worker subagent | not-needed | coordinator | 2026-06-20 | Memory/Compact Session API polish | `feature/memory-compact-session-api-polish` | 用户已授权继续任务队列；本切片无需并行 worker。 |

## 决策表

| 决策 | 选择 | 说明 |
| --- | --- | --- |
| 主执行者 | coordinator | coordinator 负责实现、验证、PR 和任务材料收口。 |
| Subagent 模式 | none | 单一小切片，避免 worker 冲突。 |
| 审查模型 | self architecture/regression review + CI/PR review | public API 小幅新增，必须用测试和 docs build 证明。 |
| Worktree 策略 | dedicated worktree | 当前实现位于 `.worktrees/feature/memory-compact-session-api-polish`。 |
| 冲突控制 | coordinator owns shared files | 不并行编辑 Regression SSoT、Cadence Ledger 和 docs-site。 |
| 证据深度 | L1 + L2 docs smoke | `ai4j-agent` 行为用 JUnit；docs-site 用 build。 |

## 子代理 / Worker 合同

| 角色 | 输入包 | 写入范围 | 交接要求 | 负责人 |
| --- | --- | --- | --- | --- |
| n/a | C-001..C-008 | n/a | n/a | coordinator |

## 证据计划

| 证据层级 | 计划命令或检查 | 记录位置 | 完成条件 |
| --- | --- | --- | --- |
| L0 | `git diff --check` | `progress.md` / `review.md` | 无 whitespace error 输出。 |
| L0 | token scan for pasted token fragments | `progress.md` / `review.md` | 不在仓库文本中出现用户粘贴 token。 |
| L1 | `mvn -pl ai4j-agent -am "-Dtest=AgentMemoryCompactContextProjectorTest" -DskipTests=false -DfailIfNoTests=false test` | `progress.md` | 新增/既有 compact tests 全通过。 |
| L1 | `mvn -pl ai4j-agent -am -DskipTests=false test` | `progress.md` | agent runtime broad gate 全通过。 |
| L2 | `npm --prefix docs-site run build` | `progress.md` | Docusaurus build 成功。 |
| L0 | `npx --yes coding-agent-harness status --json .` | `progress.md` | failures=0；若有 dirty warning，提交前解释。 |

## 暂停 / 升级条件

- 需要真实 provider key 或模型调用验证 compact 质量。
- 需要改变 `compact(CompactPolicy)` 返回类型或删除老 API。
- 需要把 CLI `/compact`、TUI 或远端 runner 行为并入本任务。
- Maven / docs build 出现与本改动相关的 P0/P1/P2 问题。

## Module Preset Strategy

| Field | Value |
| --- | --- |
| Module Key | agent-runtime |
| Module Plan | coding-agent-harness/planning/modules/agent-runtime/module_plan.md |

Keep shared module decisions in the module plan or module context files. Keep task-specific evidence in this task directory.
