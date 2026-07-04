# 收口记录：rag query planner strategy prompts

## 摘要

已修正 `ModelRagQueryPlanner`：默认只做 `REWRITE`，显式传入多策略时按策略分别调用模型 prompt。未新增公共 API。

## 范围

| 范围 | 详情 |
| --- | --- |
| 变更模块 | `ai4j` RAG query planner；`docs-site` Search and RAG 文档 |
| 新增文件 | 无 |
| 删除文件 | 无 |
| 不在范围内 | `RetrievalStrategy` 抽象、hybrid 便利 API、非 chat 协议 planner |

## 验证

| 检查 | 命令或过程 | 结果 | 证据 |
| --- | --- | --- | --- |
| RAG 定向 | `mvn -pl ai4j "-Dtest=ModelRagQueryPlannerTest,DefaultRagServiceTest,HybridRetrieverTest" -DskipTests=false test` | PASS, 12 tests | `progress.md` E-002 |
| Core 全量 | `mvn -pl ai4j -am -DskipTests=false test` | PASS, 150 tests | `progress.md` E-003 |
| Docs-site | `npm run typecheck`; `npm run build` in `docs-site/` | PASS | `progress.md` E-004 |
| Package smoke | `mvn -DskipTests package` | PASS, 11 reactor projects | `progress.md` E-005 |
| Diff hygiene | `git diff --check` | PASS, CRLF warnings only | `progress.md` diff-hygiene |

## 审查结论

| 来源 | 重要发现 | 处理 | 证据 |
| --- | --- | --- | --- |
| self-check | 无阻塞发现 | 保持最小改动，不新增 API | `git diff`、`progress.md` |

## 残余风险

| 风险 | Owner | 是否接受 | 跟进 |
| --- | --- | --- | --- |
| 多策略 planner 会按策略增加模型调用次数 | SDK user | 接受 | docs-site 已说明；默认只启用 rewrite |
| docs-site `npm ci` 报既有 npm audit vulnerabilities | repo owner | 接受 | 非本任务依赖升级范围 |

## 经验沉淀反思

| 问题 | 答案 |
| --- | --- |
| 是否完成经验候选检查？ | 是，无需新增共享 lesson |
| 经验候选详情文件 | 不适用 |

## 收口链接

| 产物 | 链接 |
| --- | --- |
| 任务计划 | `task_plan.md` |
| 进度记录 | `progress.md` |
| Regression SSoT | `../../../docs/05-TEST-QA/Regression-SSoT.md` |
| Cadence Ledger | `../../../docs/05-TEST-QA/Cadence-Ledger.md` |
