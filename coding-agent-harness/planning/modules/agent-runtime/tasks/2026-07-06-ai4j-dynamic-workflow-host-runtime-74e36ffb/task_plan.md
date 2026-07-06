# AI4J dynamic workflow host runtime

Task Contract: harness-task/v1
Task Kind: module-task
Task Preset: module
Preset Version: 1
Evidence Bundle: coding-agent-harness/planning/modules/agent-runtime/tasks/2026-07-06-ai4j-dynamic-workflow-host-runtime-74e36ffb/artifacts/preset/2026-07-06T05-34-24-908Z
Task Package Index: required

## 目标

让 AI4J 在 host 侧接住 `ai4j.dynamic_workflow.request`，并把它编译/路由到可执行的 AI4J workflow 执行路径；插件仓库继续保持第三方视角，只产出 envelope，不承担执行责任。

## 范围

- 做什么：
  - 在 `ai4j-agent` 中实现 dynamic workflow 的 host runtime / dispatcher / compiler
  - 复用现有 `AgentWorkflow`、`SequentialWorkflow`、`StateGraphWorkflow`、`WorkflowContext`、sandbox 路由能力
  - 为 envelope 解析、workflow 组装和最小端到端路径补测试
  - 根据实际需要补最小 docs-site 说明，解释 host / plugin 边界
- 不做什么：
  - 不改 `ai4j-plugin-dynamic-workflow` 仓库
  - 不先把能力扩张到 `ai4j-cli`、FlowGram 或 core SDK
  - 不默认引入新的 JS 引擎或通用脚本沙箱，除非当前 envelope 无法用 Java-native workflow 表达
- 主要风险：
  - envelope 的 script / globals 语义和现有 Java workflow primitives 可能存在落差
  - 如果先走 JS runtime，容易把范围扩成 Nashorn/ES5/现代 JS 兼容层，而不是一个清晰的 host 编排层
  - `ai4j-extension-api` 是否足够承载该 envelope 需要先被验证

## 预算选择

选择预算：complex

选择理由：这是一个跨 runtime / workflow / sandbox / docs 的 host 侧能力补齐，且很可能需要先定 contract 再实现，单元测试外还要留出最小 smoke 和回归同步。

## 上下文包（Context Packet）

| ID | 类型 | 路径 | 为什么需要 | 使用者 |
| --- | --- | --- | --- | --- |
| C-001 | EXTERNAL | EXTERNAL:G:\My_Project\java\ai4j-plugin-dynamic-workflow\README.md | 定义插件对 host 的边界、envelope 语义和 workflow globals 预期 | coordinator / reviewer |
| C-002 | EXTERNAL | EXTERNAL:G:\My_Project\java\ai4j-plugin-dynamic-workflow\src\main\java\io\github\lnyocly\ai4j\plugin\dynamicworkflow\DynamicWorkflowExtension.java | 读取 tool / command / skill / prompt 的 contract 形状 | coordinator / reviewer |
| C-003 | EXTERNAL | EXTERNAL:G:\My_Project\java\ai4j-plugin-dynamic-workflow\src\main\java\io\github\lnyocly\ai4j\plugin\dynamicworkflow\DynamicWorkflowPayloads.java | 读取 request envelope、workflowSpecVersion、truncate 规则 | coordinator / reviewer |
| C-004 | TARGET | TARGET:ai4j-agent/src/main/java/io/github/lnyocly/ai4j/agent/workflow/AgentWorkflow.java | 当前 workflow 抽象的最小入口 | worker |
| C-005 | TARGET | TARGET:ai4j-agent/src/main/java/io/github/lnyocly/ai4j/agent/workflow/SequentialWorkflow.java | 线性编排复用点 | worker |
| C-006 | TARGET | TARGET:ai4j-agent/src/main/java/io/github/lnyocly/ai4j/agent/workflow/StateGraphWorkflow.java | 图编排复用点 | worker |
| C-007 | TARGET | TARGET:ai4j-agent/src/main/java/io/github/lnyocly/ai4j/agent/workflow/WorkflowContext.java | workflow globals / state bag 复用点 | worker |
| C-008 | TARGET | TARGET:ai4j-agent/src/main/java/io/github/lnyocly/ai4j/agent/runtime/BaseAgentRuntime.java | tool routing / sandbox 入口 | worker |
| C-009 | TARGET | TARGET:ai4j-agent/src/main/java/io/github/lnyocly/ai4j/agent/codeact/CodeActRuntime.java | 仅作为 JS runtime 兼容性参照，不默认复用 | coordinator |
| C-010 | TARGET | TARGET:coding-agent-harness/planning/modules/agent-runtime/module_plan.md | 模块步骤、依赖和 shared sync 入口 | coordinator |

