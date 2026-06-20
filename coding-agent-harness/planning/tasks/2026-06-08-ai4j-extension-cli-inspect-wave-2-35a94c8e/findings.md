# AI4J extension CLI inspect wave 2 - 发现记录

本文件记录任务执行中形成的判断、事实和技术决策。它不是审查报告；阻塞性问题请写入 `review.md`。

## 研究发现

### 默认 inspect 必须保持 manifest-only

- 背景：`extension inspect` 的目标是让用户先审查 classpath 上发现的扩展，而不是把第三方扩展直接接入 agent runtime。
- 发现：`ExtensionRegistry.inspect(id)` 能从 discovery 阶段保存的 `ExtensionManifest` 返回 id/name/version/vendor/capabilities/permissions/configPrefix，不需要调用扩展 `apply()`。
- 影响：CLI 默认 `inspect <id>` 只输出 manifest/source，并明确显示 `runtime=not-inspected`；只有显式 `--runtime` 才临时调用 `apply()`。
- 后续：无；该行为已由 `Ai4jCliTest.test_extension_inspect_defaults_to_manifest_only` 覆盖。

### runtime inspect 只能返回资源规格快照

- 背景：Wave 1 的 `ExtensionRuntimeSnapshot` 包含 tool executor 和 command handler，不适合直接给 CLI inspect 使用。
- 发现：本轮新增 `ExtensionInspectionSnapshot`，只包含 tool/command/skill/prompt specs 与 guardrail name，不暴露 executor/handler。
- 影响：CLI 可以列出扩展贡献的资源名，但不会获得可执行对象，也不会改变 registry 的 enabled/exposed 状态。
- 后续：Agent/Coding runtime adapter 仍留给后续 Wave；本任务不把这些资源接入执行面。

### 完整 CLI gate 受既有 agent residual 阻塞

- 背景：Cadence 对 `ai4j-cli/**` 要求 RG-004：`mvn -pl ai4j-cli -am -DskipTests=false test`。
- 发现：该命令在 2026-06-08 运行时，`ai4j-extension-api` 和 `ai4j` 通过，但 reactor 在 `ai4j-agent` 的既有 `HandoffPolicyTest` 两个失败处停止，CLI 模块未执行到。
- 影响：本轮以 targeted CLI regression `mvn -pl ai4j-cli -am -Dtest=Ai4jCliTest -DfailIfNoTests=false -DskipTests=false test` 作为新增 CLI 行为证据，同时把完整 RG-004 失败路由到既有 R-008。
- 后续：R-008 需要在独立 agent runtime 任务中修复；不阻塞本轮 extension CLI inspect 的目标提交。

## 技术决策

| 决策 | 选择 | 原因 | 替代方案 | 状态 |
| --- | --- | --- | --- | --- |
| DEC-001 inspect default | manifest-only | 不执行第三方 `apply()`，避免默认命令产生副作用 | 默认 `inspect` 同时列 runtime 资源 | accepted |
| DEC-002 runtime output model | 新增 `ExtensionInspectionSnapshot` | 避免 CLI 拿到 executor/handler 等可执行对象 | 复用 `ExtensionRuntimeSnapshot` 后在 CLI 层忽略 executor | accepted |
| DEC-003 scope split | 本轮只做 list/inspect，不做 install/enable/adapter | 保持插件生态从“可发现、可审查”逐步推进到“可启用、可执行” | 一次性实现 marketplace/install/runtime adapter | accepted |
| DEC-004 CLI args | `list` 不接受额外参数，`inspect` 只接受一个 id 和可选 `--runtime` | 让 CLI 行为可预测，便于第三方扩展开发调试和文档示例 | 忽略多余参数 | accepted |

## 待确认问题

| 问题 | 当前判断 | Owner | 截止点 |
| --- | --- | --- | --- |
| Wave 3 是否进入 runtime enable/adapter | 不在本轮；需要单独设计启用策略、配置存储和 tool exposure 门禁 | coordinator | 下一轮 extension runtime adapter 任务启动前 |
| 是否需要稳定 CLI JSON 输出 | 本轮只提供人可读输出；若要支持脚本/marketplace，可在后续加 `--json` | coordinator | CLI install/marketplace 任务前 |
