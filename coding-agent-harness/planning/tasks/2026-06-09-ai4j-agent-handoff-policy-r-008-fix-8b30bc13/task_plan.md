# AI4J agent handoff policy R-008 fix

Task Contract: harness-task/v1
Task Package Index: required

## 目标

关闭 R-008：`ai4j-agent` 的 handoff policy allowed-tools 与 max-depth 本地回归通过，并恢复依赖 agent 模块的广义回归门禁。

## 范围

- 做什么：修复 `HandoffPolicy.FAIL` 策略失败被普通工具错误包装的问题，补充或保留对应测试，并更新 R-008 回归治理记录。
- 不做什么：不改写普通工具异常默认返回 `TOOL_ERROR` 的主循环语义，不调整插件生态、docs-site 页面或 Agent Team 调度。
- 主要风险：如果异常穿透范围过宽，可能破坏现有 Coding Agent / CLI 对普通工具错误的容错。

## 预算选择

选择预算：standard

选择理由：行为修复集中在 `ai4j-agent` 单一回归面，但会影响 `ai4j-coding` / `ai4j-cli` 的 `-am` 门禁，需要代码、回归治理和 walkthrough 收口。

## 上下文包（Context Packet）

| ID | 类型 | 路径 | 为什么需要 | 使用者 |
| --- | --- | --- | --- | --- |
| C-001 | code | TARGET:ai4j-agent/src/test/java/io/github/lnyocly/agent/HandoffPolicyTest.java | R-008 复现与验收测试源 | coordinator / reviewer |
| C-002 | code | TARGET:ai4j-agent/src/main/java/io/github/lnyocly/ai4j/agent/runtime/BaseAgentRuntime.java | 当前工具异常包装逻辑 | coordinator / reviewer |
| C-003 | code | TARGET:ai4j-agent/src/main/java/io/github/lnyocly/ai4j/agent/subagent/SubAgentToolExecutor.java | handoff policy deny/error 处理逻辑 | coordinator / reviewer |
| C-004 | regression | TARGET:docs/05-TEST-QA/Regression-SSoT.md | R-008 状态来源 | coordinator / reviewer |

## 步骤

1. 复现 `HandoffPolicyTest` 两个失败，确认 R-008 仍存在。
2. 实现最小异常传播修复，只让 handoff policy fail-fast 类错误穿透主循环。
3. 运行 targeted、agent 全量、CLI `-am`、package smoke 与 harness status。
4. 更新 Regression SSoT / Cadence Ledger、task progress、review、walkthrough。

## 验收标准

- [ ] `mvn -pl ai4j-agent -Dtest=HandoffPolicyTest -DfailIfNoTests=false -DskipTests=false test` 通过。
- [ ] `mvn -pl ai4j-agent -am -DfailIfNoTests=false -DskipTests=false test` 通过。
- [ ] `mvn -pl ai4j-cli -am -DfailIfNoTests=false -DskipTests=false test` 通过或只剩新记录且有明确路由的非 R-008 residual。
- [ ] R-008 在两份 Regression SSoT 中关闭，并在两份 Cadence Ledger 中记录证据。
- [ ] harness status 通过，任务提交到 review 队列。

## 工作树（Worktree）

- 路径：当前 checkout
- 分支：`main`
- Worker owner：不适用
- Worker handoff commit required：不适用
- Coordinator integration branch：`main`
- 未使用 worktree 的原因：修复面集中且无并行 worker；当前任务由 coordinator 顺序完成更低风险。

## 长程任务判定

- 是否属于长程任务：否
- 若是，合同文件：`long-running-task-contract.md`
- 连续执行权限：不适用
- Stop Condition 摘要：若必须改变普通工具错误容错语义则停止。

## 审查判定

- 是否需要对抗性审查：否
- 若是，报告文件：`review.md`
- Reviewer：self + harness review packet + human confirmation
- No-finding 要求：无 open material finding。

## 关联

- 相关 Regression Gate：RG-002、RG-003、RG-004、R-008
- 审查报告：`coding-agent-harness/planning/tasks/2026-06-09-ai4j-agent-handoff-policy-r-008-fix-8b30bc13/review.md`
- Generated Ledger：由 lifecycle CLI / `harness governance rebuild` 重建
- 前置任务：`2026-06-09-ai4j-cli-regression-r-009-fix-8b01af7e`

## 模块关联（启用模块并行时填写）

- Module：agent-runtime
- Step：不适用
- Module Plan：`coding-agent-harness/planning/modules/agent-runtime/module_plan.md`

## 协调者交接（Coordinator，启用模块并行时填写）

- Global sync owner：coordinator / 不适用
- Global sync status：synced
- Registry update needed：不适用
- Harness Ledger update needed：由 lifecycle CLI 同步
- Closeout / Regression update needed：`docs/05-TEST-QA/Regression-SSoT.md`、`docs/05-TEST-QA/Cadence-Ledger.md`、`coding-agent-harness/governance/regression/`
