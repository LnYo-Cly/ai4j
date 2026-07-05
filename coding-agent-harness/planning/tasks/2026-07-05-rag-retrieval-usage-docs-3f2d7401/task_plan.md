# rag retrieval usage docs

Task Contract: harness-task/v1
Task Package Index: required

## 目标

补清 RAG 召回层 Dense、BM25、HybridRetriever 的实际使用方式。

## 范围

- 做什么：增强 docs-site Hybrid Retrieval/overview 文档，更新 RG-008/SRB-064。
- 不做什么：不改 Java API，不新增 hybrid 工厂，不实现生产级 BM25 后端。
- 主要风险：示例写得太重；本任务只保留最短可用代码和链接。

## 预算选择

选择预算：simple

选择理由：纯文档补充，验证只需 docs-site build 与 diff hygiene。

## 上下文包（Context Packet）

| ID | 类型 | 路径 | 为什么需要 | 使用者 |
| --- | --- | --- | --- | --- |
| C-001 | docs | TARGET:docs-site/docs/core-sdk/search-and-rag/hybrid-retrieval.md | 主要补充位置 | coordinator |
| C-002 | docs | TARGET:docs-site/docs/ai-basics/rag/hybrid-retrieval-and-rerank-workflow.md | 已有完整长流程，core 文档只链接不复制 | coordinator |
| C-003 | code | TARGET:ai4j/src/main/java/io/github/lnyocly/ai4j/service/factory/AiService.java | 确认默认 RAG 是 DenseRetriever | coordinator |

## 步骤

1. 在 Hybrid Retrieval 文档补默认 dense、BM25、hybrid 三种短写法。
2. 补 Query Planning × retrievers 成本说明。
3. 在 overview 默认 RAG 段落加 hybrid 入口提示。
4. 跑 docs-site typecheck/build 和 diff check。

## 验收标准

- [x] 文档明确默认 `getRagService` 是 dense retrieval。
- [x] 文档给出 BM25 和 Dense + BM25 hybrid 最短代码。
- [x] 文档说明 query variants × retrievers 成本。
- [x] RG-008/SRB-064 记录本轮 docs 证据。

## 工作树（Worktree）

- 路径：`.worktrees/docs/rag-retrieval-usage`
- 分支：`docs/rag-retrieval-usage`
- Worker owner：coordinator
- Worker handoff commit required：不适用
- Coordinator integration branch：`feat/per-node-latency`
- 未使用 worktree 的原因：不适用，已使用隔离 worktree。

## 长程任务判定

- 是否属于长程任务：否
- 若是，合同文件：不适用
- 连续执行权限：不适用
- Stop Condition 摘要：需要新增 API 或改生产代码时停止确认。

## 审查判定

- 是否需要对抗性审查：否
- 若是，报告文件：不适用
- Reviewer：self
- No-finding 要求：docs-site build 通过。

## 关联

- 相关 Regression Gate：RG-008
- 审查报告：不适用（simple task self-check）
- Generated Ledger：由 lifecycle CLI 重建
- 前置任务：PR #177 RAG query planner strategy prompts

## 模块关联（启用模块并行时填写）

- Module：不适用
- Step：不适用
- Module Plan：不适用

## 协调者交接（Coordinator，启用模块并行时填写）

- Global sync owner：coordinator
- Global sync status：synced
- Registry update needed：不适用
- Harness Ledger update needed：已由 lifecycle CLI 同步
- Closeout / Regression update needed：`docs/05-TEST-QA/Regression-SSoT.md`、`docs/05-TEST-QA/Cadence-Ledger.md` 已更新
