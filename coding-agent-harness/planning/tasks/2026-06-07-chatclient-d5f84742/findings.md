# 轻量 ChatClient 首聊门面 - 发现记录

本文件记录任务执行中形成的判断、事实和技术决策。它不是审查报告；阻塞性问题请写入 `review.md`。

## 研究发现

### FND-001 - 现有对象链能直接承载轻量门面

- 背景：方案 A 需要降低第一条 chat 请求的接入成本，但不能重做 SDK service factory。
- 发现：`AiService#getChatService(PlatformType.OPENAI)` 已返回 `OpenAiChatService`，`Configuration` 默认创建 `OkHttpClient`，`OpenAiConfig` 已有默认 OpenAI host 和 chat completions path。
- 影响：`ChatClient` 可以作为薄 facade 组合现有 `Configuration`、`AiService`、`IChatService`，不改 provider adapter。
- 后续：用 MockWebServer 测试确认请求 path、Authorization header 和 response 文本抽取。

### FND-002 - 首聊文档需要短路径优先、完整对象链保留

- 背景：用户希望个人项目在 Java AI SDK 接入成本上比 Spring AI / LangChain4j 等更轻。
- 发现：当前 Plain Java recipe 仍以完整对象链作为首选；这对“第一条请求”有解释价值，但不是最低成本入口。
- 影响：docs-site 与 `ai4j-app-builder` 应把 `ChatClient` 作为推荐首聊入口，完整对象链移到进阶/扩展说明。
- 后续：同步 public docs 与 skill recipe，避免 agent 继续生成旧首聊样板。

## 技术决策

| 决策 | 选择 | 原因 | 替代方案 | 状态 |
| --- | --- | --- | --- | --- |
| DEC-001 | 新增 `io.github.lnyocly.ai4j.service.ChatClient` | 与 `Configuration` / `IChatService` 同包，导入路径短，属于 core SDK 公共入口 | 放到 `service.factory` 会暗示它是工厂；放到新包会增加首聊 import 成本 | accepted |
| DEC-002 | `chat(String model, String userMessage)` 返回 assistant 文本，`chat(ChatCompletion)` 返回原始 response | 首聊最短路径需要 `String`，进阶路径仍保留完整 response | 只返回 response 会降低低成本价值；只返回 String 会遮蔽进阶能力 | accepted |
| DEC-003 | 不支持 live-provider 默认验证 | 本任务改变 SDK facade 和 docs，不改变真实 provider 协议 | 默认跑真实 OpenAI 会引入 key/rate-limit/网络不稳定 | accepted |

## 待确认问题

| 问题 | 当前判断 | Owner | 截止点 |
| --- | --- | --- | --- |
| 是否要把 ChatClient 扩展到所有 provider | 本任务不做；先做 OpenAI 首聊路径，避免一次性包装所有 provider 造成半成品 API | coordinator | 本任务 closeout |
