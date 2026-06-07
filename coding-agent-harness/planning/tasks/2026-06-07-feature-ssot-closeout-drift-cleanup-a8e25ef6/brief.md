# Feature SSoT closeout drift cleanup

## Task ID

`2026-06-07-feature-ssot-closeout-drift-cleanup-a8e25ef6`

## 创建日期

2026-06-07

## 一句话结果

`Feature-SSoT.md` 与 2026-06-07 已关闭任务状态重新对齐，F-022/F-023 不再被后续 agent 误判为 active。

## 完成后能得到什么

完成后，下一轮 agent 打开 `docs/09-PLANNING/Feature-SSoT.md` 时会看到当前没有 active feature delivery，F-022/F-023 已在 Completed Features 中，并且能从表格直接跳到对应 walkthrough。F-023 缺失的仓库级 closeout 已补齐，避免只在 task-local walkthrough 中保存收口事实。

## 交付物

- 可见产物：Feature SSoT 状态修正；F-023 仓库级 walkthrough；本任务 review packet
- 修改位置：`docs/09-PLANNING/Feature-SSoT.md`、`docs/10-WALKTHROUGH/2026-06-07-lightweight-chatclient-first-chat-facade.md`、当前 task package
- 验证证据：targeted text scan、walkthrough `Test-Path`、`git diff --check`、`harness status --json .`

## 第一眼应该看什么

先读 `docs/09-PLANNING/Feature-SSoT.md` 的 Active/Completed Features 表，再读新增的 F-023 仓库级 walkthrough，最后查看 `progress.md` 中的 targeted verification 记录。

## 边界

- 范围内：Feature SSoT closeout 状态、F-023 仓库级 walkthrough、当前任务包材料。
- 范围外：SDK 业务代码、docs-site 正文、回归 gate 定义、远程推送。
- 停止条件：若发现 F-022/F-023 实际未关闭或验证证据缺失，停止并回到 review closeout 流程。

## 完成判断

1. F-022/F-023 不再位于 Active Features。
2. F-022/F-023 位于 Completed Features，并指向存在的 walkthrough。
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
