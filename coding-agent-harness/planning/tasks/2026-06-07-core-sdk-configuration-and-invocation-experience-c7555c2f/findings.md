# Core SDK configuration and invocation experience upgrade design - 发现记录

本文件记录任务执行中形成的判断、事实和技术决策。它不是审查报告；阻塞性问题请写入 `review.md`。

## 研究发现

### F-001 Plain Java 样板主要集中在配置创建

- 背景：用户希望降低 Java 接入成本，但前置审计要求不能新增隐藏式 Chat facade。
- 发现：`Configuration` 默认提供 `OkHttpClient` 和各 provider config 字段，但没有静态工厂或 builder；Plain Java 需要手工 new `OpenAiConfig`、set apiKey、set 到 `Configuration`、再 new `AiService`。
- 影响：最小可行升级点是 `Configuration` 级 helper，而不是 `ChatClient`。
- 后续：Wave 2 可单独评审 `Configuration.openAi(...)` / OpenAI-compatible helper。

### F-002 多实例 / profile 已有正式抽象

- 背景：需要判断是否另起 profile facade。
- 发现：`AiServiceRegistry`、`DefaultAiServiceRegistry`、`AiServiceRegistration` 已按 id 管理 scoped `AiService`；`AiConfigProperties.platforms` 已能绑定多平台配置。
- 影响：profile 升级应增强 registry/starter 配置和默认 id，不应新增平行入口。
- 后续：设计 `ai.default-platform-id` 和配置校验任务。

### F-003 OpenAI-compatible 中转平台能力已具备字段基础

- 背景：需要支持 TroveBox 等中转平台。
- 发现：`OpenAiConfig`、`AiPlatform`、`AiPlatformProperties` 都包含 `apiHost` / `apiKey` / endpoint URL 字段；Spring Boot test 已覆盖 `ai.openai.api-host` 写入 `OpenAiConfig`。
- 影响：TroveBox 可按 `platform: openai` + 自定义 `api-host` 接入；短期主要缺 docs recipe，不缺一套新 SDK。
- 后续：docs-site 增加 OpenAI-compatible / TroveBox 配置页。

### F-004 docs-site 已有真实对象链，但入口组织还可升级

- 背景：需要判断 docs-site 是否需要继续重构。
- 发现：README 和 docs-site 已回到真实 `Configuration -> AiService -> ...` 主线，并已有多平台矩阵、OpenAI-compatible 配置页面，但“中转平台、profile、recipe”的路径还分散。
- 影响：Wave 1 应先做 docs-site 真实体验重写，把配置、profile、recipe 连成可复制路径。
- 后续：单独开 docs-site Wave 任务。

## 技术决策

| 决策 | 选择 | 原因 | 替代方案 | 状态 |
| --- | --- | --- | --- | --- |
| D-001 | 先做 docs/recipe，后做 helper API | 不改 API 即可先提升理解和接入体验 | 立刻新增 API | accepted |
| D-002 | 配置 helper 应返回 `Configuration` | 保留对象链主合同，只减少配置样板 | 返回 Chat facade | accepted |
| D-003 | 多 provider/profile 继续走 `AiServiceRegistry` | 现有源码和 starter 已支持该抽象 | 新增 profile facade | accepted |
| D-004 | OpenAI-compatible / TroveBox 走 `platform: openai` + `api-host` | 字段基础已存在，学习成本最低 | 为中转平台新增 provider 类型 | accepted |
| D-005 | 明确拒绝 `ChatClient` / `Ai4j.chat()` 大门面 | 会遮蔽 Tool/MCP/RAG/Memory/Responses 边界 | 短链式调用作为主入口 | accepted |

## 待确认问题

| 问题 | 当前判断 | Owner | 截止点 |
| --- | --- | --- | --- |
| 是否新增 `Configuration.openAi(...)` helper | 值得做，但必须单独 API 评审 | user / coordinator | Wave 2 前 |
| 是否增加 `base-url` 作为 `api-host` 别名 | 值得评审，需检查 Spring Boot 绑定兼容性 | coordinator | Starter 实现前 |
| 是否增加默认 profile | 值得做，但需定义缺省行为和错误提示 | coordinator | Registry / starter 实现前 |
