# AI4J extension system wave 1 - 发现记录

本文件记录任务执行中形成的判断、事实和技术决策。它不是审查报告；阻塞性问题请写入 `review.md`。

## 研究发现

### 现有 tool/command/skill 类型不适合作为第三方公共 API

- 背景：Wave 1 需要支持 tool、command、skill、prompt、guardrail，但不能把第三方合同绑死在当前 runtime 内部类型上。
- 发现：`AgentToolRegistry` 当前只有 `List<Object> getTools()`，`ToolExecutor` 接收 `AgentToolCall`；CLI command 与 coding skill 也更像宿主内部资源读取。
- 影响：`ai4j-extension-api` 定义中立 `ExtensionToolSpec`、`ExtensionCommandSpec`、`ExtensionSkillResource`、`ExtensionPromptResource` 和 `ExtensionGuardrail`，下游 runtime 后续做 adapter。
- 后续：Wave 2 单独实现 agent/CLI/Spring 适配。

### 新增独立模块优于塞进 `ai4j`

- 背景：第三方插件开发者需要低成本依赖扩展合同。
- 发现：`ai4j` core SDK 当前包含 provider、Tika、OkHttp、GraalVM、vector/MCP 等较重依赖。
- 影响：新增 `ai4j-extension-api`，保持零运行时依赖，避免第三方插件仅为实现接口就拉入 core SDK 大依赖面。
- 后续：BOM 和 root POM 已纳入该模块。

### harness module generated view 没有独立刷新命令

- 背景：`harness.yaml` 加入 `extension-api` 后，`Module-Registry.md` 仍是旧视图。
- 发现：`harness module list --json .` 能读到新增模块；`harness module scaffold --all` 不刷新 view；`harness module register extension-api ...` 因已注册而拒绝执行。
- 影响：本任务手动同步 `coding-agent-harness/planning/modules/Module-Registry.md`，并在进度记录中保留 CLI 行为证据。
- 后续：后续可考虑给 harness 增加 `module refresh-view` 或让 `module scaffold --all` 刷新 generated view。

## 技术决策

| 决策 | 选择 | 原因 | 替代方案 | 状态 |
| --- | --- | --- | --- | --- |
| API 落点 | 新增 `ai4j-extension-api` 模块 | 第三方插件依赖更轻，模块边界更清晰，避免 core SDK 依赖膨胀 | 放入 `ai4j/src/main/java/.../extension` | accepted |
| Wave 1 runtime 接入 | 只做公共合同和本地 registry，不接入 agent/CLI/Spring | 现有宿主内部 API 还不适合直接暴露，先稳定合同再做 adapter | 一次性接入所有 runtime | accepted |
| Command handler | `ExtensionCommandSpec` 与 `ExtensionCommandHandler` 分离 | inspect 时 spec 可静态审查，handler 作为运行时对象由 snapshot 暴露 | 把 handler 放进 spec | accepted |
| Tool expose | allowlist 指向未注册工具时报错 | 配置错误必须显性失败，避免用户以为工具已暴露 | 静默忽略未知 tool | accepted |

## 待确认问题

| 问题 | 当前判断 | Owner | 截止点 |
| --- | --- | --- | --- |
| CLI inspect/list/enable 的具体 UX | 本任务不做；优先在 Wave 2 设计并接入 `ai4j-cli` | coordinator | Wave 2 开始前 |
| Spring Boot `ai4j.extensions.*` 配置绑定 | 本任务不做；需要基于 `ExtensionRegistry` 做 properties + bean 装配 | coordinator | Wave 2 开始前 |
| Agent/Coding runtime adapter | 本任务不做；需要分别适配 tool allowlist、command palette、skill/prompt resource | coordinator | Wave 2/Wave 3 |
