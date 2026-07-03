# ChatFire Videos / Suno / ElevenLabs 接入分析

## 结论

先接 **ChatFire OpenaiVideos 统一格式**，不要先做每个平台原生适配。

原因：`/v1/videos` 已经覆盖 Sora、Veo、Kling、Seedance、Vidu、Wan、Runway、PixVerse、Luma、数字人等模型的 create/query/download/remix 形态；项目当前正好缺一个 Video modality。一上来给 Fal/Doubao/Kling/MiniMax/Suno/ElevenLabs 都建独立 provider 会把 API 面撑爆。

## 项目现状

| 现有面 | 证据 | 对接影响 |
| --- | --- | --- |
| Chat / Responses / Image / Audio 已按服务接口拆分 | `ai4j/src/main/java/io/github/lnyocly/ai4j/service/` | Video 应该新增 `IVideoService`，不塞进 Image/Audio。 |
| `IAudioService` 只支持同步 TTS、转录、翻译 | `IAudioService.java` | Suno 是 async task，ElevenLabs native TTS 有 path voice_id/query/body 差异，不能硬塞现有方法。 |
| `IImageService` 是 create + stream | `IImageService.java` | Video 是 create -> poll -> result/download，流程不同。 |
| `AiService` 没有 Video factory | `AiService.java` | 需要最小增加 `getVideoService(PlatformType.OPENAI)`。 |
| `OpenAiConfig` 有 speech/image/responses URL，但无 videos URL | `OpenAiConfig.java` | 需要新增 `videoUrl = "v1/videos"`。 |
| 多实例配置 `AiPlatform` 缺 imageGenerationUrl/responsesUrl | `AiPlatform.java` | 加 videoUrl 时顺手补全这些字段，否则 `ai.platforms[]` 对 media/responses 不完整。 |

## ChatFire 文档事实

Base URL：`https://api.chatfire.cn`，另有 `https://api.chatfire.ai`、`https://api.chatfire.cc`。认证统一为 `Authorization: Bearer <token>`。

### 1. 统一 OpenaiVideos 格式

| 操作 | Endpoint | Body | 典型响应 |
| --- | --- | --- | --- |
| 创建视频 | `POST /v1/videos` | `multipart/form-data`; required: `model`, `prompt`; 常见：`seconds`, `size`, `input_reference` | `id`, `object`, `status`, `created_at`，部分模型还有 `model/seconds` |
| 查询视频 | `GET /v1/videos/{id}` | path `id` | `id`, `object`, `status`, `progress`, `video_url`, `created_at`；Sora/Veo 示例不一定返回 `video_url` |
| 下载视频 | `GET /v1/videos/{id}/content` | path `id` | 文档写 `application/json` string；SDK 应按二进制流处理更稳 |
| remix/edit | `POST /v1/videos/{id}/remix` | JSON `{ "prompt": "..." }` | 新 video task 对象 |

统一格式的 provider 差异主要体现在 form 字段：

| Provider | 差异字段 |
| --- | --- |
| Sora | `model`, `prompt`, `seconds`, `size`, `input_reference` |
| Veo | 额外 `enable_upsample`; `seconds` 为 `4/6/8`; `size` 为 `1280x720/720x1280` |
| Kling | 额外 `first_frame_image`, `last_frame_image`, `video` |
| Seedance | 额外 `first_frame_image`, `last_frame_image`; headers 可有 `x-base-url`, `input-reference-format` |

### 2. 平台原生格式

| 平台 | 创建 | 查询/结果 | 形态 |
| --- | --- | --- | --- |
| Fal.ai | `POST /fal-ai/v1/{model}/{submodel}` 或 `POST /fal-ai/{model}/{submodel}` | `GET /fal-ai/{model}/requests/{request_id}/status`，`GET /fal-ai/{model}/requests/{request_id}` | 返回 `request_id/status_url/response_url`；结果常见 `video.url` |
| Doubao Seedance 官方 | `POST /doubao/api/v3/contents/generations/tasks` | `GET /doubao/api/v3/contents/generations/tasks/{task_id}` | 创建只返回 `id`；完成后 `content.video_url` |
| Kling 官方 | `POST /kling/v1/videos/text2video` | `GET /kling/v1/videos/image2video/{task_id}` | 响应包在 `data.task_id/task_status/task_result` |
| MiniMax 官方 | `POST /minimax/v1/video_generation` | `GET /minimax/v1/query/video_generation?task_id=...` | 创建返回 `task_id`; 查询可能返回 `videos[].videoURL` |

这些 endpoint 没有统一 task schema，第一版不要抽“万能 MediaTask provider”。

### 3. Suno 音乐

| 操作 | Endpoint | Body/响应 |
| --- | --- | --- |
| 生成歌曲 | `POST /suno/submit/music` | JSON required: `prompt`, `tags`, `mv`, `title`; 响应 `code`, `data`(task id), `message` |
| 获取任务 | `GET /suno/fetch/{task_id}` | `data.status/progress/data[]`; clip 内有 `audio_url`, `image_url`, `video_url`, `duration`, `model_name` |
| 生成歌词 | `POST /suno/submit/lyrics` | JSON `{ "prompt": "..." }` |

Suno 是独立 async music task；后续单独 `ISunoMusicService` 或 `IMusicService`，不要复用现有 `IAudioService`。

