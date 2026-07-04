# rag query planner strategy prompts

## Task ID

`2026-07-05-rag-query-planner-strategy-prompts-ddd48298`

## 创建日期

2026-07-05

## 一句话结果

`ModelRagQueryPlanner` 默认只做 rewrite；显式启用 multi-query / HyDE / step-back 时按策略分别调用模型 prompt。

## 完成后能得到什么

RAG query planning 的默认路径更轻：`aiService.getModelRagQueryPlanner(platform, model)` 只产生一条 rewrite 检索变体，不再一次 prompt 生成所有策略。需要多策略时，调用方仍用既有重载传入 `RagQueryVariantType` 列表；实现会按策略分别请求模型，避免把 rewrite、multi-query、HyDE、step-back 混在同一个 prompt 目标里。docs-site 同步说明默认行为、显式多策略成本，以及与 HybridRetriever 多路召回的边界。

## 交付物

- 可见产物：per-strategy model planner 行为、更新后的 Query Planning 文档。
- 修改位置：`ai4j/src/main/java/io/github/lnyocly/ai4j/rag/ModelRagQueryPlanner.java`、`ModelRagQueryPlannerTest.java`、`docs-site/docs/core-sdk/search-and-rag/`。
- 验证证据：`progress.md` 中 E-002 至 E-005。

## 第一眼应该看什么

1. `ModelRagQueryPlannerTest.defaultPlannerShouldOnlyCallRewriteStrategy`
2. `ModelRagQueryPlannerTest.shouldCallEachEnabledStrategyWithDedicatedPrompt`
3. `docs-site/docs/core-sdk/search-and-rag/query-planning.md`
4. `docs/05-TEST-QA/Cadence-Ledger.md` SRB-063

## 边界

- 范围内：修正模型 planner 默认策略、显式多策略 prompt 执行、测试、docs-site、RG/SRB 证据。
- 范围外：新增 `RetrievalStrategy`、新增 hybrid 便利 API、改 `AiService` 公共入口、实现非 chat 协议 planner。
- 停止条件：若现有 `IChatService` 无法表达需求或需要新增公共 API，则停止回到用户确认。

## 完成判断

- 默认构造的 `ModelRagQueryPlanner` 只发起一次 rewrite prompt。
- 显式传多策略时每个策略独立 prompt，并只接收对应类型结果。
- RAG 定向测试、core 全量测试、docs-site type/build、monorepo package 均通过。
- Regression SSoT 与 Cadence Ledger 已记录本轮证据。

## 执行合同

- Owner：coordinator
- 生命周期状态：进行中
- 必需文件：`INDEX.md`、`task_plan.md`、`visual_map.md`、`progress.md`、`walkthrough.md`
- 完成条件：验证证据必须记录到 `progress.md`

## 当前下一步

提交实现分支并创建 PR。
