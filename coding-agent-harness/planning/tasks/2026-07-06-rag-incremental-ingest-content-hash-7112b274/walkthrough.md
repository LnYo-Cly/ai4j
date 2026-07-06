# 收口记录：rag incremental ingest content hash

## 摘要

完成轻量增量 ingest：`IngestionPipeline` 自动为 chunk 写入 `contentHash`，用户显式开启 `skipExistingContentHash` 后，在支持 metadata lookup 的后端中按 hash 跳过已存在 chunk，从而避免重复 embedding 和 upsert。实现保持在 RAG 入库边界内，没有引入 embedding cache、索引调度框架或删除旧版本语义。

## 范围

| 范围 | 详情 |
| --- | --- |
| 变更模块 | `ai4j` core RAG/vector；`ai4j-spring-boot-starter` config binding；`docs-site` RAG 文档；Regression SSoT/Cadence |
| 新增文件 | `ai4j/src/main/java/io/github/lnyocly/ai4j/vector/store/VectorExistsRequest.java`；任务本地 `review.md`、`findings.md`、`lesson_candidates.md` |
| 删除文件 | 无 |
| 不在范围内 | embedding cache、增量删除/版本 GC、真实后端 live smoke、fallback/retry/circuit-breaker API |

## 验证

| 检查 | 命令或过程 | 结果 | 证据 |
| --- | --- | --- | --- |
| Core targeted | `mvn -pl ai4j "-Dtest=IngestionPipelineTest,QdrantVectorStoreTest,MilvusVectorStoreTest" -DskipTests=false test` | PASS, 10 tests | `progress.md` 21:14 |
| RG-001 core | `mvn -pl ai4j -am -DskipTests=false test` | PASS, 142 tests | `progress.md` 21:15 |
| Starter targeted | `mvn -pl ai4j-spring-boot-starter -am "-Dtest=AiServiceFirstChatAutoConfigurationTest" -DfailIfNoTests=false -DskipTests=false test` | PASS, 2 tests | `progress.md` 21:33 |
| RG-005 starter | `mvn -pl ai4j-spring-boot-starter -am -DskipTests=false test` | PASS, extension API 26 / core 142 / starter 10 tests | `progress.md` 21:15 |
| RG-008 docs-site | `npm ci`; `npm run typecheck`; `npm run build` in `docs-site/` | PASS, static files generated in `build` | `progress.md` 21:30 |
| RG-007 package | `mvn -DskipTests package` | PASS, 11 reactor projects | `progress.md` 21:31 |
| Diff hygiene | `git diff --check` | PASS, no whitespace errors | `progress.md` 21:38 |

## 审查结论

| 来源 | 重要发现 | 处理 | 证据 |
| --- | --- | --- | --- |
| Self-review | 0 open material findings | Approved for PR after final diff hygiene | `review.md` |

## 残余风险

| 风险 | Owner | 是否接受 | 跟进 |
| --- | --- | --- | --- |
| 未连接真实 Qdrant/Milvus/PgVector/Redis 做 live smoke | user/operator | 接受 | 如需发布前真实后端证明，另开 credential-backed live smoke 任务 |
| Redis 自定义 tagFields 若不含 `contentHash`，增量 lookup 不生效 | application owner | 接受 | docs 已说明 metadata lookup 依赖被索引字段；默认含 `contentHash` |

## 经验沉淀反思

| 问题 | 答案 |
| --- | --- |
| 是否完成经验候选检查？ | 是 |
| 经验候选详情文件 | `lesson_candidates.md` |
| 是否沉淀全局 lesson？ | 否；沿用已有最小 RAG 能力边界偏好 |

## 收口链接

| 产物 | 链接 |
| --- | --- |
| 任务计划 | `task_plan.md` |
| 审查记录 | `review.md` |
| 进度记录 | `progress.md` |
| Regression gates | `docs/05-TEST-QA/Regression-SSoT.md` RG-001 / RG-005 / RG-007 / RG-008 |
| Cadence row | `docs/05-TEST-QA/Cadence-Ledger.md` SRB-065 |
| Branch | `feature/rag-incremental-ingest` |
| Worktree | `G:\My_Project\java\ai4j-sdk\.worktrees\feature\rag-incremental-ingest` |
