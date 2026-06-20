# 执行策略

## Subagent Authorization

| Role | Status | Permission | Authorized By | Authorized At | Scope | Worktree / Branch | Reuse |
| --- | --- | --- | --- | --- | --- | --- | --- |
| reviewer subagent | allowed by default | read-only | harness task policy | task creation | R0 digest self/adversarial review | n/a | allowed within this task |
| worker subagent | not-needed | n/a | coordinator decision | 2026-06-20 | docs/research-only slice | n/a | not used |

## Subagent Delegation Decision

| Question | Decision | Reason | Next Action |
| --- | --- | --- | --- |
| Should a reviewer subagent be used? | no | 本任务是公开资料 digest 和 docs-site 页面，风险主要是 source attribution/source gap；self review + docs build + harness scanner 足够。 | 不启动 reviewer subagent；PR 后接受 GitHub/CI review。 |
| Would a worker subagent materially help? | no | 本切片写入范围集中在一个 docs-site 页面和一个 task package；并行 worker 会增加 sidebar/roadmap/task material 冲突。 | coordinator 单线程完成并提交。 |

## User Authorization Decision

| Gate | State | Decided By | Decided At | Scope | Worktree / Branch | Notes |
| --- | --- | --- | --- | --- | --- | --- |
| worker subagent | not-needed | coordinator | 2026-06-20 | R0 digest docs/research slice | docs/agent-sdk-r0-research-digest | 用户已授权整体继续执行；本切片无需额外 worker。 |

## 决策表

| 决策 | 选择 | 说明 |
| --- | --- | --- |
| 主执行者 | coordinator | coordinator 负责公开资料筛选、docs-site 写入、验证和 PR。 |
| Subagent 模式 | reviewer-only policy available, not used | 资料 digest 风险可由 self review 和公开链接复查覆盖。 |
| 审查模型 | self-check + harness task-review + PR review | 本任务不改 Java 行为，重点是文档准确性和来源边界。 |
| Worktree 策略 | dedicated worktree | 使用 `.worktrees/docs/agent-sdk-r0-research-digest`，避免污染主工作树。 |
| 冲突控制 | coordinator owns docs-site sidebar/roadmap and task package | 本任务只改 docs-site Agent 页面和本 task package，不编辑其他任务。 |
| 证据深度 | L1 | docs build、diff check、token scan、Harness status 足以覆盖文档任务。 |

## 子代理 / Worker 合同

| 角色 | 输入包 | 写入范围 | 交接要求 | 负责人 |
| --- | --- | --- | --- | --- |
| n/a | n/a | n/a | n/a | coordinator |

## 证据计划

| 证据层级 | 计划命令或检查 | 记录位置 | 完成条件 |
| --- | --- | --- | --- |
| L0 | `git diff --check` | `progress.md` / `review.md` | 无 trailing whitespace / EOF 空白问题。 |
| L0 | token fragment scan with `rg` excluding generated dirs | `progress.md` / `review.md` | 不命中用户提供的 provider token 片段。 |
| L1 | `npm --prefix docs-site run build` | `progress.md` / `walkthrough.md` | Docusaurus build 成功。 |
| L1 | `npx --yes coding-agent-harness status --json .` | `progress.md` / `review.md` | failures=0；task materialsReady=true；lesson decision complete。 |

## 暂停 / 升级条件

- 发现需要声称某个外部项目内部实现，但没有公开资料可验证。
- docs build 失败且需要改 Docusaurus 配置而不是页面内容。
- 需要引入新的 Java API 示例，但无法在代码或测试中证明真实存在。
- 需要修改 `docs/05-TEST-QA/` 固定回归面；本任务预期不触发。
- Harness scanner 报 missing materials 或 blocker，必须先修复任务本地材料再继续 PR。

## Module Preset Strategy

| Field | Value |
| --- | --- |
| Module Key | docs-site |
| Module Plan | coding-agent-harness/planning/modules/docs-site/module_plan.md |

本任务的共享模块事实仅通过 Harness lifecycle 和 module plan 同步。所有调研结论、source gap、验证证据保留在 task-local package 和 docs-site 页面中。
