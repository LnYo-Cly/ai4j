# P4 CLI sandbox commands

Task Contract: harness-task/v1
Task Kind: module-task
Task Preset: module
Preset Version: 1
Evidence Bundle: coding-agent-harness/planning/modules/cli-host/tasks/2026-06-20-p4-cli-sandbox-commands-72f40aa0/artifacts/preset/2026-06-20T03-50-33-430Z
Task Package Index: required

## 目标

把 P3 已实现的 coding sandbox routing 暴露到 `ai4j-cli` 交互体验中：用户可以通过 `/sandbox` 查看、绑定和关闭当前 CLI session 的 sandbox 状态，同时保持真实 provider / runner 后端为后续任务。

## 范围

- 做什么：新增 `/sandbox`、`/sandbox status`、`/sandbox attach <providerId> <sessionId> [workspaceId]`、`/sandbox disable`；补全 slash completion、TUI palette/help/status；必要时调整 `CodingCliAgentFactory` 的非破坏性 overload，把当前 sandbox binding 传入 `CodingAgentBuilder.sandbox(...)`；更新 docs-site 和回归治理。
- 不做什么：不创建真实 VM/container/browser sandbox；不实现 CubeSandbox 或任何云后端；不新增 `SandboxProvider.attachSession` SPI；不把 sandbox 设为默认执行环境；不引入新 Maven 模块；不改变 provider key、token 或本地配置秘密策略。
- 主要风险：当前 `SandboxProvider` 只有 `createSession(SandboxSpec)`，没有“按 sessionId 恢复/attach”的通用 SPI；如果 CLI 伪造一个可执行 sandbox，会误导用户。因此 P4 必须明确“绑定状态”和“真实执行后端”的边界，不能静默回退到宿主机。

## 预算选择

选择预算：complex

选择理由：本任务横跨 `ai4j-cli` 命令分发、TUI completion/palette、`ai4j-coding` P3 runtime binding、docs-site、Regression SSoT / Cadence Ledger，并且需要保留真实 provider/runner 未实现的边界说明。

## 上下文包（Context Packet）

