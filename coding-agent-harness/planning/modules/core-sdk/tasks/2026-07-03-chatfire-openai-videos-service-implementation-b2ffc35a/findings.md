# ChatFire OpenAI videos service implementation - 发现记录

## 研究发现

### ChatFire 视频统一接口适合复用 OpenAI 平台

- 背景：ChatFire 文档同时提供 `/v1/videos` 统一格式和多个平台原生格式。
- 发现：统一格式覆盖 create/retrieve/content/remix；平台原生格式的状态和结果字段差异较大。
- 影响：本轮只实现 OpenAI-compatible video service，不新增 `CHATFIRE` 平台枚举，也不抽通用 media-task provider。
- 后续：Suno/ElevenLabs/native provider 需要单独任务。

### 多实例配置缺少部分 OpenAI-compatible URL 字段

- 背景：单实例 `OpenAiConfig` 已有 image/responses URL，但 `AiPlatform` 多实例配置缺少对应字段。
- 发现：如果只加 `videoUrl`，多实例用户仍不能覆盖 image/responses URL。
- 影响：本轮顺手补 `imageGenerationUrl`、`responsesUrl`、`videoUrl` 到 `AiPlatform` 和 starter properties。
- 后续：无。

## 技术决策

| 决策 | 选择 | 原因 | 替代方案 | 状态 |
| --- | --- | --- | --- | --- |
| 视频服务归属 | `IVideoService` + `OpenAiVideoService` | Video 是 create -> poll -> content/remix，不适合塞入 Image/Audio | 复用 `IImageService` 或泛化 media task | accepted |
| 请求扩展字段 | `extraFields/fileFields/headers` | ChatFire 各模型字段不同，最小代码能覆盖 provider-specific form/header | 为每个平台建 DTO | accepted |
| 响应保真 | typed fields + `raw` | 文档示例字段不完全一致，保留未建模字段 | 全字段 DTO | accepted |
| 平台枚举 | 不新增 `CHATFIRE` | ChatFire 统一接口是 OpenAI-compatible gateway | 新增 ChatFireConfig/PlatformType | accepted |

## 待确认问题

| 问题 | 当前判断 | Owner | 截止点 |
| --- | --- | --- | --- |
| 是否要 live ChatFire smoke | 非本轮；需要 API key 和费用确认 | user | 发布前如需真实 provider 证明 |
| 是否接 Suno/ElevenLabs native | 后续独立任务 | coordinator | 用户确认对应优先级时 |
