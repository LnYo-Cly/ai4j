# 收口记录：RAG query planner pre retrieval

## 摘要

本任务为 Core SDK RAG 增加可选的检索前 Query Planning 能力。默认 RAG 调用保持原行为；显式配置 `RagQueryPlanner` 后，`DefaultRagService` 会在 retriever 前生成并执行 rewrite、multi-query、HyDE、step-back 等 query variants，内部融合多 variant 命中，并保留原始 query 用于 rerank、context assembly 和 result。

## 范围

| 范围 | 详情 |
| --- | --- |
| 变更模块 | `ai4j` core RAG；`docs-site` Search & RAG 文档；Regression governance |
| 新增文件 | `RagQueryPlanner.java`、`RagQueryPlan.java`、`RagQueryVariant.java`、`RagQueryVariantType.java`、`ModelRagQueryPlanner.java`、`ModelRagQueryPlannerTest.java`、`docs-site/docs/core-sdk/search-and-rag/query-planning.md` |
| 删除文件 | 无 |
| 不在范围内 | Agent/tool routing、Spring Boot 自动配置、默认启用 LLM planner、真实 provider live 调用 |

## 验证

| 检查 | 命令或过程 | 结果 | 证据 |
| --- | --- | --- | --- |
| Targeted RAG tests | `mvn -pl ai4j "-Dtest=DefaultRagServiceTest,ModelRagQueryPlannerTest,HybridRetrieverTest" -DskipTests=false test` | PASS, 11 tests | `progress.md` 06:16 |
| RG-001 core SDK | `mvn -pl ai4j -am -DskipTests=false test` | PASS, 149 tests | `progress.md` 06:16 |
| RG-008 docs-site typecheck | `npm run typecheck` in `docs-site/` | PASS | `progress.md` 06:16 |
| RG-008 docs-site build | `npm run build` in `docs-site/` | PASS | `progress.md` 06:16 |
| RG-007 package smoke | `mvn -DskipTests package` | PASS, 11 reactor projects | `progress.md` 06:16 |
| Diff hygiene | `git diff --check` | PASS, no whitespace errors | `progress.md` final entry |

## 审查结论

| 来源 | 重要发现 | 处理 | 证据 |
| --- | --- | --- | --- |
| Self review | 0 open material findings | Ready for PR after lifecycle review submission | `review.md` |
| grill-me boundary check | 无阻塞发现；确认没有泛化为 Agent planner / Noop wrapper | 保持 RAG-only、可选启用、调用侧 `rag.search(RagQuery)` 不变 | `review.md` |

## 残余风险

| 风险 | Owner | 是否接受 | 跟进 |
| --- | --- | --- | --- |
| 未做真实 provider / 真实知识库质量评测 | 用户 / 后续任务 | yes | 需要 key、真实向量库和评测 query 集时另开 live smoke |
| 多 variants × HybridRetriever 组合可能带来成本和延迟 | SDK 使用者 | yes | docs-site 已提示限制 strategies/maxVariants 并仅在召回质量需要时启用 |

## 经验沉淀反思

| 问题 | 答案 |
| --- | --- |
| 是否完成经验候选检查？ | 是，`lesson_candidates.md` 标记 checked-none |
| 经验候选详情文件 | `lesson_candidates.md` |

## 收口链接

| 产物 | 链接 |
| --- | --- |
| 任务计划 | `task_plan.md` |
| 审查记录 | `review.md` |
| 进度记录 | `progress.md` |
| 发现记录 | `findings.md` |
| Docs-site 页面 | `docs-site/docs/core-sdk/search-and-rag/query-planning.md` |
| Regression gates | `docs/05-TEST-QA/Regression-SSoT.md` RG-001 / RG-007 / RG-008 |
| Cadence row | `docs/05-TEST-QA/Cadence-Ledger.md` SRB-062 |

## Git / PR

| 项 | 值 |
| --- | --- |
| Branch | `feature/rag-query-planner-rag-only` |
| Base | `feat/per-node-latency` |
| Implementation commit | pending |
| PR | pending |
