# Agent SDK task decomposition and technical docs

Task Contract: harness-task/v1
Task Kind: module-task
Task Preset: module
Preset Version: 1
Evidence Bundle: coding-agent-harness/planning/modules/docs-site/tasks/2026-06-21-agent-sdk-task-decomposition-and-technical-docs-5ac6fa9e/artifacts/preset/2026-06-20T17-29-35-556Z
Task Package Index: required

## 目标

把已认可的 AI4J Agent SDK / Coding Agent CLI/TUI / Sandbox / Plugin / YAML Blueprint / docs-site 增强方向拆成可执行任务队列，并同步到 docs-site 技术文档入口，便于后续按 worktree + PR + CI + closeout 逐项推进。

## 范围

- 做什么：新增 task-local 任务拆解 reference；新增 docs-site `agent/sdk-task-decomposition.md`；更新 Agent sidebar、overview、roadmap 链接；补齐 task package 审查和验证材料。
- 不做什么：不实现 Java/CLI 代码；不修改 provider/sandbox runtime；不使用用户给出的 provider token；不把规划项写成已发布能力。
- 主要风险：`origin/dev` 已有很多任务处于 review/handoff 状态，若不区分代码状态和 Harness lifecycle，容易重复实现或误导后续 agent。

## 预算选择

选择预算：complex

选择理由：本任务跨 Agent SDK、Coding Agent、Plugin、Sandbox、Remote Runner、docs-site 和 Harness lifecycle，需要完整任务队列、依赖图、状态口径、验证命令和审查材料。

## 上下文包（Context Packet）

| ID | 类型 | 路径 | 为什么需要 | 使用者 |
| --- | --- | --- | --- | --- |
| C-001 | repo-guidance | TARGET:AGENTS.md | 仓库模块边界、Harness 流程和测试命令 | coordinator / reviewer |
| C-002 | roadmap | TARGET:docs-site/docs/agent/sdk-roadmap.md | 已有 Agent SDK roadmap，需要链接新任务拆解页 | coordinator / reviewer |
| C-003 | digest | TARGET:docs-site/docs/agent/source-backed-research-digest.md | R0 公开资料约束后续设计 | coordinator / worker |
| C-004 | matrix | TARGET:docs-site/docs/agent/real-api-matrix.md | 当前真实 API 能力基线 | coordinator / worker |
| C-005 | module-plan | TARGET:coding-agent-harness/planning/modules/agent-runtime/module_plan.md | Agent runtime 已有任务状态 | coordinator |
| C-006 | module-plan | TARGET:coding-agent-harness/planning/modules/cli-host/module_plan.md | CLI/TUI 已有任务状态 | coordinator |
| C-007 | module-plan | TARGET:coding-agent-harness/planning/modules/coding-runtime/module_plan.md | Coding sandbox routing 已有任务状态 | coordinator |
| C-008 | module-plan | TARGET:coding-agent-harness/planning/modules/docs-site/module_plan.md | docs-site 当前任务链 | coordinator |
| C-009 | task-reference | TARGET:coding-agent-harness/planning/modules/docs-site/tasks/2026-06-21-agent-sdk-task-decomposition-and-technical-docs-5ac6fa9e/references/agent-sdk-task-decomposition-2026-06-21.md | 本任务完整拆解正文 | coordinator / reviewer / worker |
| C-010 | docs-page | TARGET:docs-site/docs/agent/sdk-task-decomposition.md | 面向读者和后续开发者的技术文档入口 | user / worker |

## 步骤

1. 从最新 `origin/dev` 创建 dedicated worktree 和 docs 分支。
2. 使用 Harness CLI 创建并启动本任务包。
3. 检查已有 agent-runtime、cli-host、coding-runtime、docs-site module plan 与 Harness status，确认已有任务状态。
4. 写入 task-local `references/agent-sdk-task-decomposition-2026-06-21.md`，覆盖 T0-T10 执行队列、依赖关系、验证命令和禁止事项。
5. 新增 docs-site `agent/sdk-task-decomposition.md`，并从 sidebar、overview、roadmap 链接。
6. 补齐 brief、plan、strategy、findings、visual map、review、lesson_candidates、walkthrough。
7. 运行 docs build、diff check、token scan 和 Harness status。
8. 提交、推送、创建 PR，观察 CI；绿色后按仓库策略合并并清理 worktree。

## 验收标准

- [x] 使用 dedicated worktree：`.worktrees/docs/agent-sdk-task-decomposition`。
- [x] Harness task package 已创建并启动。
- [x] 任务拆解 reference 覆盖 T0-T10，包含模块、状态、输出、不做、验证、下一步。
- [x] docs-site 新增 `agent/sdk-task-decomposition.md`。
- [x] Agent sidebar、overview、roadmap 链接到新页面。
- [ ] `npm --prefix docs-site run build` 通过。
- [ ] `git diff --check` 通过。
- [ ] token fragment scan 无命中。
- [ ] `npx --yes coding-agent-harness status --json .` 通过。
- [ ] PR 创建并 CI 通过；能合并时完成 merge。

## 工作树（Worktree）

- 路径：`G:\My_Project\java\ai4j-sdk\.worktrees\docs\agent-sdk-task-decomposition`
- 分支：`docs/agent-sdk-task-decomposition`
- Worker owner：coordinator
- Worker handoff commit required：yes
- Coordinator integration branch：`dev`
- 未使用 worktree 的原因：不适用，已使用 dedicated worktree。

## 长程任务判定

- 是否属于长程任务：否，本任务是整体 program 的规划/文档切片。
- 若是，合同文件：不适用
- 连续执行权限：用户已授权继续推进整体 program；本切片仍按 Harness lifecycle 收口。
- Stop Condition 摘要：如果需要实现 Java/CLI 行为、使用真实 provider token 或接入真实 sandbox provider，必须转入单独实现任务。

## 审查判定

- 是否需要对抗性审查：是，self adversarial review。
- 若是，报告文件：`review.md`
- Reviewer：self；PR 后由 GitHub CI 继续检查。
- No-finding 要求：无 P0/P1/P2 open finding；source/status 不确定项必须写成 residual，不得写成已实现。

## 关联

- 相关 Regression Gate：docs-site build；本任务不新增 Java regression gate。
- 审查报告：`review.md`
- Generated Ledger：由 lifecycle CLI / `harness governance rebuild` 重建
- 前置任务：R0 source-backed research digest、Agent SDK roadmap、real API matrix。

## 模块关联（启用模块并行时填写）

- Module：docs-site
- Step：DOCS-AGENT-SDK-TASK-DECOMPOSITION
- Module Plan：`coding-agent-harness/planning/modules/docs-site/module_plan.md`

## 协调者交接（Coordinator，启用模块并行时填写）

- Global sync owner：coordinator
- Global sync status：pending-pr-ci
- Registry update needed：docs-site module plan 已由 Harness CLI 同步本任务；PR 后更新状态。
- Harness Ledger update needed：task lifecycle CLI 同步；closeout 后再确认。
- Closeout / Regression update needed：本任务不新增 fixed regression；docs build 证据记录在 progress/review。