## 步骤

1. 先做现状审计：确认 host 侧哪些能力已经足够、哪些只是看起来能用但语义不匹配，尤其是 workflow、sandbox、session 和 codeact 的边界。
2. 产出一个最小 host contract：把 `ai4j.dynamic_workflow.request` 映射成 AI4J 内部可执行的 workflow / dispatch 模型，优先 Java-native；只有在 envelope 语义证明无法表达时，再考虑受控脚本执行层。
3. 实现 runtime 和测试：补 host 侧 dispatcher / compiler / parser（如果需要），写 JUnit 覆盖 envelope 解析、workflow 组装、最小执行路径和拒绝路径。
4. 补文档和回归：如果改动影响固定回归面，更新 docs-site、`Regression-SSoT.md`、`Cadence-Ledger.md`，并在任务收口前完成 review / walkthrough。

## 验收标准

- [ ] host 侧可以接收 `ai4j.dynamic_workflow.request`，并输出明确的执行路径或拒绝理由
- [ ] 实现优先复用现有 workflow / sandbox / session 能力，没有把执行责任推回插件仓库
- [ ] 测试覆盖 envelope 解析、workflow 编排和至少一条最小端到端路径
- [ ] docs-site 或模块文档说明了 host/plugin 边界和使用方式
- [ ] 若触及固定回归面，`Regression-SSoT.md` 与 `Cadence-Ledger.md` 已更新

## 工作树（Worktree）

- 路径：`.worktrees/feature/dynamic-workflow-host-runtime`
- 分支：`feature/dynamic-workflow-host-runtime`
- Worker owner：coordinator
- Worker handoff commit required：yes
- Coordinator integration branch：`feature/dynamic-workflow-host-runtime`
- 未使用 worktree 的原因：不适用

## 长程任务判定

- 是否属于长程任务：否
- 若是，合同文件：`long-running-task-contract.md`
- 连续执行权限：不适用
- Stop Condition 摘要：如果需要把方案扩大到 plugin 仓库、core SDK 或通用 JS 引擎层，就先停下来重新定界

## 审查判定

- 是否需要对抗性审查：是
- 若是，报告文件：`review.md`
- Reviewer：self + reviewer subagent
- No-finding 要求：reviewer 对 contract 边界、JS-runtime 泄漏和回归同步没有 P0/P1 发现

## 关联

- 相关 Regression Gate：`Regression-SSoT.md` 中 agent runtime / workflow / sandbox 相关门禁
- 审查报告：`review.md`
- Generated Ledger：由 lifecycle CLI / `harness governance rebuild` 重建
- 前置任务：`无`

## 模块关联（启用模块并行时填写）

- Module：agent-runtime
- Step：T-AI4J-DYNAMIC-WORKFLOW-HOST-RUNTIME-74E36FFB
- Module Plan：`coding-agent-harness/planning/modules/agent-runtime/module_plan.md`

## 协调者交接（Coordinator，启用模块并行时填写）

- Global sync owner：coordinator
- Global sync status：pending-coordinator-pass
- Registry update needed：agent-runtime / T-AI4J-DYNAMIC-WORKFLOW-HOST-RUNTIME-74E36FFB / reserved / feature/dynamic-workflow-host-runtime / 2026-07-06
- Harness Ledger update needed：`coding-agent-harness/planning/modules/agent-runtime/tasks/2026-07-06-ai4j-dynamic-workflow-host-runtime-74e36ffb/task_plan.md`, `review.md`, closeout pending
- Closeout / Regression update needed：n/a

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

