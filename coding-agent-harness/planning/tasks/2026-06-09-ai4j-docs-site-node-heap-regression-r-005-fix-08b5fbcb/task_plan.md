# AI4J docs site Node heap regression R-005 fix

Task Contract: harness-task/v1
Task Package Index: required

## 目标

关闭 docs-site RG-008 的 R-005 heap 残余：维护者在本地和 CI 只运行 `npm run typecheck` / `npm run build`，不再手动记 `NODE_OPTIONS=--max-old-space-size=8192`。

## 范围

- 做什么：更新 `docs-site/package.json` 的 `typecheck` / `build` 脚本；让 docs GitHub Actions 先 typecheck 再 build；同步 Feature SSoT、Regression SSoT、Cadence Ledger、task review 和 walkthrough。
- 不做什么：不重构 Docusaurus 配置，不改 docs 内容信息架构，不处理 Windows `EPERM` 清理残余 R-004，不新增前端依赖。
- 主要风险：直接调用本地 CLI 路径必须在 Windows 和 GitHub Actions 上可执行；docs CI workflow 不能和本地 RG-008 入口漂移。

## 预算选择

选择预算：standard

选择理由：本任务修改 docs-site 回归入口和治理记录，影响面清晰但需要本地验证与 SSoT 收口。

## 上下文包（Context Packet）

| ID | 类型 | 路径 | 为什么需要 | 使用者 |
| --- | --- | --- | --- | --- |
| C-001 | code | TARGET:docs-site/package.json | docs-site 本地回归入口定义在 package scripts 中。 | coordinator / reviewer |
| C-002 | workflow | TARGET:.github/workflows/docs-build.yml | docs PR/push 的远端构建入口。 | coordinator / reviewer |
| C-003 | workflow | TARGET:.github/workflows/docs-pages.yml | GitHub Pages 构建入口。 | coordinator / reviewer |
| C-004 | governance | TARGET:docs/05-TEST-QA/Regression-SSoT.md | 旧版详细 RG-008 / R-005 单一事实源。 | coordinator / reviewer |
| C-005 | governance | TARGET:coding-agent-harness/governance/regression/Regression-SSoT.md | harness v2 RG-008 / R-005 投影。 | coordinator / reviewer |

## 步骤

1. 诊断 docs-site package scripts、docs workflows 和 R-005 当前记录。
2. 将 8GB Node heap 固化到 `npm run typecheck` / `npm run build`，并让 docs workflows 执行同一入口。
3. 同步 Feature SSoT、Regression SSoT、Cadence Ledger、任务进度、review 和 walkthrough。
4. 运行 RG-008、workflow YAML 检查、`git diff --check` 和 harness status。

## 验收标准

- [ ] `docs-site/` 下 `npm run typecheck` 通过，且命令行不额外设置 `NODE_OPTIONS`。
- [ ] `docs-site/` 下 `npm run build` 通过，且命令行不额外设置 `NODE_OPTIONS`。
- [ ] docs workflows 运行 `npm run typecheck` 和 `npm run build`，与本地 RG-008 入口一致。
- [ ] R-005 在 legacy 和 v2 Regression SSoT 中关闭；R-004 保持独立开放。

## 工作树（Worktree）

- 路径：n/a
- 分支：main
- Worker owner：coordinator
- Worker handoff commit required：不适用
- Coordinator integration branch：不适用
- 未使用 worktree 的原因：本轮是小范围 docs-site 回归入口和治理同步，当前主工作树干净，未引入并行 worker。

## 长程任务判定

- 是否属于长程任务：否
- 若是，合同文件：`long-running-task-contract.md`
- 连续执行权限：不适用
- Stop Condition 摘要：如果 `npm run typecheck` 或 `npm run build` 在内置 heap 脚本下失败且无法定位为本轮脚本问题，停止并记录 blocker。

## 审查判定

- 是否需要对抗性审查：否
- 若是，报告文件：`review.md`
- Reviewer：self + human confirmation queue
- No-finding 要求：self-review 无 P0/P1/P2 重要发现后提交人工确认，不由 agent 代办确认。

## 关联

- 相关 Regression Gate：RG-008，R-005
- 审查报告：`coding-agent-harness/planning/tasks/2026-06-09-ai4j-docs-site-node-heap-regression-r-005-fix-08b5fbcb/review.md`
- Generated Ledger：由 lifecycle CLI / `harness governance rebuild` 重建
- 前置任务：[引用；如无写“无”]

## 模块关联（启用模块并行时填写）

- Module：[module key，例如 reader / graph / 不适用]
- Step：[step ID，例如 RDR-02 / 不适用]
- Module Plan：[link to module_plan.md / 不适用]

## 协调者交接（Coordinator，启用模块并行时填写）

- Global sync owner：coordinator / 不适用
- Global sync status：pending-coordinator-pass / synced / n/a
- Registry update needed：[module key, step, status, branch, updated / 不适用]
- Harness Ledger update needed：[task plan path, review path, closeout status / 不适用]
- Closeout / Regression update needed：[路径或 n/a]
