# 执行策略

## Subagent Authorization

任务开始时先读这一段，并向用户说明当前授权状态。这里是授权记录，不是执行沙箱。

| Role | Status | Permission | Authorized By | Authorized At | Scope | Worktree / Branch | Reuse |
| --- | --- | --- | --- | --- | --- | --- | --- |
| reviewer subagent | allowed by default | read-only | harness task policy | task creation | current task review | n/a | allowed within this task |
| worker subagent | not needed | n/a | coordinator decision | 2026-06-20 | docs-site single-slice edit | n/a | n/a |

## Subagent Delegation Decision

| Question | Decision | Reason | Next Action |
| --- | --- | --- | --- |
| Should a reviewer subagent be used? | no | 本轮是单一 docs-site 切片，self adversarial review + docs build/typecheck 足够；PR/CI 继续提供外部门禁。 | 在 `review.md` 做信心挑战。 |
| Would a worker subagent materially help? | no | 变更集中在 1 个新增页面和 3 个入口链接，拆 worker 会增加冲突与协调成本。 | coordinator 直接完成。 |

## User Authorization Decision

| Gate | State | Decided By | Decided At | Scope | Worktree / Branch | Notes |
| --- | --- | --- | --- | --- | --- | --- |
| worker subagent | not-needed | coordinator | 2026-06-20 | docs-site focused edit | docs/agent-sdk-real-api-docs | 用户已授权继续任务队列；本轮不需要写入型 worker。 |

## 决策表

| 决策 | 选择 | 说明 |
| --- | --- | --- |
| 主执行者 | coordinator | coordinator 负责 docs-site 变更、验证、PR 和收口。 |
| Subagent 模式 | none | 单切片文档任务不需要并行 worker。 |
| 审查模型 | self-check + CI | 自审重点检查伪 API、SPI 边界和 secret 风险；CI 检查构建。 |
| Worktree 策略 | dedicated worktree | 已从 `origin/dev` 创建 `.worktrees/docs/agent-sdk-real-api-docs`。 |
| 冲突控制 | coordinator owns docs-site Agent pages | 不修改 Java 模块和全局 reference。 |
| 证据深度 | L1 | docs-site typecheck/build + Harness status 覆盖本切片。 |

## 子代理 / Worker 合同

| 角色 | 输入包 | 写入范围 | 交接要求 | 负责人 |
| --- | --- | --- | --- | --- |
| n/a | C-001..C-008 | n/a | n/a | coordinator |

## 证据计划

| 证据层级 | 计划命令或检查 | 记录位置 | 完成条件 |
| --- | --- | --- | --- |
| L0 | `rg` 检查伪 API/secret/token 字符串；`git diff --check` | `progress.md` | 无误导性伪 API 示例、无 token、diff whitespace clean |
| L1 | `npm --prefix docs-site run typecheck`; `npm --prefix docs-site run build` | `progress.md` | docs-site typecheck/build 通过 |
| L1 | `npx --yes coding-agent-harness status --json .` | `progress.md` | failures=0；任务材料不在 Missing Materials |
| L2 | GitHub PR checks | PR / `progress.md` | PR checks green before merge |
| L3 | 不适用 | n/a | n/a |

## 暂停 / 升级条件

- 发现需要描述尚不存在的 API。
- docs build 暴露断链或 MDX 错误，且修复需要改 Java API 或大规模 IA。
- 出现 secrets/token/local path 风险。
- PR 与远端 dev 冲突，需要重新 rebase 或拆分。

## Module Preset Strategy

| Field | Value |
| --- | --- |
| Module Key | docs-site |
| Module Plan | coding-agent-harness/planning/modules/docs-site/module_plan.md |

Keep shared module decisions in the module plan or module context files. Keep task-specific evidence in this task directory.
