# Agent Runtime backlog reconciliation after runner merge

Task Contract: harness-task/v1
Task Kind: module-task
Task Preset: module
Preset Version: 1
Evidence Bundle: coding-agent-harness/planning/modules/agent-runtime/tasks/2026-06-20-agent-runtime-backlog-reconciliation-after-runne-d9f9832a/artifacts/preset/2026-06-20T06-42-28-077Z
Task Package Index: required

## 目标

在 PR #118 `Remote Agent Runner SPI` 合并到 `dev` 后，校准 `agent-runtime` 模块 backlog：把已经出现在 `dev` 的 P0/P1/P2/P5 基座标成“已合并/待人工确认/待 closeout”，并记录下一步真正值得进入实现的 Agent SDK 增强切片。

## 范围

- 做什么：核对当前 worktree、GitHub PR、关键代码路径、docs-site 页面和 Harness task 状态；更新本任务包与 `agent-runtime/module_plan.md`；给出下一步实施队列。
- 不做什么：不改 Java 生产代码；不新增 docs-site 用户文档；不创建真实 sandbox/runner provider；不把 Harness review 状态伪装成人工确认；不推送远程。
- 主要风险：`module_plan.md` 的历史状态可能落后于 `dev`；Harness task lifecycle 仍处于 review 队列，容易被误读成代码没合并；P3/P4 是 coding/cli 模块成果，不能全部算作 agent-runtime 继续实现项。

## 预算选择

选择预算：standard

选择理由：本任务是单模块 backlog reconciliation，改动集中在 Harness task package 和 module plan；不涉及代码实现，但需要足够的事实核查、review 和 closeout 材料。

## 上下文包（Context Packet）

