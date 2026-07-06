# rag incremental ingest content hash

## Task ID

`2026-07-06-rag-incremental-ingest-content-hash-7112b274`

## 创建日期

2026-07-06

## 一句话结果

为 RAG 入库流水线增加轻量增量 ingest：每个 chunk 自动带 `contentHash`，用户显式开启后可跳过已存在内容，避免重复 embedding/upsert。

## 完成后能得到什么

SDK 使用者可以继续使用现有 `IngestionPipeline`，只在 `IngestionRequest` 上打开 `skipExistingContentHash(Boolean.TRUE)`，就能在支持 metadata lookup 的向量后端中按 chunk 内容 hash 跳过重复入库。结果对象会返回 `skippedCount`，便于 demo、后台任务和企业知识库导入页展示本次实际新增与跳过数量。实现保持最小边界：不做 embedding cache、不做全量索引框架、不删除旧版本，只解决“重复内容不要反复 embed/upsert”的高频增量入库缺口。

## 交付物

- 可见产物：`contentHash` metadata、`skipExistingContentHash` 请求开关、`skippedCount` 结果字段、`VectorStore.exists(...)` 可选能力、docs-site 使用说明。
- 修改位置：`ai4j/` RAG ingestion 与 vector store；`ai4j-spring-boot-starter/` vector lookup 配置绑定；`docs-site/` RAG 文档。
- 验证证据：`progress.md` 中记录的 core/starter/docs/package gates。

## 第一眼应该看什么

1. `ai4j/src/main/java/io/github/lnyocly/ai4j/rag/ingestion/IngestionPipeline.java`
2. `ai4j/src/test/java/io/github/lnyocly/ai4j/rag/IngestionPipelineTest.java`
3. `docs-site/docs/core-sdk/search-and-rag/ingestion-pipeline.md`
4. `review.md` 与 `walkthrough.md`

## 边界

- 范围内：content hash 增量跳过、后端 metadata-only exists、starter 配置、docs-site、回归记录。
- 范围外：embedding cache、增量删除/版本清理、重试/超时/熔断策略、独立索引任务调度框架、live vector backend smoke。
- 停止条件：如果需要真实后端凭据或要求删除旧版本，必须另开任务并获得用户确认。

## 完成判断

- `IngestionPipeline` 对每个 chunk 写入稳定 `contentHash`。
- `skipExistingContentHash=true` 时，仅在 `metadataLookup=true` 的后端调用 `exists`，命中则跳过 embedding/upsert。
- 查询失败或能力不可用时 fail-open，继续正常入库。
- Qdrant/Milvus/PgVector/Redis 暴露 metadata lookup；Pinecone 保持默认 false。
- core、starter、docs-site、package、本地 diff hygiene 均通过。

## 执行合同

- Owner：coordinator
- 生命周期状态：进行中
- 必需文件：`INDEX.md`、`task_plan.md`、`visual_map.md`、`progress.md`、`review.md`、`walkthrough.md`
- 完成条件：验证证据必须记录到 `progress.md`

## 当前下一步

提交实现与任务材料，创建 PR，等待 CI 后合并并清理 worktree。
