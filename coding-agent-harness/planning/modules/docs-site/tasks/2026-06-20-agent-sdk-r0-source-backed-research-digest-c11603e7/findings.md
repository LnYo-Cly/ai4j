# Agent SDK R0 source backed research digest - 发现记录

本文件记录任务执行中形成的判断、事实和技术决策。它不是审查报告；阻塞性问题请写入 `review.md`。

## 研究发现

### Pi 可证明插件/TUI/session 组合思路，但不能证明内部实现

- 背景：用户希望参考 Pi 的插件生态和 TUI 体验。
- 发现：Pi coding-agent 公开文档可证明 interactive TUI、session management、extension system、skills、tools、run modes 和 SDK surface；但不能完整证明内部 renderer、extension isolation、安全策略。
- 影响：AI4J 可借鉴“贡献点丰富 + CLI/TUI + session”的产品结构，但不能照搬实现。
- 后续：插件任务先做 Tool/Command/Hook/Resource，TUI render plugin 暂缓。

### Codex/Claude/OpenCode 共同证明命令、权限、sandbox、memory 应是一等体验

- 背景：AI4J 目标是接近 Codex / Claude Code / OpenCode 的终端 agent 体验。
- 发现：公开文档均把 slash commands、permission/approval、memory/compact、plugins/hooks、sandbox 或 tool control 做成一等操作面。
- 影响：AI4J CLI 后续不应只把这些能力藏在配置中，应有 `/memory`、`/compact`、`/sandbox`、`/permissions`、`/plugins`、`/model`、`/provider` 等可发现入口。
- 后续：优先推进 memory/compact CLI UX 和 plugin contribution contract。

### Java AI SDK 对比显示 AI4J 应差异化而非复制大框架

- 背景：Spring AI、LangChain4j、AgentScope Java 都有团队和生态优势。
- 发现：Spring AI 强在 Spring/ChatClient/Advisor，LangChain4j 强在 AI Services/tools/RAG/memory，AgentScope Java 强在 JVM agent framework/memory/HITL/multi-agent。
- 影响：AI4J 应强调低接入成本、AgentSession/Blueprint/插件启用和 coding-agent 产品体验，而不是声称全面超过。
- 后续：docs-site 对比页应写“适合场景”和“AI4J 差异化”，避免营销化措辞。

### Sandbox provider 共同模式是 lifecycle + filesystem + command + snapshot

- 背景：用户希望支持类似豆包/点点的云端 agent 产品能力。
- 发现：E2B、Daytona、Modal 的公开资料都指向隔离执行、filesystem、commands/process、terminal/preview、snapshots/checkpoints、artifacts/logs。
- 影响：AI4J 先做 `SandboxProvider`、`SandboxSession`、`SandboxToolRouter`、`AgentRunnerClient` 抽象和 fake provider tests，而不是默认绑定真实云 provider。
- 后续：Sandbox SPI hardening 和 coding sandbox tool routing 必须单独开实现任务。

## 技术决策

| 决策 | 选择 | 原因 | 替代方案 | 状态 |
| --- | --- | --- | --- | --- |
| 调研来源 | 只用公开 docs/GitHub/provider docs | 可复查，避免泄露源码或传闻污染设计 | 使用未公开源码分析 | accepted |
| Pi 结论 | 记录可确认能力和 source gap | 公开资料能证明产品面，不能证明内部实现 | 直接照搬 Pi 插件/TUI | accepted |
| docs-site 呈现 | 新增用户可读 digest 页面并链接 roadmap | 让设计依据对外可见 | 只放 task package | accepted |
| 竞品对比 | 写差异化和设计约束 | 避免贬低式营销 | 声称 AI4J 全面优于大框架 | accepted |
| Sandbox | 抽象优先，真实 provider opt-in | 个人项目维护成本可控 | 官方内置重型云平台 | accepted |

## 待确认问题

| 问题 | 当前判断 | Owner | 截止点 |
| --- | --- | --- | --- |
| CubeSandbox 具体 API | 当前公开资料不足，只作为 source gap | coordinator | Sandbox provider task 前 |
| TUI render plugin 是否开放 | 暂缓，仅先做 CLI Command Plugin | coordinator | CLI/TUI 基础体验稳定后 |
| one-command install 方案 | 需后续比较 native/jbang/npm/zip | coordinator | CLI packaging task |
