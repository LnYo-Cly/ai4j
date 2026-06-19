# P0-D Agent approval and permission policy

Task Contract: harness-task/v1
Task Kind: module-task
Task Preset: module
Preset Version: 1
Evidence Bundle: coding-agent-harness/planning/modules/agent-runtime/tasks/2026-06-20-p0-d-agent-approval-and-permission-policy-95b57bb5/artifacts/preset/2026-06-19T20-50-05-207Z
Task Package Index: required

## 目标

为 `ai4j-agent` 增加一个小而稳定的工具执行审批/权限策略层：在工具真正执行前，策略可以根据工具名、参数和执行环境元数据决定 allow、deny 或 require-approval，并把该边界记录为后续 Sandbox SPI、Blueprint 和 CLI/TUI approval UX 的共用基础。

## 范围

- 做什么：
  - 在 `ai4j-agent` 内新增 permission policy 基础类型，不新增 Maven 模块。
  - 通过 `ToolExecutor` wrapper 在执行边界拦截工具调用。
  - 在 `AgentBuilder` / `AgentContext` 中配置和保存 policy 与 execution environment。
  - 增加 deterministic tests 覆盖 allow / deny / require-approval / environment metadata / builder runtime path。
  - 增加 docs-site 页面，并在 roadmap/sidebar 中链接。
  - 更新 Regression SSoT 与 Cadence Ledger，记录新的 agent runtime policy 回归面。
- 不做什么：
  - 不实现真实 sandbox provider、VM、容器、microVM 或远端执行。
  - 不改 `ai4j-coding` 的文件、shell、git、browser 工具路由。
  - 不实现 CLI/TUI 交互式审批弹窗或 `/sandbox` 命令。
  - 不落地 YAML Blueprint approval 字段。
  - 不使用 live provider，不写入任何 provider token。
- 主要风险：
  - wrapper 顺序不当会绕过 extension/subagent/tool guardrail；本轮必须明确放在执行链外层。
  - runtime 对异常的呈现可能只显示 `TOOL_ERROR`；本轮接受这种最小行为，但需测试错误可观察。
  - `AgentTeamToolExecutor` 运行时再包装 executor 的路径可能需要后续单独补强；本轮先记录 residual，不扩大范围。

## 预算选择

选择预算：complex

选择理由：该任务虽然实现应保持小，但影响 agent runtime 执行边界、docs-site、回归治理和后续 Sandbox/CLI/Blueprint 路线，需要完整任务包、reference 设计记录、review 和 walkthrough。

## 上下文包（Context Packet）

| ID | 类型 | 路径 | 为什么需要 | 使用者 |
| --- | --- | --- | --- | --- |
| C-001 | repo-guidance | TARGET:AGENTS.md | monorepo、Java 8、Harness、回归和任务目录约束 | coordinator / reviewer |
| C-002 | module-context | TARGET:AGENT.md | agent runtime 当前架构与模块边界 | coordinator |
| C-003 | engineering-standard | TARGET:docs/11-REFERENCE/engineering-standard.md | Java / 模块边界 / public API 约束 | coordinator / reviewer |
| C-004 | testing-standard | TARGET:docs/11-REFERENCE/testing-standard.md | targeted regression 和证据深度选择 | coordinator / reviewer |
| C-005 | roadmap | TARGET:coding-agent-harness/planning/tasks/2026-06-20-ai4j-agent-sdk-architecture-enhancement-planning-b6a2e312/references/ai4j-agent-sdk-execution-roadmap-and-research-gates.md | P0-D 在整体 Agent SDK 路线中的位置 | coordinator |
| C-006 | tool-boundary-doc | TARGET:docs-site/docs/agent/tools-and-registry.md | 现有文档已把 `ToolExecutor` 定义为执行/权限边界 | coordinator |
| C-007 | code-surface | TARGET:ai4j-agent/src/main/java/io/github/lnyocly/ai4j/agent/tool/ToolExecutor.java | 最小拦截点 | coordinator |
| C-008 | code-surface | TARGET:ai4j-agent/src/main/java/io/github/lnyocly/ai4j/agent/AgentBuilder.java | builder wiring 入口 | coordinator |

## 步骤

