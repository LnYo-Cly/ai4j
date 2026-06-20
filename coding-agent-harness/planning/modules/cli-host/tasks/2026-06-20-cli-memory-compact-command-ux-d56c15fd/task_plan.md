# CLI memory compact command UX

Task Contract: harness-task/v1
Task Kind: module-task
Task Preset: module
Preset Version: 1
Evidence Bundle: coding-agent-harness/planning/modules/cli-host/tasks/2026-06-20-cli-memory-compact-command-ux-d56c15fd/artifacts/preset/2026-06-20T09-35-04-206Z
Task Package Index: required

## 目标

补齐 `ai4j-cli` 面向用户的一等 `/memory` 诊断命令，让用户能直接理解当前 Coding Agent 会话记忆、compact、checkpoint 与 auto-compact 状态，而不是只能从 `/status`、`/session`、`/compacts`、`/checkpoint` 中拼信息。

## 范围

- 做什么：新增或完善 `/memory` slash command；同步 CLI dispatch、TUI palette、JLine 补全、ACP command list 和渲染；更新 docs-site 命令参考与 compact/checkpoint 文档；补 deterministic tests。
- 不做什么：不重写 compact/checkpoint 算法；不改 provider 访问；不新增真实模型调用；不打印 raw memory 或工具输出全文；不扩大成完整 memory editor；不引入新的 Maven 模块。
- 主要风险：现有 `/compact`、`/compacts`、`/checkpoint` 已实现，任务如果不收窄容易重复造轮子；`/memory` 输出如果过度详细会泄露敏感上下文；如果强行读取缺失字段可能引入跨模块 API 膨胀。

## 预算选择

选择预算：complex

选择理由：任务虽然实现切片不大，但跨 CLI runtime、JLine slash completion、TUI palette、ACP command surface、docs-site 和回归治理；同时需要明确与已有 compact/checkpoint 的边界，避免重复实现或泄露会话内容。

## 上下文包（Context Packet）

| ID | 类型 | 路径 | 为什么需要 | 使用者 |
| --- | --- | --- | --- | --- |
| C-001 | repo-guidance | TARGET:AGENTS.md | 仓库模块边界、Harness 生命周期、Java 8 和任务位置约束 | coordinator / worker / reviewer |
| C-002 | reference | TARGET:docs/11-REFERENCE/engineering-standard.md | CLI/TUI 属于 `ai4j-cli`，compact/session runtime 不应被 CLI 反向污染 | coordinator / worker / reviewer |
| C-003 | reference | TARGET:docs/11-REFERENCE/testing-standard.md | CLI/TUI 与 docs-site 的最小回归入口 | coordinator / worker |
| C-004 | reference | TARGET:docs/11-REFERENCE/execution-workflow-standard.md | 非平凡改动必须记录 progress、review、walkthrough 和验证 | coordinator |
| C-005 | reference | TARGET:docs/11-REFERENCE/worktree-standard.md | 本实现应使用 dedicated worktree，不混入当前 roadmap 分支 | coordinator / worker |
| C-006 | module-plan | TARGET:coding-agent-harness/planning/modules/cli-host/module_plan.md | CLI host 模块当前范围、已有 extension/command 任务和验证入口 | coordinator / worker |
| C-007 | code | TARGET:ai4j-cli/src/main/java/io/github/lnyocly/ai4j/cli/SlashCommandController.java | `/memory` root command、补全、palette root 候选入口 | worker |
| C-008 | code | TARGET:ai4j-cli/src/main/java/io/github/lnyocly/ai4j/cli/runtime/CodingCliSessionRunner.java | CLI interactive dispatch、help、status/session 输出、TUI palette 和渲染 helper | worker |
| C-009 | code | TARGET:ai4j-cli/src/main/java/io/github/lnyocly/ai4j/cli/acp/AcpSlashCommandSupport.java | ACP 命令列表和 headless `/memory` 输出 | worker |
| C-010 | docs | TARGET:docs-site/docs/coding-agent/command-reference.md | 用户命令参考必须讲清 `/memory`、`/compact`、`/compacts`、`/checkpoint` 的分工 | worker / reviewer |
| C-011 | docs | TARGET:docs-site/docs/coding-agent/compact-and-checkpoint.md | compact/checkpoint 机制页需补充 `/memory` 的定位 | worker / reviewer |
| C-012 | task-reference | TARGET:coding-agent-harness/planning/modules/cli-host/tasks/2026-06-20-cli-memory-compact-command-ux-d56c15fd/references/cli-memory-compact-command-ux-plan.md | 本任务的完整执行方案和验收矩阵 | coordinator / worker / reviewer |

## 步骤

