# 收口记录：Feature SSoT closeout drift cleanup

## 摘要

本任务修正了 2026-06-07 review closeout batch 后的治理漂移：F-022/F-023 已经在 harness 中关闭，但 `Feature-SSoT.md` 仍显示为 active。现在它们已移动到 Completed Features，F-023 指向已存在的 task-local walkthrough，并且 repo 规则明确新任务 planning/progress/review/walkthrough 默认归入 `coding-agent-harness/planning/tasks/`。

## 范围

| 范围 | 详情 |
| --- | --- |
| 变更模块 | governance docs / task package |
| 新增文件 | 无 |
| 删除文件 | `docs/10-WALKTHROUGH/2026-06-07-lightweight-chatclient-first-chat-facade.md` |
| 不在范围内 | SDK 业务代码、docs-site 正文、回归 gate 定义、远程提交 |

## 验证

| 检查 | 命令或过程 | 结果 | 证据 |
| --- | --- | --- | --- |
| stale active scan | `rg -n "F-022|F-023|pending explicit human review|\| F-02[23] \|.*in_progress" docs\09-PLANNING\Feature-SSoT.md coding-agent-harness\planning\tasks -S` | pass；只剩 Completed Features 和 task-local walkthrough 引用 | `progress.md` |
| task-local walkthrough link | `Test-Path coding-agent-harness\planning\tasks\2026-06-07-chatclient-d5f84742\walkthrough.md` | pass；返回 true | `progress.md` |
| removed transitional docs/10 file | `Test-Path docs\10-WALKTHROUGH\2026-06-07-lightweight-chatclient-first-chat-facade.md` | pass；返回 false | `progress.md` |
| diff hygiene | `git diff --check` | pass；仅 Windows LF/CRLF 提示 | `progress.md` |
| harness status | `npx.cmd --yes coding-agent-harness status --json .` | 提交前仅 dirty-state warning；提交后复核 | `progress.md` |

## 审查结论

| 来源 | 重要发现 | 处理 | 证据 |
| --- | --- | --- | --- |
| self-review | 0 | ready for human confirmation | `review.md` |

## 残余风险

| 风险 | Owner | 是否接受 | 跟进 |
| --- | --- | --- | --- |
| 本任务不重新验证 F-022/F-023 的业务代码，只修正治理索引和 closeout 归档规则 | coordinator | yes | 原任务 progress/review/walkthrough 保留业务验证证据 |

## 经验沉淀反思

| 问题 | 答案 |
| --- | --- |
| 是否完成经验候选检查？ | yes，接受 no-candidate |
| 经验候选详情文件 | `lesson_candidates.md` |

## 收口链接

| 产物 | 链接 |
| --- | --- |
| 任务计划 | `task_plan.md` |
| 审查记录 | `review.md` |
| 进度记录 | `progress.md` |
| Feature SSoT | `docs/09-PLANNING/Feature-SSoT.md` |
| F-023 task-local walkthrough | `coding-agent-harness/planning/tasks/2026-06-07-chatclient-d5f84742/walkthrough.md` |

Closeout Status: closed
