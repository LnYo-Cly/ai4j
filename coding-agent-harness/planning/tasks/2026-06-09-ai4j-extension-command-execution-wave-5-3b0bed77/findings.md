# AI4J extension command execution wave 5 - 发现记录

本文件记录任务执行中形成的判断、事实和技术决策。它不是审查报告；阻塞性问题请写入 `review.md`。

## 研究发现

### CLI command 是人工调用入口，不是模型 tool 暴露

- 背景：Wave 1 已有 `CommandRegistry` 和 `ExtensionCommandHandler`，Wave 2 CLI 只能 inspect，用户还不能直接调用插件 command。
- 发现：command 资源适合成为 CLI 手动入口；它不进入 Agent tool registry，因此不应复用 `exposeTool` 语义，也不应暗示模型可以调用 command。
- 影响：本轮新增 `extension run --enable <id> <command>`，只从显式启用的插件快照中查找 command handler。
- 后续：如果未来要让 Coding Agent/TUI slash command 自动接入插件 command，需要单独设计权限和交互语义。

### 插件 command 参数应透传给 handler

- 背景：插件 command 可能有自己的 `--flag` 参数。
- 发现：如果 CLI 在 command 名称后继续拦截 `--xxx`，会破坏第三方 command 的可用性。
- 影响：解析规则设为：command 名称前解析 AI4J CLI 选项；command 名称后所有 token 都拼接后传给 handler。
- 后续：如果未来需要结构化参数，应在 extension API 中设计 typed command request，而不是在 CLI 层猜测。

## 技术决策

| 决策 | 选择 | 原因 | 替代方案 | 状态 |
| --- | --- | --- | --- | --- |
| command 执行门禁 | `run` 必须带 `--enable <extension-id>` | classpath discovery 不应执行第三方代码；启用动作必须由用户显式给出。 | 发现后直接执行 command | accepted |
| 参数格式 | command 名称后的 token 全部传给 handler | 保留第三方 command 自己的 `--flag` 参数能力。 | 继续由 AI4J CLI 解析所有 `--xxx` | accepted |
| API 范围 | 不改 `ai4j-extension-api` | 现有 `ExtensionCommandRequest(command, arguments)` 已够本轮使用，避免公共合同膨胀。 | 新增 typed args / env / IO contract | accepted |

## 待确认问题

| 问题 | 当前判断 | Owner | 截止点 |
| --- | --- | --- | --- |
| 是否把插件 command 接入 TUI slash command palette | 暂不做；需要独立交互和权限设计。 | coordinator | 后续 CLI/TUI 插件命令任务 |
| 是否提供 CLI install | 暂不做；继续由 Maven/Gradle 管理插件依赖。 | coordinator | marketplace/install 任务前 |
