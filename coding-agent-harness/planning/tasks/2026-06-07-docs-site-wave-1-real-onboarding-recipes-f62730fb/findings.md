# docs site wave 1 real onboarding recipes - 发现记录

本文件记录任务执行中形成的判断、事实和技术决策。它不是审查报告；阻塞性问题请写入 `review.md`。

## 研究发现

### F-001 Canonical docs-site 已经是新 IA，Wave 1 应改默认 docs

- 背景：需要决定改 canonical docs 还是 i18n。
- 发现：`docs-site/sidebars.ts` 指向 `docs-site/docs/start-here`、`core-sdk/model-access`、`spring-boot` 等新路径；中文 i18n 仍保留大量旧路径。
- 影响：Wave 1 只改 canonical docs，避免同时维护两套 IA。
- 后续：中文 i18n 同步可单独开任务。

### F-002 中转平台能力缺的是 recipe，不是新 API

- 背景：需要支持 TroveBox / OpenAI-compatible。
- 发现：现有 API 可通过 `OpenAiConfig#setApiHost(...)` 和 Spring Boot `ai.openai.api-host` / `ai.platforms[].api-host` 表达。
- 影响：新增 `start-here/openai-compatible-and-trovebox.md`，并从 quickstart / config reference / registry 页面链接。
- 后续：后续如实现 `Configuration` helper，再单独 API 评审。

## 技术决策

| 决策 | 选择 | 原因 | 替代方案 | 状态 |
| --- | --- | --- | --- | --- |
| D-001 | 只改 canonical `docs-site/docs` | sidebar 与 build 以 canonical docs 为主，范围可控 | 同步 i18n | accepted |
| D-002 | 新增 TroveBox recipe | 中转平台路径需要独立可链接入口 | 只在 quickstart 里短句提及 | accepted |
| D-003 | 在文档里明确不推荐 Chat facade | 防止再次误导用户以为有 `ChatClient` 主入口 | 不提错误入口 | accepted |

## 待确认问题

| 问题 | 当前判断 | Owner | 截止点 |
| --- | --- | --- | --- |
| 是否同步中文 i18n | 本任务不做 | user / coordinator | 后续 i18n 任务 |
