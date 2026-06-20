# Memory Compact Session API polish

Task Contract: harness-task/v1
Task Kind: module-task
Task Preset: module
Preset Version: 1
Evidence Bundle: coding-agent-harness/planning/modules/agent-runtime/tasks/2026-06-20-memory-compact-session-api-polish-53845a17/artifacts/preset/2026-06-20T07-24-53-971Z
Task Package Index: required

## 目标

把现有 Memory/Compact/Context Projector 基座打磨成更好用的 `AgentSession` 级 API：用户可以用简单、可读、可诊断的 `SessionCompactPlan` 执行 compact，并获得 `SessionCompactReport` 说明保留/丢弃/摘要状态。

## 范围

- 做什么：新增 Java 8 兼容的 session compact plan/report 小型 API；保持 `compact(CompactPolicy)` 兼容；补测试；更新 docs-site Memory/Session 文档。
- 不做什么：不引入模型驱动摘要；不接真实 token/provider；不做 CLI `/compact`；不改 `ai4j-coding` checkpoint；不新增 Maven 模块。
- 主要风险：API 不能过度抽象；不能和现有 `CompactPolicy`、`ContextBudget`、`CompactResult` 重叠；docs 示例必须是真实 API。

## 预算选择

选择预算：complex

选择理由：本任务同时改 public API、session runtime、测试、docs-site 和 Harness 材料，虽然实现切片小，但需要完整 review 和回归证据。

## 上下文包（Context Packet）

| ID | 类型 | 路径 | 为什么需要 | 使用者 |
| --- | --- | --- | --- | --- |
| C-001 | repo-guidance | TARGET:AGENTS.md | Java 8、Harness、模块边界约束 | coordinator / reviewer |
| C-002 | module-plan | TARGET:coding-agent-harness/planning/modules/agent-runtime/module_plan.md | 本任务来自 next-planned slice | coordinator |
| C-003 | code | TARGET:ai4j-agent/src/main/java/io/github/lnyocly/ai4j/agent/AgentSession.java | 新 API 入口 | coordinator / reviewer |
| C-004 | code | TARGET:ai4j-agent/src/main/java/io/github/lnyocly/ai4j/agent/compact/** | 现有 CompactPolicy/Result | coordinator / reviewer |
| C-005 | code | TARGET:ai4j-agent/src/main/java/io/github/lnyocly/ai4j/agent/context/** | 现有 ContextBudget/Report | coordinator / reviewer |
| C-006 | tests | TARGET:ai4j-agent/src/test/java/io/github/lnyocly/agent/AgentMemoryCompactContextProjectorTest.java | 回归和新增测试位置 | coordinator |
| C-007 | docs | TARGET:docs-site/docs/agent/memory-compact-context.md | 用户文档必须更新真实 API | coordinator |
| C-008 | docs | TARGET:docs-site/docs/agent/session-runtime.md | Session runtime 文档需要说明新 API | coordinator |

## 设计选择

推荐方案：`SessionCompactPlan` + `SessionCompactReport`。

- `SessionCompactPlan` 是面向使用者的 fluent factory：`keepRecentItems(int)`、`withPinnedPrefixItems(int)`、`withMaxApproxChars(int)`、`withPolicy(CompactPolicy)`。
- `AgentSession.compact(SessionCompactPlan)` 直接执行并返回 `SessionCompactReport`。
- `SessionCompactReport` 聚合 `sessionId`、`summary`、`ContextReport`、`CompactResult`、`compacted`、`source/projected/dropped counts`，方便 UI/docs/CLI 后续复用。
- 原 `AgentSession.compact(CompactPolicy)` 保持返回 `AgentSession`，兼容老代码。

替代方案：只给 `CompactPolicy` 增加 helper factory。放弃原因：用户仍需要理解 policy/budget/result 三层，不能明显降低接入成本。

## 步骤

1. 新增 `io.github.lnyocly.ai4j.agent.compact.SessionCompactPlan` 与 `SessionCompactReport`。
2. 给 `AgentSession` 增加 `compact(SessionCompactPlan)` 和 `compactAndReport(CompactPolicy)`。
3. 补 `AgentMemoryCompactContextProjectorTest` 测试 plan/report、防御性复制和空 memory 行为。
4. 更新 `docs-site/docs/agent/memory-compact-context.md` 和 `docs-site/docs/agent/session-runtime.md`。
5. 运行 targeted tests、agent module tests、docs build、Harness status。
6. 提交、推送、PR。

## 验收标准

- [ ] 用户可以写 `session.compact(SessionCompactPlan.keepRecentItems(30).withPinnedPrefixItems(1))` 并得到 `SessionCompactReport`。
- [ ] 原有 `session.compact(CompactPolicy)` API 兼容。
- [ ] `SessionCompactReport` 能报告 session id、是否 compacted、summary、source/projected/dropped item counts 和 `ContextReport`。
- [ ] 新增/更新 JUnit4 测试覆盖 report 与 session snapshot/store 基本行为。
- [ ] docs-site 文档示例使用真实 API。
- [ ] targeted Maven、agent module test、docs build、Harness status 通过。

## 工作树（Worktree）

- 路径：`G:\My_Project\java\ai4j-sdk\.worktrees\feature\memory-compact-session-api-polish`
- 分支：`feature/memory-compact-session-api-polish`
- Worker owner：coordinator
- Worker handoff commit required：不适用
- Coordinator integration branch：`dev`
- 未使用 worktree 的原因：已使用 dedicated worktree。

## 长程任务判定

- 是否属于长程任务：否，属于 Agent SDK 增强队列中的一个实现切片。
- 若是，合同文件：不适用
- 连续执行权限：用户已授权继续执行队列，但本任务本身仍按标准 review/PR 收口。
- Stop Condition 摘要：如果需要真实 provider token、模型调用或改 CLI/TUI runtime，停止并另开任务。

## 审查判定

- 是否需要对抗性审查：是，self architecture/regression review。
- 若是，报告文件：`review.md`
- Reviewer：self；PR 后使用 CI/人工 review。
- No-finding 要求：不能有 public API 破坏、Java 8 破坏、docs 假 API 或未验证回归。

## 关联

- 相关 Regression Gate：`docs/05-TEST-QA/Regression-SSoT.md` agent runtime / docs-site。
- 审查报告：`review.md`
- Generated Ledger：由 lifecycle CLI / `harness governance rebuild` 重建
- 前置任务：P0-A AgentSession、P0-B Memory/Compact、P2-B session sandbox binding、P5 runner SPI。

## 模块关联（启用模块并行时填写）

- Module：agent-runtime
- Step：T-NEXT-MEMORY-COMPACT-SESSION-API-POLISH
- Module Plan：`coding-agent-harness/planning/modules/agent-runtime/module_plan.md`

## 协调者交接（Coordinator，启用模块并行时填写）

- Global sync owner：coordinator
- Global sync status：pending-coordinator-pass
- Registry update needed：完成后更新 module plan 状态和 PR 链接。
- Harness Ledger update needed：task-review/task-complete 后自动同步。
- Closeout / Regression update needed：如果新增固定回归面，同步 `docs/05-TEST-QA/`。

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
