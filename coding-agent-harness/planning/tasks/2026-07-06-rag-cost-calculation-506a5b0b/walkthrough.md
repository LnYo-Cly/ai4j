# 收口记录：rag cost calculation

## 摘要

当前 `origin/main` 已有 token cost 计算实现：`TracePricingResolver` 按模型返回每百万 token 单价，`AgentTraceListener` 从 provider usage 计算 `inputCost` / `outputCost` / `totalCost` / `currency`，并由 OTel / Langfuse exporter 输出。本任务没有新增第二套 cost abstraction，只补 docs-site 可复制使用示例，并完成验证。

## 范围

| 范围 | 详情 |
| --- | --- |
| 变更模块 | `docs-site/` trace 文档、`docs/05-TEST-QA/` 回归记录、任务包 |
| 新增文件 | `review.md`、`lesson_candidates.md`（任务本地） |
| 删除文件 | 无 |
| 不在范围内 | 默认价格表、预算告警、dashboard、`AgentResult` cost 字段、RAG 专属 cost API |

## 验证

| 检查 | 命令或过程 | 结果 | 证据 |
| --- | --- | --- | --- |
| Trace cost targeted | `mvn -pl ai4j-agent -am "-Dtest=AgentTraceListenerTest,LangfuseTraceExporterTest" -DskipTests=false -DfailIfNoTests=false test` | PASS, 6 tests | `progress.md` trace-cost-targeted-test |
| Docs typecheck | `npm run typecheck` in `docs-site/` after `npm ci` | PASS | `progress.md` docs-site-gate |
| Docs build | `npm run build` in `docs-site/` | PASS, generated `docs-site/build` | `progress.md` docs-site-gate |
| Package smoke | `mvn -DskipTests package` | PASS, 11 reactor projects | `progress.md` package-smoke |
| Regression governance | RG-007/RG-008/SRB-064 updates | PASS | `docs/05-TEST-QA/Regression-SSoT.md`, `docs/05-TEST-QA/Cadence-Ledger.md` |
| Diff hygiene | `git diff --check` | PASS, CRLF warnings only | `progress.md` diff-hygiene |

## 审查结论

| 来源 | 重要发现 | 处理 | 证据 |
| --- | --- | --- | --- |
| self-review | No material findings | 保持 docs/verification 增量，不新增 runtime API | `review.md` |

## 残余风险

| 风险 | Owner | 是否接受 | 跟进 |
| --- | --- | --- | --- |
| 模型价格漂移，用户需维护 resolver 价格 | app owner | 是 | docs 已说明不内置价格表，resolver 返回 `null` 时只记 token |

## 经验沉淀反思

| 问题 | 答案 |
| --- | --- |
| 是否完成经验候选检查？ | 是 |
| 经验候选详情文件 | `lesson_candidates.md` |
| 是否需要推广为全局 lesson？ | 否，局部判断足够 |

## 收口链接

| 产物 | 链接 |
| --- | --- |
| 任务计划 | `task_plan.md` |
| 审查记录 | `review.md` |
| 进度记录 | `progress.md` |
| 文档示例 | `docs-site/docs/agent/trace-observability.md` |
| 回归记录 | `docs/05-TEST-QA/Regression-SSoT.md`、`docs/05-TEST-QA/Cadence-Ledger.md` |
