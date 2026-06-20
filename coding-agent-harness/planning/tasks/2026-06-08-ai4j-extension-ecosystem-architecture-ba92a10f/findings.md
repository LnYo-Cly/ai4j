# AI4J extension ecosystem architecture - 发现记录

本文件记录任务执行中形成的判断、事实和技术决策。它不是审查报告；阻塞性问题请写入 `review.md`。

## 研究发现

### Pi 生态不是单纯 Tool Plugin

- 背景：用户指出 Pi 的插件生态很好，要求先调研 Pi 插件到底是什么，再规划 AI4J。
- 发现：Pi package 可以打包 extensions、skills、prompt templates、themes；package 可以来自 npm、git、本地路径，并支持全局 / 项目级配置、资源过滤和启用禁用。Pi extension 还覆盖 tool、command、shortcut、flag、event、UI、provider 等运行时扩展面。
- 影响：AI4J 不能只设计 `ToolPlugin` 或 RAG connector。正确方向是 Package / Manifest / Extension / Resource 分层，并同时覆盖 SDK 能力扩展和 Coding Agent / CLI 体验扩展。
- 后续：实现前应把 `tool`、`command`、`skill`、`prompt`、`guardrail` 作为 Wave 1 候选，而不是过早公开完整 RAG / Memory / Provider 插件 API。

### OpenAI-compatible 中转平台不应成为专属 provider plugin

- 背景：前序讨论中曾错误把具体中转平台示例化为 provider / profile plugin。
- 发现：用户明确指出中转平台本质都是 OpenAI-compatible，不应随意起平台名。
- 影响：设计中必须把中转平台归入 core/starter 的 OpenAI-compatible endpoint/profile 配置体验，不作为插件生态卖点，也不作为 `ProviderExtension` Wave 1。
- 后续：后续 docs-site 插件文档必须避免把中转平台写成官方 provider 插件。

### AI4J 已有扩展落点但缺统一分发和治理层

- 背景：需要判断插件系统应落在哪些模块，而不是另起一套孤立 runtime。
- 发现：`ai4j-agent` 已有 `AgentToolRegistry`、`ToolExecutor`、memory、runtime、trace、workflow；`ai4j-coding` 已有 skill discovery、tool policy、subagent、session、workspace；`ai4j-cli` 已有 slash command、TUI、provider profile、MCP、session 管理。
- 影响：插件系统应优先把现有 registry / policy / command / skill discovery 收束成受控入口，而不是让插件绕过现有模块边界。
- 后续：Wave 1 先做 discover/inspect/enable 与 tool/command/skill/prompt/guardrail registry adapter。

## 技术决策

| 决策 | 选择 | 原因 | 替代方案 | 状态 |
| --- | --- | --- | --- | --- |
| 生态命名 | AI4J Extension System / AI4J Package | “plugin”用于对外理解，但内部要区分 package、manifest、extension、resource，才能覆盖 Pi 式能力组合。 | 只叫 Tool Plugin | accepted |
| 第一版扩展点 | Tool、Command、Skill、Prompt、Guardrail | 这几类最贴近 Pi，同时能利用 AI4J 现有 Agent/Coding/CLI 边界。 | Provider、完整 RAG、FlowGram Node 一起公开 | accepted |
| 安全模型 | 发现、启用、暴露三阶段门禁 | 第三方插件是代码，classpath 上存在不等于允许模型调用。 | classpath 自动启用全部能力 | accepted |
| 中转平台处理 | OpenAI-compatible endpoint/profile 配置 | 避免随意把中转平台命名成 provider 或插件。 | 为每个平台做 provider plugin | accepted |

## 待确认问题

| 问题 | 当前判断 | Owner | 截止点 |
| --- | --- | --- | --- |
| 是否第一版新增 `ai4j-extension-api` 独立 Maven 模块 | 推荐在实现 Wave 1 新增，但必须同步 parent pom、BOM、release docs。 | coordinator | Wave 1 实现任务设计前 |
| 是否把 plugin install 做进 CLI 第一版 | 不做。第一版只做 list / inspect / enable，install 后置。 | coordinator | CLI 实现任务前 |
| Guardrail 是否做强 enforcement | Wave 1 至少做 shell/file/tool 暴露前拦截；更细的 network/domain enforcement 后置。 | coordinator | Guardrail 实现任务前 |
