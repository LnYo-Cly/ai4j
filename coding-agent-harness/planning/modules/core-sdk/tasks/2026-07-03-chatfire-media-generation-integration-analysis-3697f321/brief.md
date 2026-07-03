# ChatFire media generation integration analysis

## Task ID

`2026-07-03-chatfire-media-generation-integration-analysis-3697f321`

## 创建日期

2026-07-03

## 一句话结果

给出 ai4j-sdk 对接 ChatFire Videos/Suno/ElevenLabs 的最小可落地方案。

## 完成后能得到什么

下一轮 agent 可以直接按 `references/chatfire-media-integration-analysis.md` 实现第一版 Video 接入：新增 `IVideoService` + `OpenAiVideoService`，基于 ChatFire OpenaiVideos 统一 `/v1/videos` 格式完成 create/query/content/remix；同时明确 Suno 和 ElevenLabs native 不在第一版里，避免把多个异步/原生接口一次性过度抽象。

## 交付物

- 可见产物：`references/chatfire-media-integration-analysis.md`
- 修改位置：task-local harness files only
- 验证证据：`progress.md` 记录项目源码检查和 ChatFire Markdown 文档检查

## 第一眼应该看什么

先读 `references/chatfire-media-integration-analysis.md` 的“结论”和“最小实现方案”，再看 `task_plan.md` 的范围和验收标准。

## 边界

- 范围内：项目现有 core-sdk provider/media 服务边界分析；ChatFire API 文档分析；任务本地方案报告。
- 范围外：生产代码实现、live provider 调用、Suno/ElevenLabs 全量原生适配。
- 停止条件：需要真实 API key 或付费接口验证时停止。

## 完成判断

- Video 接入归属和最小 API 面已明确。
- 统一 OpenaiVideos 与平台原生格式差异已明确。
- Suno 与 ElevenLabs 的后续接入路径已明确。
- 不做项和测试建议已记录。

## 执行合同

- Owner：coordinator
- 生命周期状态：已完成
- 必需文件：`INDEX.md`、`task_plan.md`、`visual_map.md`、`progress.md`、`walkthrough.md`
- 完成条件：分析报告和 closeout 已记录到任务包。

## 当前下一步

如果用户确认进入实现，创建后续 core-sdk 实现任务并按报告的 Phase 1 添加 Video 服务和 MockWebServer 测试。
