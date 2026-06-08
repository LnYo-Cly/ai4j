# AI4J extension runtime adapter wave 3 - 发现记录

本文件记录任务执行中形成的判断、事实和技术决策。它不是审查报告；阻塞性问题请写入 `review.md`。

## 研究发现

### Extension API 已经具备 runtime snapshot 合同

- 背景：Wave 3 需要把插件资源接入 Agent / Coding Agent，但不能重新发明插件生命周期。
- 发现：`ExtensionRegistry.snapshot()` 已经把 enabled extensions 和 exposed tool allowlist 投影成 `ExtensionRuntimeSnapshot`，其中包含 `getTools()` 与 `getToolExecutors()`。
- 影响：runtime adapter 只需要消费 snapshot，不需要修改 extension API 的 enable/expose 语义。
- 后续：如后续接 Spring Boot 配置化插件装配，也应复用 `ExtensionRegistry`，不要绕开 snapshot。

### Agent / Coding Agent 可以共用同一 extension-to-tool adapter

- 背景：Agent 和 Coding Agent 都使用 `AgentToolRegistry` 与 `ToolExecutor` 作为工具抽象。
- 发现：`ExtensionToolSpec` 可以映射到现有 OpenAI-compatible `Tool`，`ExtensionToolExecutor` 可以桥接为现有 `ToolExecutor`。
- 影响：新增 `ExtensionAgentTools` 放在 `ai4j-agent`，`ai4j-coding` 依赖 `ai4j-agent` 后复用，不在 coding runtime 复制一套 mapper。
- 后续：若后续插件命令进入 CLI runtime，应继续复用 extension API snapshot，不要把 CLI inspect 和 runtime execute 写成两套事实。

### docs-site 必须明确“当前没有 marketplace / hotload / provider plugin”

- 背景：用户希望插件生态可由第三方开发者扩展，但也明确不要随意起名或夸大尚未实现能力。
- 发现：当前实现路径是 Java classpath + `ServiceLoader` + `ExtensionRegistry`，不包含远程安装、热加载 jar、provider 自动注册或 Spring Boot 配置化插件装配。
- 影响：新增 `plugin-packages.md` 把使用者路径、开发者路径、安全门禁和当前边界分开说明。
- 后续：后续如做 Spring Boot 配置化接入，应另开任务并补 starter 文档。

## 技术决策

| 决策 | 选择 | 原因 | 替代方案 | 状态 |
| --- | --- | --- | --- | --- |
| 插件工具接入方式 | `ExtensionAgentTools.from(registry.snapshot())` 转成 `AgentToolRegistry` + `ToolExecutor` | 复用现有 Agent tool loop，避免改主循环 | 让 Agent runtime 直接认识 `ExtensionToolSpec` | accepted |
| 安全门禁 | 保持 discover / enable / expose 三段式 | tool 暴露给模型有执行风险，必须由宿主显式授权 | enable 后自动暴露全部 tool | accepted |
| Coding Agent adapter | 复用 `ai4j-agent` 中的 adapter | coding runtime 已依赖 agent runtime，避免重复 schema mapper | 在 `ai4j-coding` 重写一套 adapter | accepted |
| 路由执行器 | 在 `ai4j-agent` 增加 `RoutingToolExecutor`，coding 使用已有 `coding.tool.RoutingToolExecutor` | 保持模块内可用，不让 agent 依赖 coding | 将 coding executor 上移 | accepted |
| docs 表述 | 写清当前能力和不包含能力 | 防止用户把插件包误解为 marketplace/provider plugin | 使用更泛化的“插件生态”营销文案 | accepted |

## 待确认问题

| 问题 | 当前判断 | Owner | 截止点 |
| --- | --- | --- | --- |
| 是否做 Spring Boot 配置化插件装配 | 不在本轮范围，需另开任务确认配置模型 | owner / coordinator | 下一轮插件生态实现任务 |
| 是否做远程插件索引或 marketplace | 不在本轮范围；当前推荐 Maven/Gradle 坐标 + README 维护 | owner / coordinator | 后续产品规划 |
