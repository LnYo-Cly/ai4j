# ai4j dynamic workflow host runtime

Task Contract: harness-task/v1
Task Package Index: required

## 目标

在 `ai4j-agent` 增加一个可选 host/runtime executor，让 dynamic workflow 插件返回的 `ai4j.dynamic_workflow.request` envelope 可以由 AI4J 宿主解析、执行、追踪和返回结果。

## 范围

- 做什么：新增 `ai4j-agent` dynamic workflow runtime、envelope parser、可注入 agent bridge、host tool wrapper、JUnit 回归；更新 docs-site 的 dynamic workflow 插件文档，说明插件独立 + host 执行层的关系。
- 不做什么：不修改独立插件仓库；不把插件并入 SDK reactor/BOM；不修改 `ai4j-extension-api`；不实现后台 run manager、resume journal、per-agent worktree isolation、模型 tier 路由或 CLI `/workflows` 管理。
- 主要风险：Java 8 默认 Nashorn 只支持 ES5，需要在 host runtime 里明确支持范围并做轻量 normalizer；host 执行不能绕过现有 tool/permission/subagent 策略；docs 不能暗示插件自身执行 JS。

## 预算选择

选择预算：standard

选择理由：本轮改动集中在 `ai4j-agent` + docs-site，范围清晰但涉及安全敏感的 host execution 和 extension envelope，因此需要标准 task、专用 worktree、回归和 walkthrough。

## 上下文包（Context Packet）

| ID | 类型 | 路径 | 为什么需要 | 使用者 |
| --- | --- | --- | --- | --- |
| C-001 | code | TARGET:ai4j-agent/src/main/java/io/github/lnyocly/ai4j/agent/codeact/NashornCodeExecutor.java | 复用/对齐已有 Java 8 JS 执行约束、timeout 和 tool bridge 风格。 | coordinator / reviewer |
| C-002 | code | TARGET:ai4j-agent/src/main/java/io/github/lnyocly/ai4j/agent/extension/ExtensionAgentToolExecutor.java | 理解 extension tool envelope 如何进入 agent host。 | coordinator / reviewer |
| C-003 | code | TARGET:ai4j-agent/src/main/java/io/github/lnyocly/ai4j/agent/subagent/SubAgentToolExecutor.java | 确保 dynamic workflow 的 `agent()` 不绕过现有 handoff/policy 思路。 | coordinator / reviewer |
| C-004 | docs | TARGET:docs-site/docs/core-sdk/extension/dynamic-workflow-plugin.md | 用户口径已经固定为独立插件 + host-mediated envelope，本轮只补 host runtime 章节。 | coordinator / reviewer |
| C-005 | external | TARGET:G:/My_Project/java/ai4j-plugin-dynamic-workflow/README.md | 独立插件 README 是 envelope contract 的外部来源。 | coordinator / reviewer |

## 步骤

1. 设计最小 runtime contract：`DynamicWorkflowRequest`、`DynamicWorkflowAgentBridge`、`DynamicWorkflowExecutionResult`、`DynamicWorkflowExecutor`。
2. 在 `ai4j-agent` 实现 Nashorn-backed host executor：解析 envelope，注入 `args`、`phase`、`log`、`agent`、`parallel`、`pipeline` primitives，输出 JSON trace。
3. 增加 host tool wrapper：委托现有 extension tool executor，识别 `execute_dynamic_workflow` envelope 后调用 runtime；非 workflow tool 原样透传。
4. 增加 JUnit 4 回归：envelope 解析、happy path、phase/log、agent bridge、parallel/pipeline、失败/timeout/非法输入、tool wrapper。
5. 更新 docs-site：说明为什么插件不执行脚本、host 如何接入 runtime、Nashorn/ES5 与现代脚本 normalizer 边界。
6. 运行 targeted Maven + docs-site 验证，更新 progress/review/walkthrough。

## 验收标准

- [x] `DynamicWorkflowRequestParser` 能从插件 envelope 的 `argumentsRaw` 解析 script/args/background/maxAgents/tokenBudget。
- [x] `NashornDynamicWorkflowExecutor` 能执行含 `export const meta` / `await agent(...)` 的最小脚本，并记录 phase/log/agent trace。
- [x] `parallel()` 和 `pipeline()` primitives 有确定性测试；parallel 至少保持 group 语义和结果顺序。
- [x] `DynamicWorkflowHostToolExecutor` 能把 extension tool pending envelope 转成 completed/failed execution result JSON。
- [x] 回归命令通过，docs-site 文档构建/类型检查通过或 residual 明确。

## 工作树（Worktree）

- 路径：`.worktrees/feature/dynamic-workflow-executor`
- 分支：`feature/dynamic-workflow-executor`
- Worker owner：coordinator
- Worker handoff commit required：no
- Coordinator integration branch：`feature/dynamic-workflow-executor`
- 未使用 worktree 的原因：已使用专用 worktree；根 checkout 保持 untouched，因为根工作区存在 unrelated `feat/per-node-latency` 改动。

## 长程任务判定

- 是否属于长程任务：是
- 若是，合同文件：`long-running-task-contract.md`
- 连续执行权限：已授权（用户要求继续直到完成与验证）
- Stop Condition 摘要：若需要修改 `ai4j-extension-api` 或引入非 Java 8 baseline 才能继续，必须暂停说明。

## 审查判定

- 是否需要对抗性审查：是（self-review）
- 若是，报告文件：`review.md`
- Reviewer：self
- No-finding 要求：`review.md` 无 open P0/P1，P2 residual 必须明确 owner/action/status。

## 关联

- 相关 Regression Gate：`docs/05-TEST-QA/Regression-SSoT.md` 中 Agent runtime / extension host 相关门禁；如新增固定 gate 则同步更新。
- 审查报告：`coding-agent-harness/planning/tasks/2026-07-06-ai4j-dynamic-workflow-host-runtime-ef15599f/review.md`
- Generated Ledger：由 lifecycle CLI / `harness governance rebuild` 重建
- 前置任务：`2026-07-06-ai4j-dynamic-workflow-plugin-d652ef2e`

## 模块关联（启用模块并行时填写）

- Module：[module key，例如 reader / graph / 不适用]
- Step：[step ID，例如 RDR-02 / 不适用]
- Module Plan：[link to module_plan.md / 不适用]

## 协调者交接（Coordinator，启用模块并行时填写）

- Global sync owner：coordinator / 不适用
- Global sync status：pending-coordinator-pass / synced / n/a
- Registry update needed：[module key, step, status, branch, updated / 不适用]
- Harness Ledger update needed：[task plan path, review path, closeout status / 不适用]
- Closeout / Regression update needed：[路径或 n/a]
