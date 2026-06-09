# 收口记录：AI4J docs site Node heap regression R-005 fix

## 摘要

本任务关闭 docs-site RG-008 的 R-005 Node heap 残余。`npm run typecheck` 和 `npm run build` 现在都在 package scripts 内部使用 `node --max-old-space-size=8192`，维护者和 CI 不再需要手动设置 `NODE_OPTIONS`。

## 范围

| 范围 | 详情 |
| --- | --- |
| 变更模块 | `docs-site/`、docs GitHub Actions、Regression SSoT / Cadence Ledger、docs-site module plan、任务包 |
| 新增文件 | none |
| 删除文件 | none |
| 不在范围内 | R-004 Windows Docusaurus cleanup/file-lock risk, Docusaurus upgrade, docs content IA, branch protection changes |

## 验证

| 检查 | 命令或过程 | 结果 | 证据 |
| --- | --- | --- | --- |
| RG-008 typecheck | `npm run typecheck` in `docs-site/` | pass | `progress.md` |
| RG-008 build | `npm run build` in `docs-site/` | pass, generated `docs-site/build` | `progress.md` |
| Workflow YAML | `npx.cmd --yes yaml-lint .github/workflows/docs-build.yml .github/workflows/docs-pages.yml` | pass | `progress.md` |

## 审查结论

| 来源 | 重要发现 | 处理 | 证据 |
| --- | --- | --- | --- |
| Self review | none for R-005 | Submit for human confirmation; do not run `review-confirm` as agent | `review.md` |

## 残余风险

| 风险 | Owner | 是否接受 | 跟进 |
| --- | --- | --- | --- |
| R-004 Windows Docusaurus output/cache cleanup may still hit `EPERM` locks. | project coordinator | yes | Future R-004-specific task |
| Remote docs workflow evidence is only available after push. | project coordinator | yes | Observe docs-build/docs-pages after push; append evidence if needed |

## 经验沉淀反思

| 问题 | 答案 |
| --- | --- |
| 是否完成经验候选检查？ | yes |
| 经验候选详情文件 | `lesson_candidates.md` |
| 结论 | checked-none:repo-specific-docs-script-regression |

## 收口链接

| 产物 | 链接 |
| --- | --- |
| 任务计划 | `task_plan.md` |
| 审查记录 | `review.md` |
| 进度记录 | `progress.md` |
| Regression SSoT | `docs/05-TEST-QA/Regression-SSoT.md` and `coding-agent-harness/governance/regression/Regression-SSoT.md` |
