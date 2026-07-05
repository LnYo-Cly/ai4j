# rag retrieval usage docs

## Task ID

`2026-07-05-rag-retrieval-usage-docs-3f2d7401`

## 创建日期

2026-07-05

## 一句话结果

补清 RAG 中 Dense、BM25、HybridRetriever 的最短使用方式和与 Query Planning 的成本关系。

## 完成后能得到什么

用户不看源码也能知道：`AiService.getRagService(platform, vectorStore)` 默认是 dense embedding 检索；BM25 要显式提供内存 corpus；Dense + BM25 要自己组合 `HybridRetriever`；如果再叠加 `RagQueryPlanner`，成本会变成 `query variants × retrievers`。本任务只补 docs-site，不新增 API。

## 交付物

- 可见产物：`Hybrid Retrieval` 页面新增最短用法、决策表和 Query Planning 成本说明；overview 新增入口提示。
- 修改位置：`docs-site/docs/core-sdk/search-and-rag/hybrid-retrieval.md`、`overview.md`、RG/SRB 记录。
- 验证证据：`progress.md` E-001 至 E-003。

## 第一眼应该看什么

1. `docs-site/docs/core-sdk/search-and-rag/hybrid-retrieval.md` 的 `3.1 最短怎么用`。
2. `docs-site/docs/core-sdk/search-and-rag/overview.md` 默认 RAG 段落。
3. `docs/05-TEST-QA/Cadence-Ledger.md` SRB-064。

## 边界

- 范围内：docs-site RAG retrieval 用法说明、RG-008/SRB-064 证据。
- 范围外：新增 `getHybridRagService(...)`、新增 `RetrievalStrategy`、改 Java 生产代码。
- 停止条件：需要新增 API 或修改 RAG 行为时停止确认。

## 完成判断

- 文档清楚展示默认 dense、BM25、Dense + BM25 hybrid 三种写法。
- 文档说明 Query Planning 与 Hybrid 叠加的乘法成本。
- docs-site typecheck/build 和 diff check 通过。

## 执行合同

- Owner：coordinator
- 生命周期状态：进行中
- 必需文件：`INDEX.md`、`task_plan.md`、`visual_map.md`、`progress.md`、`walkthrough.md`
- 完成条件：验证证据必须记录到 `progress.md`

## 当前下一步

提交 docs 分支并创建 PR。
