# AI4J CLI Regression R-009 Fix

## Task ID

`2026-06-09-ai4j-cli-regression-r-009-fix-8b01af7e`

## 创建日期

2026-06-09

## 一句话结果

修复 R-009，使 `ai4j-cli` 直接全量测试重新通过，并把 RG-004 只剩 R-008 上游阻断的状态同步到回归 SSoT。

## 完成后能得到什么

完成后，下一轮开发可以把 CLI/TUI/ACP 本模块直接测试作为可用本地基线继续使用：ACP 流式正文不会再混入 auto-stop/block 等 loop-control 文案，JLine 多行 transcript 测试也能正确处理真实终端 ANSI 样式。回归记录会明确 R-009 已关闭，RG-004 的 broad `-am` 缺口只剩既有 R-008。

## 交付物

- 可见产物：R-009 修复代码、测试断言更新、Regression SSoT/Cadence Ledger 更新、任务 walkthrough。
- 修改位置：`ai4j-cli/src/main/java/io/github/lnyocly/ai4j/cli/acp/AcpJsonRpcServer.java`、`ai4j-cli/src/test/java/io/github/lnyocly/ai4j/cli/JlineShellTerminalIOTest.java`、`docs/05-TEST-QA/`、`coding-agent-harness/governance/regression/`。
- 验证证据：目标失败类、直接 CLI 全套、broad `-am` R-008 复核、monorepo package 烟测。

## 第一眼应该看什么

先读 `progress.md` 的命令证据，再看 `review.md` 的残余风险路由；代码 diff 很小，核心在 ACP loop-control 事件不再映射为 `agent_message_chunk`。

## 边界

- 范围内：R-009 的 ACP/JLine 失败、CLI 直接测试、RG-004/RG-007 回归记录。
- 范围外：R-008 agent handoff policy 修复、插件生态功能继续开发、docs-site 内容重构。
- 停止条件：如果 CLI 直接全套仍失败或需要改变 ACP 对外协议字段，必须先记录 blocker 并回到用户确认。

## 完成判断

- `AcpCommandTest` 和 `JlineShellTerminalIOTest` 目标失败类通过。
- `mvn -pl ai4j-cli -DfailIfNoTests=false -DskipTests=false test` 通过。
- `mvn -DskipTests package` 通过。
- Regression SSoT 和 Cadence Ledger 同步 R-009 resolved，R-008 保持 open。

## 执行合同

- Owner：coordinator
- 生命周期状态：进行中
- 必需文件：`INDEX.md`、`task_plan.md`、`execution_strategy.md`、`visual_map.md`、
  `progress.md`、`findings.md`、`review.md`
- 完成条件：验证证据必须记录到 `progress.md`

## 当前下一步

补齐 review/walkthrough，并提交待人工确认。
