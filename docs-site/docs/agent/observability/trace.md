# Trace

`Trace` 是 `Agent` 章节里把“任务到底怎么跑起来的”讲清楚的关键页面。

没有 trace，长任务的问题通常只能靠猜：

- 模型到底什么时候发起了 tool call
- 哪一步最慢
- 为什么 handoff 失败了
- team 的任务状态卡在了哪里

## 1. 真实代码路径

关键包：

- `ai4j-agent/src/main/java/io/github/lnyocly/ai4j/agent/trace`

核心类：

- `AgentTraceListener`
- `TraceConfig`
- `TraceExporter`
- `TraceSpan`
- `TraceSpanEvent`
- `TraceMetrics`
- `TraceSpanType`
- `ConsoleTraceExporter`
- `InMemoryTraceExporter`
- `JsonlTraceExporter`
- `OpenTelemetryTraceExporter`
- `LangfuseTraceExporter`

## 2. 启用方式非常直接

在 `AgentBuilder` 里，只要你设置：

```java
.traceExporter(exporter)
```

`build()` 时就会自动把 `AgentTraceListener` 挂到 `AgentEventPublisher`。

这意味着 trace 不是额外旁路，而是 runtime 的正式事件消费链。

## 3. 它记录什么

当前 trace 关注的不是“只有一层大日志”，而是分层 span：

- `RUN`
- `STEP`
- `MODEL`
- `TOOL`
- `HANDOFF`
- `TEAM_TASK`

同时还会记录内部事件，例如：

- `model.reasoning`
- `model.retry`
- `model.response.delta`

这样既能看主链，也能看中间细节。

## 4. 事件如何映射到 trace

`AgentTraceListener` 会消费 runtime 事件，例如：

- `STEP_START / STEP_END`
- `MODEL_REQUEST / MODEL_RESPONSE`
- `MODEL_REASONING / MODEL_RETRY`
- `TOOL_CALL / TOOL_RESULT`
- `HANDOFF_START / HANDOFF_END`
- `TEAM_TASK_CREATED / TEAM_TASK_UPDATED`
- `MEMORY_COMPRESS`
- `FINAL_OUTPUT`
- `ERROR`

它不是“事后解析日志”，而是在运行时同步折叠成 span。

## 5. 为什么这一层重要

- 对单 Agent：能看清 step loop 和 tool path
- 对 handoff：能看清委派给谁、多久返回、是否失败
- 对 team：能看清任务创建、更新、完成链路
- 对平台：能把本地调试、文件导出、OTel、Langfuse 接到同一套事件语义上

这就是它对面试讲解和线上排障都很有价值的原因。

## 6. Exporter 怎么选

### `ConsoleTraceExporter`

适合本地开发、快速观察。

### `InMemoryTraceExporter`

适合测试断言和临时调试。

### `JsonlTraceExporter`

适合把每次运行落成可回放文件。

### `OpenTelemetryTraceExporter`

适合接已有 OTel pipeline。

### `LangfuseTraceExporter`

适合把 AI4J trace 接到 Langfuse 观察面。

## 7. 和 Flowgram 观测面的关系

`Agent` trace 解决的是 runtime 事件抽象。

`Flowgram` 侧还有自己面向任务和节点的 report / trace projection。

两者相关，但不应混成一个概念：

- `Agent trace`：更通用的 runtime 观测语义
- `Flowgram trace view`：更偏前端工作流任务展示

## 8. 推荐下一步

1. [Memory and State](/docs/agent/memory-and-state)
2. [Teams](/docs/agent/orchestration/teams)
3. [Reference Core Classes](/docs/agent/reference-core-classes)
