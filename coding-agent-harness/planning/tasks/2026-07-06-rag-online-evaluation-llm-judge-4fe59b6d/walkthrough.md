# 收口记录：rag online evaluation llm judge

## 摘要

本任务为 core RAG 增加了一个显式的 online LLM judge：`RagService.search(...)` 仍只负责检索和 context 组装；上层生成 answer 后，可调用 `RagOnlineEvaluator.evaluate(ragResult, answer)` 得到 faithfulness / context relevance / answer relevance，并将结果写回 `RagTrace.judgeEvaluation`。

## 范围

| 范围 | 详情 |
| --- | --- |
| 变更模块 | `ai4j` core RAG；`docs-site` RAG 文档；Regression SSoT / Cadence Ledger |
| 新增文件 | `RagJudge.java`、`RagJudgeRequest.java`、`RagJudgeEvaluation.java`、`RagOnlineEvaluator.java`、`ChatRagJudge.java`、`RagOnlineEvaluatorTest.java` |
| 删除文件 | 无 |
| 不在范围内 | 不做 live provider 调用；不默认挂入 `search(...)`；不做完整评估平台、pricing/cost、streaming trace 或 error structure 后续项 |

## 验证

| 检查 | 命令或过程 | 结果 | 证据 |
| --- | --- | --- | --- |
| RAG targeted | `mvn -pl ai4j "-Dtest=RagOnlineEvaluatorTest,RagEvaluatorTest,DefaultRagServiceTest" -DskipTests=false test` | PASS, 7 tests | `progress.md` 15:18 |
| RG-001 core | `mvn -pl ai4j -am -DskipTests=false test` | PASS, 138 tests | `progress.md` 15:19 |
| RG-007 package | `mvn -DskipTests package` | PASS, 11 reactor projects | `progress.md` 15:22 |
| RG-008 docs build | `npm ci`; `npm run build` in `docs-site/` | PASS; build generated static files | `progress.md` 15:10 |
| RG-008 docs typecheck | `npm run typecheck` in `docs-site/` | PASS | `progress.md` 15:23 |
| Diff hygiene | `git diff --check` | PASS, CRLF warnings only | `progress.md` 15:24 |

## 审查结论

| 来源 | 重要发现 | 处理 | 证据 |
| --- | --- | --- | --- |
| self-review | `ChatRagJudge` 有未使用 import；`RagOnlineEvaluator` 对 null result 会把 hits 显式传 null | 已移除 import，并将 null hits 收敛为 `Collections.emptyList()` | diff + targeted tests |
| self-review | docs 中一处行尾双空格导致 diff check 失败 | 已去掉行尾空白并重跑 `git diff --check` | `progress.md` 15:24 |
| self-review | LLM judge 容易被误认为强证明或离线评测替代 | docs-site 明确写明它是线上质量信号，不是强审计证明 | `citations-and-trace.md` |

## 残余风险

| 风险 | Owner | 是否接受 | 跟进 |
| --- | --- | --- | --- |
| 未运行真实 provider judge | 用户/后续任务 | 接受 | 需要 env key 且可能产生费用；本轮只验证 deterministic SDK contract |
| `npm ci` 报告 44 个既有 audit vulnerabilities | 后续依赖治理任务 | 接受 | 本任务未新增 Node 依赖 |
| LLM judge 分数可能受模型/prompt/provider 影响 | 使用方 | 接受 | 企业可实现自有 `RagJudge` 替换内置 prompt |

## 经验沉淀反思

| 问题 | 答案 |
| --- | --- |
| 是否完成经验候选检查？ | 已检查，无需沉淀新 lesson；本轮沿用既有经验：online evaluation 必须显式调用，避免给 RAG search 增加隐式成本。 |
| 经验候选详情文件 | simple task 未生成 `lesson_candidates.md` |

## 收口链接

| 产物 | 链接 |
| --- | --- |
| 任务计划 | `task_plan.md` |
| 进度记录 | `progress.md` |
| 变更说明 | `brief.md` |
| 可视化图 | `visual_map.md` |
| 回归记录 | `docs/05-TEST-QA/Regression-SSoT.md`; `docs/05-TEST-QA/Cadence-Ledger.md` |

Closeout Status: closed
