# Suno music generation service implementation - 发现记录

本文件记录任务执行中形成的判断、事实和技术决策。它不是审查报告；阻塞性问题请写入 `review.md`。

## 研究发现

### ChatFire Suno API 形态

- 背景：用户要求接入 Suno 生成音乐，前序 ChatFire 分析已确认文档入口为 `https://api.chatfire.cn/doc`，文档站实际 embed 到 Apifox。
- 发现：ChatFire `/api/status` 的 `header_nav` 指向 `https://oneapis.apifox.cn`；Apifox Suno 分组列出：`POST /suno/submit/lyrics`、`POST https://api.chatfire.cn/suno/submit/music`、`GET https://api.chatfire.cn/suno/fetch/{task_id}`。
- 影响：Suno 不是 OpenAI-compatible `/v1/audio` 路径，应新增独立 `PlatformType.SUNO` 和 Suno 原生 service，而不是塞进 OpenAI audio/video service。
- 后续：用 MockWebServer 固定 URL、Bearer auth、JSON body 和响应解析。

### 字段与响应

- 背景：Suno music 有灵感、自定义、续写、上传生成多种模式。
- 发现：使用说明给出通用字段：`gpt_description_prompt`、`make_instrumental`、`mv`、`prompt`、`title`、`tags`、`generation_type`、`negative_tags`、`continue_at`、`continue_clip_id`、`task`、`cover_clip_id` 等；submit 响应为 `{code,message,data}`，其中 `data` 是 task id；fetch 响应为 `{code,message,data:{task_id,action,status,progress,data:[song...]}}`。
- 影响：request DTO 需要固定常用字段并允许 extra fields；fetch 结果的 nested `data` 保留为 `JsonNode`，避免 lyrics/其他 action 结果形态变化导致反序列化失败。
- 后续：本轮只暴露 submit music/lyrics/fetch 三个稳定入口。

## 技术决策

| 决策 | 选择 | 原因 | 替代方案 | 状态 |
| --- | --- | --- | --- | --- |
| 服务边界 | 新增 `IMusicService` + `SunoMusicService` | 与现有 `IImageService`/`IVideoService` 模式一致，避免媒体大抽象 | 复用 `IAudioService` 或新建 generic media task service | accepted |
| 配置 | 新增 `SunoConfig` 和 `ai.suno.*` starter binding | ChatFire Suno base/api/key/endpoint 与 OpenAI audio 不同 | 使用 OpenAiConfig | accepted |
| 结果数据 | `SunoTask.data` 用 `JsonNode`，另建 `SunoSong` DTO | 保留原始响应并兼容不同 action | 强类型 `List<SunoSong>` | accepted |

## 待确认问题

| 问题 | 当前判断 | Owner | 截止点 |
| --- | --- | --- | --- |
| 是否运行真实 ChatFire Suno 任务 | 需要 API key/余额，当前不运行；作为 LV-001 opt-in residual | user/operator | closeout |
