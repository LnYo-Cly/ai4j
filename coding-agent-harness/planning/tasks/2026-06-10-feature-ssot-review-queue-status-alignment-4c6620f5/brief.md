# Feature SSoT review queue status alignment

## Task ID

`2026-06-10-feature-ssot-review-queue-status-alignment-4c6620f5`

## 创建日期

2026-06-10

## 一句话结果

`docs/09-PLANNING/Feature-SSoT.md` 的 active rows 与 generated Harness Ledger 的 review queue 状态重新对齐，不再把已提交 agent review 的任务显示为实施中。

## 完成后能得到什么

完成后，下一轮 agent 和人工 reviewer 打开 `Feature-SSoT.md` 时，可以直接区分“仍在实施”和“已提交 agent review、等待人工确认”的任务。F-024 到 F-037 会与 `coding-agent-harness/governance/generated/Harness-Ledger.md` 的 review queue 投影一致，避免误判这些任务还需要继续实现或重复开工。该文件也会明确自己是人工可读 summary，generated ledger 才是 harness v2 的机器投影来源。

## 交付物

- 可见产物：Feature SSoT review 状态对齐；当前任务 review packet。
- 修改位置：`docs/09-PLANNING/Feature-SSoT.md`、当前 task package。
- 验证证据：F-024 到 F-037 stale `in_progress` 扫描、generated ledger review 状态扫描、`git diff --check`、`npx.cmd --yes coding-agent-harness status --json .`。

## 第一眼应该看什么

先看 `docs/09-PLANNING/Feature-SSoT.md` 的 Active Features 表和 Status Legend，再对照 `coding-agent-harness/governance/generated/Harness-Ledger.md` 中对应任务的 `review | review` 行。任务自身证据看 `progress.md`，审查口径看 `review.md`。

## 边界

- 范围内：Feature SSoT 的状态/说明修正；当前 harness task package 的 brief、plan、progress、review、walkthrough、lesson decision。
- 范围外：业务代码、docs-site 内容、回归 gate 定义、人工 review confirmation、关闭其他 review queue 任务。
- 停止条件：如果 generated Harness Ledger 不是 review 状态，或发现某个 F-024 到 F-037 任务确实仍在 implementation active queue，停止并按 ledger 状态逐项拆分处理。

## 完成判断

1. F-024 到 F-037 在 Feature SSoT 中不再显示 `🟡 in_progress`。
2. Feature SSoT Status Legend 包含 `🟣 review`。
3. Feature SSoT 页首说明 generated Harness Ledger 是 harness v2 机器投影来源。
4. Targeted scans、diff hygiene 和 harness status 通过。
5. 当前任务 package 已提交 Agent Review Submission，等待人工确认。

## 执行合同

- Owner：coordinator
- 生命周期状态：审查中
- 必需文件：`INDEX.md`、`task_plan.md`、`execution_strategy.md`、`visual_map.md`、
  `progress.md`、`findings.md`、`review.md`
- 完成条件：验证证据必须记录到 `progress.md`，并通过 human review confirmation 后关闭。

## 当前下一步

等待人工确认本任务 review packet；确认前不运行 `review-confirm`。
