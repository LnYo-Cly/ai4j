# [Wave / 功能名称] 收口记录

## 概要

[用一两句话说明本轮交付完成了什么、影响哪些用户或系统能力。]

## 改动范围

- 代码范围：[包、模块、目录或 PR]
- 文档范围：[任务计划、SSoT、reference、walkthrough、lesson 等]
- 运行时范围：[服务、页面、接口、脚本、配置；不适用则写 `n/a`]
- 明确未覆盖：[本轮刻意没有处理的范围]

## 关键决策

| 决策 | 选择 | 原因 | 证据 / 链接 |
| --- | --- | --- | --- |
| [决策点] | [采用的方案] | [为什么这样做] | [任务计划、review、issue、commit 等] |

## 验证结果

| 验证项 | 命令 / 步骤 | 结果 | 证据 |
| --- | --- | --- | --- |
| [测试或检查名称] | `[命令或步骤]` | pass / fail / inconclusive / skipped-with-reason | [输出摘要、报告路径或截图路径] |

- 回归依据：[Regression SSoT Gate、Cadence Ledger 批次或 `n/a: [原因]`]
- 证据深度：[L1-tests / L2-local-smoke / L3-live / L4-browser-human-proxy / L5-hard-gate]
- 未能验证的内容：[无则写 `none`；否则写原因和补验负责人]

## 审查结果

- 审查报告：[review.md、PR review、人工确认或 `n/a: [原因]`]
- 关键审查发现：[无 / 已修复 / 已接受残余；列出 ID]
- 未关闭审查项：[无则写 `none`；否则写负责人和路由]

## 残余

| 残余 | 严重级别 | 负责人 | 路由 | 期限 / 复查条件 |
| --- | --- | --- | --- | --- |
| `none` | n/a | n/a | n/a | n/a |

## 经验沉淀反思（Lessons）

| 检查问题 | 结论 |
| --- | --- |
| 本轮是否暴露 reference、workflow、模板或 checker 的缺口？ | [有 / 无，写一句理由] |
| 是否出现跨模块、跨阶段、跨 agent 的重复问题？ | [有 / 无，写一句理由] |
| 下次 agent 是否可能在同类任务中重复踩坑？ | [有 / 无，写一句理由] |
| Lessons 结果 | `checked-candidate: LC-...` / `queued-promotion: LC-...` / `checked-created: L-YYYY-MM-DD-NNN` / 旧任务 `checked-none: [一句话原因]` |
| Lessons 详情文档 | `lesson_candidates.md` / `coding-agent-harness/governance/lessons/[file].md` / `none` |

## 关联索引

- 任务计划：`coding-agent-harness/planning/tasks/[task]/task_plan.md`
- 进度记录：`coding-agent-harness/planning/tasks/[task]/progress.md`
- 审查报告：`coding-agent-harness/planning/tasks/[task]/review.md` / `n/a`
- 功能 SSoT：[F-...]
- Delivery SSoT：[DB-...] / `n/a`
- 回归 Gate：[RG-...] / `n/a`
- Harness Ledger：[HL-...]
- 任务本地收口：`walkthrough.md`
- Commit / PR：[hash、branch 或 PR 链接]
