# Plugin contribution contract expansion

Task Contract: harness-task/v1
Task Kind: module-task
Task Preset: module
Preset Version: 1
Evidence Bundle: coding-agent-harness/planning/modules/agent-runtime/tasks/2026-06-20-plugin-contribution-contract-expansion-e2b3bcae/artifacts/preset/2026-06-20T08-13-20-159Z
Task Package Index: required

## 目标

把 AI4J 插件生态从“已实现 registry 的能力”扩展到“插件包可声明具体贡献项”的稳定公共契约，让第三方插件可以声明 Tool、CLI Command、Memory/Compact、Sandbox Provider、Remote Runner Provider 等贡献，并让宿主能 inspect、activation plan 和 validate。

## 范围

- 做什么：新增 `ExtensionContribution` / `ExtensionContributionType`；扩展 `ExtensionManifest` builder；把 contribution 投影到 `ExtensionInspectionSnapshot`、`ExtensionActivationPlan`、`ExtensionValidator`；更新 Ask User 插件示例和 docs-site。
- 不做什么：不实现真实 sandbox provider、runner provider、memory store registry、插件市场、远端安装协议或 CLI `/extension install`。
- 主要风险：过度设计成万能插件系统；破坏现有 tool enable/expose 安全语义；docs 写出尚不存在的自动安装/自动绑定能力。

## 预算选择

选择预算：complex

选择理由：本任务跨 `ai4j-extension-api`、`ai4j-plugin-ask-user`、`ai4j-agent` 回归和 `docs-site`，且是第三方插件生态公共契约，需要完整 task package、验证证据和 review。

## 上下文包（Context Packet）

| ID | 类型 | 路径 | 为什么需要 | 使用者 |
| --- | --- | --- | --- | --- |
| C-001 | repo-guidance | TARGET:AGENTS.md | 仓库模块边界、Java 8 和 Harness 流程约束 | coordinator / reviewer |
| C-002 | reference | TARGET:docs/11-REFERENCE/testing-standard.md | 本 worktree 中可用的测试标准 | coordinator / reviewer |
| C-003 | code | TARGET:ai4j-extension-api/src/main/java/io/github/lnyocly/ai4j/extension/ExtensionManifest.java | manifest 公共契约入口 | coordinator / reviewer |
| C-004 | code | TARGET:ai4j-extension-api/src/main/java/io/github/lnyocly/ai4j/extension/ExtensionRegistry.java | inspect / snapshot / activation plan 入口 | coordinator / reviewer |
| C-005 | code | TARGET:ai4j-extension-api/src/main/java/io/github/lnyocly/ai4j/extension/validation/ExtensionValidator.java | 插件作者校验入口 | coordinator / reviewer |
| C-006 | code | TARGET:ai4j-plugin-ask-user/src/main/java/io/github/lnyocly/ai4j/plugin/askuser/AskUserExtension.java | 官方示例插件 | coordinator / reviewer |
| C-007 | docs | TARGET:docs-site/docs/agent/plugin-lifecycle-hooks.md | 已有插件生命周期文档，避免重复和冲突 | coordinator / reviewer |
| C-008 | docs | TARGET:docs-site/docs/agent/sandbox-spi.md | sandbox provider 后续贡献点说明 | coordinator / reviewer |
| C-009 | docs | TARGET:docs-site/docs/agent/remote-agent-runner-spi.md | runner provider 后续贡献点说明 | coordinator / reviewer |

## 步骤

1. 诊断现有 `ExtensionCapability`、manifest、runtime snapshot、activation plan、validator 和 ask-user 示例。
2. 设计轻量 manifest-level contribution contract，不新增 Maven，不强制实现所有 provider registry。
3. 实现 `ExtensionContribution` / `ExtensionContributionType`、manifest builder、inspection/activation/validator 投影。
4. 更新 ask-user 示例插件，使其成为第三方插件作者可参考的 contribution metadata 示例。
5. 更新 docs-site：新增 Plugin Contribution Contract 页面，并从 lifecycle/sandbox/runner/roadmap 链接过去。
6. 运行 targeted regression、docs build、diff check、Harness status。
7. 提交、task-review、推送并创建 PR 到 `dev`。

## 验收标准

- [x] `ExtensionManifest` 能携带不可变 contribution metadata。
- [x] `ExtensionRegistry.inspectRuntime(...)` 能返回 contribution 清单。
- [x] `ExtensionRegistry.activationPlan(...)` 能展示 provider-style contribution 为需要 host binding。
- [x] `ExtensionValidator` 能识别 metadata-only capability 缺少 contribution，以及高影响 contribution 缺少 permission。
- [x] Ask User 插件 manifest 声明 tool/CLI command/skill/prompt contributions。
- [x] docs-site 有 Plugin Contribution Contract 页面，并通过 build。
- [ ] PR 创建并通过远端检查。

## 工作树（Worktree）

- 路径：`.worktrees/feature/plugin-contribution-contract-expansion`
- 分支：`feature/plugin-contribution-contract-expansion`
- Worker owner：coordinator
- Worker handoff commit required：yes
- Coordinator integration branch：`dev`
- 未使用 worktree 的原因：不适用，已使用 dedicated worktree。

## 长程任务判定

- 是否属于长程任务：否，本任务是单一插件公共契约切片。
- 若是，合同文件：不适用
- 连续执行权限：用户已授权继续推进任务队列，但本任务本身不需要 long-running contract。
- Stop Condition 摘要：一旦需要实现真实 provider、插件市场或 CLI 安装协议，停止并新建后续任务。

## 审查判定

- 是否需要对抗性审查：是，公共插件 API 需要 self adversarial review。
- 若是，报告文件：`review.md`
- Reviewer：self；PR 创建后使用远端 CI 和用户/维护者审查。
- No-finding 要求：不得存在 P0/P1/P2 open finding；P3 residual 可记录后续任务。

## 关联

- 相关 Regression Gate：Extension API、Ask User plugin、Agent extension bridge、docs-site build。
- 审查报告：`review.md`
- Generated Ledger：由 lifecycle CLI / `harness governance rebuild` 重建。
- 前置任务：P0-C lifecycle hooks、P2-A/P2-B sandbox foundation、remote runner SPI 已存在；本任务不替代真实 provider 实现。

## 模块关联（启用模块并行时填写）

- Module：agent-runtime
- Step：AGENT-PLUGIN-CONTRIBUTION-01
- Module Plan：`coding-agent-harness/planning/modules/agent-runtime/module_plan.md`

## 协调者交接（Coordinator，启用模块并行时填写）

- Global sync owner：coordinator
- Global sync status：pending-coordinator-pass
- Registry update needed：本任务完成后 module plan 状态应更新为 review/merged。
- Harness Ledger update needed：task plan path、review path、closeout status 由 lifecycle CLI 同步。
- Closeout / Regression update needed：若该公共 API 成为固定回归面，后续同步 `docs/05-TEST-QA/`；本任务目前记录为 targeted gate。

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
