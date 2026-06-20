# Agent Runtime backlog reconciliation after runner merge

## Task ID

`2026-06-20-agent-runtime-backlog-reconciliation-after-runne-d9f9832a`

## 创建日期

2026-06-20

## 一句话结果

把 PR #118 合并后的 `agent-runtime` 任务队列、模块计划和下一步实现方向校准到当前 `dev` 事实，避免后续 agent 继续按过期的 P0/P1/P2 “PR pending” 状态推进。

## 完成后能得到什么

完成后，下一轮 agent 可以直接从 `agent-runtime/module_plan.md` 和本任务包判断：P0/P1/P2/P5 哪些代码与文档已经出现在 `dev`，哪些 Harness task 只是等待人工确认和 closeout，真正还需要继续做的实现切片是什么。这个结果用于后续排期、创建新实现任务、docs-site 补强和 review/closeout 清理，而不是继续重复实现已经合并的 SPI、Blueprint、Sandbox 或 Runner 基座。

## 交付物

- 可见产物：`agent-runtime` backlog reconciliation 规划记录、当前状态表、下一步任务队列。
- 修改位置：本任务目录、`coding-agent-harness/planning/modules/agent-runtime/module_plan.md`。
- 验证证据：代码/文档存在性检查、PR #118 合并证据、open PR 列表、`git diff --check`、`npx --yes coding-agent-harness status --json .`。

## 第一眼应该看什么

1. `task_plan.md`：本轮 reconciliation 的目标、范围和验收标准。
2. `findings.md`：PR #118、P0-P5 当前事实和 next slice 判断。
3. `coding-agent-harness/planning/modules/agent-runtime/module_plan.md`：更新后的模块 backlog 和下一步。
4. `review.md`：self adversarial review 和 residual 风险。

## 边界

- 范围内：只整理 Harness 规划材料、agent-runtime module plan、当前事实和后续队列。
- 范围外：不实现 Java 代码；不新增 docs-site 页面；不新增 Maven 模块；不替代人工 review-confirm；不推送远程。
- 停止条件：如果发现 `dev` 上缺少 P0-P5 关键代码、PR #118 未合并、或 Harness status 出现 blocker，必须先回到事实核查而不是继续写路线图。

## 完成判断

- [x] 当前 `dev`/本 worktree 能证明 P0-A/P0-B/P0-C/P0-D/P1-A/P1-B/P1-C/P2-A/P2-B/P5 关键代码和 docs 存在。
- [x] `agent-runtime/module_plan.md` 不再把这些切片描述成“PR pending / implementation-only”。
- [x] 下一步实现切片明确，不再重复已有基座。
- [x] 本任务材料无模板占位符，lesson routing 有明确结论。
- [x] `git diff --check` 和 Harness status 通过。

## 执行合同

- Owner：coordinator
- 生命周期状态：进行中
- 必需文件：`INDEX.md`、`task_plan.md`、`execution_strategy.md`、`visual_map.md`、`progress.md`、`findings.md`、`review.md`
- 完成条件：验证证据必须记录到 `progress.md`，并通过 `task-review` 进入人工确认队列。

## 当前下一步

运行静态检查与 Harness status；通过后执行 `task-review`，再提交本地规划记录。
