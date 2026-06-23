# Agent observability enhancement

## Task ID

`2026-06-22-agent-observability-enhancement-57c03f6b`

## 创建日期

2026-06-22

## 一句话结果

让 ai4j-agent / ai4j-coding / ai4j-cli / ACP 的运行观测字段统一到 runId / sessionId / turnId / eventId，同一套事实在 trace、session event、CLI 输出里可稳定关联。

## 完成后能得到什么

完成后，开发者可以直接从 agent trace、Langfuse 投影、CLI/ACP 会话事件和任务日志中串起一次运行的完整链路，定位模型请求、tool call、memory 压缩、会话重绑和恢复事件，不再依赖零散字段或单点日志。这个结果用于调试 agent、排查会话恢复问题、对齐 CLI 与 ACP 的状态显示，并为后续更完整的 observability / sandbox / session 诊断继续打底。

## 交付物

- 可见产物：统一 correlation 链路的代码修改、任务本地 review / walkthrough / findings / lesson 候选收口
- 修改位置：`ai4j-agent/`、`ai4j-coding/`、`ai4j-cli/`
- 验证证据：真实 Maven 回归通过

## 第一眼应该看什么

先看 `task_plan.md`、`review.md`、`progress.md`，然后看 `ai4j-agent/src/main/java/io/github/lnyocly/ai4j/agent/runtime/BaseAgentRuntime.java`、`ai4j-agent/src/main/java/io/github/lnyocly/ai4j/agent/trace/LangfuseTraceExporter.java`、`ai4j-cli/src/main/java/io/github/lnyocly/ai4j/cli/session/DefaultCodingSessionManager.java`。

## 边界

- 范围内：观测 correlation 贯通、Langfuse 投影、CLI/ACP 事件归一化、真实回归验证。
- 范围外：docs-site 改写、memory 算法重构、其他未触及模块。
- 停止条件：如果 runId / sessionId 链路再断、或真实测试失败，必须回到 coordinator / reviewer 复核。

## 完成判断

1. `runId/sessionId/turnId/eventId` 在 agent runtime / trace / CLI / ACP 事件中可追踪。
2. Langfuse projection 不再吞掉 correlation 字段。
3. 真实 Maven 回归通过。
4. task-local review / walkthrough / findings / lesson 文件已收口。

## 执行合同

- Owner：coordinator
- 生命周期状态：审查中
- 必需文件：`INDEX.md`、`task_plan.md`、`execution_strategy.md`、`visual_map.md`、`progress.md`、`findings.md`、`review.md`
- 完成条件：验证证据必须记录到 `progress.md`

## 当前下一步

检查收口文件是否仍含模板占位，然后刷新生成索引并等待人工确认。
