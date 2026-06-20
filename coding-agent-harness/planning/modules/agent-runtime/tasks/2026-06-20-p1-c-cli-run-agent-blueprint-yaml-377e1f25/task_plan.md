# P1-C CLI run Agent Blueprint YAML

Task Contract: harness-task/v1
Task Kind: module-task
Task Preset: module
Preset Version: 1
Evidence Bundle: coding-agent-harness/planning/modules/agent-runtime/tasks/2026-06-20-p1-c-cli-run-agent-blueprint-yaml-377e1f25/artifacts/preset/2026-06-19T23-39-08-883Z
Task Package Index: required

## 目标

在 `ai4j-cli` 中实现最小可用 `run <agent.yaml>` 命令，承接 P1-A Blueprint loader/validator 与 P1-B `AgentFactory`，让单 Agent YAML 可以在 CLI host 提供模型客户端后运行一次。

## 范围

- 做什么：新增 `AgentBlueprintRunCommand`、run options、model-client factory；在 `Ai4jCli` 顶层路由 `run`；扩展 CLI provider profile resolution；新增确定性单测；更新 Agent Blueprint docs 和 roadmap；更新回归治理与任务材料。
- 不做什么：不实现真实 sandbox provider；不扫描/安装插件；不支持 Team/Workflow graph Blueprint；不重构 TUI；不做 live provider 调用；不把 token 写入 YAML、fixture、docs 或日志。
- 主要风险：CLI host 误读/误用 profile secret；显式 profile 不存在却静默回退；用户误以为 `sandbox.enabled=true` 已创建 VM；docs 把路线图写成已发布能力。

## 预算选择

选择预算：complex

选择理由：本任务跨 `ai4j-cli`、`ai4j-agent` 消费边界、docs-site、Regression SSoT、Cadence Ledger 和 Harness task package；需要明确运行时配置、安全边界、测试和 PR/CI 证据。

## 设计方案

采用 CLI-host-supplied model client 方案：

```text
ai4j-cli run agent.yaml --input "..."
  -> AgentBlueprintLoader
  -> CliProviderConfigManager.resolveWithProfile(...)
  -> DefaultAgentBlueprintRunModelClientFactory
  -> AgentFactory.create(blueprint, AgentFactoryContext.modelClient(...))
  -> Agent.run(AgentRequest.input(...))
```

关键边界：YAML 不保存 token；`AgentFactory` 不读取 profile；CLI host 可以读取 CLI provider profile，但显式 `--profile` / YAML `model.profile` 不存在时必须失败，不能静默回退。

## 上下文包（Context Packet）

