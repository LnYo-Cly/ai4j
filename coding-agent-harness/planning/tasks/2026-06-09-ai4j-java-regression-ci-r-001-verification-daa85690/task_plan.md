# AI4J Java regression CI R-001 verification

Task Contract: harness-task/v1
Task Package Index: required

## 目标

关闭 R-001：让 Java regression workflow 具备可手动/推送验证的真实绿灯证据，并把 `main` / `dev` 分支保护配置为要求稳定的 `java-regression` 检查。

## 范围

- 做什么：更新 `.github/workflows/java-regression.yml` 的触发和聚合检查；核对 Java matrix 覆盖面；用 GitHub Actions 跑出远端 green run；配置 `main` / `dev` branch protection required status check；同步 Regression SSoT、Cadence Ledger 和任务证据。
- 不做什么：不修改业务 Java 实现；不调整 docs-site/webapp CI；不引入 release signing、Central publish 或 live-provider gate。
- 主要风险：GitHub branch protection 是远端仓库策略，必须以 `gh api` 返回结果作为证据；如果 Actions 首次运行失败，不能关闭 R-001，只能记录阻塞项。

## 预算选择

选择预算：standard

选择理由：本任务跨 CI workflow、远端仓库策略和回归治理文件，但不涉及业务代码重构或多 worker 并行实现。

## 上下文包（Context Packet）

| ID | 类型 | 路径 | 为什么需要 | 使用者 |
| --- | --- | --- | --- | --- |
| C-001 | code | TARGET:.github/workflows/java-regression.yml | R-001 的 CI 触发、matrix 和 required-check 名称来自此 workflow | coordinator / reviewer |
| C-002 | private-plan | TARGET:docs/05-TEST-QA/Regression-SSoT.md | 历史 R-001 残余和本地 gate 状态的详细 SSoT | coordinator / reviewer |
| C-003 | private-plan | TARGET:coding-agent-harness/governance/regression/Regression-SSoT.md | harness v2 回归投影，必须与详细 SSoT 保持一致 | coordinator / reviewer |
| C-004 | private-plan | TARGET:docs/05-TEST-QA/Cadence-Ledger.md | 共享批次历史与 PR/merge-batch 触发规则 | coordinator / reviewer |
| C-005 | private-plan | TARGET:coding-agent-harness/governance/regression/Cadence-Ledger.md | harness v2 cadence 投影，必须记录 R-001 收口证据 | coordinator / reviewer |

## 步骤

1. 诊断 GitHub Actions run 历史、远端默认分支和 `main` / `dev` branch protection 状态。
2. 调整 `java-regression.yml`，提供 `workflow_dispatch` / `push` / `pull_request` 触发和稳定聚合 job。
3. 本地验证新增 matrix 覆盖面可通过，提交并推送 workflow。
4. 触发远端 `java-regression`，等待 green run 并记录 run URL / job 结果。
5. 配置 `main` / `dev` branch protection required status check，并用 GitHub API 读取确认。
6. 更新 Regression SSoT、Cadence Ledger、review、walkthrough 和 lesson routing。

## 验收标准

- [ ] `.github/workflows/java-regression.yml` 有稳定的 `java-regression` 聚合检查，可作为 branch protection required check。
- [ ] 远端 Actions 出现一次完成且成功的 `java-regression` run。
- [ ] `main` / `dev` branch protection 已要求 `java-regression` status check。
- [ ] R-001 在两套 Regression SSoT 中从 open 改为 closed，Cadence Ledger 记录对应批次。
- [ ] harness status、`git diff --check` 和必要本地 Maven gate 通过。

## 工作树（Worktree）

- 路径：当前主工作区
- 分支：`main`
- Worker owner：coordinator
- Worker handoff commit required：no
- Coordinator integration branch：`main`
- 未使用 worktree 的原因：用户连续要求“继续”并已在当前 main 上推进前序 R-008/R-009 收口；当前工作树干净，变更集中在 CI/governance 文件，直接在当前分支推进可减少远端 branch protection 配置和 workflow run 的分支漂移。

## 长程任务判定

- 是否属于长程任务：否
- 若是，合同文件：`long-running-task-contract.md`
- 连续执行权限：不适用
- Stop Condition 摘要：如果远端 Actions 失败或 GitHub API 无法读取/写入 branch protection，则停止关闭 R-001 并记录 blocker。

## 审查判定

- 是否需要对抗性审查：是
- 若是，报告文件：`review.md`
- Reviewer：self + human review gate
- No-finding 要求：不得有 open P0/P1/P2 finding；远端 CI 和 branch protection 证据必须可复查。

## 关联

- 相关 Regression Gate：RG-001..RG-007、RG-010、RG-011、R-001
- 审查报告：`coding-agent-harness/planning/tasks/2026-06-09-ai4j-java-regression-ci-r-001-verification-daa85690/review.md`
- Generated Ledger：由 lifecycle CLI / `harness governance rebuild` 重建
- 前置任务：R-008 / R-009 已修复，RG-002、RG-003、RG-004、RG-007 本地 broad gates 已恢复通过。

## 模块关联（启用模块并行时填写）

- Module：不适用
- Step：不适用
- Module Plan：不适用

## 协调者交接（Coordinator，启用模块并行时填写）

- Global sync owner：coordinator
- Global sync status：pending-coordinator-pass
- Registry update needed：不适用
- Harness Ledger update needed：任务进入 review/closeout 时由 lifecycle CLI 同步
- Closeout / Regression update needed：`docs/05-TEST-QA/Regression-SSoT.md`、`docs/05-TEST-QA/Cadence-Ledger.md`、`coding-agent-harness/governance/regression/Regression-SSoT.md`、`coding-agent-harness/governance/regression/Cadence-Ledger.md`
