# rag incremental ingest content hash

Task Contract: harness-task/v1
Task Package Index: required

## 目标

在 RAG ingestion 中提供 opt-in 的内容 hash 增量跳过能力，减少重复文档导入时不必要的 embedding 和 upsert。

## 范围

- 做什么：
  - 为 chunk metadata 自动写入 `contentHash`。
  - 在 `IngestionRequest` 上增加 `skipExistingContentHash`。
  - 在 `IngestionResult` 上增加 `skippedCount`。
  - 给 `VectorStore` 增加默认 false 的 metadata-only `exists` 能力和 `metadataLookup` capability。
  - 为 Qdrant、Milvus、PgVector、Redis 实现最小 exists；Pinecone 保持默认 false。
  - 补 starter 配置、docs-site 和回归证据。
- 不做什么：
  - 不实现 embedding cache。
  - 不实现完整增量索引/删除旧版本/重建任务系统。
  - 不新增 retry、timeout、fallback policy、circuit breaker 公共 API。
  - 不运行真实后端 smoke。
- 主要风险：
  - 后端 metadata lookup API 形态不同，必须保持可选能力和 fail-open。
  - Lombok all-args 字段新增可能破坏源码兼容，因此补旧签名构造器。
  - Redis 只有被建成 TAG/NUMERIC 的字段能过滤，默认只补 `contentHash` TAG。

## 预算选择

选择预算：simple

选择理由：这是单一 RAG 入库缺口的窄实现；无需新增架构层或多 agent 并行，但需要跨 core、starter、docs-site 和回归表收口。

## 上下文包（Context Packet）

| ID | 类型 | 路径 | 为什么需要 | 使用者 |
| --- | --- | --- | --- | --- |
| C-001 | code | TARGET:ai4j/src/main/java/io/github/lnyocly/ai4j/rag/ingestion/IngestionPipeline.java | 入库主流程，决定 skip 发生在 embedding 前还是后 | coordinator |
| C-002 | code | TARGET:ai4j/src/main/java/io/github/lnyocly/ai4j/vector/store | 各向量后端能力和 filter/lookup 边界 | coordinator |
| C-003 | docs | TARGET:docs-site/docs/core-sdk/search-and-rag | 用户使用说明与后端能力矩阵 | coordinator |
| C-004 | regression | TARGET:docs/05-TEST-QA/Regression-SSoT.md | 本轮 touched surface gates | coordinator |

## 步骤

1. 在 dedicated worktree 中实现 `contentHash`、skip、exists 和 backend capability。
2. 补 deterministic tests 覆盖跳过、fail-open、能力不可用、全跳过不 upsert、Qdrant/Milvus lookup 请求形态。
3. 更新 starter binding、docs-site、Regression SSoT/Cadence 和任务 closeout 材料。
4. 跑 core、starter、docs-site、package、diff hygiene。
5. 提交、PR、CI、merge、清理。

## 验收标准

- [x] `skipExistingContentHash` 默认关闭，现有 ingest 行为不变。
- [x] 开启 skip 且后端支持 metadata lookup 时，命中内容 hash 的 chunk 不 embedding、不 upsert。
- [x] lookup 失败或不支持时 fail-open，不阻断 ingest。
- [x] `IngestionResult.skippedCount` 返回跳过数，`records` 只包含实际待 upsert 的 chunk。
- [x] docs-site 明确这是轻量增量入库，不负责删除旧版本。
- [x] RG-001/RG-005/RG-007/RG-008 已运行并记录。

## 工作树（Worktree）

- 路径：`G:\My_Project\java\ai4j-sdk\.worktrees\feature\rag-incremental-ingest`
- 分支：`feature/rag-incremental-ingest`
- Worker owner：coordinator
- Worker handoff commit required：不适用
- Coordinator integration branch：`main`
- 未使用 worktree 的原因：不适用；已使用 dedicated worktree，主工作区保持不动。

## 长程任务判定

- 是否属于长程任务：否
- 若是，合同文件：不适用
- 连续执行权限：不适用
- Stop Condition 摘要：需要 live 后端凭据或删除旧版本语义时停止并另开任务。

## 审查判定

- 是否需要对抗性审查：否
- 若是，报告文件：不适用
- Reviewer：self
- No-finding 要求：`review.md` 记录自审，0 open material findings。

## 关联

- 相关 Regression Gate：RG-001、RG-005、RG-007、RG-008
- 审查报告：`review.md`
- Generated Ledger：由 lifecycle CLI / `harness governance rebuild` 重建
- 前置任务：用户确认优先做“增量 ingest”

## 模块关联（启用模块并行时填写）

- Module：不适用
- Step：不适用
- Module Plan：不适用

## 协调者交接（Coordinator，启用模块并行时填写）

- Global sync owner：coordinator
- Global sync status：n/a
- Registry update needed：不适用
- Harness Ledger update needed：task lifecycle CLI closeout
- Closeout / Regression update needed：`walkthrough.md`、Regression SSoT、Cadence Ledger
