# 收口记录：RAG token-aware context assembler

## 摘要

新增可选 `TokenAwareRagContextAssembler`。它复用现有 token 工具，按预算组装 RAG context；默认 `DefaultRagContextAssembler` 不变。

## 范围

| 范围 | 详情 |
| --- | --- |
| 变更模块 | `ai4j` core RAG、docs-site RAG/Spring 文档、Regression/Cadence |
| 新增文件 | `TokenAwareRagContextAssembler.java`、`TokenAwareRagContextAssemblerTest.java` |
| 删除文件 | 无 |
| 不在范围内 | `RagQuery` 新字段、starter 配置、复杂 tokenizer registry、live provider |

## 验证

| 检查 | 命令或过程 | 结果 | 证据 |
| --- | --- | --- | --- |
| RAG 定向 | `mvn -pl ai4j "-Dtest=TokenAwareRagContextAssemblerTest,DefaultRagServiceTest" -DskipTests=false test` | PASS, 5 tests | `progress.md` E-001 |
| RG-001 | `mvn -pl ai4j -am -DskipTests=false test` | PASS, 145 tests | `progress.md` E-002 |
| RG-008 | `npm ci`; `npm run typecheck`; `npm run build` in `docs-site/` | PASS | `progress.md` E-003 |
| RG-007 | `mvn -DskipTests package` | PASS, 11 reactor projects | `progress.md` E-004 |

## 审查结论

| 来源 | 重要发现 | 处理 | 证据 |
| --- | --- | --- | --- |
| self-review | 0 | 可提交 PR | `review.md` |

## 残余风险

| 风险 | Owner | 是否接受 | 跟进 |
| --- | --- | --- | --- |
| provider-specific tokenizer 仍可能和 `cl100k_base` 有差异 | SDK maintainer | yes | 有真实需求时另开 tokenizer registry 任务 |

## 经验沉淀反思

| 问题 | 答案 |
| --- | --- |
| 是否完成经验候选检查？ | 是，checked-none |
| 经验候选详情文件 | `lesson_candidates.md` |

## 收口链接

| 产物 | 链接 |
| --- | --- |
| 任务计划 | `task_plan.md` |
| 审查记录 | `review.md` |
| 进度记录 | `progress.md` |
| Regression 记录 | `docs/05-TEST-QA/Regression-SSoT.md` RG-001/RG-007/RG-008 |
| Cadence 记录 | `docs/05-TEST-QA/Cadence-Ledger.md` SRB-066 |
