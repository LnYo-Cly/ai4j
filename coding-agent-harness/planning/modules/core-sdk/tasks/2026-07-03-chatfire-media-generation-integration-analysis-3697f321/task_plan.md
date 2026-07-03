# ChatFire media generation integration analysis

Task Contract: harness-task/v1
Task Kind: module-task
Task Preset: module
Preset Version: 1
Evidence Bundle: coding-agent-harness/planning/modules/core-sdk/tasks/2026-07-03-chatfire-media-generation-integration-analysis-3697f321/artifacts/preset/2026-07-03T10-21-28-839Z
Task Package Index: required

## 目标

分析 ai4j-sdk 当前 provider/audio/image 架构与 ChatFire Videos/Suno/ElevenLabs API，给出最小可落地接入方案。

## 范围

- 做什么：读取 core-sdk 现有服务边界；拉取 ChatFire Apifox Markdown 文档；输出任务本地接入分析报告。
- 不做什么：本轮不改生产代码、不新增 provider、不调用 live ChatFire 付费接口。
- 主要风险：ChatFire 文档仍在更新；部分响应示例字段不一致，第一版实现必须保留 raw/extra 字段。

## 预算选择

选择预算：simple

选择理由：本轮是接入分析和方案选择，不是代码实现；产物为任务本地 report。

## 上下文包（Context Packet）

| ID | 类型 | 路径 | 为什么需要 | 使用者 |
| --- | --- | --- | --- | --- |
| C-001 | code | TARGET:ai4j/src/main/java/io/github/lnyocly/ai4j/service | 现有 Chat/Image/Audio/Responses 服务边界 | coordinator |
| C-002 | code | TARGET:ai4j/src/main/java/io/github/lnyocly/ai4j/platform/openai | 现有 OpenAI-compatible provider 实现模式 | coordinator |
| C-003 | external | URL:https://api.chatfire.cn/doc | ChatFire 文档入口，实际嵌入 Apifox `https://oneapis.apifox.cn` | coordinator |
| C-004 | report | TARGET:coding-agent-harness/planning/modules/core-sdk/tasks/2026-07-03-chatfire-media-generation-integration-analysis-3697f321/references/chatfire-media-integration-analysis.md | 本轮接入结论和后续实现建议 | coordinator / reviewer |

## 步骤

1. 检查 core-sdk 现有 audio/image/service factory/config 边界。
2. 拉取 ChatFire Apifox Markdown，确认统一 `/v1/videos` 与平台原生格式差异。
3. 写入任务本地分析报告并收口。

## 验收标准

- [x] 明确 Video 应新增的最小 SDK 面。
- [x] 区分 OpenaiVideos 统一格式、Fal/Doubao/Kling/MiniMax 原生格式、Suno、ElevenLabs。
- [x] 记录不做项和后续测试建议。

## 工作树（Worktree）

- 路径：不适用
- 分支：当前工作树
- Worker owner：coordinator
- Worker handoff commit required：不适用
- Coordinator integration branch：不适用
- 未使用 worktree 的原因：分析任务只写任务本地 harness 材料，不改生产代码。

## 长程任务判定

- 是否属于长程任务：否
- 若是，合同文件：不适用
- 连续执行权限：不适用
- Stop Condition 摘要：完成 report 或发现文档不可访问即停。

## 审查判定

- 是否需要对抗性审查：否
- 若是，报告文件：不适用
- Reviewer：self
- No-finding 要求：不适用

## 关联

- 相关 Regression Gate：无生产代码变更；无需新增固定 regression surface。
- 审查报告：不适用
- Generated Ledger：由 lifecycle CLI / `harness governance rebuild` 重建
- 前置任务：T-ANTHROPIC-NATIVE-MESSAGES-SURFACE-5914B973

## 模块关联（启用模块并行时填写）

- Module：core-sdk
- Step：T-CHATFIRE-MEDIA-GENERATION-INTEGRATION-ANALYSIS-3
- Module Plan：coding-agent-harness/planning/modules/core-sdk/module_plan.md

## 协调者交接（Coordinator，启用模块并行时填写）

- Global sync owner：coordinator
- Global sync status：synced
- Registry update needed：core-sdk step 可在后续实现任务中改为 merged
- Harness Ledger update needed：task plan path 和 closeout status 已在任务包内记录
- Closeout / Regression update needed：本轮无 Regression SSoT 更新

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
