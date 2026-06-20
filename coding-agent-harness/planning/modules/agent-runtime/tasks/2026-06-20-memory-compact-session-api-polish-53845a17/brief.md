# Memory Compact Session API polish

## Task ID

`2026-06-20-memory-compact-session-api-polish-53845a17`

## 创建日期

2026-06-20

## 一句话结果

为 `AgentSession` 增加 session-first compact API：普通 Java 用户可以用 `SessionCompactPlan` 发起 compact，并直接得到可给 UI/CLI/日志展示的 `SessionCompactReport`。

## 完成后能得到什么

完成后，`ai4j-agent` 的 Memory / Compact 能力不再只面向熟悉 `CompactPolicy`、`ContextBudget` 和 `CompactResult` 的高级用户。开发者可以从 `AgentSession` 出发，用 `SessionCompactPlan.keepRecentItems(...).withPinnedPrefixItems(...)` 表达常见长会话压缩策略，并通过 `SessionCompactReport` 读取本次 compact 是否执行、保留/丢弃数量、summary 与 `ContextReport`。docs-site 同步给出真实 API 示例，后续 CLI `/compact`、TUI 状态面板和远端 runner 事件流也可以复用这个 report 形态。

## 交付物

- 可见产物：`SessionCompactPlan`、`SessionCompactReport`、`AgentSession.compact(SessionCompactPlan)`、`AgentSession.compactAndReport(CompactPolicy)`。
- 修改位置：`ai4j-agent/src/main/java/io/github/lnyocly/ai4j/agent/**`、`ai4j-agent/src/test/java/io/github/lnyocly/agent/AgentMemoryCompactContextProjectorTest.java`、`docs-site/docs/agent/*`、本 Harness task package、`docs/05-TEST-QA/*`。
- 验证证据：targeted JUnit、agent module broad test、docs-site build、diff hygiene、Harness status、token scan。

## 第一眼应该看什么

1. `ai4j-agent/src/main/java/io/github/lnyocly/ai4j/agent/compact/SessionCompactPlan.java`
2. `ai4j-agent/src/main/java/io/github/lnyocly/ai4j/agent/compact/SessionCompactReport.java`
3. `ai4j-agent/src/main/java/io/github/lnyocly/ai4j/agent/AgentSession.java`
4. `docs-site/docs/agent/memory-compact-context.md`
5. `review.md` 和 `progress.md` 中的验证证据。

## 边界

- 范围内：session compact 易用 API、诊断 report、防御性复制测试、docs-site 真实 API 示例、Regression SSoT / Cadence Ledger 证据刷新。
- 范围外：CLI `/compact`、模型驱动摘要策略、真实 provider/token 调用、`ai4j-coding` checkpoint、额外 Maven 模块。
- 停止条件：如果需要真实模型 compact、CLI/TUI runtime 行为或 public API 破坏性改名，停止并另开任务。

## 完成判断

- [x] `session.compact(SessionCompactPlan.keepRecentItems(...))` 可返回 `SessionCompactReport`。
- [x] 原有 `session.compact(CompactPolicy)` 返回 `AgentSession` 的兼容行为保留。
- [x] report 提供 session id、summary、source/projected/dropped counts、`ContextReport` 和防御性 copy。
- [x] docs-site 示例使用真实 API。
- [x] 回归证据记录到 `progress.md` / `review.md` / `walkthrough.md`。

## 执行合同

- Owner：coordinator
- 生命周期状态：进行中
- 必需文件：`INDEX.md`、`task_plan.md`、`execution_strategy.md`、`visual_map.md`、`progress.md`、`findings.md`、`review.md`
- 完成条件：提交 PR 前必须通过 targeted Maven、agent broad test、docs build、Harness status 和 token scan。

## 当前下一步

运行最终验证，提交代码与 Harness 材料，然后通过 `task-review` 进入人工确认队列并创建 PR。