| ID | 类型 | 路径 | 为什么需要 | 使用者 |
| --- | --- | --- | --- | --- |
| C-001 | repo-guidance | TARGET:AGENTS.md | 仓库 Harness 流程、模块边界和任务目录约束 | coordinator / reviewer |
| C-002 | module-plan | TARGET:coding-agent-harness/planning/modules/agent-runtime/module_plan.md | 本轮需要校准的 agent-runtime backlog | coordinator / reviewer |
| C-003 | regression-standard | TARGET:docs/05-TEST-QA/Regression-SSoT.md | 判断本轮是否新增固定回归面 | coordinator |
| C-004 | cadence | TARGET:docs/05-TEST-QA/Cadence-Ledger.md | 后续实现任务的回归触发依据 | coordinator |
| C-005 | docs-roadmap | TARGET:docs-site/docs/agent/sdk-roadmap.md | 已公开的 Agent SDK roadmap，避免 task 计划与用户文档漂移 | coordinator / reviewer |
| C-006 | runner-doc | TARGET:docs-site/docs/agent/remote-agent-runner-spi.md | PR #118 的用户文档证据 | coordinator / reviewer |
| C-007 | github-pr | URL:https://github.com/LnYo-Cly/ai4j/pull/118 | 证明 P5 Remote Agent Runner SPI 已合并到 `dev` | coordinator / reviewer |
| C-008 | code-evidence | TARGET:ai4j-agent/src/main/java/io/github/lnyocly/ai4j/agent/** | P0/P1/P2/P5 关键运行时路径事实 | coordinator |
| C-009 | cli-coding-evidence | TARGET:ai4j-cli/** and TARGET:ai4j-coding/** | P3/P4 已落在 coding/cli 模块，后续不要重复归入 agent-runtime 实现 | coordinator |

## 步骤

1. 核查 `dev` 当前事实：PR #118 合并状态、open PR 列表、关键代码路径、docs-site 页面。
2. 更新本任务 `brief/task_plan/execution_strategy/findings/visual_map/progress/review/walkthrough/lesson_candidates`，替换模板占位内容。
3. 更新 `coding-agent-harness/planning/modules/agent-runtime/module_plan.md`：把 P0-P5 状态改成当前事实，并记录下一步 true implementation slice。
4. 运行 `git diff --check` 与 `npx --yes coding-agent-harness status --json .`。
5. 通过后执行 `task-review`，把本任务提交到人工确认队列；本地提交，不推送远程。

## 验收标准

- [x] PR #118 状态、merge commit、open PR 列表已核查。
- [x] P0-A/P0-B/P0-C/P0-D/P1-A/P1-B/P1-C/P2-A/P2-B/P5 关键代码和 docs 路径已核查。
- [x] `module_plan.md` 已消除“PR pending / planning-only”的过期描述。
- [x] 下一步实现切片明确为 `Memory/Compact Session API polish`，后续 `Plugin contribution contract expansion` 次之。
- [x] 本任务 lesson routing 明确为 `checked-none`，不阻塞 closeout。
- [x] `git diff --check` 通过。
- [x] `npx --yes coding-agent-harness status --json .` 通过或仅剩已解释的人审/closeout状态。

## 工作树（Worktree）

- 路径：`G:\My_Project\java\ai4j-sdk\.worktrees\docs\agent-runtime-backlog-reconciliation`
- 分支：`docs/agent-runtime-backlog-reconciliation`
- Worker owner：不适用
- Worker handoff commit required：不适用
- Coordinator integration branch：`dev`
- 未使用额外 worktree 的原因：当前 worktree 已由 Harness 创建，用于 backlog 文档/规划 reconciliation；本轮不改生产代码。

## 长程任务判定

- 是否属于长程任务：否
- 若是，合同文件：不适用
- 连续执行权限：不适用
- Stop Condition 摘要：一旦开始实现下一步 `Memory/Compact Session API polish` 或改 public API，必须新建实现任务和 dedicated worktree。

## 审查判定

- 是否需要对抗性审查：是，做 self architecture/reconciliation review。
- 若是，报告文件：`review.md`
- Reviewer：self；如后续真正改 API，再使用 reviewer pass。
- No-finding 要求：不能存在会导致重复实现已合并切片、误判模块边界或把未确认任务当已 closeout 的 P0/P1/P2 open finding。

## 关联

- 相关 Regression Gate：本轮不新增固定 regression gate；后续实现任务按 `docs/05-TEST-QA/Regression-SSoT.md` 和 `docs/05-TEST-QA/Cadence-Ledger.md` 触发。
- 审查报告：`review.md`
- Generated Ledger：由 lifecycle CLI / `harness governance rebuild` 重建
- 前置任务：P0-A/P0-B/P0-C/P0-D/P1-A/P1-B/P1-C/P2-A/P2-B/P5 的实现任务与 PR #116/#118 合并事实。

## 模块关联（启用模块并行时填写）

- Module：agent-runtime
- Step：AGENT-RECONCILE-01
- Module Plan：`coding-agent-harness/planning/modules/agent-runtime/module_plan.md`

## 协调者交接（Coordinator，启用模块并行时填写）

- Global sync owner：coordinator
- Global sync status：pending-coordinator-pass
- Registry update needed：更新 agent-runtime module plan 状态表和 next slice。
- Harness Ledger update needed：task-review 后由 lifecycle CLI 派生。
- Closeout / Regression update needed：本轮不改 Regression SSoT；人工确认后再 closeout。

## Module Preset

This module task was created through the `module` preset.

| Field | Value |
| --- | --- |
| Module Key | agent-runtime |

## Module Context Entry Points

Read these module-level entry points before changing shared module behavior. Continue into narrower context only when the task surface requires it.

| Reference | Path | Why / When |
| --- | --- | --- |
| Module brief | coding-agent-harness/planning/modules/agent-runtime/brief.md | Start here for the module purpose and current scope. |
| Module plan | coding-agent-harness/planning/modules/agent-runtime/module_plan.md | Use this for module steps, active task links, and handoff state. |
| Module visual map | coding-agent-harness/planning/modules/agent-runtime/visual_map.md | Inspect when the change affects module sequencing or dependencies. |
