# P2-B AgentSession sandbox binding

Task Contract: harness-task/v1
Task Kind: module-task
Task Preset: module
Preset Version: 1
Evidence Bundle: coding-agent-harness/planning/modules/agent-runtime/tasks/2026-06-20-p2-b-agentsession-sandbox-binding-e8175553/artifacts/preset/2026-06-20T01-31-46-573Z
Task Package Index: required

## 目标

把 P2-A 的 provider-neutral Sandbox SPI 以“非敏感摘要”的形式绑定到 `AgentSession`，使 session snapshot / store / restore / event log 能知道当前 sandbox 状态，但不创建真实 sandbox、不路由 coding tools、不保存 secret。

## 范围

- 做什么：新增 `AgentSessionSandboxBinding` 摘要模型；在 `AgentSession` 中提供 bind/update/clear/get sandbox binding API；让 `AgentSessionSnapshot` 和 `InMemoryAgentSessionStore` 保留 binding；记录 sandbox bind/update/clear 事件；补确定性单测和 docs-site 说明。
- 不做什么：不实现真实 CubeSandbox / Docker / E2B / K8s provider；不让插件贡献 provider；不改 `ai4j-coding` 工具路由；不改 CLI `/sandbox` UX；不保存 provider token、cookie、API key 或本机绝对私密路径。
- 主要风险：snapshot 不能把 `SandboxSpec.config` 里的潜在 secret 写入持久化状态；event log 只能记录摘要，不能记录 provider 原始请求或凭证。

## 预算选择

选择预算：complex

选择理由：本任务虽只改 `ai4j-agent`，但涉及 runtime session、snapshot/store、event log、docs-site 和 regression governance，需要完整任务包与 review 材料。

## 上下文包（Context Packet）

| ID | 类型 | 路径 | 为什么需要 | 使用者 |
| --- | --- | --- | --- | --- |
| C-001 | code | TARGET:ai4j-agent/src/main/java/io/github/lnyocly/ai4j/agent/AgentSession.java | 当前 session lifecycle、snapshot、compact、store API | coordinator / reviewer |
| C-002 | code | TARGET:ai4j-agent/src/main/java/io/github/lnyocly/ai4j/agent/session/AgentSessionSnapshot.java | P2-B 要扩展的可持久化 snapshot 模型 | coordinator / reviewer |
| C-003 | code | TARGET:ai4j-agent/src/main/java/io/github/lnyocly/ai4j/agent/sandbox/ | P2-A Sandbox SPI model，是本任务的输入合同 | coordinator / reviewer |
| C-004 | test | TARGET:ai4j-agent/src/test/java/io/github/lnyocly/agent/AgentSessionRuntimeContainerTest.java | 现有 session snapshot/store/restore 回归模式 | coordinator / reviewer |
| C-005 | docs | TARGET:docs-site/docs/agent/sandbox-spi.md | P2-A docs 页面，需要补 P2-B binding 说明 | coordinator / reviewer |
| C-006 | governance | TARGET:docs/05-TEST-QA/Regression-SSoT.md; TARGET:docs/05-TEST-QA/Cadence-Ledger.md | 新增固定 regression evidence 需要同步 | coordinator / reviewer |

## 步骤

1. 填充 P2-B task package，明确 worktree、边界、证据计划和 stop conditions。
2. 新增 `AgentSessionSandboxBinding`，只保存 providerId、sandboxSessionId、status、profile、image、workspaceId、labels 和时间戳等非敏感摘要。
3. 扩展 `AgentSession` / `AgentSessionSnapshot` / `InMemoryAgentSessionStore`：支持 bind/update/clear/get sandbox binding，并在 snapshot/store/restore 中保留。
4. 新增 event type 和 event log 记录：sandbox bound / updated / cleared。
5. 新增 `AgentSessionSandboxBindingTest`，覆盖 snapshot、restore、store、event log、defensive copies、敏感 label 过滤。
6. 更新 docs-site Sandbox SPI 与 Agent SDK Roadmap。
7. 更新 Regression SSoT / Cadence Ledger，运行 targeted/broad/docs/Harness 验证。
8. 提交、task-review、PR、CI、merge、清理 worktree。

## 验收标准

- [ ] `AgentSession.bindSandbox(...)` 能从 `SandboxSession` 或摘要对象绑定 sandbox 状态。
- [ ] `AgentSession.snapshot()`、`AgentSession.restore(...)`、`AgentSessionStore` 能保留 sandbox binding 摘要。
- [ ] `AgentSession` event log 能记录 sandbox bind/update/clear 事件。
- [ ] snapshot / binding 返回 defensive copies，外部不能篡改内部状态。
- [ ] 不保存 `SandboxSpec.config`，敏感 label key 不进入 binding 摘要。
- [ ] docs-site 解释 P2-B 的作用、示例和边界。
- [ ] targeted test、broad `ai4j-agent` regression、docs build、Harness status 通过。

## 工作树（Worktree）

- 路径：`.wt/p2b`
- 分支：`feature/agent-session-sandbox-binding`
- Worker owner：coordinator
- Worker handoff commit required：no
- Coordinator integration branch：main
- 未使用 worktree 的原因：不适用；本任务已使用 dedicated worktree。

## 长程任务判定

- 是否属于长程任务：否
- 若是，合同文件：不适用
- 连续执行权限：用户已授权继续拆解、worktree、实现、自测、PR/CI/merge
- Stop Condition 摘要：如果需要真实 sandbox provider、coding tool routing、plugin provider contribution 或 CLI UX，停止并转 P2-C/P3/P4。

## 审查判定

- 是否需要对抗性审查：否
- 若是，报告文件：不适用
- Reviewer：self + Harness scanner + PR/CI
- No-finding 要求：review.md 无 open material finding；PR CI 全绿后 merge。

## 关联

- 相关 Regression Gate：RG-002 agent runtime；RG-008 docs-site build
- 审查报告：`review.md`
- Generated Ledger：由 lifecycle CLI / `harness governance rebuild` 重建
- 前置任务：`MODULES/agent-runtime/2026-06-20-p2-a-sandbox-spi-model-c9c66766`

## 模块关联（启用模块并行时填写）

- Module：agent-runtime
- Step：T-P2-B-AGENTSESSION-SANDBOX-BINDING-E8175553
- Module Plan：TARGET:coding-agent-harness/planning/modules/agent-runtime/module_plan.md

## 协调者交接（Coordinator，启用模块并行时填写）

- Global sync owner：coordinator
- Global sync status：pending-coordinator-pass
- Registry update needed：agent-runtime step status / branch / evidence
- Harness Ledger update needed：由 lifecycle CLI 自动同步
- Closeout / Regression update needed：`docs/05-TEST-QA/Regression-SSoT.md`、`docs/05-TEST-QA/Cadence-Ledger.md`、task-local walkthrough

## Module Preset

This module task was created through the `module` preset.

| Field | Value |
| --- | --- |
| Module Key | agent-runtime |

## Module Context Entry Points

| Reference | Path | Why / When |
| --- | --- | --- |
| Module brief | coding-agent-harness/planning/modules/agent-runtime/brief.md | Start here for the module purpose and current scope. |
| Module plan | coding-agent-harness/planning/modules/agent-runtime/module_plan.md | Use this for module steps, active task links, and handoff state. |
| Module visual map | coding-agent-harness/planning/modules/agent-runtime/visual_map.md | Inspect when the change affects module sequencing or dependencies. |
