# Agent Blueprint schema export and docs hardening

Task Contract: harness-task/v1
Task Kind: module-task
Task Preset: module
Preset Version: 1
Evidence Bundle: coding-agent-harness/planning/modules/agent-runtime/tasks/2026-06-20-agent-blueprint-schema-export-and-docs-hardening-4741edc1/artifacts/preset/2026-06-20T12-48-36-308Z
Task Package Index: required

## 目标

把 Agent Blueprint 从“能加载和校验 YAML”升级为“能给用户提供稳定 authoring schema 的 YAML Agent 入口”：内置 `ai4j.agent/v1` JSON Schema，提供 Java accessor 和 CLI 导出命令，并在 docs-site 讲清用法与边界。

## 范围

- 做什么：新增 JSON Schema resource、`AgentBlueprintSchemas`、`ai4j-cli blueprint schema`、targeted tests、docs-site Blueprint/command 文档更新。
- 不做什么：不引入运行期 JSON Schema validator 依赖；不改变 Blueprint v1 字段语义；不创建真实 sandbox；不使用 live provider；不发布远端 schema URL。
- 主要风险：schema 与 Java validator 漂移；用户误以为 schema 能替代 runtime/host policy；CLI 子命令与现有 `run`/`extension` 命令冲突。

## 预算选择

选择预算：complex

选择理由：本任务虽是窄切片，但跨 `ai4j-agent`、`ai4j-cli`、`docs-site` 和 Harness module task，需要代码、CLI、文档和多类验证同步，且会成为后续 YAML Agent 使用体验的公开契约。

## 上下文包（Context Packet）

| ID | 类型 | 路径 | 为什么需要 | 使用者 |
| --- | --- | --- | --- | --- |
| C-001 | repo-guidance | TARGET:AGENTS.md | Java 8、module boundary、Harness flow、docs-site 更新要求 | coordinator / reviewer |
| C-002 | module-plan | TARGET:coding-agent-harness/planning/modules/agent-runtime/module_plan.md | 确认本任务属于 Agent Blueprint 后续硬化切片 | coordinator |
| C-003 | code | TARGET:ai4j-agent/src/main/java/io/github/lnyocly/ai4j/agent/blueprint | Blueprint DTO、loader、validator、factory 真实 API | worker / reviewer |
| C-004 | code | TARGET:ai4j-cli/src/main/java/io/github/lnyocly/ai4j/cli | 顶层 CLI 路由和 Blueprint run 命令风格 | worker / reviewer |
| C-005 | docs | TARGET:docs-site/docs/agent/agent-blueprint.md | 用户可见 Blueprint 文档，必须同步真实 API | coordinator / reviewer |

## 步骤

1. 从 `origin/dev` 创建 dedicated worktree `feature/agent-blueprint-schema-export`。
2. 新增内置 `ai4j/agent-blueprint.schema.json` 和 `AgentBlueprintSchemas`，并让 loader 忽略 `$schema` authoring hint。
3. 新增 `ai4j-cli blueprint schema [--out]`，并接入顶层 help 和 CLI tests。
4. 更新 docs-site Agent Blueprint、real API matrix、coding-agent command reference。
5. 跑 targeted agent/cli tests、docs-site typecheck/build、`git diff --check`、Harness status。
6. 补齐 review、progress、walkthrough、lesson decision，提交并发 PR 到 `dev`。

## 验收标准

- [ ] `AgentBlueprintSchemas.v1JsonSchema()` 能读取内置 schema，`writeV1JsonSchema(...)` 能写出文件。
- [ ] `AgentBlueprintLoader` 不再把 YAML 顶部 `$schema` 视为 unknown top-level field。
- [ ] `ai4j-cli blueprint schema` 能打印 schema；`--out` 能写入目标文件。
- [ ] docs-site 写清 schema 用法、IDE 配置、不能替代 runtime policy 的边界。
- [ ] Targeted JUnit、docs-site build/typecheck、Harness status 通过。

## 工作树（Worktree）

- 路径：`G:\My_Project\javai4j-sdk\.worktreeseaturegent-blueprint-schema-export`
- 分支：`feature/agent-blueprint-schema-export`
- Worker owner：coordinator
- Worker handoff commit required：yes
- Coordinator integration branch：`dev`
- 未使用 worktree 的原因：不适用，已使用 dedicated worktree。

## 长程任务判定

- 是否属于长程任务：否
- 若是，合同文件：不适用
- 连续执行权限：不适用
- Stop Condition 摘要：一旦需要改变 Blueprint v1 字段或引入 schema validator runtime 依赖，停止并另开设计任务。

## 审查判定

- 是否需要对抗性审查：是，执行 self review，重点检查 schema/validator/docs 一致性。
- 若是，报告文件：`review.md`
- Reviewer：self；PR 后由 CI / reviewer 继续验证
- No-finding 要求：不能存在 schema 与 runtime 明显冲突、docs 误导用户、或 token/secret 暴露风险。

## 关联

- 相关 Regression Gate：agent-runtime targeted tests、cli-host targeted tests、docs-site build/typecheck。
- 审查报告：`review.md`
- Generated Ledger：由 lifecycle CLI / `harness governance rebuild` 重建
- 前置任务：P1-A/P1-B/P1-C Agent Blueprint 已合并到 `origin/dev`；PR #124 docs real API matrix 已合并。

## 模块关联（启用模块并行时填写）

- Module：agent-runtime
- Step：T-AGENT-BLUEPRINT-SCHEMA-EXPORT-AND-DOCS-HARDENING-4741EDC1
- Module Plan：`coding-agent-harness/planning/modules/agent-runtime/module_plan.md`

## 协调者交接（Coordinator，启用模块并行时填写）

- Global sync owner：coordinator
- Global sync status：pending-coordinator-pass
- Registry update needed：module plan 已由 Harness CLI 添加任务；后续 review/closeout 自动同步。
- Harness Ledger update needed：task plan、review path、closeout status 由 lifecycle CLI 同步。
- Closeout / Regression update needed：如果新增固定回归面，再更新 `docs/05-TEST-QA/`；本任务预计只记录命令证据。

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
