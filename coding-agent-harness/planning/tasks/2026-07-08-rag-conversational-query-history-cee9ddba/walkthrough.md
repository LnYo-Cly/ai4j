# 收口记录：rag conversational query history

## 摘要

完成 RAG conversational query history 最小接入：RAG core 复用已有 `ChatMemoryItem`，`RagQuery.history` 可选传入对话历史，内置 `ModelRagQueryPlanner` 会把 history 写入检索前 rewrite/multi-query/HyDE/step-back prompt。未新增 RAG 专用 memory，也未改动 AgentMemory。

## 范围

| 范围 | 详情 |
| --- | --- |
| 变更模块 | `ai4j` core RAG；`docs-site` RAG Query Planning 文档；Regression/Cadence 记录 |
| 新增文件 | 无 |
| 删除文件 | 无 |
| 不在范围内 | `AgentMemory` 重构、RAG 专用 memory、live provider 调用 |

## 验证

| 检查 | 命令或过程 | 结果 | 证据 |
| --- | --- | --- | --- |
| Targeted RAG tests | `mvn -pl ai4j "-Dtest=ModelRagQueryPlannerTest,DefaultRagServiceTest" -DskipTests=false test` | PASS, 9 tests | `progress.md` targeted-core |
| RG-001 core SDK | `mvn -pl ai4j -am -DskipTests=false test` | PASS, 154 tests | `progress.md` rg-001 |
| RG-008 docs typecheck | `npm --prefix docs-site run typecheck` | PASS | `progress.md` rg-008 |
| RG-008 docs build | `npm --prefix docs-site run build` | PASS | `progress.md` rg-008 |
| RG-007 package smoke | `mvn -DskipTests package` | PASS, 11 reactor projects | `progress.md` rg-007 |

## 审查结论

| 来源 | 重要发现 | 处理 | 证据 |
| --- | --- | --- | --- |
| self-check | 没有新增 memory 抽象；history 复用 core `ChatMemoryItem` | 接受 | code diff + tests |
| self-check | `engineering-standard.md` 缺失 | 记录 residual，不在本任务修复 | `progress.md` 残余 |

## 残余风险

| 风险 | Owner | 是否接受 | 跟进 |
| --- | --- | --- | --- |
| 传入过长 history 会增加 planner prompt token | app / SDK user | 接受 | 使用已有 `MessageWindowChatMemoryPolicy` 或 `SummaryChatMemoryPolicy` 控制 |
| 未验证真实模型 rewrite 质量 | app / provider smoke owner | 接受 | 需要 live provider 时另开 opt-in smoke |

## 经验沉淀反思

| 问题 | 答案 |
| --- | --- |
| 是否完成经验候选检查？ | 是；没有新增可推广经验，沿用既有“RAG-only、复用已有 memory、不新增 no-op wrapper”原则 |
| 经验候选详情文件 | 不新增 |

## 收口链接

| 产物 | 链接 |
| --- | --- |
| 任务计划 | `task_plan.md` |
| 进度记录 | `progress.md` |
| Regression SSoT | `../../../docs/05-TEST-QA/Regression-SSoT.md` |
| Cadence Ledger | `../../../docs/05-TEST-QA/Cadence-Ledger.md` |

Closeout Status: closed
