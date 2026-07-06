# agent trace streaming error structure

## Task ID

`2026-07-06-agent-trace-streaming-error-structure-16d40045`

## 创建日期

2026-07-06

## 一句话结果

让流式模型文本在 replay capture 里可回放，并让工具错误默认带上 `errorType`。

## 完成后能得到什么

`ai4j-agent` 的 Node I/O capture 会把流式模型消息累积到 `NodeIoRecord.outputText`，同时保留
最终 raw response 在 `outputs` 里；`NodeReplayer` 的 mock replay 能优先使用 raw response，
缺失时回退到 `outputText`。`BaseAgentRuntime` 产出的 `TOOL_ERROR` 会带上
`errorType/error/tool/callId`，方便模型和排障消费，而不是依赖堆栈文本。对应的单测与
docs-site 说明已经同步，下一轮 agent 可以直接据此恢复、重放或排查。

## 交付物

- 可见产物：`ai4j-agent` replay / runtime 代码与 `docs-site` 说明页。
- 修改位置：`NodeIoRecord`、`IoCaptureAgentListener`、`NodeReplayer`、`JsonlIoCaptureSink`、
  `BaseAgentRuntime`、`NodeIoCaptureReplayTest`、`ExtensionAgentToolsTest`、`docs-site/docs/agent/*`。
- 验证证据：`mvn -pl ai4j-agent -am "-Dtest=NodeIoCaptureReplayTest,ExtensionAgentToolsTest" -DfailIfNoTests=false -DskipTests=false test`；
  `mvn -pl ai4j-agent -am -DskipTests=false test`；
  `npm run build` in `docs-site/`；
  `mvn -DskipTests package`。

## 第一眼应该看什么

`progress.md`、`walkthrough.md`、`NodeIoCaptureReplayTest`、`ExtensionAgentToolsTest`、
`docs-site/docs/agent/replay-recovery-audit.md`、`docs-site/docs/agent/tools-and-registry.md`。

## 边界

- 范围内：replay capture / replay / JSONL 读写 / tool error payload / docs-site 说明 / 本地验证。
- 范围外：live provider smoke、新的抽象层、其它 agent runtime 行为、RAG/检索改造。
- 停止条件：若 streaming / raw response 语义与现有 capture contract 冲突，必须回到 coordinator
  确认。

## 完成判断

- streaming text 已累积到 `NodeIoRecord.outputText`，JSONL 可 round-trip。
- `NodeReplayer.replayModelMock(...)` 能优先 raw response，缺失时回退到 `outputText`。
- `TOOL_ERROR` 默认暴露 `errorType`，且没有 stack trace 字段。
- ai4j-agent 全量测试、docs-site build、monorepo package smoke 均通过。

## 执行合同

- Owner：coordinator
- 生命周期状态：已完成
- 必需文件：`INDEX.md`、`task_plan.md`、`visual_map.md`、`progress.md`、`walkthrough.md`
- 完成条件：验证证据必须记录到 `progress.md`

## 当前下一步

收口 task 包并同步 Regression / Cadence 记录。
