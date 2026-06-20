# AI4J extension runtime adapter wave 3

Task Contract: harness-task/v1
Task Package Index: required

## 目标

把 `ai4j-extension-api` 的显式 enable/expose 插件资源接入 Agent 与 Coding Agent 运行时，让第三方插件包可以在不改主循环的情况下把已授权工具暴露给模型调用，并同步 docs-site 插件包生态说明。

## 范围

- 做什么：
  - 在 `ai4j-agent` 中增加 extension tool registry/executor adapter。
  - 在 `AgentBuilder` 增加 `.extensions(ExtensionRegistry)` 与 `.extensions(ExtensionAgentTools)` 入口。
  - 在 `ai4j-coding` 中复用同一 adapter，让 `CodingAgentBuilder` 可以把插件工具并入 coding session。
  - 补 agent/coding 运行时测试，覆盖 discover / enable / expose / execute 门禁。
  - 补 docs-site 插件包文档、侧边栏和 README 入口。
  - 更新 Feature SSoT、Regression SSoT、Cadence Ledger 与任务证据。
- 不做什么：
  - 不实现远程 marketplace。
  - 不实现 CLI 自动安装插件依赖。
  - 不实现运行时热加载 jar。
  - 不把 provider 注册改成自动插件化。
  - 不做 Spring Boot 配置化插件装配。
- 主要风险：
  - 插件工具如果绕过显式暴露，会扩大模型可调用面。
  - `ai4j-agent` 与 `ai4j-coding` 的 tool executor 路由需要保持模块边界清晰。
  - docs-site 不能把尚未实现的 marketplace / provider plugin 写成已有能力。

## 预算选择

选择预算：complex

选择理由：本任务跨 `ai4j-agent`、`ai4j-coding`、docs-site 与治理记录，并建立插件生态的运行时接入边界；需要代码、测试、文档和回归证据同步收口。

## 上下文包（Context Packet）

| ID | 类型 | 路径 | 为什么需要 | 使用者 |
| --- | --- | --- | --- | --- |
| C-001 | code | TARGET:ai4j-extension-api/src/main/java/io/github/lnyocly/ai4j/extension/ExtensionRegistry.java | 明确 discover / enable / expose / snapshot 的公共合同 | coordinator / reviewer |
| C-002 | code | TARGET:ai4j-agent/src/main/java/io/github/lnyocly/ai4j/agent/AgentBuilder.java | Agent runtime 接入点 | coordinator / reviewer |
| C-003 | code | TARGET:ai4j-coding/src/main/java/io/github/lnyocly/ai4j/coding/CodingAgentBuilder.java | Coding Agent runtime 接入点 | coordinator / reviewer |
| C-004 | docs | TARGET:docs-site/docs/core-sdk/extension/overview.md | 现有扩展面边界，避免把插件包和 provider extension 混淆 | coordinator / reviewer |
| C-005 | planning | TARGET:coding-agent-harness/planning/tasks/2026-06-08-ai4j-extension-ecosystem-architecture-ba92a10f/references/ai4j-extension-system-design.md | 前置插件生态规划结论 | coordinator / reviewer |

## 步骤

1. 读取 extension API、AgentBuilder、CodingAgentBuilder 和 docs-site extension 文档，确认真实边界。
2. 实现 `ExtensionAgentTools` adapter，把 exposed extension tools 映射到现有 `AgentToolRegistry` / `ToolExecutor`。
3. 在 Agent 与 Coding Agent builder 中增加 `.extensions(...)` 入口，并把插件工具并入既有路由。
4. 补测试覆盖 agent loop 和 coding session 中的插件工具调用。
5. 补 docs-site 插件包页、侧边栏和 README 文档入口。
6. 更新 Feature SSoT、Regression SSoT、Cadence Ledger、模块计划、review、walkthrough 和 progress。
7. 运行目标回归、docs-site 构建、diff 检查和 harness status。

## 验收标准

- [x] Agent 可以通过 `.extensions(registry)` 暴露并执行已 `exposeTool(...)` 的插件工具。
- [x] Coding Agent 可以通过 `.extensions(registry)` 在 coding session 内暴露并执行插件工具。
- [x] 未显式暴露的插件工具不会进入模型 tool 列表，也不能通过 extension adapter 执行。
- [x] docs-site 明确区分 plugin package、provider extension、HTTP SPI 和未实现的 marketplace / hotload 能力。
- [x] 本轮目标回归、docs-site 构建和治理记录完成。

## 工作树（Worktree）

- 路径：不适用
- 分支：`main`
- Worker owner：coordinator
- Worker handoff commit required：不适用
- Coordinator integration branch：`main`
- 未使用 worktree 的原因：用户要求继续完成并推送当前线性工作；本轮没有并行 worker，且当前分支已有 Wave 3 harness lifecycle 提交。

## 长程任务判定

- 是否属于长程任务：否
- 若是，合同文件：不适用
- 连续执行权限：已授权继续当前线性任务，但不启用 long-running-task 合同
- Stop Condition 摘要：出现需要新增远程安装、provider 自动注册、Spring Boot 配置化接入或外部系统文档时停止并另开任务。

## 审查判定

- 是否需要对抗性审查：是
- 若是，报告文件：`review.md`
- Reviewer：self + human confirmation pending
- No-finding 要求：无 P0/P1/P2 open material finding。

## 关联

- 相关 Regression Gate：RG-010、RG-002 targeted、RG-003 targeted、RG-004 targeted、RG-007、RG-008
- 审查报告：`review.md`
- Generated Ledger：由 lifecycle CLI / `harness governance rebuild` 重建
- 前置任务：
  - `coding-agent-harness/planning/tasks/2026-06-08-ai4j-extension-ecosystem-architecture-ba92a10f/task_plan.md`
  - `coding-agent-harness/planning/tasks/2026-06-08-ai4j-extension-cli-inspect-wave-2-35a94c8e/task_plan.md`

## 模块关联（启用模块并行时填写）

- Module：`agent-runtime`、`coding-runtime`、`docs-site`
- Step：AGENT-01、CODING-01、DOCS-01
- Module Plan：
  - `coding-agent-harness/planning/modules/agent-runtime/module_plan.md`
  - `coding-agent-harness/planning/modules/coding-runtime/module_plan.md`
  - `coding-agent-harness/planning/modules/docs-site/module_plan.md`

## 协调者交接（Coordinator，启用模块并行时填写）

- Global sync owner：coordinator
- Global sync status：synced
- Registry update needed：module plans updated for Wave 3 active task
- Harness Ledger update needed：task review / status commands after validation
- Closeout / Regression update needed：Regression SSoT、Cadence Ledger、walkthrough
