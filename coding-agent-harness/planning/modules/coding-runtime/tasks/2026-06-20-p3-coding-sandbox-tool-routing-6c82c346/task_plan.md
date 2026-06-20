# P3 Coding sandbox tool routing

Task Contract: harness-task/v1
Task Kind: module-task
Task Preset: module
Preset Version: 1
Evidence Bundle: coding-agent-harness/planning/modules/coding-runtime/tasks/2026-06-20-p3-coding-sandbox-tool-routing-6c82c346/artifacts/preset/2026-06-20T02-54-32-109Z
Task Package Index: required

## 目标

把 `ai4j-coding` 的第一个执行型工具接入 `ai4j-agent` Sandbox SPI：宿主通过 `CodingAgentBuilder.sandbox(SandboxSession)` 绑定 live sandbox 后，`bash action=exec` 不再直接落到宿主 shell，而是调用 `SandboxSession.execute(SandboxCommand)`；未绑定 sandbox 时现有本地行为保持不变。

## 范围

- 做什么：新增 `CodingSandboxRuntime` live sandbox handle、`SandboxShellCommandExecutor`、builder/session wiring、`ShellCommandResult` 执行位置字段、fake sandbox 单测、docs-site 技术说明、Regression SSoT / Cadence Ledger 记录。
- 不做什么：不接真实外部 sandbox provider；不远端化 read/write/patch/browser/git/project-run；不改 CLI `/sandbox`；不引入新 Maven 模块；不使用任何真实 provider token。
- 主要风险：sandbox routing 是安全敏感执行面，必须避免因为进入 sandbox 就绕过 approval；文档必须清楚首切片只覆盖 foreground `bash exec`。

## 预算选择

选择预算：complex

选择理由：本任务跨 `ai4j-coding` 执行器、`ai4j-agent` sandbox SPI 使用边界、docs-site 技术文档、Regression SSoT/Cadence Ledger 和 Harness 生命周期，且属于安全敏感执行路由面。

## 上下文包（Context Packet）

| ID | 类型 | 路径 | 为什么需要 | 使用者 |
| --- | --- | --- | --- | --- |
| C-001 | repo-guidance | TARGET:AGENTS.md | 仓库模块边界、Harness 任务位置和回归规则 | coordinator / reviewer |
| C-002 | reference | TARGET:docs/11-REFERENCE/engineering-standard.md | `ai4j-coding` owns workspace/code execution；安全执行面不能随意放宽 | coordinator / reviewer |
| C-003 | reference | TARGET:docs/11-REFERENCE/testing-standard.md | RG-003 / RG-008 验证入口 | coordinator |
| C-004 | source | TARGET:ai4j-agent/src/main/java/io/github/lnyocly/ai4j/agent/sandbox | Sandbox SPI 类型来源 | worker / reviewer |
| C-005 | source | TARGET:ai4j-agent/src/main/java/io/github/lnyocly/ai4j/agent/session/AgentSessionSandboxBinding.java | session 绑定非敏感 sandbox 摘要 | worker / reviewer |
| C-006 | source | TARGET:ai4j-coding/src/main/java/io/github/lnyocly/ai4j/coding/tool/BashToolExecutor.java | 本轮改动的内置 shell 工具执行入口 | worker / reviewer |
| C-007 | docs | TARGET:docs-site/docs/agent/sandbox-spi.md | P2 Sandbox SPI 文档与 P3 边界衔接 | docs reviewer |
| C-008 | docs | TARGET:docs-site/docs/coding-agent/tools-and-approvals.md | 说明 approval 与 sandbox routing 分层 | docs reviewer |

## 步骤

1. 创建 dedicated worktree 和 Harness module task。
2. 盘点 `ai4j-coding` tool assembly：`CodingAgentBuilder.createBuiltInToolExecutor(...)`、`BashToolExecutor`、`LocalShellCommandExecutor`、`CodingAgent.newSession(...)`。
3. 新增 sandbox-aware shell executor，保持 `ShellCommandExecutor` 抽象和本地 fallback。
4. 在 `CodingAgentBuilder` 暴露 `sandbox(SandboxSession)` / `sandboxRuntime(...)`，并把 live sandbox 注入 built-in bash exec。
5. 在 `CodingAgent.newSession(...)` 绑定 `AgentSessionSandboxBinding` 到 delegate `AgentSession`。
6. 新增 targeted tests：直接 BashToolExecutor routing、agent loop 里 bash exec routing、敏感 label 过滤。
7. 更新 docs-site `coding-agent/sandbox-routing`、`tools-and-approvals`、`agent/sdk-roadmap` 和 sidebar。
8. 更新 RG-003/RG-008 与 SRB-057。
9. 运行 targeted/broad Java 回归、docs build、diff/harness 检查。
10. 提交、推送、开 PR，等待 CI，合并并清理 worktree。

