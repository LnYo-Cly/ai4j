# AI4J agent handoff policy R-008 fix

## Task ID

`2026-06-09-ai4j-agent-handoff-policy-r-008-fix-8b30bc13`

## 创建日期

2026-06-09

## 一句话结果

修复 R-008，使 `HandoffPolicy` 的 `allowedTools` 与 `maxDepth` 失败重新按 `FAIL` 语义穿透 agent 运行，并解除 `ai4j-agent` 本地回归阻塞。

## 完成后能得到什么

完成后，`ai4j-agent` 的子代理 handoff 安全策略会回到可验证状态：策略拒绝不再被 ReAct 主循环包装成普通 `TOOL_ERROR` 后继续执行，`HandoffPolicyTest` 的 allowed-tools 与 nested max-depth 场景应恢复通过。回归记录会同步关闭 R-008，并让 `ai4j-agent -am` 与依赖它的 `ai4j-cli -am` 重新获得可用的本地门禁证据。

## 交付物

- 可见产物：R-008 修复提交、Regression SSoT / Cadence Ledger 更新、walkthrough 与 review 材料。
- 修改位置：`ai4j-agent/src/main/java`、`ai4j-agent/src/test/java`、`docs/05-TEST-QA/`、`coding-agent-harness/governance/regression/`。
- 验证证据：`HandoffPolicyTest`、`ai4j-agent` 本地测试、`ai4j-cli -am` 广义门禁、package smoke、harness status。

## 第一眼应该看什么

先读 `task_plan.md` 的范围和验收标准，再看 `progress.md` 的命令证据；代码侧优先查看 `BaseAgentRuntime` 与 `SubAgentToolExecutor` 的异常传播路径。

## 边界

- 范围内：`HandoffPolicy.FAIL` 类策略失败传播、最小回归测试、R-008 治理记录。
- 范围外：插件生态、docs-site 功能文档重写、Agent Team 行为改造、普通工具错误策略重构。
- 停止条件：若修复需要改变普通工具异常转 `TOOL_ERROR` 的公共语义，必须停下重新评估。

## 完成判断

- `HandoffPolicyTest` 全部通过。
- `mvn -pl ai4j-agent -am -DskipTests=false test` 通过。
- `mvn -pl ai4j-cli -am -DfailIfNoTests=false -DskipTests=false test` 不再被 R-008 阻塞。
- R-008 在 Regression SSoT 中关闭，Cadence Ledger 写入本次证据。
- task 进入 review 队列，等待人工确认。

## 执行合同

- Owner：coordinator
- 生命周期状态：进行中
- 必需文件：`INDEX.md`、`task_plan.md`、`execution_strategy.md`、`visual_map.md`、
  `progress.md`、`findings.md`、`review.md`
- 完成条件：验证证据必须记录到 `progress.md`

## 当前下一步

实现 handoff 策略失败专用异常，并让 `BaseAgentRuntime` 对该异常 fail fast。
