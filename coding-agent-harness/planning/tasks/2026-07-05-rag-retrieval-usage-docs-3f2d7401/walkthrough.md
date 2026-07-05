# 收口记录：rag retrieval usage docs

## 摘要

已补充 RAG 召回层使用说明：默认 dense、BM25、Dense + BM25 hybrid，以及 Query Planning 与 hybrid 叠加时的乘法成本。

## 范围

| 范围 | 详情 |
| --- | --- |
| 变更模块 | `docs-site` Search and RAG 文档 |
| 新增文件 | 无 |
| 删除文件 | 无 |
| 不在范围内 | Java API、hybrid 工厂、生产级 BM25 后端 |

## 验证

| 检查 | 命令或过程 | 结果 | 证据 |
| --- | --- | --- | --- |
| Docs-site | `npm run typecheck`; `npm run build` in `docs-site/` | PASS | `progress.md` E-002 |
| Diff hygiene | `git diff --check` | PASS, CRLF warnings only | `progress.md` E-003 |

## 审查结论

| 来源 | 重要发现 | 处理 | 证据 |
| --- | --- | --- | --- |
| self-check | 无阻塞发现 | 保持 docs-only，不新增 API | `git diff`、`progress.md` |

## 残余风险

| 风险 | Owner | 是否接受 | 跟进 |
| --- | --- | --- | --- |
| `npm ci` 报既有 npm audit vulnerabilities | repo owner | 接受 | 非本任务依赖升级范围 |

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
