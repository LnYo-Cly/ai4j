# Feature SSoT review queue status alignment

Task Contract: harness-task/v1
Task Package Index: required

## 目标

让 `docs/09-PLANNING/Feature-SSoT.md` 的 F-024 到 F-037 active rows 与 generated Harness Ledger 的 review queue 投影一致，避免把已提交 agent review 的任务误显示为仍在实施。

## 范围

- 做什么：更新 Feature SSoT 的状态说明、F-024 到 F-037 的 status/residual 文案，并补齐当前任务 package。
- 不做什么：不修改 Java/runtime/docs-site 业务内容；不确认任何 human review；不关闭 F-024 到 F-037。
- 主要风险：把 review queue 误写成 completed，或让 Feature SSoT 与 generated Harness Ledger 继续表达不同 lifecycle state。

## 预算选择

选择预算：standard

选择理由：本任务是跨治理文件的状态修正，需要完整 brief、plan、progress、review、walkthrough 和 lesson decision，但不涉及业务代码或复杂回归。

## 上下文包（Context Packet）

| ID | 类型 | 路径 | 为什么需要 | 使用者 |
| --- | --- | --- | --- | --- |
| C-001 | governance | TARGET:docs/09-PLANNING/Feature-SSoT.md | 待修正的人工可读 planning summary。 | coordinator / reviewer |
| C-002 | generated-ledger | TARGET:coding-agent-harness/governance/generated/Harness-Ledger.md | generated Harness Ledger 是本轮对齐目标，提供 review queue 状态投影。 | coordinator / reviewer |
| C-003 | reference | TARGET:docs/11-REFERENCE/execution-workflow-standard.md | 确认 agent review 与 human review confirmation 的边界。 | coordinator |

## 步骤

1. 扫描 Feature SSoT 中 F-024 到 F-037 的旧 `in_progress` 状态。
2. 对照 generated Harness Ledger 中对应任务的 `review | review` 投影。
3. 将 Feature SSoT active rows 改为 `🟣 review`，并保留“等待人工确认”的 residual 语义。
4. 补齐当前任务 package 的 brief、计划、执行策略、发现、lesson decision、review 和 walkthrough。
5. 运行 targeted scans、diff hygiene 和 harness status 验证。

## 验收标准

- [x] F-024 到 F-037 不再显示 `🟡 in_progress`。
- [x] Feature SSoT Status Legend 包含 `🟣 review`。
- [x] Feature SSoT 页首说明它是人工可读 summary，generated ledger 是 harness v2 机器投影来源。
- [x] `git diff --check` 与 `npx.cmd --yes coding-agent-harness status --json .` 通过或有明确 residual。
- [ ] 当前任务进入 Agent Review Submission，等待人工确认。

## 工作树（Worktree）

- 路径：当前 checkout
- 分支：`main`
- Worker owner：不适用
- Worker handoff commit required：不适用
- Coordinator integration branch：不适用
- 未使用 worktree 的原因：只修改治理 summary 和当前 task package，写入面窄且无需并行 worker。

## 长程任务判定

- 是否属于长程任务：否
- 若是，合同文件：`long-running-task-contract.md`
- 连续执行权限：不适用
- Stop Condition 摘要：如 generated ledger 显示对应任务并非 review queue，立即停止并逐项核对。

## 审查判定

- 是否需要对抗性审查：否
- 若是，报告文件：`review.md`
- Reviewer：self；最终等待 human review confirmation
- No-finding 要求：无 P0/P1/P2 blocking finding。

## 关联

- 相关 Regression Gate：无；governance-only 状态对齐。
- 审查报告：`review.md`
- Generated Ledger：由 lifecycle CLI / `harness governance rebuild` 重建
- 前置任务：F-024 到 F-037 对应任务已提交 Agent Review Submission。

## 模块关联（启用模块并行时填写）

- Module：base / governance
- Step：不适用
- Module Plan：不适用

## 协调者交接（Coordinator，启用模块并行时填写）

- Global sync owner：coordinator / 不适用
- Global sync status：pending-coordinator-pass
- Registry update needed：不适用
- Harness Ledger update needed：当前任务 task plan / review / walkthrough 需要在 review submission 后由 generated ledger 反映。
- Closeout / Regression update needed：`walkthrough.md`；Regression SSoT 无需更新。