1. 重新对齐现状：确认 main 与 P0-D worktree 的 dirty 差异，避免把实现散落在主工作区。
2. 固定 API 设计：`AgentPermissionRequest`、`AgentPermissionDecision`、`AgentPermissionPolicy`、`AgentExecutionEnvironment`、异常类型和便捷 policies。
3. 实现执行边界：用 `AgentPermissionToolExecutor` 包装 delegate，并确保 deny / require-approval 不执行 delegate。
4. 接入 Builder/Context：增加 `permissionPolicy(...)`、`executionEnvironment(...)`，默认环境 `LOCAL`，并在 context snapshot 中可见。
5. 写单测：直接 executor tests + builder runtime path test，覆盖 allow / deny / require-approval / remote sandbox metadata。
6. 写 docs-site：说明能力解决的问题、最小示例、API、限制、和 sandbox/CLI/Blueprint 的关系。
7. 更新治理：Regression SSoT / Cadence Ledger 增加或更新 agent runtime policy 回归记录；module_plan 记录 P0-D step。
8. 验证与审查：运行 targeted/broad tests、docs-site build、harness status、diff check，补 review 和 walkthrough 后提交 PR。

## 验收标准

- [ ] `ai4j-agent` 提供 Java 8 兼容的 permission policy 类型，不引入额外运行时依赖。
- [ ] deny / require-approval 决策在 delegate tool executor 前生效，并有测试证明 delegate 未执行。
- [ ] `AgentBuilder` 可配置 policy 和 execution environment，runtime 工具调用路径能触发策略。
- [ ] docs-site 有独立页面讲清 approval/permission policy，不把它描述成真实 sandbox。
- [ ] Regression SSoT / Cadence Ledger / module_plan / task package 都记录本轮新增回归面和证据。
- [ ] 本地命令通过：targeted permission tests、`mvn -pl ai4j-agent -am -DskipTests=false test`、`docs-site npm run build`、`npx --yes coding-agent-harness status --json .`、`git diff --check`。

## 工作树（Worktree）

- 路径：`G:/My_Project/java/ai4j-sdk/.worktrees/feature/agent-approval-permission-policy`
- 分支：`feature/agent-approval-permission-policy`
- Worker owner：coordinator
- Worker handoff commit required：yes
- Coordinator integration branch：`main`
- 未使用 worktree 的原因：不适用；本任务已创建独立 worktree。当前 main 工作区若存在同任务实现差异，必须先归并到该 worktree，再清理 main dirty 状态。

## 长程任务判定

- 是否属于长程任务：否
- 若是，合同文件：不适用
- 连续执行权限：不适用
- Stop Condition 摘要：一旦实现需要真实 sandbox、CLI 交互审批、`ai4j-coding` 路由或跨模块 API 破坏性调整，停止并拆分后续任务。

## 审查判定

- 是否需要对抗性审查：是，使用 self adversarial review + 后续 human confirmation。
- 若是，报告文件：`review.md`
- Reviewer：self / human
- No-finding 要求：review 必须挑战 wrapper 顺序、sandbox 语义误导、异常可观察性、Java 8 兼容、测试充分性。

## 关联

- 相关 Regression Gate：`RG-002 ai4j-agent runtime`；新增本轮 `SRB-050 P0-D Agent approval/permission policy`。
- 审查报告：`coding-agent-harness/planning/modules/agent-runtime/tasks/2026-06-20-p0-d-agent-approval-and-permission-policy-95b57bb5/review.md`
- Generated Ledger：由 lifecycle CLI / `harness governance rebuild` 重建
- 前置任务：P0-A AgentSession、P0-B Memory/Compact、P0-C Plugin Lifecycle Hooks；P0-D 只复用其运行时基础，不重复实现。

## 模块关联（启用模块并行时填写）

- Module：`agent-runtime`
- Step：`T-P0-D-AGENT-APPROVAL-PERMISSION-POLICY-95B57BB5`
- Module Plan：`coding-agent-harness/planning/modules/agent-runtime/module_plan.md`

## 协调者交接（Coordinator，启用模块并行时填写）

- Global sync owner：coordinator
- Global sync status：pending-coordinator-pass
- Registry update needed：`agent-runtime` step P0-D active -> review -> handoff
- Harness Ledger update needed：task plan path, review path, closeout status
- Closeout / Regression update needed：`docs/05-TEST-QA/Regression-SSoT.md`、`docs/05-TEST-QA/Cadence-Ledger.md`、task-local `walkthrough.md`

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
