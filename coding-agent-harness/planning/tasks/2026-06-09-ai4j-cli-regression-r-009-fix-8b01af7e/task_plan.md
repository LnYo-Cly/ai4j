# AI4J CLI Regression R-009 Fix

Task Contract: harness-task/v1
Task Package Index: required

## 目标

修复 R-009，使 CLI 直接本地回归重新通过，并将 R-009 的关闭事实同步到旧版 `docs/` 和新版 `coding-agent-harness/` 回归治理文档。

## 范围

- 做什么：修复 `AcpCommandTest` 中 ACP 流式正文被 loop-control 文案污染的问题；修正 `JlineShellTerminalIOTest` 对 ANSI-styled `printAbove` 的断言；刷新 RG-004/RG-007 证据和 R-009 状态。
- 不做什么：不修 R-008 `ai4j-agent/HandoffPolicyTest`；不扩展 ACP 新状态协议；不继续插件生态功能波次。
- 主要风险：broad `mvn -pl ai4j-cli -am ... test` 仍会被 R-008 上游阻断，需要在 closeout 中明确区分。

## 预算选择

选择预算：standard

选择理由：这是一个窄范围回归修复，涉及 CLI 一个实现文件、一个测试文件和回归治理记录，不需要 complex 级外部资料或多 worker 协调。

## 上下文包（Context Packet）

| ID | 类型 | 路径 | 为什么需要 | 使用者 |
| --- | --- | --- | --- | --- |
| C-001 | code | TARGET:ai4j-cli/src/test/java/io/github/lnyocly/ai4j/cli/acp/AcpCommandTest.java | R-009 ACP 失败断言和 fake stream chunk 合同 | coordinator / reviewer |
| C-002 | code | TARGET:ai4j-cli/src/test/java/io/github/lnyocly/ai4j/cli/JlineShellTerminalIOTest.java | R-009 JLine 失败断言和 printAbove 合同 | coordinator / reviewer |
| C-003 | code | TARGET:ai4j-cli/src/main/java/io/github/lnyocly/ai4j/cli/acp/AcpJsonRpcServer.java | ACP session/update 映射实现 | coordinator / reviewer |
| C-004 | governance | TARGET:docs/05-TEST-QA/Regression-SSoT.md | 旧版详细回归事实源 | coordinator / reviewer |
| C-005 | governance | TARGET:coding-agent-harness/governance/regression/Regression-SSoT.md | 新版 harness 回归投影 | coordinator / reviewer |

## 步骤

1. 复现 `JlineShellTerminalIOTest,AcpCommandTest` 的 R-009 失败，并确认失败不是 R-008。
2. 修复 ACP loop-control 事件映射和 JLine 视觉文本断言。
3. 运行目标失败类、CLI 直接全套、broad `-am` 复核和 package 烟测。
4. 更新 Regression SSoT、Cadence Ledger、task progress/review/walkthrough。

## 验收标准

- [x] `mvn -pl ai4j-cli "-Dtest=JlineShellTerminalIOTest,AcpCommandTest" -DfailIfNoTests=false -DskipTests=false test` 通过。
- [x] `mvn -pl ai4j-cli -DfailIfNoTests=false -DskipTests=false test` 通过。
- [x] `mvn -DskipTests package` 通过。
- [x] `mvn -pl ai4j-cli -am -DfailIfNoTests=false -DskipTests=false test` 的失败仍限定在已知 R-008，上游 agent 模块阻断 CLI 执行。

## 工作树（Worktree）

- 路径：不适用
- 分支：`main`
- Worker owner：coordinator
- Worker handoff commit required：不适用
- Coordinator integration branch：不适用
- 未使用 worktree 的原因：当前工作区干净，修复范围只涉及 CLI 小切片和治理记录；没有并行 worker 或冲突风险。

## 长程任务判定

- 是否属于长程任务：否
- 若是，合同文件：`long-running-task-contract.md`
- 连续执行权限：不适用
- Stop Condition 摘要：若直接 CLI 全套无法通过或需要新增 ACP 协议字段，则停止并回报。

## 审查判定

- 是否需要对抗性审查：否
- 若是，报告文件：`review.md`
- Reviewer：self，提交后等待 human review confirmation
- No-finding 要求：无 open material finding；R-008 必须保留为 out-of-scope residual。

## 关联

- 相关 Regression Gate：RG-004、RG-007；R-008 作为 out-of-scope residual；R-009 closed
- 审查报告：`coding-agent-harness/planning/tasks/2026-06-09-ai4j-cli-regression-r-009-fix-8b01af7e/review.md`
- Generated Ledger：由 lifecycle CLI / `harness governance rebuild` 重建
- 前置任务：`2026-06-09-ai4j-extension-scaffold-author-experience-wave-1-50e4b9e6` 发现 R-009

## 模块关联（启用模块并行时填写）

- Module：cli-host
- Step：不适用
- Module Plan：`coding-agent-harness/planning/modules/cli-host/module_plan.md`

## 协调者交接（Coordinator，启用模块并行时填写）

- Global sync owner：coordinator
- Global sync status：synced
- Registry update needed：不适用
- Harness Ledger update needed：由 `task-review` 同步
- Closeout / Regression update needed：已更新 `docs/05-TEST-QA/*` 和 `coding-agent-harness/governance/regression/*`
