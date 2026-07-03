# ChatFire OpenAI videos service implementation

## Task ID

`2026-07-03-chatfire-openai-videos-service-implementation-b2ffc35a`

## 创建日期

2026-07-03

## 一句话结果

为 core SDK 增加 OpenAI-compatible `/v1/videos` 视频生成服务，并让 ChatFire 网关可通过现有 OpenAI 平台配置使用。

## 完成后能得到什么

用户可以通过 `AiService.getVideoService(PlatformType.OPENAI)` 或多实例 `FreeAiService.getVideoService("chatfire")` 调用 ChatFire/OpenAI-compatible 视频接口，支持创建视频任务、查询任务、下载内容流和 remix。Spring 配置新增 `video-url`，多实例配置也补齐 `imageGenerationUrl`、`responsesUrl`、`videoUrl`，避免媒体/Responses URL 只能在单实例配置里生效。

## 交付物

- 可见产物：`IVideoService`、`OpenAiVideoService`、`VideoCreateRequest`、`VideoResponse`、本地 MockWebServer 测试。
- 修改位置：`ai4j/**`、`ai4j-spring-boot-starter/**`、`docs/05-TEST-QA/**`、task-local harness files。
- 验证证据：`progress.md` 中记录的 Maven 类级、core 全量和 starter 全量测试。

## 第一眼应该看什么

先看 `ai4j/src/main/java/io/github/lnyocly/ai4j/platform/openai/video/OpenAiVideoService.java`，再看 `ai4j/src/test/java/io/github/lnyocly/ai4j/platform/openai/video/OpenAiVideoServiceTest.java`。

## 边界

- 范围内：OpenAI-compatible `/v1/videos` create/retrieve/content/remix；ChatFire 通过 `apiHost` 使用；配置和多实例入口；本地回归。
- 范围外：Suno 音乐、ElevenLabs native、Fal/Doubao/Kling/MiniMax 原生 endpoint、live provider 付费验证。
- 停止条件：需要真实 `CHATFIRE_API_KEY` 或付费接口验证时停止并转为 opt-in live gate。

## 完成判断

- 新增 Video 服务 public API 并接入 `AiService`、`AiServiceRegistry`、`FreeAiService`。
- `OpenAiVideoServiceTest` 覆盖 create/retrieve/content/remix。
- `AiServiceRegistryTest` 覆盖多实例 `videoUrl` 和 video service 入口。
- RG-001、RG-005、RG-007 本地回归通过。
- Regression SSoT 与 Cadence Ledger 已同步新增 video surface 证据。

## 执行合同

- Owner：coordinator
- 生命周期状态：进行中
- 必需文件：`INDEX.md`、`task_plan.md`、`execution_strategy.md`、`visual_map.md`、`progress.md`、`findings.md`、`review.md`、`walkthrough.md`
- 完成条件：验证证据必须记录到 `progress.md`

## 当前下一步

提交实现分支，推送并创建 PR；PR 合并后清理 worktree/临时分支。
