# regression baseline live split

Task Contract: harness-task/v1
Task Kind: standard-task
Task Preset: standard-task
Preset Version: 1
Evidence Bundle: coding-agent-harness/planning/tasks/2026-06-04-regression-baseline-live-split-b2f834db/artifacts/preset/2026-06-04T09-15-42-552Z
Task Package Index: required

## 目标

建立 ai4j-sdk 回归治理的 local baseline / live-provider / credential gate 分层，并让 Cadence Ledger 明确每类改动的触发频率和最低证据深度。

## 范围

- 做什么：更新 tracked v2 Regression SSoT 与 Cadence Ledger；同步 legacy docs projection；补任务材料、发现、审查和验证记录。
- 不做什么：不重构测试代码，不新增 Maven live-test profile，不配置 GitHub required checks，不运行真实 provider、发布签名或浏览器端到端 gate。
- 主要风险：现有测试树存在 provider/env 依赖，不能在本轮仅靠文档断言所有 Maven module test 已完全本地确定性；必须作为残余路由。

## 预算选择

选择预算：complex

选择理由：本任务跨回归 SSoT、Cadence、testing standard 投影和任务审查材料；虽然不改业务代码，但会改变后续所有任务的验证门禁判断。

## 上下文包（Context Packet）

| ID | 类型 | 路径 | 为什么需要 | 使用者 |
| --- | --- | --- | --- | --- |
| C-001 | code | TARGET:coding-agent-harness/governance/regression/Regression-SSoT.md | tracked v2 回归 gate 的事实源 | coordinator / reviewer |
| C-002 | code | TARGET:coding-agent-harness/governance/regression/Cadence-Ledger.md | tracked v2 cadence 与批次事实源 | coordinator / reviewer |
| C-003 | code | TARGET:.github/workflows/java-regression.yml | Java PR gate 的真实 CI 入口 | coordinator / reviewer |
| C-004 | code | TARGET:docs-site/package.json | docs-site build/typecheck 脚本事实源 | coordinator / reviewer |
| C-005 | code | TARGET:ai4j-flowgram-webapp-demo/package.json | FlowGram webapp lint/type/build 脚本事实源 | coordinator / reviewer |

## 步骤

1. 读取现有 SSoT、Cadence、testing standard、CI workflow 和 package scripts。
2. 扫描测试树中的 provider/env/Assume 依赖，区分本地 gate 与 live/credential gate。
3. 更新 tracked v2 回归总账与 cadence 表，并同步 legacy projection。
4. 补齐任务材料、发现、审查和 walkthrough。
5. 运行 harness status、关键字段扫描和 git diff 边界检查，提交待审。

## 验收标准

- [ ] SSoT 中 local-required 与 live/credential opt-in gate 分离。
- [ ] Cadence Ledger 每类改动有必跑 gate、opt-in gate、节奏和最低证据深度。
- [ ] live provider/profile hygiene 与 webapp CI 缺口被路由为残余。
- [ ] `npx --yes coding-agent-harness status --json .` 通过且 git diff 只包含本任务边界。
- [ ] 任务进入 Agent Review Submission，等待人工 dashboard 确认。

## 工作树（Worktree）

- 路径：当前 checkout
- 分支：main
- Worker owner：不适用
- Worker handoff commit required：不适用
- Coordinator integration branch：不适用
- 未使用 worktree 的原因：只改 coordinator-owned harness/governance 文档，没有并行代码切片。

## 长程任务判定

- 是否属于长程任务：否
- 若是，合同文件：`long-running-task-contract.md`
- 连续执行权限：不适用
- Stop Condition 摘要：需要修改测试代码、CI required checks、真实 provider 或发布凭证时停止。

## 审查判定

- 是否需要对抗性审查：否
- 若是，报告文件：`review.md`
- Reviewer：self + human dashboard confirmation
- No-finding 要求：无 open P0/P1/P2 finding；残余必须路由。

## 关联

- 相关 Regression Gate：RG-001..RG-009, LV-001..LV-003, CR-001
- 审查报告：`review.md`
- Generated Ledger：由 lifecycle CLI / `harness governance rebuild` 重建
- 前置任务：`2026-06-04-first-wave-project-upgrades-93da333c`、`2026-06-04-module-parallel-harness-upgrade-d6ab88ce`

## 模块关联（启用模块并行时填写）

- Module：base / governance
- Step：不适用
- Module Plan：不适用

## 协调者交接（Coordinator，启用模块并行时填写）

- Global sync owner：coordinator
- Global sync status：pending-review
- Registry update needed：不适用
- Harness Ledger update needed：任务生命周期 CLI 自动同步
- Closeout / Regression update needed：`coding-agent-harness/governance/regression/Regression-SSoT.md`、`coding-agent-harness/governance/regression/Cadence-Ledger.md`

## Standard Task Preset

This task was created through the declarative `standard-task` preset.

| Field | Value |
| --- | --- |
| Preset Title | regression baseline live split |
