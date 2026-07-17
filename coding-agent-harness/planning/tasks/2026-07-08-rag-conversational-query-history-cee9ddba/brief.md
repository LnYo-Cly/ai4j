# rag conversational query history

## Task ID

`2026-07-08-rag-conversational-query-history-cee9ddba`

## 创建日期

2026-07-08

## 一句话结果

RAG Query Planning 现在可以可选读取 `RagQuery.history`，用已有 `ChatMemoryItem` 支持多轮对话下的检索前 query rewrite。

## 完成后能得到什么

用户可以把 core `ChatMemory` / `InMemoryChatMemory` 的 `getItems()` 直接传给 `RagQuery.history(...)`。`ModelRagQueryPlanner` 会在 rewrite、multi-query、HyDE、step-back 等策略 prompt 中加入这些历史内容，从而把“那 Suno 呢？”这类追问改写成更完整的检索 query。实现没有新增 `RagMemory`，也没有改动 agent runtime 的 `AgentMemory`。

## 交付物

- 可见产物：`RagQuery.history(List<ChatMemoryItem>)`、history-aware planner prompt、docs-site Query Planning 示例。
- 修改位置：`ai4j/src/main/java/io/github/lnyocly/ai4j/rag/`、`ai4j/src/test/java/io/github/lnyocly/ai4j/rag/`、`docs-site/docs/core-sdk/search-and-rag/query-planning.md`。
- 验证证据：`progress.md` 记录 targeted/core/docs/package gates。

## 第一眼应该看什么

先看 `walkthrough.md` 的摘要和验证表，再看 `progress.md` 的命令证据；代码入口看 `RagQuery.java` 与 `ModelRagQueryPlanner.java`。

## 边界

- 范围内：RAG core query history 字段、planner prompt、planned query 复制、测试、docs-site、Regression/Cadence 记录。
- 范围外：`AgentMemory` 重构、RAG 专用 memory、live provider 质量评估、Spring starter 配置。
- 停止条件：本地 deterministic gates 失败、需要 live provider 凭证、或发现需要跨 agent runtime 重构。

## 完成判断

- `RagQuery` 复用 core `ChatMemoryItem`，没有新增第三套 RAG memory。
- `ModelRagQueryPlannerTest` 覆盖 history 进入 prompt。
- `DefaultRagServiceTest` 覆盖 planned variants 保留 history。
- docs-site 展示 `InMemoryChatMemory` 使用方式。
- RG-001、RG-007、RG-008 证据已记录。

## 执行合同

- Owner：coordinator
- 生命周期状态：已完成
- 必需文件：`INDEX.md`、`task_plan.md`、`visual_map.md`、`progress.md`、`walkthrough.md`
- 完成条件：验证证据已记录到 `progress.md`，收口见 `walkthrough.md`

## 当前下一步

等待 PR 创建、CI/检查通过后合并并清理 worktree。
