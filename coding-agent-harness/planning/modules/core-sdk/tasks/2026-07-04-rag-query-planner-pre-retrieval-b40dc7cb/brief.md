# RAG query planner pre retrieval

## Task ID

`2026-07-04-rag-query-planner-pre-retrieval-b40dc7cb`

## 创建日期

2026-07-04

## 一句话结果

为 Core SDK RAG 增加可选的检索前 query planning：默认不启用，显式配置后支持 rewrite、multi-query、HyDE、step-back 等 query variants，并由 `DefaultRagService` 内部执行和融合。

## 完成后能得到什么

用户继续调用 `rag.search(RagQuery)`，不需要在调用侧手动包一层 retriever。需要检索前处理时，可以传入自定义 `RagQueryPlanner`，也可以用 `AiService.getModelRagQueryPlanner(...)` 创建 LLM-backed planner。原 query 会保留给 rerank、context assembly 和 result trace；planner 失败会回退原 query，避免 RAG 主链路被额外 LLM 调用拖垮。

## 交付物

- 可见产物：RAG query planner API、模型 planner、DefaultRagService planner 执行/fusion、docs-site Query Planning 页面。
- 修改位置：`ai4j/src/main/java/io/github/lnyocly/ai4j/rag/`、`ai4j/src/main/java/io/github/lnyocly/ai4j/service/factory/AiService.java`、`docs-site/docs/core-sdk/search-and-rag/`。
- 验证证据：`progress.md` 中 E-001/E-002，包含 RAG 定向、core 全量、docs-site typecheck/build、monorepo package smoke。

## 第一眼应该看什么

1. `ai4j/src/main/java/io/github/lnyocly/ai4j/rag/DefaultRagService.java`
2. `ai4j/src/main/java/io/github/lnyocly/ai4j/rag/ModelRagQueryPlanner.java`
3. `docs-site/docs/core-sdk/search-and-rag/query-planning.md`
4. `ai4j/src/test/java/io/github/lnyocly/ai4j/rag/DefaultRagServiceTest.java`
5. `ai4j/src/test/java/io/github/lnyocly/ai4j/rag/ModelRagQueryPlannerTest.java`

## 边界

- 范围内：RAG-only 检索前 query planning；rewrite / multi-query / HyDE / step-back 的 plan 表达、执行、trace、docs-site 说明。
- 范围外：Agent/tool 路由、Spring Boot 自动装配、全局默认启用、真实 provider live 调用。
- 停止条件：如果需要改变 agent/workflow 抽象或默认所有 RAG 自动调用模型，必须回到用户确认。

## 完成判断

- `DefaultRagService` 无 planner 时行为保持兼容。
- 有 planner 时，planner 在 retriever 前执行，多 variant 命中会融合，rerank/assembler 仍使用原 query。
- `ModelRagQueryPlanner` 可开箱生成常见策略 variants，且可由用户替换为自定义实现。
- docs-site 有 Query Planning 页面并进入 Search & RAG sidebar。
- RG-001、RG-007、RG-008 本地 gate 通过并记录。

## 执行合同

- Owner：coordinator
- 生命周期状态：进行中
- 必需文件：`INDEX.md`、`task_plan.md`、`execution_strategy.md`、`visual_map.md`、`progress.md`、`findings.md`、`review.md`、`walkthrough.md`
- 完成条件：验证证据必须记录到 `progress.md`

## 当前下一步

提交 PR，等待/检查 CI，通过后合并并清理 feature worktree/branch。
