# ChatFire OpenAI videos service implementation

Task Contract: harness-task/v1
Task Kind: module-task
Task Preset: module
Preset Version: 1
Evidence Bundle: coding-agent-harness/planning/modules/core-sdk/tasks/2026-07-03-chatfire-openai-videos-service-implementation-b2ffc35a/artifacts/preset/2026-07-03T11-00-35-699Z
Task Package Index: required

## 目标

实现 ChatFire/OpenAI-compatible `/v1/videos` 视频生成服务，并通过现有 OpenAI 平台配置暴露给 SDK 和 Spring 多实例用户。

## 范围

- 做什么：新增 `IVideoService`、`OpenAiVideoService`、视频 DTO、factory/registry/free-service 入口、OpenAI video URL 配置、MockWebServer 测试、Regression SSoT/Cadence 记录。
- 不做什么：不接 Suno、ElevenLabs native、Fal/Doubao/Kling/MiniMax 原生接口；不调用 live ChatFire 付费 API。
- 主要风险：ChatFire 不同视频模型响应字段不完全一致，因此响应保留 `raw`，请求保留 `extraFields/fileFields/headers`。

## 预算选择

选择预算：standard

选择理由：改动跨 core SDK、starter 配置和固定回归治理，不适合 simple；但不需要长程任务或 subagent worker。

## 上下文包（Context Packet）

| ID | 类型 | 路径 | 为什么需要 | 使用者 |
| --- | --- | --- | --- | --- |
| C-001 | report | TARGET:coding-agent-harness/planning/modules/core-sdk/tasks/2026-07-03-chatfire-media-generation-integration-analysis-3697f321/references/chatfire-media-integration-analysis.md | 前置 API 调研和最小实现结论 | coordinator |
| C-002 | code | TARGET:ai4j/src/main/java/io/github/lnyocly/ai4j/service | 现有服务接口和 factory 边界 | coordinator |
| C-003 | code | TARGET:ai4j-spring-boot-starter/src/main/java/io/github/lnyocly/ai4j | starter 配置绑定面 | coordinator |
| C-004 | reference | TARGET:docs/11-REFERENCE/testing-standard.md | 回归命令和固定 gate 更新规则 | coordinator |

## 步骤

1. 新增 OpenAI-compatible Video 服务、DTO 和配置字段。
2. 接入 `AiService`、`AiServiceRegistry`、`FreeAiService`，补多实例配置字段。
3. 添加本地 contract tests，运行 core/starter 回归，更新 Regression SSoT/Cadence。
4. 提交实现分支、创建 PR、合并并清理 worktree。

## 验收标准

- [x] `POST /v1/videos` multipart create 可被本地测试验证。
- [x] `GET /v1/videos/{id}` 查询可解析 `video_url` 并保留 raw。
- [x] `GET /v1/videos/{id}/content` 返回可读 InputStream，且 id 中 `/` 被编码。
- [x] `POST /v1/videos/{id}/remix` 发送 prompt JSON。
- [x] core 和 starter 本地回归通过。

## 工作树（Worktree）

- 路径：`G:\My_Project\java\ai4j-sdk\.worktrees\feature\chatfire-openai-videos`
- 分支：`feature/chatfire-openai-videos`
- Worker owner：coordinator
- Worker handoff commit required：yes
- Coordinator integration branch：`feat/per-node-latency`
- 未使用 worktree 的原因：不适用；已使用独立 worktree。

## 长程任务判定

- 是否属于长程任务：否
- 若是，合同文件：不适用
- 连续执行权限：不适用
- Stop Condition 摘要：本地回归通过后进入 PR/merge；live key 缺失不阻塞。

## 审查判定

- 是否需要对抗性审查：否
- 若是，报告文件：不适用
- Reviewer：self
- No-finding 要求：self-review 无阻塞发现。

## 关联

- 相关 Regression Gate：RG-001、RG-005、RG-007
- 审查报告：`review.md`
- Generated Ledger：由 lifecycle CLI / `harness governance rebuild` 重建
- 前置任务：`2026-07-03-chatfire-media-generation-integration-analysis-3697f321`

## 模块关联（启用模块并行时填写）

- Module：core-sdk
- Step：T-CHATFIRE-OPENAI-VIDEOS-SERVICE-IMPLEMENTATION-B2FFC35A
- Module Plan：`coding-agent-harness/planning/modules/core-sdk/module_plan.md`

## 协调者交接（Coordinator，启用模块并行时填写）

- Global sync owner：coordinator
- Global sync status：pending-coordinator-pass
- Registry update needed：core-sdk module plan 已由 harness new-task/task-start 同步，closeout 后需 task-review/task-complete
- Harness Ledger update needed：task closeout status 待 lifecycle 命令同步
- Closeout / Regression update needed：`docs/05-TEST-QA/Regression-SSoT.md`、`docs/05-TEST-QA/Cadence-Ledger.md` 已更新

## Module Preset

This module task was created through the `module` preset.

| Field | Value |
| --- | --- |
| Module Key | core-sdk |

## Module Context Entry Points

| Reference | Path | Why / When |
| --- | --- | --- |
| Module brief | coding-agent-harness/planning/modules/core-sdk/brief.md | Start here for the module purpose and current scope. |
| Module plan | coding-agent-harness/planning/modules/core-sdk/module_plan.md | Use this for module steps, active task links, and handoff state. |
| Module visual map | coding-agent-harness/planning/modules/core-sdk/visual_map.md | Inspect when the change affects module sequencing or dependencies. |
