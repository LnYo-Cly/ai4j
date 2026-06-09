# AI4J FlowGram webapp CI R-007 fix

Task Contract: harness-task/v1
Task Package Index: required

## 目标

关闭 R-007：为 `ai4j-flowgram-webapp-demo/` 建立 dedicated GitHub Actions regression gate，并用本地与远端证据更新 RG-009。

## 范围

- 做什么：新增 `.github/workflows/flowgram-webapp-regression.yml`；让 webapp 相关 PR/push/manual run 覆盖 `npm run lint`、`npm run ts-check`、`npm run build`；同步 Regression SSoT、Cadence Ledger、task review 和 walkthrough。
- 不做什么：不重构 webapp 功能代码；不引入新的前端测试框架；不处理 FlowGram 前后端浏览器 E2E runbook；不代办人工 `review-confirm`。
- 主要风险：webapp 现有依赖或 lint/build 可能在当前环境失败；如果失败不是 CI 配置问题，必须记录残余而不是改业务功能扩大范围。

## 预算选择

选择预算：standard

选择理由：本任务跨 GitHub Actions、webapp build gate 和回归治理文件，但实现边界清晰，不需要多 worker 或业务代码重构。

## 上下文包（Context Packet）

| ID | 类型 | 路径 | 为什么需要 | 使用者 |
| --- | --- | --- | --- | --- |
| C-001 | code | TARGET:ai4j-flowgram-webapp-demo/package.json | webapp 的 canonical lint/type/build scripts 来自这里 | coordinator / reviewer |
| C-002 | code | TARGET:.github/workflows/java-regression.yml | 复用稳定 aggregate required-check 设计模式 | coordinator / reviewer |
| C-003 | private-plan | TARGET:docs/05-TEST-QA/Regression-SSoT.md | R-007 / RG-009 的详细治理源 | coordinator / reviewer |
| C-004 | private-plan | TARGET:coding-agent-harness/governance/regression/Regression-SSoT.md | harness v2 回归投影需要同步 | coordinator / reviewer |
| C-005 | private-plan | TARGET:docs/05-TEST-QA/Cadence-Ledger.md | 记录本次 webapp CI batch 和触发规则 | coordinator / reviewer |

## 步骤

1. 诊断 `ai4j-flowgram-webapp-demo/` scripts、现有 CI workflow 和 R-007/RG-009 状态。
2. 新增 FlowGram webapp regression workflow，提供 detect job、实际 webapp checks 和稳定聚合 job。
3. 本地运行 YAML/static checks 与 `npm run lint` / `npm run ts-check` / `npm run build`。
4. 推送后确认远端 `flowgram-webapp-regression` green run。
5. 同步 Regression SSoT、Cadence Ledger、review、walkthrough 和 lesson routing。

## 验收标准

- [ ] `.github/workflows/flowgram-webapp-regression.yml` 存在稳定聚合 job `flowgram-webapp-regression`。
- [ ] 本地 webapp lint/type/build 通过。
- [ ] 远端 GitHub Actions `flowgram-webapp-regression` run 成功。
- [ ] R-007 在两套 Regression SSoT 中关闭，RG-009 记录 CI evidence。
- [ ] harness status、`git diff --check` 和 task review materials 通过。

## 工作树（Worktree）

- 路径：当前主工作区
- 分支：`main`
- Worker owner：coordinator
- Worker handoff commit required：no
- Coordinator integration branch：`main`
- 未使用 worktree 的原因：范围集中在单条 workflow 与治理文件，且当前工作树干净；无需写入型 subagent。

## 长程任务判定

- 是否属于长程任务：否
- 若是，合同文件：`long-running-task-contract.md`
- 连续执行权限：不适用
- Stop Condition 摘要：webapp 本地 gate 或远端 CI 失败且无法在本任务范围内解释/修复时停止并记录 blocker。

## 审查判定

- 是否需要对抗性审查：是
- 若是，报告文件：`review.md`
- Reviewer：self + human review gate
- No-finding 要求：不得有 open P0/P1/P2 finding；R-007 关闭必须有本地和远端 CI 证据。

## 关联

- 相关 Regression Gate：RG-009、R-007
- 审查报告：`coding-agent-harness/planning/tasks/2026-06-09-ai4j-flowgram-webapp-ci-r-007-fix-eaa0ecce/review.md`
- Generated Ledger：由 lifecycle CLI / `harness governance rebuild` 重建
- 前置任务：R-001 已关闭，Java required check 不再阻塞 push 验证。

## 模块关联（启用模块并行时填写）

- Module：flowgram-webapp-demo
- Step：WEBAPP-R007
- Module Plan：`coding-agent-harness/planning/modules/flowgram-webapp-demo/module_plan.md`

## 协调者交接（Coordinator，启用模块并行时填写）

- Global sync owner：coordinator
- Global sync status：pending-coordinator-pass
- Registry update needed：不适用
- Harness Ledger update needed：task-review / closeout 时由 lifecycle CLI 同步
- Closeout / Regression update needed：`docs/05-TEST-QA/Regression-SSoT.md`、`docs/05-TEST-QA/Cadence-Ledger.md`、`coding-agent-harness/governance/regression/Regression-SSoT.md`、`coding-agent-harness/governance/regression/Cadence-Ledger.md`
