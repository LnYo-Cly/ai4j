# Suno music generation service implementation

## Task ID

`2026-07-03-suno-music-generation-service-implementation-15778b9f`

## 创建日期

2026-07-03

## 一句话结果

在 core SDK 中接入 ChatFire Suno 音乐生成：提交歌曲/歌词任务、查询任务结果，并提供 registry 与 Spring Boot 配置入口。

## 完成后能得到什么

用户可以通过 `AiService.getMusicService(PlatformType.SUNO)` 或多实例 registry/FreeAiService 获取 Suno 音乐生成服务，用 ChatFire 文档中的 `/suno/submit/music`、`/suno/submit/lyrics`、`/suno/fetch/{task_id}` 完成异步任务提交与查询。实现包含 Java 8 DTO、服务接口、Suno 配置、Spring Boot 属性绑定和 MockWebServer 本地回归，不依赖真实 provider key。

## 交付物

- 可见产物：Suno music service API、DTO、配置项、确定性本地测试与任务收口记录。
- 修改位置：`ai4j/`、`ai4j-spring-boot-starter/`、`docs/05-TEST-QA/`、本任务包。
- 验证证据：目标测试、core/starter Maven 回归、monorepo package smoke。

## 第一眼应该看什么

1. `ai4j/src/main/java/io/github/lnyocly/ai4j/platform/suno/music/SunoMusicService.java`
2. `ai4j/src/test/java/io/github/lnyocly/ai4j/platform/suno/music/SunoMusicServiceTest.java`
3. `ai4j-spring-boot-starter/src/test/java/io/github/lnyocly/ai4j/AiServiceSunoAutoConfigurationTest.java`
4. 本目录 `progress.md` / `walkthrough.md` 的验证命令。

## 边界

- 范围内：ChatFire Suno 原生接口（歌词、歌曲、fetch）、core SDK/registry/starter 配置和本地回归。
- 范围外：ElevenLabs、视频服务、Suno uploads/concat/persona/stems 等未本轮点名接口、真实付费生成验证。
- 停止条件：如果 ChatFire 文档与 runtime 行为冲突，先以可复现的本地契约测试收口，把 live-provider 验证作为 opt-in residual。

## 完成判断

- [x] `PlatformType.SUNO` 可创建 `IMusicService` / `SunoMusicService`。
- [x] submit music/lyrics/fetch 的 URL、Authorization、JSON snake_case 请求体被 MockWebServer 回归覆盖。
- [x] Spring Boot `ai.suno.*` 和 `ai.platforms[].platform=suno` 能绑定。
- [x] RG-001、RG-005、RG-007 相关验证记录已更新。
- [x] 本任务 `walkthrough.md` 完成并记录 residual。

## 执行合同

- Owner：coordinator
- 生命周期状态：审查中
- 必需文件：`INDEX.md`、`task_plan.md`、`execution_strategy.md`、`visual_map.md`、`progress.md`、`findings.md`、`review.md`、`walkthrough.md`
- 完成条件：验证证据必须记录到 `progress.md`

## 当前下一步

提交实现、运行 `harness task-review`、创建 PR 并合并后清理 worktree。
