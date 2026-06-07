# Feature SSoT closeout drift cleanup

Task Contract: harness-task/v1
Task Package Index: required

## 目标

修正 2026-06-07 review closeout batch 之后的治理漂移，并明确新任务 closeout 以 harness task package 为 SSoT。

## 范围

- 做什么：把已关闭的 F-022/F-023 从 Active Features 移到 Completed Features；将 F-023 链接指向已存在的 task-local walkthrough；删除错误新增的过渡 `docs/10` closeout；把 AGENTS/reference/context 同步为 harness-first 规则；记录本次治理修正证据。
- 不做什么：不改 SDK 业务代码、不改 docs-site 正文、不新增回归 gate、不推送远程。
- 主要风险：治理文档与 harness generated 状态不一致，或继续新增 numbered `docs/10` closeout，会误导后续 agent 在两套 SSoT 间来回漂移。

## 预算选择

选择预算：standard

选择理由：本任务只修正文档治理漂移，范围小但需要按 harness 流程留下计划、进度、审查和 closeout。

## 上下文包（Context Packet）

| ID | 类型 | 路径 | 为什么需要 | 使用者 |
| --- | --- | --- | --- | --- |
| C-001 | governance | TARGET:docs/09-PLANNING/Feature-SSoT.md | 历史/摘要 Feature SSoT，需将 F-023 指向 task-local walkthrough | coordinator |
| C-002 | task | TARGET:coding-agent-harness/planning/tasks/2026-06-06-item-885d365a/walkthrough.md | F-022 已关闭证据和 task-local walkthrough 链接来源 | coordinator |
| C-003 | task | TARGET:coding-agent-harness/planning/tasks/2026-06-07-chatclient-d5f84742/walkthrough.md | F-023 已关闭证据和 task-local walkthrough 内容来源 | coordinator |
| C-004 | reference | TARGET:AGENTS.md | repo-wide agent 入口规则，需明确新任务归档位置 | coordinator / reviewer |
| C-005 | reference | TARGET:docs/11-REFERENCE/execution-workflow-standard.md | closeout 和 task lifecycle 规则的长期 reference | coordinator / reviewer |

## 步骤

1. 诊断 Feature SSoT 与 harness task closeout 的差异。
2. 修正 Feature SSoT，并删除错误新增的 F-023 `docs/10` 过渡 walkthrough。
3. 更新 AGENTS、reference 和 harness context，明确新任务 closeout 写入 `coding-agent-harness/planning/tasks/<task>/walkthrough.md`。
4. 运行 targeted governance verification：文本扫描、`harness status --json .`、`git diff --check`。
5. 更新 progress、lesson candidate、review、walkthrough，并提交审查。

## 验收标准

- [ ] `Feature-SSoT.md` 不再把 F-022/F-023 标为 active/in_progress。
- [ ] F-023 Completed Features 指向存在的 task-local walkthrough。
- [ ] 新任务 planning/progress/review/walkthrough 默认路径在 AGENTS、reference、harness context 中一致。
- [ ] 错误新增的 `docs/10-WALKTHROUGH/2026-06-07-lightweight-chatclient-first-chat-facade.md` 不存在。
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
