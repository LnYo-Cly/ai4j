# P0-C Agent plugin lifecycle hooks

Task Contract: harness-task/v1
Task Kind: module-task
Task Preset: module
Preset Version: 1
Evidence Bundle: coding-agent-harness/planning/modules/agent-runtime/tasks/2026-06-20-p0-c-agent-plugin-lifecycle-hooks-10df8009/artifacts/preset/2026-06-19T19-40-20-680Z
Task Package Index: required

## 目标

让 AI4J 插件可以通过公共、可选、向后兼容的生命周期 Hook 观察 Agent 运行过程，为后续插件生态、Blueprint、Memory/Compact 策略和 SandboxProvider 打基础。

## 范围

- 做什么：
  - 在 `ai4j-extension-api` 增加 lifecycle hook contract，并通过现有 `ExtensionContext` / `ExtensionRuntimeState` / `ExtensionRuntimeSnapshot` 收集 Hook。
  - 在 `ai4j-agent` 增加 Hook dispatcher，并接入 `AgentBuilder` / `AgentContext` / `ExtensionAgentTools`。
  - 在 runtime 关键节点触发观察型 Hook：turn、model request/response、tool call/result、compact。
  - 为 Hook 顺序、异常策略、老插件兼容性补 deterministic tests。
  - 在 docs-site 增加插件生命周期文档，并更新 roadmap/sidebar。
- 不做什么：
  - 不实现 YAML Agent Blueprint。
  - 不实现 Sandbox SPI 或真实 sandbox provider。
  - 不实现远端 Agent Runner。
  - 不做 CLI/TUI 插件管理 UI。
  - 不把 Hook 设计成可随意改写 prompt、tool 参数或 model response 的拦截器；首版先做稳定观察点。
- 主要风险：
  - 公共 API 一旦发布很难改名，因此首版命名要小而稳定。
  - Hook 如果默认可修改运行数据，会引入不可控副作用；本任务先采用 observation-first。
  - Hook 异常如果直接抛出会让第三方插件破坏 Agent 主流程；默认策略应记录事件并继续。
  - `ai4j-extension-api` 与 `ai4j-agent` 是跨模块变更，需要同时覆盖两个模块的回归。

## 预算选择

选择预算：complex

选择理由：该任务跨 `ai4j-extension-api`、`ai4j-agent`、docs-site 和 Harness 任务包，涉及公共 API、runtime 编排、异常策略和跨模块回归；需要完整上下文、设计记录、验证证据和 review packet。

## 上下文包（Context Packet）

| ID | 类型 | 路径 | 为什么需要 | 使用者 |
| --- | --- | --- | --- | --- |
| C-001 | code | TARGET:ai4j-extension-api/src/main/java/io/github/lnyocly/ai4j/extension/Ai4jExtension.java | 当前第三方插件入口，决定是否用 default method 或 context registry 扩展 | coordinator / reviewer |
| C-002 | code | TARGET:ai4j-extension-api/src/main/java/io/github/lnyocly/ai4j/extension/ExtensionContext.java | 当前 Tool/Command/Skill/Prompt/Guardrail 注册入口，决定 lifecycle 是否作为同级 registry | coordinator / reviewer |
| C-003 | code | TARGET:ai4j-extension-api/src/main/java/io/github/lnyocly/ai4j/extension/runtime/ExtensionRuntimeState.java | 当前 extension runtime state 聚合点，决定 lifecycle hooks 如何进入 snapshot | coordinator / reviewer |
| C-004 | code | TARGET:ai4j-agent/src/main/java/io/github/lnyocly/ai4j/agent/extension/ExtensionAgentTools.java | extension-api 到 agent runtime 的桥接点 | coordinator / reviewer |
| C-005 | code | TARGET:ai4j-agent/src/main/java/io/github/lnyocly/ai4j/agent/AgentBuilder.java | 现有 `.extensions(...)` 接入点，决定 hook dispatcher 如何进入 AgentContext | coordinator / reviewer |
| C-006 | code | TARGET:ai4j-agent/src/main/java/io/github/lnyocly/ai4j/agent/runtime/BaseAgentRuntime.java | ReAct/Base runtime 的 model/tool/turn 触发点 | coordinator / reviewer |
| C-007 | code | TARGET:ai4j-agent/src/main/java/io/github/lnyocly/ai4j/agent/runtime/CodeActRuntime.java | CodeAct runtime 是否复用 shared lifecycle path 的验证点 | coordinator / reviewer |
| C-008 | report | TARGET:coding-agent-harness/planning/tasks/2026-06-20-ai4j-agent-sdk-architecture-enhancement-planning-b6a2e312/references/ai4j-agent-sdk-complete-planning-refresh.md | 上层 P0-P5 总规划，本任务是 P0-C 的实施切片 | coordinator / reviewer |
| C-009 | report | TARGET:coding-agent-harness/planning/modules/agent-runtime/tasks/2026-06-20-p0-c-agent-plugin-lifecycle-hooks-10df8009/references/p0-c-agent-plugin-lifecycle-hooks-plan.md | 本任务可执行设计和测试计划 | coordinator / reviewer / future worker |