| ID | 类型 | 路径 | 为什么需要 | 使用者 |
| --- | --- | --- | --- | --- |
| C-001 | code | TARGET:ai4j-agent/src/main/java/io/github/lnyocly/ai4j/agent/blueprint/** | P1-A/P1-B Blueprint loader/validator/factory API | coordinator / reviewer |
| C-002 | code | TARGET:ai4j-cli/src/main/java/io/github/lnyocly/ai4j/cli/Ai4jCli.java | 顶层命令路由入口 | coordinator / reviewer |
| C-003 | code | TARGET:ai4j-cli/src/main/java/io/github/lnyocly/ai4j/cli/provider/CliProviderConfigManager.java | CLI provider/profile/model/base-url/api-key 解析 | coordinator / reviewer |
| C-004 | code | TARGET:ai4j-cli/src/main/java/io/github/lnyocly/ai4j/cli/factory/DefaultCodingCliAgentFactory.java | 复用 provider client 创建逻辑 | coordinator / reviewer |
| C-005 | docs | TARGET:docs-site/docs/agent/agent-blueprint.md | 用户文档需要说明 CLI run 用法和边界 | coordinator / reviewer |
| C-006 | reference | TARGET:coding-agent-harness/planning/tasks/2026-06-20-ai4j-agent-sdk-architecture-enhancement-planning-b6a2e312/references/ai4j-agent-sdk-integrated-implementation-plan-2026-06-20.md | P1-C 在整体 Agent SDK 路线中的位置 | coordinator / reviewer |

## 步骤

1. INIT-01：任务创建、启动和 `.wt/p1c` / `feature/cli-run-agent-blueprint` worktree 建立。
2. EXEC-01：新增 `AgentBlueprintRunCommand`、options、model-client factory。
3. EXEC-02：接入 `Ai4jCli` 顶层 `run` 命令和 help 文案。
4. EXEC-03：扩展 `CliProviderConfigManager.resolveWithProfile(...)`，让 Blueprint profile 能由 CLI host 解析。
5. EXEC-04：新增 JUnit 4 tests，覆盖运行成功、profile、missing profile、sandbox guard、validation error、top-level help。
6. EXEC-05：更新 docs-site Agent Blueprint 页面、Agent SDK roadmap、Regression SSoT / Cadence Ledger。
7. VERIFY-01：运行 targeted CLI tests、broad CLI regression、docs-site build、diff check、Harness status。
8. GATE-01：task-review。
9. GATE-02：push、PR、CI、merge、worktree cleanup。

## 验收标准

- [ ] `run` 命令从顶层 help 可见，并路由到 `AgentBlueprintRunCommand`。
- [ ] 有效单 Agent YAML + fake host model client 能运行一次并打印 `AgentResult.outputText`。
- [ ] YAML `model.profile` 可解析 CLI provider profile，但 missing/incompatible profile 会确定性失败。
- [ ] `sandbox.enabled=true` 默认失败，`--allow-sandbox-declaration` 只允许声明通过。
- [ ] 无真实 token 写入 docs/test/fixture；live provider 不作为默认证据。
- [ ] targeted / broad CLI regression、docs-site build、Harness status 通过。
- [ ] Regression SSoT / Cadence Ledger 记录 P1-C CLI `run <agent.yaml>` 固定回归面。

## 工作树（Worktree）

- 路径：`.wt/p1c`
- 分支：`feature/cli-run-agent-blueprint`
- Worker owner：coordinator
- Worker handoff commit required：yes（若启用 worker）；coordinator 实施则不适用
- Coordinator integration branch：`feature/cli-run-agent-blueprint`
- 未使用 worktree 的原因：不适用。P1-C 修改代码、docs 和治理记录，使用 dedicated worktree。

## 长程任务判定

- 是否属于长程任务：否
- 若是，合同文件：不适用
- 连续执行权限：用户已授权连续推进 Agent SDK 拆解，但本切片本身不是 long-running task。
- Stop Condition 摘要：如果进入真实 sandbox、live provider、TUI 全量重构或新增 Maven module，停止并另开任务。

## 审查判定

- 是否需要对抗性审查：否
- Reviewer：self + Harness scanner + PR/CI
- No-finding 要求：self-review 必须覆盖 token/profile/sandbox 边界、CLI behavior 和 docs 准确性；PR 后等 CI。

## 关联

- 相关 Regression Gate：RG-004 CLI/TUI/ACP host；RG-008 docs-site build；RG-007 package build按 PR/merge-batch 或共享构建变更触发。
- 审查报告：`review.md`
- Generated Ledger：由 lifecycle CLI / `harness governance rebuild` 重建
- 前置任务：P1-A Agent Blueprint schema/model/loader/validator；P1-B Agent Blueprint to AgentFactory

## 模块关联（启用模块并行时填写）

- Module：agent-runtime
- Step：T-P1-C-CLI-RUN-AGENT-BLUEPRINT-YAML-377E1F25
- Module Plan：TARGET:coding-agent-harness/planning/modules/agent-runtime/module_plan.md

## 协调者交接（Coordinator，启用模块并行时填写）

- Global sync owner：coordinator
- Global sync status：pending-pr
- Registry update needed：agent-runtime P1-C 从 active 更新为 implementation-verified，merge 后更新为 merged。
- Harness Ledger update needed：task-review 后由 Harness lifecycle 扫描生成。
- Closeout / Regression update needed：`docs/05-TEST-QA/Regression-SSoT.md`、`docs/05-TEST-QA/Cadence-Ledger.md`

## Module Preset

This module task was created through the `module` preset.

| Field | Value |
| --- | --- |
| Module Key | agent-runtime |

## Module Context Entry Points

| Reference | Path | Why / When |
| --- | --- | --- |
| Module brief | coding-agent-harness/planning/modules/agent-runtime/brief.md | Module purpose and scope. |
| Module plan | coding-agent-harness/planning/modules/agent-runtime/module_plan.md | Module steps, active task links, and handoff state. |
| Integrated Agent SDK plan | coding-agent-harness/planning/tasks/2026-06-20-ai4j-agent-sdk-architecture-enhancement-planning-b6a2e312/references/ai4j-agent-sdk-integrated-implementation-plan-2026-06-20.md | Overall route and P1-C priority. |