1. 基于最新 `origin/dev` 创建 worktree：`.worktrees/feature/cli-memory-compact-ux`，分支 `feature/cli-memory-compact-ux`。
2. 核实现有命令：确认 `/compact`、`/compacts`、`/checkpoint` 已存在，只新增 `/memory` 一等诊断入口。
3. 在 `SlashCommandController` 注册 `/memory`，加入 executable root commands；如需要支持 `/memory status`，只作为别名，不引入复杂子命令树。
4. 在 `CodingCliSessionRunner` 增加 `/memory` dispatch 与 `renderMemoryOutput(...)`：输出 memory item count、estimated tokens、mode、checkpoint goal、last compact mode/tokens、auto-compact failure/breaker 状态、process counts 和安全提示。
5. 在 `AcpSlashCommandSupport` 增加 `memory` command spec 和 headless renderer，保证 ACP 可获取同样的摘要。
6. 更新 help、TUI palette、`CodeCommand` 顶层帮助、`CodexStyleBlockFormatter` 信息块标题识别。
7. 更新 docs-site：命令参考、compact/checkpoint 机制页、必要时 session runtime 页。
8. 添加/更新 tests：`SlashCommandControllerTest`、`CodeCommandTest`、`AcpSlashCommandSupportTest`、必要时 `CodexStyleBlockFormatterTest`。
9. 运行 targeted tests、`mvn -pl ai4j-cli -am -DskipTests=false test`、`npm --prefix docs-site run build`、`git diff --check`、`npx --yes coding-agent-harness status --json .`。
10. 更新 task-local progress/review/walkthrough；如新增固定回归面，同步 `docs/05-TEST-QA/Regression-SSoT.md` 和 `docs/05-TEST-QA/Cadence-Ledger.md`。

## 验收标准

- [ ] `/memory` 在 CLI/TUI slash root、补全、palette、help 中可见。
- [ ] `/memory` 在 ACP `available_commands` 中可见，并能返回 deterministic memory summary。
- [ ] `/memory` 输出不包含 raw memory item、prompt 原文、API key、baseUrl credential、工具输出全文。
- [ ] `/memory` 与 `/status`、`/session`、`/compact`、`/compacts`、`/checkpoint` 的职责边界在 docs-site 中写清楚。
- [ ] 测试覆盖 root command、scripted command、ACP execution 和格式化；targeted/broad CLI tests 通过。
- [ ] docs-site build 通过。
- [ ] task-local review、walkthrough 和 progress 记录完整。

## 工作树（Worktree）

- 路径：`.worktrees/feature/cli-memory-compact-ux`
- 分支：`feature/cli-memory-compact-ux`
- Worker owner：coordinator 或授权 worker subagent
- Worker handoff commit required：yes，如使用 worker subagent；coordinator 自行实现时按普通 commit/PR 流程
- Coordinator integration branch：`dev`
- 未使用 worktree 的原因：不适用；实现必须使用 dedicated worktree。当前 checkout 只记录规划，不改生产代码。

## 长程任务判定

- 是否属于长程任务：否，本切片是一个中等规模 CLI UX 增强；但属于 Agent SDK program 的后续任务队列。
- 若是，合同文件：不适用
- 连续执行权限：用户已授权继续推进同类任务；实现前仍按本 task scope 执行。
- Stop Condition 摘要：一旦需要修改 `ai4j-coding` public runtime API 或新增跨模块模型字段，先暂停并更新计划。

## 审查判定

- 是否需要对抗性审查：是，至少 self adversarial review；如 diff 跨 `ai4j-coding` 或 regression SSoT，建议加 reviewer subagent。
- 若是，报告文件：`review.md`
- Reviewer：self；可追加 read-only reviewer subagent
- No-finding 要求：不得存在 P0/P1/P2 open finding，尤其是 raw memory 泄露、命令行为与文档不一致、CLI/ACP 命令面漂移。

## 关联

- 相关 Regression Gate：`docs/05-TEST-QA/Regression-SSoT.md` 中 CLI host / docs-site 相关 gate；如新增 `/memory` 固定命令面，后续实现同步新增或扩展条目。
- 审查报告：`review.md`
- Generated Ledger：由 lifecycle CLI / `harness governance rebuild` 重建
- 前置任务：`MODULES/agent-runtime/2026-06-20-ai4j-agent-sdk-architecture-enhancement-roadmap-9effae81`；P0-B compact 基座、CLI 现有 compact/checkpoint 命令已在 `origin/dev` 存在。

## 模块关联（启用模块并行时填写）

- Module：cli-host
- Step：CLI-MEMORY-01
- Module Plan：`coding-agent-harness/planning/modules/cli-host/module_plan.md`

## 协调者交接（Coordinator，启用模块并行时填写）

- Global sync owner：coordinator
- Global sync status：pending-implementation
- Registry update needed：`cli-host` module plan 已由 Harness CLI 添加本任务；实现开始后将状态更新为 implementation。
- Harness Ledger update needed：本 task package 已创建并 task-start；后续 review/closeout 由 lifecycle CLI 同步。
- Closeout / Regression update needed：实现完成后按是否新增固定 gate 决定是否同步 `docs/05-TEST-QA/**`。

## Module Preset

This module task was created through the `module` preset.

| Field | Value |
| --- | --- |
| Module Key | cli-host |

## Module Context Entry Points

Read these module-level entry points before changing shared module behavior. Continue into narrower context only when the task surface requires it.

| Reference | Path | Why / When |
| --- | --- | --- |
| Module brief | coding-agent-harness/planning/modules/cli-host/brief.md | Start here for the module purpose and current scope. |
| Module plan | coding-agent-harness/planning/modules/cli-host/module_plan.md | Use this for module steps, active task links, and handoff state. |
| Module visual map | coding-agent-harness/planning/modules/cli-host/visual_map.md | Inspect when the change affects module sequencing or dependencies. |
