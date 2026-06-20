# AI4J agent handoff policy R-008 fix - 发现记录

本文件记录任务执行中形成的判断、事实和技术决策。它不是审查报告；阻塞性问题请写入 `review.md`。

## 研究发现

### R-008 root cause

- 背景：`HandoffPolicyTest` 期望 allowed-tools 拒绝和 nested max-depth 违规直接让 `Agent.run` 失败，但实际运行没有抛出异常。
- 发现：`SubAgentToolExecutor` 已经能生成拒绝原因并抛出异常，但 `BaseAgentRuntime.executeTool` 会捕获普通 `Exception` 并统一转成 `TOOL_ERROR`，导致 `HandoffPolicy.FAIL` 语义被吞掉。
- 影响：修复不应改变普通工具异常的容错输出；只需要让 handoff policy 的 fail-fast 类异常穿透 runtime。
- 后续：已新增 `HandoffPolicyException` 并在 runtime 中专门 rethrow。

## 技术决策

| 决策 | 选择 | 原因 | 替代方案 | 状态 |
| --- | --- | --- | --- | --- |
| Handoff policy fail-fast marker | 新增 `HandoffPolicyException extends IllegalStateException` | 保持 `catch (IllegalStateException)` 兼容测试，同时避免让所有普通 `IllegalStateException` 穿透 runtime | 让 `BaseAgentRuntime` rethrow 所有 `IllegalStateException`，但会误伤普通工具错误容错 | accepted |
| Ordinary tool errors | 保持 `TOOL_ERROR` 输出 | 现有 agent / coding / CLI 依赖普通工具错误可回填模型上下文 | 改成所有工具异常 fail fast，风险过宽 | accepted |

## 待确认问题

| 问题 | 当前判断 | Owner | 截止点 |
| --- | --- | --- | --- |
| 是否需要 docs-site 更新 handoff policy 页面 | 不需要；当前页面已有 `FAIL / FALLBACK_TO_PRIMARY` 合同，本轮是实现回归修复 | coordinator | closeout |
