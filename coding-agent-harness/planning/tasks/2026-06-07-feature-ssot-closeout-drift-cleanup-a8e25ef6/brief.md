# Feature SSoT closeout drift cleanup

## Task ID

`2026-06-07-feature-ssot-closeout-drift-cleanup-a8e25ef6`

## 创建日期

2026-06-07

## 一句话结果

`Feature-SSoT.md` 与 2026-06-07 已关闭任务状态重新对齐，并把新任务 closeout 口径改为 harness-first。

## 完成后能得到什么

完成后，下一轮 agent 会以 `coding-agent-harness/planning/tasks/` 作为新任务、progress、review 和 walkthrough 的默认 SSoT。`docs/09-PLANNING/Feature-SSoT.md` 只保留历史/摘要索引，F-023 指向已存在的 task-local walkthrough，不再新增 `docs/10-WALKTHROUGH/` closeout 文件。

## 交付物

- 可见产物：harness-first governance 规则修正；Feature SSoT F-023 链接修正；错误过渡 `docs/10` closeout 删除；本任务 review packet
- 修改位置：`AGENTS.md`、`coding-agent-harness/context/`、`docs/09-PLANNING/Feature-SSoT.md`、`docs/11-REFERENCE/`、当前 task package
- 验证证据：targeted text scan、task-local walkthrough `Test-Path`、`git diff --check`、`harness status --json .`

## 第一眼应该看什么

先读 `AGENTS.md` 的 Hard Rules / Execution Flow，再读 `coding-agent-harness/context/development/codebase-map.md` 和 `docs/09-PLANNING/Feature-SSoT.md` 的 F-023 链接，最后查看 `progress.md` 中的 targeted verification 记录。

## 边界

- 范围内：harness-first closeout 规则、Feature SSoT closeout 链接、当前任务包材料。
- 范围外：SDK 业务代码、docs-site 正文、回归 gate 定义、远程推送。
- 停止条件：若发现 F-022/F-023 实际未关闭或验证证据缺失，停止并回到 review closeout 流程。

## 完成判断

1. 新任务默认 SSoT 明确为 `coding-agent-harness/planning/tasks/`。
2. F-023 Completed Features 指向存在的 task-local walkthrough。
3. `git diff --check` 无 whitespace error。
4. `npx --yes coding-agent-harness status --json .` 在提交后通过。

## 执行合同

- Owner：coordinator
- 生命周期状态：审查中
- 必需文件：`INDEX.md`、`task_plan.md`、`execution_strategy.md`、`visual_map.md`、
  `progress.md`、`findings.md`、`review.md`
- 完成条件：验证证据必须记录到 `progress.md`，并通过 human review confirmation 后关闭。

## 当前下一步

等待人工确认本任务 review packet；确认后可通过 Dashboard workbench 关闭任务。
