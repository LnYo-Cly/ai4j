# Suno music generation service implementation

Task Contract: harness-task/v1
Task Kind: module-task
Task Preset: module
Preset Version: 1
Evidence Bundle: coding-agent-harness/planning/modules/core-sdk/tasks/2026-07-03-suno-music-generation-service-implementation-15778b9f/artifacts/preset/2026-07-03T12-09-38-724Z
Task Package Index: required

## 目标

在 ai4j core SDK 中最小化接入 ChatFire Suno 音乐生成服务，覆盖提交歌曲/歌词任务与查询任务结果。

## 范围

- 做什么：新增 Suno 配置、`PlatformType.SUNO`、`IMusicService`、`SunoMusicService`、Suno DTO、registry/FreeAiService/Spring Boot 配置入口和 deterministic tests。
- 不做什么：不做 ElevenLabs、视频、通用 media task 抽象、Suno uploads/concat/persona/stems 全量接口，也不运行真实付费生成。
- 主要风险：ChatFire Suno 是异步任务接口，真实任务状态/结果可能随 provider 变化；本轮以文档契约与 MockWebServer 固定本地回归。

## 预算选择

选择预算：standard

选择理由：跨 core SDK、starter、测试和回归文档，但范围固定、接口数量少、无需长程 worker。

## 上下文包（Context Packet）

| ID | 类型 | 路径 | 为什么需要 | 使用者 |
| --- | --- | --- | --- | --- |
| C-001 | external | URL:https://api.chatfire.cn/doc | ChatFire 文档入口，指向 Apifox 文档站 | coordinator / reviewer |
| C-002 | external | URL:https://oneapis.apifox.cn/246593467e0 | Suno submit music API：`POST /suno/submit/music` | coordinator / reviewer |
| C-003 | external | URL:https://oneapis.apifox.cn/246605116e0 | Suno submit lyrics API：`POST /suno/submit/lyrics` | coordinator / reviewer |
| C-004 | external | URL:https://oneapis.apifox.cn/246600101e0 | Suno fetch API：`GET /suno/fetch/{task_id}` | coordinator / reviewer |
| C-005 | code | TARGET:ai4j/src/main/java/io/github/lnyocly/ai4j/service/factory/AiService.java | 平台服务入口模式 | coordinator |
| C-006 | code | TARGET:ai4j-spring-boot-starter/src/main/java/io/github/lnyocly/ai4j/AiConfigAutoConfiguration.java | starter 配置绑定模式 | coordinator |

## 步骤

1. 文档确认：从 ChatFire/Apifox 提取 Suno endpoints、字段、响应形态，记录到 `findings.md`。
2. Core 实现：新增 SunoConfig、service interface/implementation、DTO、PlatformType/registry/FreeAiService 入口。
3. Starter 实现：新增 `SunoConfigProperties`，更新 auto-configuration 和 multi-platform 属性绑定。
4. 回归：补 MockWebServer core tests、starter binding tests，运行 targeted/core/starter/package gates。
5. 收口：更新 Regression SSoT、Cadence Ledger、review/walkthrough/progress。

## 验收标准

- [x] MockWebServer 证明 `POST /suno/submit/music` 请求体包含 `prompt/tags/mv/title` 与 snake_case 可选字段。
- [x] MockWebServer 证明 `POST /suno/submit/lyrics` 和 `GET /suno/fetch/{task_id}` 的 Authorization 和 URL 正确。
- [x] `AiService`、`AiServiceRegistry`、`FreeAiService` 能以 `PlatformType.SUNO` 返回 `SunoMusicService`。
- [x] Spring Boot 单实例 `ai.suno.*` 与多实例 `ai.platforms[].platform=suno` 配置可绑定。
- [x] 本地回归通过，live provider 验证未授权时记录为 opt-in residual。

## 工作树（Worktree）

- 路径：`.worktrees/feature/suno-music-service`
- 分支：`feature/suno-music-service`
- Worker owner：coordinator
- Worker handoff commit required：yes
- Coordinator integration branch：`feat/per-node-latency`
- 未使用 worktree 的原因：不适用

## 长程任务判定

- 是否属于长程任务：否
- 若是，合同文件：不适用
- 连续执行权限：不适用
- Stop Condition 摘要：若需真实 provider key 或付费任务，停止并记录 live-provider opt-in residual。

## 审查判定

- 是否需要对抗性审查：否（self-review + deterministic regression 足够）
- 若是，报告文件：`review.md`
- Reviewer：self
- No-finding 要求：实现/测试/配置/文档一致，无 open material finding。

## 关联

- 相关 Regression Gate：RG-001、RG-005、RG-007；live residual 对应 LV-001。
- 审查报告：`review.md`
- Generated Ledger：由 lifecycle CLI / `harness governance rebuild` 重建
- 前置任务：SRB-060 ChatFire OpenAI videos service（同一 ChatFire media integration 背景）

## 模块关联（启用模块并行时填写）

- Module：core-sdk
- Step：provider media connector slice
- Module Plan：`coding-agent-harness/planning/modules/core-sdk/module_plan.md`

## 协调者交接（Coordinator，启用模块并行时填写）

- Global sync owner：coordinator
- Global sync status：pending-coordinator-pass（实现与验证完成，待 task-review / PR merge）
- Registry update needed：core-sdk task registered by harness
- Harness Ledger update needed：task plan path + closeout status
- Closeout / Regression update needed：`docs/05-TEST-QA/Regression-SSoT.md`、`docs/05-TEST-QA/Cadence-Ledger.md`

## Module Preset

This module task was created through the `module` preset.

| Field | Value |
| --- | --- |
| Module Key | core-sdk |

## Module Context Entry Points

Read these module-level entry points before changing shared module behavior. Continue into narrower context only when the task surface requires it.

| Reference | Path | Why / When |
| --- | --- | --- |
| Module brief | coding-agent-harness/planning/modules/core-sdk/brief.md | Start here for the module purpose and current scope. |
| Module plan | coding-agent-harness/planning/modules/core-sdk/module_plan.md | Use this for module steps, active task links, and handoff state. |
| Module visual map | coding-agent-harness/planning/modules/core-sdk/visual_map.md | Inspect when the change affects module sequencing or dependencies. |
