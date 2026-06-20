# AI4J FlowGram webapp CI R-007 fix

## Task ID

`2026-06-09-ai4j-flowgram-webapp-ci-r-007-fix-eaa0ecce`

## 创建日期

2026-06-09

## 一句话结果

关闭 R-007：FlowGram webapp demo 获得独立的 `flowgram-webapp-regression` CI gate，覆盖 lint、typecheck 和 production build。

## 完成后能得到什么

本任务完成后，`ai4j-flowgram-webapp-demo/` 不再只依赖本地 RG-009 说明。GitHub Actions 会在 PR、`main` / `dev` push 和手动触发时执行稳定聚合检查：命中 webapp surface 时运行 `npm run lint`、`npm run ts-check`、`npm run build`，非 webapp 变更时检查仍会出现并快速通过。下一轮 agent 可以把这条 CI 作为 FlowGram webapp demo 的默认回归证据，并在 Regression SSoT / Cadence Ledger 中引用远端 run。

## 交付物

- 可见产物：`.github/workflows/flowgram-webapp-regression.yml`，R-007 / RG-009 治理记录，task review packet。
- 修改位置：GitHub workflow、`docs/05-TEST-QA/`、`coding-agent-harness/governance/regression/`、当前任务目录、Feature SSoT。
- 验证证据：本地 webapp lint / ts-check / build，workflow YAML 检查，远端 GitHub Actions run，harness status。

## 第一眼应该看什么

先读 `task_plan.md` 的范围和验收标准，再看 `walkthrough.md` 的验证清单与 `review.md` 的 Evidence Checked 表。

## 边界

- 范围内：新增 FlowGram webapp dedicated CI、同步 RG-009 / R-007 治理记录、验证并提交 Agent Review Submission。
- 范围外：不重构 webapp 业务代码，不添加前端单元测试框架，不改 docs-site CI，不处理 LV-003 浏览器端到端 runbook。
- 停止条件：本地 webapp gate 失败且不是本任务范围内的 CI 配置问题；远端 Actions 无法运行；需要修改分支保护但 GitHub API 无权限。

## 完成判断

- 本地 `npm run lint`、`npm run ts-check`、`npm run build` 在 `ai4j-flowgram-webapp-demo/` 通过。
- 新 workflow 具备稳定聚合 job `flowgram-webapp-regression`。
- 远端 GitHub Actions 出现一次成功的 `flowgram-webapp-regression` run。
- R-007 在 Regression SSoT 和 harness v2 regression projection 中关闭。
- Cadence Ledger 记录本次 webapp CI verification batch。

## 执行合同

- Owner：coordinator
- 生命周期状态：进行中
- 必需文件：`INDEX.md`、`task_plan.md`、`execution_strategy.md`、`visual_map.md`、
  `progress.md`、`findings.md`、`review.md`
- 完成条件：验证证据必须记录到 `progress.md`

## 当前下一步

填写任务合同、实现 workflow、运行本地 webapp gate。
