# CLI TUI status context bar

Task Contract: harness-task/v1
Task Kind: module-task
Task Preset: module
Preset Version: 1
Evidence Bundle: coding-agent-harness/planning/modules/cli-host/tasks/2026-06-21-cli-tui-status-context-bar-e2d583b1/artifacts/preset/2026-06-20T16-33-37-904Z
Task Package Index: required

## 目标

让 `ai4j` JLine TUI 顶部具备可读的状态上下文栏，向用户展示当前 coding agent 的 provider/model、workspace/session、memory/compact、sandbox、permissions 和 pending approval 状态。

## 范围

- 做什么：改进 `TuiSessionView` header/context row；更新 TUI tests；更新 docs-site `coding-agent/cli-and-tui.md`。
- 不做什么：不改 provider runtime、不新增 slash command、不改 AgentSession public API；仅扩展 TUI 内部 `TuiRenderContext` 的非敏感 sandbox 摘要字段、不引入 Ink/Node renderer、不执行真实模型调用。
- 主要风险：状态栏过长会降低终端可读性；应采用 clip/chips，保持无 ANSI 模式可测试。

## 预算选择

选择预算：standard

选择理由：这是单模块 CLI/TUI UX 切片，涉及代码、测试和 docs-site，但不改跨模块 public API。

## 上下文包（Context Packet）

| ID | 类型 | 路径 | 为什么需要 | 使用者 |
| --- | --- | --- | --- | --- |
| C-001 | repo-guidance | TARGET:AGENTS.md | Java 8、Harness、CLI/TUI 任务边界 | coordinator / reviewer |
| C-002 | code | TARGET:ai4j-cli/src/main/java/io/github/lnyocly/ai4j/tui/TuiSessionView.java | TUI header/render 主实现 | coordinator / reviewer |
| C-003 | code | TARGET:ai4j-cli/src/main/java/io/github/lnyocly/ai4j/tui/TuiScreenModel.java | 可用 session/snapshot/checkpoint/render context 状态 | coordinator |
| C-004 | code | TARGET:ai4j-cli/src/test/java/io/github/lnyocly/ai4j/tui/TuiSessionViewTest.java | TUI rendering 回归入口 | coordinator / reviewer |
| C-005 | docs | TARGET:docs-site/docs/coding-agent/cli-and-tui.md | 用户可见 CLI/TUI 体验说明 | coordinator / reviewer |
| C-006 | design-note | ui-ux-pro-max design-system query | 暗色终端、JetBrains Mono、可见状态 chips、避免低对比和过载 | coordinator |
| C-007 | research | TARGET:docs-site/docs/agent/source-backed-research-digest.md | R0 公开资料说明 slash/status/context 是 coding agent 一等体验 | coordinator / reviewer |

## 步骤

1. 读取现有 TUI renderer、screen model、tests 和 CLI/TUI docs。
2. 设计状态栏：第一行保持 identity，第二行新增 `ctx` chips，包含 memory/compact/sandbox/permissions/approval。
3. 实现 `TuiSessionView` context row，优先使用已有 `CodingSessionSnapshot`、`CodingSessionCheckpoint`、descriptor、interaction approval snapshot，不新增 runtime API。
4. 更新 `TuiSessionViewTest` 覆盖无 ANSI 输出中 context row 的关键字段。
5. 更新 docs-site CLI/TUI 页面，解释状态栏含义和限制。
6. 运行 targeted CLI/TUI tests、docs build、diff check、token scan、Harness status。
7. 提交、task-review、PR。

## 验收标准

- [x] Header 第一行仍包含 AI4J、provider/protocol、model、workspace、session id。
- [x] Header 第二行显示 context chips，至少覆盖 memory、compact、sandbox、permissions 或 approval。
- [x] pending approval 时状态栏能突出 `approval=pending`。
- [x] 无 ANSI 模式输出可稳定测试，不依赖终端宽度或真实 provider。
- [x] `mvn -pl ai4j-cli -am "-Dtest=TuiSessionViewTest" -DskipTests=false -DfailIfNoTests=false test` 通过。
- [x] `npm --prefix docs-site run build` 通过。
- [x] `git diff --check`、token scan、Harness status 通过。

## 工作树（Worktree）

- 路径：`G:\My_Project\java\ai4j-sdk\.worktrees\feature\cli-tui-status-context-bar`
- 分支：`feature/cli-tui-status-context-bar`
- Worker owner：coordinator
- Worker handoff commit required：yes
- Coordinator integration branch：`dev`
- 未使用 worktree 的原因：不适用。

## 长程任务判定

- 是否属于长程任务：否。
- 若是，合同文件：不适用
- 连续执行权限：用户已授权整体 program 继续，但本切片独立收口。
- Stop Condition 摘要：需要新增 runtime/public API 或真实 provider 验证时停止并另开任务。

## 审查判定

- 是否需要对抗性审查：是，self review。
- 若是，报告文件：`review.md`
- Reviewer：self；PR/CI 后继续审查。
- No-finding 要求：无 P0/P1/P2 open finding。

## 关联

- 相关 Regression Gate：CLI/TUI targeted tests；docs build。
- 审查报告：`review.md`
- Generated Ledger：由 lifecycle CLI / `harness governance rebuild` 重建
- 前置任务：R0 source-backed research digest、CLI memory/compact、permissions、sandbox 基座已在 `dev`。

## 模块关联（启用模块并行时填写）

- Module：cli-host
- Step：CLI-TUI-STATUS-CONTEXT-BAR
- Module Plan：`coding-agent-harness/planning/modules/cli-host/module_plan.md`

## 协调者交接（Coordinator，启用模块并行时填写）

- Global sync owner：coordinator
- Global sync status：pending-coordinator-pass
- Registry update needed：由 Harness lifecycle 同步。
- Harness Ledger update needed：由 lifecycle CLI 同步。
- Closeout / Regression update needed：如新增固定 CLI/TUI gate，再同步 `docs/05-TEST-QA/`；本切片预计不新增固定 gate。

## Module Preset

This module task was created through the `module` preset.

| Field | Value |
| --- | --- |
| Module Key | cli-host |

## Module Context Entry Points

| Reference | Path | Why / When |
| --- | --- | --- |
| Module brief | coding-agent-harness/planning/modules/cli-host/brief.md | CLI host purpose and scope. |
| Module plan | coding-agent-harness/planning/modules/cli-host/module_plan.md | Active CLI host steps. |
