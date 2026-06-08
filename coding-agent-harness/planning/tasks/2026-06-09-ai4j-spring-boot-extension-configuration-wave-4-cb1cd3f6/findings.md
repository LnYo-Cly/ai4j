# AI4J Spring Boot extension configuration wave 4 - 发现记录

本文件记录任务执行中形成的判断、事实和技术决策。它不是审查报告；阻塞性问题请写入 `review.md`。

## 研究发现

### Spring Boot 只装配 registry/snapshot，不自动创建 Agent

- 背景：插件生态需要降低 Spring 用户接入成本，但 starter 不应复制 Agent/Coding Agent 运行时逻辑。
- 发现：`ai4j-agent` 已提供 `AgentBuilder.extensions(...)` 和 adapter；starter 只需要暴露 `ExtensionRegistry` / `ExtensionRuntimeSnapshot`，业务方可按需注入 registry。
- 影响：`ai4j-spring-boot-starter` 依赖 `ai4j-extension-api`，但不依赖 `ai4j-agent`，避免把 Agent runtime 变成 starter 传递依赖。
- 后续：如需 Spring 自动创建 Agent，应另开 starter/agent integration 任务。

### 配置错误应该 fail fast

- 背景：Spring Boot 用户可能只写 `tools.expose` 或拼错 extension id。
- 发现：`ExtensionRegistry.enable(...)` 对未知插件抛 `ExtensionException`；`snapshot()` 对未注册工具抛 `ExtensionException`。
- 影响：starter 自动装配保留 fail fast 语义，不静默忽略配置错误。
- 后续：docs-site 明确“discover / enable / expose”三段式门禁同样适用于 Spring Boot。

## 技术决策

| 决策 | 选择 | 原因 | 替代方案 | 状态 |
| --- | --- | --- | --- | --- |
| Spring Boot 配置前缀 | `ai.extensions.*` | 与现有 starter `ai.openai`、`ai.agentflow`、`ai.vector.*` 配置体系一致 | `ai4j.extensions.*`，会和现有 `ai.*` 风格割裂 | accepted |
| 自动装配产物 | `ExtensionRegistry` + `ExtensionRuntimeSnapshot` | 让 Spring 项目配置化启用插件，同时保持宿主决定是否接入 Agent | 自动创建 Agent/Coding Agent，范围过大且会引入不必要依赖 | accepted |
| 安全语义 | discover / enable / expose 三段式 | 与 extension API 和 Agent/Coding Agent adapter 一致 | 启用插件后自动暴露所有 tools，风险过大 | accepted |

## 待确认问题

| 问题 | 当前判断 | Owner | 截止点 |
| --- | --- | --- | --- |
| Spring 自动创建 Agent 是否必要 | 不在本轮；应由单独 starter-agent 任务设计 | owner / coordinator | 后续插件生态任务 |