## 步骤

1. 诊断现有 extension API 与 agent runtime 接入点，确认 Hook 的最小公共合同。
2. 在 `ai4j-extension-api` 新增 lifecycle package / registry / snapshot 支持，并保持老插件无需实现新方法。
3. 在 `ai4j-agent` 增加 lifecycle dispatcher，接入 `AgentBuilder` / `AgentContext` / `ExtensionAgentTools`。
4. 在 runtime 中触发最小事件集：`BEFORE_TURN`、`AFTER_TURN`、`BEFORE_MODEL_REQUEST`、`AFTER_MODEL_RESPONSE`、`BEFORE_TOOL_CALL`、`AFTER_TOOL_CALL`、`ON_COMPACT`。
5. 为老插件兼容、Hook 顺序、Hook 异常继续执行、Base/ReAct 与 CodeAct 覆盖补测试。
6. 更新 docs-site 插件生命周期页面、roadmap 和 sidebar。
7. 运行 targeted + cross-module + docs-site + Harness status 验证，提交 review packet。

## 验收标准

- [x] `ai4j-extension-api` 提供稳定的 optional lifecycle hook contract，且老 `Ai4jExtension` 实现不需要修改。
- [x] `ExtensionRegistry.snapshot()` 能返回启用插件贡献的 Hook，并保持显式资源 allowlist 语义不破坏现有 Tool/Command/Skill/Prompt/Guardrail。
- [x] `AgentBuilder.extensions(...)` 自动把 Hook 接入 `AgentContext`，未启用 extension 或无 Hook 时 runtime 行为不变。
- [x] Base/ReAct 与 CodeAct runtime 能触发最小 Hook 集，session compact 能触发 `ON_COMPACT` 或等价事件。
- [x] Hook 抛异常默认被记录为事件并继续执行；只有后续显式策略才允许 fail-fast。
- [x] 测试覆盖 extension-api 合同、agent runtime 触发顺序、异常策略和老插件兼容。
- [x] docs-site 增加 lifecycle hooks 页面并通过 `npm run build`。
- [x] `npx --yes coding-agent-harness status --json .` 无 failure。

## 工作树（Worktree）

- 路径：`.worktrees/feature/agent-plugin-lifecycle-hooks`
- 分支：`feature/agent-plugin-lifecycle-hooks`
- Worker owner：coordinator
- Worker handoff commit required：yes
- Coordinator integration branch：main
- 未使用 worktree 的原因：不适用；本任务已使用独立 worktree。

## 长程任务判定

- 是否属于长程任务：否
- 若是，合同文件：不适用
- 连续执行权限：不适用
- Stop Condition 摘要：如果实现需要可变 Hook 拦截语义、真实 sandbox provider、CLI 插件 UI 或新增远端 runner module，停止并另开任务。

## 审查判定

- 是否需要对抗性审查：否，首轮使用 self architecture/regression review；PR 阶段可再开只读 reviewer。
- 若是，报告文件：不适用
- Reviewer：self
- No-finding 要求：`review.md` 必须确认无 P0/P1/P2 open material finding，或明确阻塞/残余路由。

## 关联

- 相关 Regression Gate：RG-010 extension API；RG-002 agent runtime；RG-008 docs-site build；SRB-049 cadence row；具体命令见本任务证据计划。
- 审查报告：`review.md`
- Generated Ledger：由 lifecycle CLI / `harness governance rebuild` 重建
- 前置任务：P0-A `MODULES/agent-runtime/2026-06-20-p0-a-agentsession-runtime-container-389dbf12`；P0-B `MODULES/agent-runtime/2026-06-20-p0-b-memory-compact-context-projector-47effd57`

## 模块关联（启用模块并行时填写）

- Module：agent-runtime
- Step：T-P0-C-AGENT-PLUGIN-LIFECYCLE-HOOKS-10DF8009
- Module Plan：TARGET:coding-agent-harness/planning/modules/agent-runtime/module_plan.md

## 协调者交接（Coordinator，启用模块并行时填写）

- Global sync owner：coordinator
- Global sync status：pending-coordinator-pass
- Registry update needed：agent-runtime step P0-C active on `feature/agent-plugin-lifecycle-hooks`
- Harness Ledger update needed：task plan path, review path, closeout status 由 lifecycle CLI / status rebuild 投影
- Closeout / Regression update needed：已更新 `docs/05-TEST-QA/Regression-SSoT.md` 和 `docs/05-TEST-QA/Cadence-Ledger.md`，记录 extension lifecycle hook surface。

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