## 验收标准

- [x] `CodingAgentBuilder.sandbox(SandboxSession)` 可把 live sandbox 注入 coding agent。
- [x] 新建 `CodingSession` 时 delegate `AgentSession.getSandboxBinding()` 返回非敏感 binding。
- [x] `bash action=exec` 在有 sandbox 时返回 `executionEnvironment=sandbox`、`sandboxSessionId`、`sandboxProviderId`。
- [x] 无 sandbox 时仍走 `LocalShellCommandExecutor`，返回 `executionEnvironment=local`。
- [x] Targeted tests 覆盖 direct executor 和 full agent loop。
- [x] `mvn -pl ai4j-coding -am -DskipTests=false test` 通过。
- [x] `npm --prefix docs-site run build` 通过。
- [x] docs-site 不误称 read/write/patch/browser/git 已远端化。
- [x] Regression SSoT / Cadence Ledger 已记录本轮固定回归证据。

## 工作树（Worktree）

- 路径：`G:\My_Project\java\ai4j-sdk\.worktrees\feature\coding-sandbox-routing`
- 分支：`feature/coding-sandbox-routing`
- Worker owner：coordinator
- Worker handoff commit required：yes
- Coordinator integration branch：`main`
- 未使用 worktree 的原因：不适用；本任务已使用 dedicated worktree。

## 长程任务判定

- 是否属于长程任务：否，本任务是 P3 的单一实现切片。
- 若是，合同文件：不适用
- 连续执行权限：不适用
- Stop Condition 摘要：如需远端化文件/patch/browser/git 或新增真实 provider，必须另开后续任务。

## 审查判定

- 是否需要对抗性审查：是，执行路由和安全边界属于安全敏感面。
- 若是，报告文件：`review.md`
- Reviewer：self；PR/CI 后等待 human review confirmation
- No-finding 要求：不得存在会导致 host shell fallback 错误、approval 绕过、敏感配置落盘或文档夸大能力的 open P0/P1/P2 finding。

## 关联

- 相关 Regression Gate：RG-003、RG-008；共享 PR/merge 由 RG-007/CI 覆盖。
- 审查报告：`review.md`
- Generated Ledger：由 lifecycle CLI / `harness governance rebuild` 重建
- 前置任务：P2-A Sandbox SPI model；P2-B AgentSession sandbox binding

## 模块关联（启用模块并行时填写）

- Module：coding-runtime
- Step：CODING-P3-SANDBOX-ROUTING
- Module Plan：`coding-agent-harness/planning/modules/coding-runtime/module_plan.md`

## 协调者交接（Coordinator，启用模块并行时填写）

- Global sync owner：coordinator
- Global sync status：pending-coordinator-pass
- Registry update needed：coding-runtime module plan 状态在 PR/merge 后更新
- Harness Ledger update needed：task plan path、review path、closeout status 由 lifecycle CLI 同步
- Closeout / Regression update needed：`docs/05-TEST-QA/Regression-SSoT.md`、`docs/05-TEST-QA/Cadence-Ledger.md` 已更新

## Module Preset

This module task was created through the `module` preset.

| Field | Value |
| --- | --- |
| Module Key | coding-runtime |

## Module Context Entry Points

Read these module-level entry points before changing shared module behavior. Continue into narrower context only when the task surface requires it.

| Reference | Path | Why / When |
| --- | --- | --- |
| Module brief | coding-agent-harness/planning/modules/coding-runtime/brief.md | Start here for the module purpose and current scope. |
| Module plan | coding-agent-harness/planning/modules/coding-runtime/module_plan.md | Use this for module steps, active task links, and handoff state. |
| Module visual map | coding-agent-harness/planning/modules/coding-runtime/visual_map.md | Inspect when the change affects module sequencing or dependencies. |