| ID | 类型 | 路径 | 为什么需要 | 使用者 |
| --- | --- | --- | --- | --- |
| C-001 | repo-guidance | TARGET:AGENTS.md | monorepo 边界、Java 8、Harness task package 和 regression 规则 | coordinator / reviewer |
| C-002 | reference | TARGET:docs/11-REFERENCE/engineering-standard.md | 模块职责、公共 API 和实现边界 | coordinator / reviewer |
| C-003 | reference | TARGET:docs/11-REFERENCE/testing-standard.md | CLI/TUI 和 docs-site 的验证入口 | coordinator / reviewer |
| C-004 | reference | TARGET:docs/05-TEST-QA/Regression-SSoT.md | RG-004/RG-008 当前状态和更新要求 | coordinator |
| C-005 | reference | TARGET:docs/05-TEST-QA/Cadence-Ledger.md | `ai4j-cli/**` 和 `docs-site/**` 触发的固定 gate | coordinator |
| C-006 | module-plan | TARGET:coding-agent-harness/planning/modules/cli-host/module_plan.md | 当前 P4 所属模块、写入范围和验证要求 | coordinator |
| C-007 | upstream-code | TARGET:ai4j-coding/src/main/java/io/github/lnyocly/ai4j/coding/CodingAgentBuilder.java | P3 `.sandbox(SandboxSession)` 和 shell routing 接入点 | coordinator |
| C-008 | upstream-code | TARGET:ai4j-agent/src/main/java/io/github/lnyocly/ai4j/agent/sandbox/*.java | sandbox SPI 当前只定义 provider/create/session/execute，无 attach SPI | coordinator |
| C-009 | cli-code | TARGET:ai4j-cli/src/main/java/io/github/lnyocly/ai4j/cli/runtime/CodingCliSessionRunner.java | slash dispatch、runtime rebind、status/help/palette 的主接线点 | coordinator |
| C-010 | cli-code | TARGET:ai4j-cli/src/main/java/io/github/lnyocly/ai4j/cli/SlashCommandController.java | root completion、action completion、palette 行为 | coordinator |
| C-011 | cli-code | TARGET:ai4j-cli/src/main/java/io/github/lnyocly/ai4j/cli/factory/DefaultCodingCliAgentFactory.java | 创建 `CodingAgentBuilder` 并能传入 sandbox runtime 的位置 | coordinator |
| C-012 | task-reference | TARGET:coding-agent-harness/planning/modules/cli-host/tasks/2026-06-20-p4-cli-sandbox-commands-72f40aa0/references/cli-sandbox-command-plan.md | 本任务详细实现计划和验收矩阵 | coordinator / reviewer |

## 步骤

1. 重新诊断 CLI runtime 接缝：确认 `/mcp`、`/stream`、`/provider` 如何重建 runtime，确认 `CodingCliAgentFactory` 增加 overload 不破坏现有实现。
2. 新增 CLI sandbox 状态模型：保存 `providerId`、`sessionId`、可选 `workspaceId`、状态来源和创建时间；只保存非敏感值。
3. 实现 `/sandbox` 命令族：默认/status 展示 direct-host 或 attached；attach 校验参数并记录绑定；disable 清除绑定；所有输出说明真实 provider 后端边界。
4. 接入 runtime rebind：attach/disable 后重建当前 `CodingAgent`；当绑定存在时传入 sandbox handle，使后续 shell routing 不再被误认为 direct-host。
5. 补充 discoverability：`SlashCommandController` root/action completion、TUI palette、`/help`、`/status`、command summary 都包含 sandbox。
6. 补测试：至少覆盖 slash suggestions；尽量覆盖 sandbox command parsing、status render、attach/disable state transition 和 factory sandbox overload。
7. 补文档和治理：更新 docs-site sandbox routing / CLI command 文档；如固定 gate 证据或说明变化，同步 `docs/05-TEST-QA/Regression-SSoT.md` 与 `docs/05-TEST-QA/Cadence-Ledger.md`。
8. 验证并收口：运行 targeted CLI tests、必要的 broad CLI tests、docs-site build、`git diff --check`、`npx --yes coding-agent-harness status --json .`，再提交、PR、CI、merge 和清理 worktree。

## 验收标准

- [ ] `/sandbox` 与 `/sandbox status` 输出当前 mode、provider/session/workspace 摘要和执行边界。
- [ ] `/sandbox attach <providerId> <sessionId> [workspaceId]` 能在当前 CLI session 内记录绑定并触发 runtime rebind。
- [ ] `/sandbox disable` 能清除绑定并回到 direct-host runtime。
- [ ] attached 状态不会静默把 shell 执行伪装成 sandbox 成功；没有真实 provider bridge 时必须给出清晰限制或错误。
- [ ] Slash completion、TUI palette、help/status 命令均能发现 sandbox 入口。
- [ ] 文档明确 P4 不提供真实 sandbox 创建/恢复后端，真实 provider/runner 是后续任务。
- [ ] `mvn -pl ai4j-cli -am "-Dtest=SlashCommandControllerTest,CodingCliSessionRunnerArgumentParsingTest,DefaultCodingCliAgentFactoryTest" -DskipTests=false -DfailIfNoTests=false test` 通过或记录替代 targeted set。
- [ ] `mvn -pl ai4j-cli -am -DskipTests=false test` 根据变更风险通过或记录明确 residual。
- [ ] `npm --prefix docs-site run build` 在 docs-site 变更时通过。
- [ ] `git diff --check` 和 `npx --yes coding-agent-harness status --json .` 通过或只剩已解释 residual。

## 工作树（Worktree）

- 路径：`G:\My_Project\java\ai4j-sdk\.worktrees\feature\cli-sandbox-commands`
- 分支：`feature/cli-sandbox-commands`
- Worker owner：coordinator
- Worker handoff commit required：yes，已验证切片需要提交
- Coordinator integration branch：`main` / remote PR base 按当前仓库流程执行
- 未使用 worktree 的原因：不适用；本任务已在 dedicated worktree 执行。

## 长程任务判定

- 是否属于长程任务：否，本任务是单个 CLI 可见切片；但执行时可连续推进到验证和 PR。
- 若是，合同文件：不适用
- 连续执行权限：用户已授权继续执行同一任务；若 scope 扩展到真实 sandbox provider/remote runner，必须暂停。
- Stop Condition 摘要：完成 CLI 可见状态/attach/disable、文档和验证后停止；真实后端能力转入后续 P5/runner 任务。

## 审查判定

- 是否需要对抗性审查：是，至少 self adversarial review；如改到 public factory/SPI 或 provider 后端，再升级 reviewer。
- 若是，报告文件：`review.md`
- Reviewer：self；必要时追加只读 reviewer subagent
- No-finding 要求：不得存在“误称已有真实 sandbox 后端”“静默执行在宿主机”“破坏现有 CLI factory 实现”的 P0/P1/P2 open finding。

## 关联

- 相关 Regression Gate：RG-004 CLI/TUI/ACP host；RG-008 docs-site build；RG-007 仅当共享 build/packaging 风险升高时触发。
- 审查报告：`coding-agent-harness/planning/modules/cli-host/tasks/2026-06-20-p4-cli-sandbox-commands-72f40aa0/review.md`
- Generated Ledger：由 lifecycle CLI / `harness governance rebuild` 重建
- 前置任务：`MODULES/coding-runtime/2026-06-20-p3-coding-sandbox-tool-routing-6c82c346`；P2-A Sandbox SPI 与 P2-B AgentSession binding 已提供上游语义。

## 模块关联（启用模块并行时填写）

- Module：cli-host
- Step：T-P4-CLI-SANDBOX-COMMANDS-72F40AA0
- Module Plan：`coding-agent-harness/planning/modules/cli-host/module_plan.md`

## 协调者交接（Coordinator，启用模块并行时填写）

- Global sync owner：coordinator
- Global sync status：pending-coordinator-pass
- Registry update needed：cli-host step active；实现提交后同步状态和证据
- Harness Ledger update needed：task plan path、review path、closeout status 由 lifecycle CLI / governance rebuild 同步
- Closeout / Regression update needed：实现阶段如新增/更新固定 regression 证据，需要同步 `docs/05-TEST-QA/Regression-SSoT.md` 和 `docs/05-TEST-QA/Cadence-Ledger.md`

## Module Preset

This module task was created through the `module` preset.

| Field | Value |
| --- | --- |
| Module Key | cli-host |

## Module Context Entry Points

Read these module-level entry points before changing shared module behavior. Continue into narrower context only when the task surface requires it.

| Reference | Path | Why / When |
| --- | --- | --- |
| Module brief | coding-agent-harness/planning/modules/cli-host/brief.md | Start here for the module purpose and current scope. |
| Module plan | coding-agent-harness/planning/modules/cli-host/module_plan.md | Use this for module steps, active task links, and handoff state. |
| Module visual map | coding-agent-harness/planning/modules/cli-host/visual_map.md | Inspect when the change affects module sequencing or dependencies. |
