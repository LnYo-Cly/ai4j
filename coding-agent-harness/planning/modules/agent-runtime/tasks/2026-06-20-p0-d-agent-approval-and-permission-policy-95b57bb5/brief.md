# P0-D Agent approval and permission policy

## Task ID

`2026-06-20-p0-d-agent-approval-and-permission-policy-95b57bb5`

## 创建日期

2026-06-20

## 一句话结果

在 `ai4j-agent` 中建立最小可测试的工具执行审批/权限策略基础，让 Agent 在调用工具前可以被策略明确放行、拒绝或要求人工审批，并为后续 CLI 审批 UI 与真实 sandbox SPI 留出稳定接入点。

## 完成后能得到什么

完成后，开发者可以在构建 Agent 时配置 `AgentPermissionPolicy`，在 `ToolExecutor` 真正执行前得到一条包含工具名、参数和执行环境元数据的权限请求，并返回 allow / deny / require-approval 决策。该能力不冒充真实 VM/容器沙箱，只解决 P0 阶段最关键的 host-side permission gate：让工具执行边界先可治理、可测试、可文档化。后续 P1 Blueprint、P2 Sandbox SPI、P3 coding tools routing、P4 CLI `/sandbox` 和 approval UI 都可以复用这个策略层，而不是各自发明一套审批语义。

## 交付物

- 可见产物：`ai4j-agent` permission policy API、Builder wiring、确定性测试、docs-site 技术说明页、回归治理记录。
- 修改位置：`ai4j-agent/**`、`docs-site/docs/agent/**`、`docs-site/sidebars.ts`、`docs/05-TEST-QA/**`、本任务 Harness 包和 `agent-runtime/module_plan.md`。
- 验证证据：targeted permission policy test、`ai4j-agent` 模块回归、docs-site build、`harness status --json .`、`git diff --check`。

## 第一眼应该看什么

1. `task_plan.md`：本轮 P0-D 的范围、验收标准和不做事项。
2. `references/p0-d-agent-approval-permission-policy-plan.md`：策略层设计与后续 P1/P2/P4 的接续关系。
3. `visual_map.md`：阶段、证据和人工确认门禁。
4. `progress.md`：执行日志和最终命令证据。

## 边界

- 范围内：最小 permission policy contract；`ToolExecutor` 外层拦截；`AgentBuilder` / `AgentContext` 挂载；本地 deterministic tests；docs-site 页面；Regression SSoT / Cadence Ledger 记录。
- 范围外：真实 VM/容器/microVM sandbox provider；CLI/TUI 交互式审批弹窗；YAML Blueprint 字段落地；`ai4j-coding` 文件/命令工具 sandbox routing；远端 Agent Runner；live provider 测试；任何 provider token 写入。
- 停止条件：如果实现需要改变 `ToolExecutor` 基础签名、跨模块改 `ai4j-coding` / `ai4j-cli`、或需要真实 sandbox provider，必须暂停并拆出后续任务。

## 完成判断

- `AgentPermissionPolicy` 能对工具调用返回 allow / deny / require-approval，deny 和 require-approval 都不会执行 delegate。
- `AgentBuilder.permissionPolicy(...)` 与 `executionEnvironment(...)` 能将策略挂到 runtime 工具执行链，默认环境为 `LOCAL`。
- blocked tool call 在 runtime 中以可观察错误结果返回，不造成未捕获崩溃；测试能证明 delegate 未被执行。
- docs-site 明确说明这是 permission / approval gate，不是真实沙箱；并说明和后续 Sandbox SPI、CLI approval UI、Blueprint 的关系。
- 本任务的 targeted tests、模块回归、docs-site build、Harness status 和 diff check 证据都记录在 `progress.md`。

## 执行合同

- Owner：coordinator
- 生命周期状态：进行中
- 必需文件：`INDEX.md`、`task_plan.md`、`execution_strategy.md`、`visual_map.md`、`progress.md`、`findings.md`、`review.md`
- 完成条件：验证证据必须记录到 `progress.md`；standard/complex 任务必须进入 `task-review`，人工确认由 human review 单独完成。

## 当前下一步

先把已出现的 P0-D 实现差异归并到专用 worktree `G:/My_Project/java/ai4j-sdk/.worktrees/feature/agent-approval-permission-policy`，避免在 main 工作区混入未提交实现；随后运行 targeted test 并补齐 docs-site 与治理记录。
