# Memory Compact Session API polish - 发现记录

本文件记录任务执行中形成的判断、事实和技术决策。它不是审查报告；阻塞性问题请写入 `review.md`。

## 研究发现

### 现有能力已经能 compact，但使用成本偏高

- 背景：当前用户需要理解 `ContextBudget`、`StructuredSummaryCompactPolicy`、`CompactResult` 和 `AgentSession.getLastCompactResult()` 才能完成常见 compact。
- 发现：`AgentSession.compact(CompactPolicy)` 已存在，但只返回 session 自身；诊断数据需要再调用 `getLastCompactResult()` 并解析 `ContextReport`。
- 影响：对“简化 Java Agent SDK 接入成本”的目标不够友好。
- 后续：新增 `SessionCompactPlan` 和 `SessionCompactReport`，把常见 compact 写法变成 session-first API。

### 不应引入模型驱动摘要

- 背景：用户提供了 token，但本任务目标是 SDK API polish，不是验证真实模型效果。
- 发现：现有 `StructuredSummaryCompactPolicy` 是确定性策略，可以本地测试，不需要 provider key。
- 影响：本任务不使用、不记录、不提交任何 token。
- 后续：真实 LLM compact policy 可作为插件/后续任务。

## 技术决策

| 决策 | 选择 | 原因 | 替代方案 | 状态 |
| --- | --- | --- | --- | --- |
| 新 API 形态 | `SessionCompactPlan` + `SessionCompactReport` | 使用者心智最直接，后续 CLI/TUI 也可复用 report | 只加 `CompactPolicy` helper | accepted |
| 原 API 兼容 | 保留 `compact(CompactPolicy)` 返回 `AgentSession` | 避免破坏已有代码 | 改返回值为 report | rejected |
| 测试策略 | JUnit4 deterministic local tests | 不依赖真实 provider/token | live model test | accepted |

## 待确认问题

| 问题 | 当前判断 | Owner | 截止点 |
| --- | --- | --- | --- |
| 是否把 report 放入 snapshot？ | 不新增字段；snapshot 已保存 `CompactResult`，report 可从 result 构造 | coordinator | 本任务实现前 |
| 是否同步 CLI `/compact`？ | 不做，后续 cli-host 任务 | coordinator | 本任务收口 |
