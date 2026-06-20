# docs site agent sdk real api completeness pass

Task Contract: harness-task/v1
Task Kind: module-task
Task Preset: module
Preset Version: 1
Evidence Bundle: coding-agent-harness/planning/modules/docs-site/tasks/2026-06-20-docs-site-agent-sdk-real-api-completeness-pass-d9906610/artifacts/preset/2026-06-20T12-09-57-828Z
Task Package Index: required

## 目标

把 docs-site 的 Agent SDK 文档补成可落地的真实 API 导航：用户能一眼看到每个特色能力是否已经实现、源码入口在哪里、下一篇文档读什么，并明确哪些只是 SPI/host-bound/规划项。

## 范围

- 做什么：新增 Agent SDK 真实 API 能力矩阵；接入 sidebar、overview、quickstart；修正 `AgentSession` 核心类参考中过期描述；记录文档忽略规则和验证证据。
- 不做什么：不改 Java 模块；不新增 fake fluent API；不写真实 provider token；不承诺官方云 runner/sandbox；不迁移整个 docs-site 信息架构。
- 主要风险：`docs-site/docs/**` 被 `.gitignore` 的 `docs/` 规则忽略，新增页面必须 `git add -f`；文档中若把 SPI 写成真实 provider，会误导用户。

## 预算选择

选择预算：complex

选择理由：本任务虽然是 docs-site 切片，但覆盖 Agent SDK 多个模块能力和真实 API 状态，需要对照 `ai4j-agent`、`ai4j-extension-api`、`ai4j-coding`、`ai4j-cli` 源码，并保留完整 Harness 证据，避免再次出现伪 API 文档。

## 上下文包（Context Packet）

| ID | 类型 | 路径 | 为什么需要 | 使用者 |
| --- | --- | --- | --- | --- |
| C-001 | repo-guidance | TARGET:AGENTS.md | monorepo 边界、Harness 流程、docs-site 验证要求 | coordinator / reviewer |
| C-002 | reference | TARGET:docs/11-REFERENCE/testing-standard.md | docs-site typecheck/build gate 和 live-provider secret 约束 | coordinator / reviewer |
| C-003 | module-plan | TARGET:coding-agent-harness/planning/modules/docs-site/module_plan.md | docs-site 模块范围和交接规则 | coordinator |
| C-004 | source | TARGET:ai4j-agent/src/main/java/io/github/lnyocly/ai4j/agent/** | Agent SDK 真实类、接口、SPI、session/compact/sandbox/runner 能力 | coordinator / reviewer |
| C-005 | source | TARGET:ai4j-extension-api/src/main/java/io/github/lnyocly/ai4j/extension/** | 插件 manifest、contribution、lifecycle、tool/skill/prompt/guardrail 合同 | coordinator / reviewer |
| C-006 | source | TARGET:ai4j-cli/src/main/java/io/github/lnyocly/ai4j/cli/SlashCommandController.java | CLI/TUI 真实 slash command、provider/model/memory/sandbox 命令入口 | coordinator / reviewer |
| C-007 | docs | TARGET:docs-site/docs/agent/** | 现有 Agent 文档入口、roadmap、session、blueprint、sandbox、runner 页面 | coordinator / reviewer |
| C-008 | diff | TARGET:docs-site/docs/agent/real-api-matrix.md | 本任务新增真实 API 能力矩阵 | coordinator / reviewer |

## 步骤

1. 从最新 `origin/dev` 创建 `docs/agent-sdk-real-api-docs` worktree。
2. 创建并启动 docs-site 模块 Harness task。
3. 扫描 docs-site 与真实源码，确认当前已实现类/命令和伪 API 风险。
4. 新增 `agent/real-api-matrix` 页面，按能力状态组织真实 API、源码入口和下一篇文档。
5. 从 sidebar、Agent overview、Agent quickstart 接入新页面。
6. 修正 `reference-core-classes.md` 中 `AgentSession` 的过期职责描述。
7. 运行 docs-site typecheck/build、`git diff --check`、Harness status。
8. 提交、推送并创建 PR 到 `dev`，等待 CI 绿后合并。

## 验收标准

- [x] 新页面只引用源码中存在的类、接口或 CLI 命令。
- [x] 明确区分可直接使用、SPI/合同已存在、Host/CLI 绑定中、规划中。
- [x] `AgentSession` 文档不再只写“切换 memory”，而覆盖 metadata/event log/snapshot/store/compact/sandbox binding。
- [x] `npm --prefix docs-site run typecheck` 通过。
- [x] `npm --prefix docs-site run build` 通过。
- [x] `npx --yes coding-agent-harness status --json .` 通过（failures=0；提交前 dirty warning expected）。
- [ ] PR 创建到 `dev` 并通过 CI/合并。

## 工作树（Worktree）

- 路径：`.worktrees/docs/agent-sdk-real-api-docs`
- 分支：`docs/agent-sdk-real-api-docs`
- Worker owner：coordinator
- Worker handoff commit required：不适用
- Coordinator integration branch：`dev`
- 未使用 worktree 的原因：不适用；已使用 dedicated worktree。

## 长程任务判定

- 是否属于长程任务：否，本轮是单个 docs-site 切片。
- 若是，合同文件：不适用
- 连续执行权限：用户已授权继续推进任务队列。
- Stop Condition 摘要：如果要新增/改变 Java API 或描述真实云 provider，必须停止并另开实现/设计任务。

## 审查判定

- 是否需要对抗性审查：是，进行 self adversarial review，重点挑战“是否写了不存在 API / 是否把 SPI 写成产品能力”。
- 若是，报告文件：`review.md`
- Reviewer：self；PR 可再接受 CI 和 human review。
- No-finding 要求：无 P0/P1/P2 open finding；若出现伪 API 或 secrets 风险必须修复后再提交。

## 关联

- 相关 Regression Gate：docs-site build/typecheck existing gate；本任务不新增固定 regression surface。
- 审查报告：`review.md`
- Generated Ledger：由 lifecycle CLI / `harness governance rebuild` 重建
- 前置任务：`MODULES/agent-runtime/2026-06-20-agent-runtime-backlog-reconciliation-after-runne-d9f9832a`、`MODULES/agent-runtime/2026-06-20-ai4j-agent-sdk-architecture-enhancement-roadmap-9effae81`

## 模块关联（启用模块并行时填写）

- Module：docs-site
- Step：DOCS-AGENT-REAL-API-01
- Module Plan：`coding-agent-harness/planning/modules/docs-site/module_plan.md`

## 协调者交接（Coordinator，启用模块并行时填写）

- Global sync owner：coordinator
- Global sync status：pending-coordinator-pass
- Registry update needed：docs-site module plan 已由 Harness CLI 添加任务；收口后随 PR 更新
- Harness Ledger update needed：task plan、review、closeout status 由 lifecycle CLI 维护
- Closeout / Regression update needed：规划不新增 Regression SSoT；验证证据写入本任务 progress/walkthrough

## Module Preset

This module task was created through the `module` preset.

| Field | Value |
| --- | --- |
| Module Key | docs-site |

## Module Context Entry Points

Read these module-level entry points before changing shared module behavior. Continue into narrower context only when the task surface requires it.

| Reference | Path | Why / When |
| --- | --- | --- |
| Module brief | coding-agent-harness/planning/modules/docs-site/brief.md | Start here for the module purpose and current scope. |
| Module plan | coding-agent-harness/planning/modules/docs-site/module_plan.md | Use this for module steps, active task links, and handoff state. |
| Module visual map | coding-agent-harness/planning/modules/docs-site/visual_map.md | Inspect when the change affects module sequencing or dependencies. |
