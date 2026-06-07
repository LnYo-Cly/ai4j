# docs-site 文档重构总任务 - 任务包索引

Task Contract: harness-task/v1

## 任务身份

| Field | Value |
| --- | --- |
| Task ID | `2026-06-05-docs-site-enterprise-documentation-program-1fdb4d8b` |
| Budget | `complex` |
| Preset | `standard-task` |
| Module | `n/a` |
| Long-running | `no` |
| Created | 2026-06-05 |

## Task Audit Metadata

| Field | Value |
| --- | --- |
| Created By | harness new-task |
| Created At | 2026-06-05 |
| Command Shape | harness new-task --budget complex --locale zh-CN --title 'docs site enterprise documentation program' --preset standard-task '<target>' |
| Budget | complex |
| Template Source | templates-zh-CN/planning/INDEX.md |
| Task Creator | LnYo-Cly <lnyocly@gmail.com> |
| Task Creator Source | git-config |
| Human Review Status | confirmed |
| Confirmation ID | HRC-202606070653 |
| Confirmed At | 2026-06-07 06:53 |
| Reviewer | Human Reviewer |
| Reviewer Email | lnyocly@gmail.com |
| Confirm Text | 2026-06-05-docs-site-enterprise-documentation-program-1fdb4d8b |
| Evidence Checked | TARGET:coding-agent-harness/planning/tasks/2026-06-05-docs-site-enterprise-documentation-program-1fdb4d8b/review.md |
| Review Commit SHA | e159bdda8f090ffd01e6f04bb72a29015f1b18e7 |
| Audit Source | native-index |
| Audit Status | committed |
| Exception Reason | n/a |
| Message | 用户在对话中回复同意，确认 review packet 通过。 |
| Migration Status | native |
| Migrated From | n/a |
| Legacy Extra Fields | {} |
| Migration Notes | n/a |
## 核心合同文件

| 文件 | 用途 |
| --- | --- |
| `brief.md` | 面向人和下一轮 agent 的任务摘要与上下文入口。 |
| `task_plan.md` | 当前任务目标、范围、所选预算、验收标准和执行决策。 |
| `visual_map.md` | 阶段图、证据状态、下一步生命周期命令和支持性图表。 |
| `progress.md` | 执行日志、验证证据、决策和交接记录。 |
| `walkthrough.md` | 任务本地 closeout 摘要、验证、审查处置、残余风险和链接。 |

## 标准任务文件

standard 和 complex 任务包含以下文件。

| 文件 | 用途 |
| --- | --- |
| `execution_strategy.md` | 执行模式、owner、冲突控制和证据策略。 |
| `findings.md` | 发现、研究记录、已接受风险和未解决问题。 |
| `lesson_candidates.md` | closeout 前的任务本地 lesson candidate 决策。 |
| `review.md` | Agent Review Submission、对抗审查、findings、evidence 和 routing。 |

## 可选索引

| 索引 | 用途 |
| --- | --- |
| `references/INDEX.md` | 参考资料和 preset 提供的 required reads。 |
| `artifacts/INDEX.md` | 生成产物、证据包、截图、报告和命令输出。 |

## Preset 摘要

本节由系统渲染。Preset 不能新增自定义根级文件，也不能任意追加根 `INDEX.md` 内容。

| Field | Value |
| --- | --- |
| Preset | `standard-task` |
| Preset Version | `1` |
| Evidence Bundle | `coding-agent-harness/planning/tasks/2026-06-05-docs-site-enterprise-documentation-program-1fdb4d8b/artifacts/preset/2026-06-05T03-27-44-497Z` |
| Resource Indexes | `references/INDEX.md`; `artifacts/INDEX.md` |

## 更新规则

- 状态和决策写入 `progress.md`。
- 任务专属目标和验收标准写入 `task_plan.md`。
- 大段命令输出、截图、报告和生成文件放入 `artifacts/INDEX.md`。
- 源材料、外部链接和 preset required reads 放入 `references/INDEX.md`。