### 4. ElevenLabs 语音

| 操作 | Endpoint | Body/响应 |
| --- | --- | --- |
| TTS | `POST /elevenlabs/v1/text-to-speech/{voice_id}?output_format=...` | JSON required `text`; 默认返回文件流；`response_format=url` 时返回 `{ "audio": "https://...mp3" }` |
| TTS with timestamps | `POST /elevenlabs/v1/text-to-speech/{voice_id}/with-timestamps` | 返回 `audio_base64`, `alignment`, `normalized_alignment` |
| STT | `POST /elevenlabs/v1/speech-to-text` | multipart required `file`, `model_id`; 返回 `text`, `words` 等 |

现有 `IAudioService.textToSpeech(TextToSpeech)` 是 OpenAI `/v1/audio/speech` 风格，不能表达 ElevenLabs 的 `voice_id` path、query output_format、timestamps 响应。

## 最小实现方案

### Phase 1：只做统一 Video

新增最少文件：

- `ai4j/src/main/java/io/github/lnyocly/ai4j/service/IVideoService.java`
- `ai4j/src/main/java/io/github/lnyocly/ai4j/platform/openai/video/OpenAiVideoService.java`
- `ai4j/src/main/java/io/github/lnyocly/ai4j/platform/openai/video/entity/VideoCreateRequest.java`
- `ai4j/src/main/java/io/github/lnyocly/ai4j/platform/openai/video/entity/VideoResponse.java`

改现有文件：

- `OpenAiConfig` / `OpenAiConfigProperties`：加 `videoUrl = "v1/videos"`
- `AiPlatform` / `AiPlatformProperties`：加 `videoUrl`，并补 `imageGenerationUrl`、`responsesUrl`
- `AiConfigAutoConfiguration`：拷贝 `videoUrl`
- `AiService`：加 `getVideoService(PlatformType.OPENAI)`

接口建议：

```java
public interface IVideoService {
    VideoResponse create(String baseUrl, String apiKey, VideoCreateRequest request) throws Exception;
    VideoResponse create(VideoCreateRequest request) throws Exception;
    VideoResponse retrieve(String baseUrl, String apiKey, String id) throws Exception;
    VideoResponse retrieve(String id) throws Exception;
    InputStream content(String baseUrl, String apiKey, String id) throws Exception;
    InputStream content(String id) throws Exception;
    VideoResponse remix(String baseUrl, String apiKey, String id, String prompt) throws Exception;
    VideoResponse remix(String id, String prompt) throws Exception;
}
```

DTO 建议保持窄：

- `VideoCreateRequest`: `model`, `prompt`, `seconds`, `size`, `Map<String, Object> extraFields`, `Map<String, File> fileFields`。
- `VideoResponse`: `id`, `object`, `status`, `model`, `size`, `seconds`, `progress`, `videoUrl`, `createdAt`, `Map<String, Object> raw`。

`extraFields/fileFields` 是为了覆盖 `enable_upsample`、`first_frame_image`、`last_frame_image`、`video` 等 provider 字段，避免每个视频平台都建一套 DTO。

### Phase 2：Suno

等 Video 合并后再加：

- `SunoMusicService.submitMusic(...)`
- `SunoMusicService.fetch(...)`
- `SunoMusicService.submitLyrics(...)`

不要和 Video 共用 task DTO；Suno result 是 song clips，不是 video object。

### Phase 3：ElevenLabs

两条路：

1. 简单 TTS：新增 ElevenLabs native DTO/service，支持文件流和 URL 两种响应。
2. 如果只要 ChatFire 的 OpenAI 语音兼容接口，继续复用 `OpenAiAudioService` + `ai.openai.apiHost=https://api.chatfire.cn/`，只补 `emotion` 字段即可。

## 配置示例

### 单实例

```java
OpenAiConfig config = new OpenAiConfig();
config.setApiHost("https://api.chatfire.cn/");
config.setApiKey(System.getenv("CHATFIRE_API_KEY"));
config.setVideoUrl("v1/videos");
```

### Spring 多实例

```yaml
ai:
  platforms:
    - id: chatfire
      platform: openai
      api-host: https://api.chatfire.cn/
      api-key: ${CHATFIRE_API_KEY}
      video-url: v1/videos
```

这里继续用 `platform: openai`，不要新增 `CHATFIRE` 平台枚举；ChatFire 的统一接口本质上是 OpenAI-compatible 网关。

## 测试建议

最小本地测试用 `MockWebServer`：

1. `create`：断言 `POST /v1/videos`、Bearer header、multipart 包含 `model/prompt/size`。
2. `retrieve`：解析 `status=completed` 与 `video_url`。
3. `content`：返回 `video/mp4` 字节流并验证关闭 response。
4. `remix`：断言 `POST /v1/videos/{id}/remix` JSON body。

命令：

```bash
mvn -pl ai4j -Dtest=OpenAiVideoServiceTest -DskipTests=false test
mvn -pl ai4j -DskipTests=false test
```

## 不做

- 不新增 `CHATFIRE`、`SUNO`、`ELEVENLABS` 三个平台枚举；第一版没有必要。
- 不做通用 media-task 抽象；Fal/Doubao/Kling/MiniMax/Suno 的状态和结果字段差异太大。
- 不跑 live provider 测试；需要用户提供 `CHATFIRE_API_KEY`，且会产生费用。
