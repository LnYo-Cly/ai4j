# AI4J agent handoff policy R-008 fix - 进度

## 状态：审查中

`## 状态` 是受控机器字段，只能使用以下值之一：

- `未开始`
- `计划中`
- `进行中`
- `审查中`
- `已阻塞`
- `已完成`

不要把 `计划审阅中`、`等待 coordinator pass`、`本地审查就绪` 等细粒度协作状态写入本字段。
这些状态应记录到进度记录、残余或协调者交接中。

## 进度记录

证据使用 `type:path:summary` 格式。

允许的 `type`：`command`, `diff`, `fixture`, `screenshot`, `review`, `report`。

证据较长或数量较多时，不要粘贴全文；放入 `artifacts/INDEX.md` 并在这里引用 ID。

## 残余

- R-001 仍 open：Java PR workflow 首次绿色运行和 required branch protection 仍需项目层面确认；本任务已关闭 R-008。

## 协调者交接（Coordinator，启用模块并行时填写）

- Global sync status：synced
- Registry update needed：不适用
- Harness Ledger update needed：由 lifecycle CLI 同步
- 负责人：coordinator

### [2026-06-09 09:04] - task-start

- 做了什么：开始修复 R-008：ai4j-agent HandoffPolicy allowedTools 与 maxDepth 本地回归
- 验证结果：已记录
- 下一步：继续执行
- 证据：n/a

### [2026-06-09 17:05] - reproduce

- 做了什么：复现 R-008，确认 `HandoffPolicyTest` 仍有两个失败：allowed-tools 策略拒绝未抛出、nested max-depth 违规未抛出。
- 验证结果：目标测试失败，6 tests 中 2 failures。
- 下一步：定位工具执行异常传播路径。
- 证据：command:TARGET:.:`mvn -pl ai4j-agent -Dtest=HandoffPolicyTest -DfailIfNoTests=false -DskipTests=false test` failed in `testAllowedToolsPolicyDeniesUnexpectedSubagent` and `testNestedHandoffBlockedByMaxDepth`

### [2026-06-09 17:36] - fix-and-targeted-regression

- 做了什么：新增 `HandoffPolicyException`，`SubAgentToolExecutor` 在 `HandoffPolicy.FAIL` 策略拒绝时抛出该异常；`BaseAgentRuntime` 对该异常 fail fast，普通工具异常仍保持 `TOOL_ERROR` 输出合同。
- 验证结果：R-008 目标测试和扩展 guardrail 防回归测试通过，11 tests, 0 failures。
- 下一步：运行 owning module 和 dependent broad gates。
- 证据：diff:TARGET:ai4j-agent/src/main/java/io/github/lnyocly/ai4j/agent/subagent/HandoffPolicyException.java:new fail-fast marker exception for handoff policy violations; diff:TARGET:ai4j-agent/src/main/java/io/github/lnyocly/ai4j/agent/runtime/BaseAgentRuntime.java:rethrow `HandoffPolicyException` instead of wrapping it into `TOOL_ERROR`; command:TARGET:.:`mvn -pl ai4j-agent "-Dtest=HandoffPolicyTest,ExtensionAgentToolsTest" -DfailIfNoTests=false -DskipTests=false test` passed with 11 tests

### [2026-06-09 17:36] - agent-broad-gate

- 做了什么：运行 RG-002 broad gate。
- 验证结果：通过；extension API 12 tests、core 103 tests、agent 74 tests 全绿。
- 下一步：确认 dependent coding / CLI broad gates。
- 证据：command:TARGET:.:`mvn -pl ai4j-agent -am -DfailIfNoTests=false -DskipTests=false test` passed with extension API 12 tests, core 103 tests, and agent 74 tests

### [2026-06-09 17:38] - cli-broad-gate

- 做了什么：运行曾被 R-008 阻断的 RG-004 broad gate。
- 验证结果：通过；CLI 阶段 261 tests 全绿，说明 R-008 不再阻断 CLI `-am`。
- 下一步：补跑 RG-003 和 package smoke。
- 证据：command:TARGET:.:`mvn -pl ai4j-cli -am -DfailIfNoTests=false -DskipTests=false test` passed through CLI with 261 CLI tests

### [2026-06-09 17:40] - package-smoke

- 做了什么：运行 RG-007 package smoke 和 diff 静态检查。
- 验证结果：`mvn -DskipTests package` 通过 11 个 reactor projects；`git diff --check` 仅有 CRLF warning，无 whitespace error。
- 下一步：更新 Regression SSoT / Cadence Ledger。
- 证据：command:TARGET:.:`mvn -DskipTests package` passed across 11 reactor projects; command:TARGET:.:`git diff --check` passed with CRLF warnings only

### [2026-06-09 17:42] - coding-broad-gate

- 做了什么：运行 RG-003 broad gate，确认 R-008 对 coding runtime 依赖链的阻塞也已解除。
- 验证结果：通过；extension API 12 tests、core 103 tests、agent 74 tests、coding 59 tests 全绿。
- 下一步：提交 review packet。
- 证据：command:TARGET:.:`mvn -pl ai4j-coding -am -DfailIfNoTests=false -DskipTests=false test` passed through coding with 59 coding tests

### [2026-06-09 17:43] - regression-governance

- 做了什么：更新 `docs/05-TEST-QA/Regression-SSoT.md`、`docs/05-TEST-QA/Cadence-Ledger.md` 和 `coding-agent-harness/governance/regression/` 投影，标记 R-008 closed，并把 RG-002/RG-003/RG-004 更新为 pass。
- 验证结果：治理记录已与本轮证据对齐。
- 下一步：提交 Agent Review Submission。
- 证据：diff:TARGET:docs/05-TEST-QA/Regression-SSoT.md:R-008 marked resolved; diff:TARGET:coding-agent-harness/governance/regression/Cadence-Ledger.md:SRB-V2-008 added for R-008 fix

### [2026-06-09 09:50] - task-review

- 做了什么：R-008 fix ready for human review: HandoffPolicy FAIL propagation restored; HandoffPolicyTest, RG-002, RG-003, RG-004, and package smoke all pass.
- 验证结果：已记录
- 下一步：继续执行
- 证据：n/a
