# Feature SSoT closeout drift cleanup

Task Contract: harness-task/v1
Task Package Index: required

## 目标

修正 2026-06-07 review closeout batch 之后的治理漂移，让 `Feature-SSoT.md`、仓库级 walkthrough 和 harness task 状态一致。

## 范围

- 做什么：把已关闭的 F-022/F-023 从 Active Features 移到 Completed Features；补齐 F-023 仓库级 walkthrough；记录本次治理修正证据。
- 不做什么：不改 SDK 业务代码、不改 docs-site 正文、不新增回归 gate、不推送远程。
- 主要风险：治理文档与 harness generated 状态不一致会误导后续 agent 判断还有 active feature。

## 预算选择

选择预算：standard

选择理由：本任务只修正文档治理漂移，范围小但需要按 harness 流程留下计划、进度、审查和 closeout。

## 上下文包（Context Packet）

| ID | 类型 | 路径 | 为什么需要 | 使用者 |
| --- | --- | --- | --- | --- |
| C-001 | governance | TARGET:docs/09-PLANNING/Feature-SSoT.md | 当前显示 F-022/F-023 仍在进行中，是本次修正对象 | coordinator |
| C-002 | task | TARGET:coding-agent-harness/planning/tasks/2026-06-06-item-885d365a/walkthrough.md | F-022 已关闭证据和仓库级 walkthrough 链接来源 | coordinator |
| C-003 | task | TARGET:coding-agent-harness/planning/tasks/2026-06-07-chatclient-d5f84742/walkthrough.md | F-023 已关闭证据和仓库级 walkthrough 内容来源 | coordinator |
| C-004 | reference | TARGET:docs/11-REFERENCE/execution-workflow-standard.md | closeout 要求 Feature SSoT 和 walkthrough 同步 | coordinator / reviewer |

## 步骤

1. 诊断 Feature SSoT 与 harness task closeout 的差异。
2. 修正 Feature SSoT，并补齐缺失的 F-023 仓库级 walkthrough。
3. 运行 targeted governance verification：文本扫描、`harness status --json .`、`git diff --check`。
4. 更新 progress、lesson candidate、review、walkthrough，并提交审查。

## 验收标准

- [ ] `Feature-SSoT.md` 不再把 F-022/F-023 标为 active/in_progress。
- [ ] Completed Features 包含 F-022/F-023，且 walkthrough 链接存在。
- [ ] `harness status --json .` 通过。
- [ ] `git diff --check` 通过。

## 工作树（Worktree）

- 路径：same checkout
- 分支：current branch
- Worker owner：coordinator
- Worker handoff commit required：不适用
- Coordinator integration branch：不适用
- 未使用 worktree 的原因：治理文档小范围修正，工作区起始干净，无并行实现冲突。

## 长程任务判定

- 是否属于长程任务：否
- 若是，合同文件：`long-running-task-contract.md`
- 连续执行权限：不适用
- Stop Condition 摘要：若发现 F-022/F-023 实际未关闭或验证证据缺失，停止并回到任务审查。

## 审查判定

- 是否需要对抗性审查：否
- 若是，报告文件：`review.md`
- Reviewer：self
- No-finding 要求：review.md 无重要发现，harness status 通过。

## 关联

- 相关 Regression Gate：governance-only；不新增 executable gate
- 审查报告：`review.md`
- Generated Ledger：由 lifecycle CLI / `harness governance rebuild` 重建
- 前置任务：F-022/F-023 review closeout batch

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
