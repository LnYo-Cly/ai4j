# 收口记录：Feature SSoT review queue status alignment

## 摘要

本任务修正了 Feature SSoT 的 review queue 表达：F-024 到 F-037 已经在 generated Harness Ledger 中进入 review queue，但 legacy summary 仍显示 `🟡 in_progress`。现在这些 rows 改为 `🟣 review`，并明确等待 human review confirmation；Feature SSoT 页首也说明它是人工可读 planning summary，generated Harness Ledger 是 harness v2 机器投影来源。

## 范围

| 范围 | 详情 |
| --- | --- |
| 变更模块 | governance docs / task package |
| 新增文件 | 无 |
| 删除文件 | 无 |
| 不在范围内 | Java/runtime/docs-site 业务代码、Regression SSoT、人工 review confirmation、关闭其他 review queue 任务 |

## 验证

| 检查 | 命令或过程 | 结果 | 证据 |
| --- | --- | --- | --- |
| stale active scan | `rg -n "\| F-0(2[4-9]|3[0-7]) \|.*in_progress" docs/09-PLANNING/Feature-SSoT.md` | pass；无匹配 | `progress.md` |
| review row scan | `rg -n "\| F-0(2[4-9]|3[0-7]) \|.*🟣 review" docs/09-PLANNING/Feature-SSoT.md` | pass；F-024 到 F-037 均命中 | `progress.md` |
| generated ledger review scan | `rg -n "HL-2026.*\| review \| review" coding-agent-harness/governance/generated/Harness-Ledger.md` | pass；generated ledger 保持 review queue 投影 | `progress.md` |
| diff hygiene | `git diff --check` | pass | `progress.md` |
| harness status | `npx.cmd --yes coding-agent-harness status --json .` | pass | `progress.md` |

## 审查结论

| 来源 | 重要发现 | 处理 | 证据 |
| --- | --- | --- | --- |
| self-review | 0 | ready for human confirmation | `review.md` |

## 残余风险

| 风险 | Owner | 是否接受 | 跟进 |
| --- | --- | --- | --- |
| 本任务只修正 legacy summary/projection，不替代 generated ledger，也不确认 review queue 中的其他任务 | coordinator | yes | 其他任务仍等待人工 review confirmation |

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
| Generated Harness Ledger | `coding-agent-harness/governance/generated/Harness-Ledger.md` |
