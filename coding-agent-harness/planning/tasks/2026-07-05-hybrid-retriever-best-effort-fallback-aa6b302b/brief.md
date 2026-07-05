# hybrid retriever best effort fallback

## Task ID

`2026-07-05-hybrid-retriever-best-effort-fallback-aa6b302b`

## 创建日期

2026-07-05

## 一句话结果

`HybridRetriever` 支持最小生产级 best-effort 降级：单路子检索失败不再拖垮整次 hybrid 检索。

## 完成后能得到什么

RAG Dense + BM25 等多路召回场景下，如果某一路子 `Retriever` 抛异常，`HybridRetriever` 会跳过失败路并返回其他成功路的融合结果；只有所有非空子检索器都失败时才抛出第一个异常。用户侧仍然使用原来的 `rag.search(...)` / `new HybridRetriever(...)` 形态，不新增 public API、不引入策略对象或配置负担。

## 交付物

- 可见产物：HybridRetriever best-effort fallback 行为、单测覆盖、docs-site hybrid retrieval 说明。
- 修改位置：`ai4j/src/main/java/io/github/lnyocly/ai4j/rag/HybridRetriever.java`、`ai4j/src/test/java/io/github/lnyocly/ai4j/rag/HybridRetrieverTest.java`、`docs-site/docs/core-sdk/search-and-rag/hybrid-retrieval.md`。
- 验证证据：`progress.md` 记录 targeted/core/docs/package/diff hygiene gate。

## 第一眼应该看什么

1. `HybridRetriever.java` 的 per-child try/catch 和 all-failed throw 逻辑。
2. `HybridRetrieverTest` 的 3 个新增 fallback 回归。
3. docs-site `hybrid-retrieval.md` 的 “子检索器失败时怎么降级” 小节。
4. `walkthrough.md` 的验证表。

## 边界

- 范围内：`HybridRetriever` 单路失败降级、对应单测、docs-site 行为说明、RG-001/RG-007/RG-008 证据更新。
- 范围外：`FallbackRetriever`、`RetrievalFailurePolicy`、timeout/retry/circuit breaker、metrics、并行检索、新 public API。
- 停止条件：如果需要暴露新配置或改变 `RagService` public API，停止并回到设计讨论。

## 完成判断

- `HybridRetriever` 至少一路子检索成功时返回成功结果。
- 所有非空子检索器失败时抛出第一个异常。
- 成功但返回空结果的子检索器被视为成功，不误抛异常。
- docs-site 明确说明 best-effort 边界和未做 retry/timeout/circuit breaker。
- RG-001、RG-007、RG-008 本地 gate 通过并记录。

## 执行合同

- Owner：coordinator
- 生命周期状态：已完成
- 必需文件：`INDEX.md`、`task_plan.md`、`visual_map.md`、`progress.md`、`walkthrough.md`
- 完成条件：验证证据已记录到 `progress.md`

## 当前下一步

提交实现与任务材料，创建 PR 到 `feat/per-node-latency`，等待 CI 后合并并清理 worktree。
