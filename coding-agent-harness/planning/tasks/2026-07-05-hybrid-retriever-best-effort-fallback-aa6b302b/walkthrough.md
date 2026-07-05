# 收口记录：hybrid retriever best effort fallback

## 摘要

已为 `HybridRetriever` 增加最小 best-effort fallback：单路子检索器失败时跳过该路，保留其他成功结果；所有非空子检索器都失败时抛第一个异常。没有新增 public API 或复杂策略配置。

## 范围

| 范围 | 详情 |
| --- | --- |
| 变更模块 | `ai4j` core RAG；`docs-site` RAG 文档；regression governance |
| 新增文件 | 无 |
| 删除文件 | 无 |
| 不在范围内 | retry、timeout、circuit breaker、metrics、并行检索、`FallbackRetriever`、`RetrievalFailurePolicy`、`RagService` public API 改动 |

## 验证

| 检查 | 命令或过程 | 结果 | 证据 |
| --- | --- | --- | --- |
| Targeted hybrid | `mvn -pl ai4j "-Dtest=HybridRetrieverTest" -DskipTests=false test` | PASS, 7 tests | `progress.md` 21:46 |
| RAG near-path | `mvn -pl ai4j "-Dtest=HybridRetrieverTest,DefaultRagServiceTest" -DskipTests=false test` | PASS, 11 tests | `progress.md` 21:46 |
| RG-001 core | `mvn -pl ai4j -am -DskipTests=false test` | PASS, 153 tests | `progress.md` 21:47 |
| RG-008 docs typecheck | `npm run typecheck` in `docs-site/` | PASS | `progress.md` 21:52 |
| RG-008 docs build | `npm run build` in `docs-site/` | PASS, generated static files | `progress.md` 21:52 |
| RG-007 package smoke | `mvn -DskipTests package` | PASS, 11 reactor projects | `progress.md` 21:55 |
| Diff hygiene | `git diff --check` | PASS, no whitespace errors | `progress.md` 21:56 |

## 审查结论

| 来源 | 重要发现 | 处理 | 证据 |
| --- | --- | --- | --- |
| Self-review | 无 material finding | simple task 不需要独立 review.md；自动化 gate 覆盖行为边界 | `HybridRetrieverTest` + docs diff |

## 残余风险

| 风险 | Owner | 是否接受 | 跟进 |
| --- | --- | --- | --- |
| 不做 retry/timeout/circuit breaker/metrics | maintainer | 接受 | 这是刻意的最小生产基线；如未来需要企业级观测治理，另开设计任务 |
| `docs/11-REFERENCE/engineering-standard.md` 缺失 | repo | 接受 | 当前任务按 AGENTS、`testing-standard.md` 和 Regression/Cadence 执行；不在本任务修复 |

## 经验沉淀反思

| 问题 | 答案 |
| --- | --- |
| 是否完成经验候选检查？ | checked-none |
| 经验候选详情文件 | simple task 未创建 `lesson_candidates.md`；本轮无可沉淀的新流程规则 |

## 收口链接

| 产物 | 链接 |
| --- | --- |
| 任务计划 | `task_plan.md` |
| 进度记录 | `progress.md` |
| 代码实现 | `ai4j/src/main/java/io/github/lnyocly/ai4j/rag/HybridRetriever.java` |
| 行为测试 | `ai4j/src/test/java/io/github/lnyocly/ai4j/rag/HybridRetrieverTest.java` |
| 用户文档 | `docs-site/docs/core-sdk/search-and-rag/hybrid-retrieval.md` |

Closeout Status: closed
