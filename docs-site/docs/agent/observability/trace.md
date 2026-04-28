# Trace

`Trace` 是把 Agent 执行过程从“只能猜”变成“可以观察”的关键能力。

没有 trace 时，长任务最常见的问题通常是这些：

- 模型到底在第几步出错
- 工具调用有没有真的执行
- handoff 为什么失败
- team 任务卡在哪个阶段
- 输出质量下降是 prompt 问题、tool 问题还是 memory 问题

`ai4j-agent` 的 trace 不是额外大日志，而是把 runtime 事件折叠成有层级的 span。

## 1. 代码路径

主路径：

- `ai4j-agent/src/main/java/io/github/lnyocly/ai4j/agent/trace`

关键对象：

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

## 2. Trace 如何接进 Agent

在 `AgentBuilder.build()` 中，只要你设置了：

```java
.traceExporter(exporter)
```

Builder 就会自动把：

- `AgentTraceListener`

挂到：

- `AgentEventPublisher`

这意味着 trace 不是外围“事后读日志”，而是 runtime 正式事件消费链的一部分。

## 3. 真实执行模型：事件 -> Span

`AgentTraceListener` 的核心职责是消费 `AgentEvent`，并将其折叠为分层 span。

### 3.1 根 span

第一次收到 `STEP_START` 时，listener 会创建：

- 一个 `RUN` root span
- 对应的 `traceId`

### 3.2 Step span

每个 step 会生成一个 `STEP` span。

### 3.3 Model span

收到 `MODEL_REQUEST` 时创建 `MODEL` span；
收到最终 `MODEL_RESPONSE` payload 后结束该 span。

### 3.4 Tool span

收到 `TOOL_CALL` 时创建 `TOOL` span；
收到 `TOOL_RESULT` 时结束该 span。

### 3.5 Handoff / Team span

handoff 和 team 任务也会分别映射到自己的 span 类型，而不是混进普通 tool span 里。

## 4. 事件到 span 的主要映射

`AgentTraceListener` 当前会处理这些 runtime 事件：

- `STEP_START`
- `STEP_END`
- `MODEL_REQUEST`
- `MODEL_RESPONSE`
- `MODEL_RETRY`
- `MODEL_REASONING`
- `TOOL_CALL`
- `TOOL_RESULT`
- `HANDOFF_START`
- `HANDOFF_END`
- `TEAM_TASK_CREATED`
- `TEAM_TASK_UPDATED`
- `TEAM_MESSAGE`
- `MEMORY_COMPRESS`
- `FINAL_OUTPUT`
- `ERROR`

这说明 trace 覆盖的不是单一模型链路，而是完整运行时语义。

## 5. Span 层级长什么样

典型单 Agent 运行可以抽象为：

```text
RUN
  -> STEP
    -> MODEL
    -> TOOL
  -> STEP
    -> MODEL
```

如果有 handoff 或 team，就会再往下出现：

```text
RUN
  -> STEP
    -> TOOL
      -> HANDOFF
  -> TEAM_TASK
```

这也是为什么 trace 比平铺日志更适合解释长任务执行链。

## 6. `AgentTraceListener` 实际记录了什么

### 6.1 模型输入输出

在 `MODEL_REQUEST` / `MODEL_RESPONSE` 路径中，listener 会根据 `TraceConfig` 决定是否记录：

- `systemPrompt`
- `instructions`
- `items`
- `tools`
- `toolChoice`
- `parallelToolCalls`
- 采样参数
- 原始输出或输出增量

### 6.2 Tool 参数和结果

在 `TOOL_CALL` / `TOOL_RESULT` 路径中，listener 会记录：

- 工具名
- `callId`
- 参数
- 输出
- CodeAct 下的 `stdout/result/error`

### 6.3 Handoff / Team 状态

handoff 和 team 不是普通 message，而是独立 span 与 span events。这样才能看清：

- 委派给了谁
- 尝试了几次
- 用了多长时间
- 为什么失败或回退

### 6.4 Metrics

如果 `TraceConfig.recordMetrics = true`，listener 还会尝试累积：

- duration
- prompt tokens
- completion tokens
- total tokens
- input/output/total cost

成本计算依赖 `TracePricingResolver`。

## 7. `TraceConfig` 的作用

源码：

- `trace/TraceConfig`

当前最关键的开关包括：

- `recordModelInput`
- `recordModelOutput`
- `recordToolArgs`
- `recordToolOutput`
- `recordMetrics`
- `maxFieldLength`
- `masker`
- `pricingResolver`

这说明 trace 不是只能“全开”或“全关”，而是可以按信息敏感度和观测成本做裁剪。

## 8. Exporter 的边界

`TraceExporter` 接口非常窄：

```java
public interface TraceExporter {
    void export(TraceSpan span);
}
```

这意味着 exporter 的职责只是：

> 当某个 span 完成时，把这份 span 输出到某个目标。

它不负责 span 组织逻辑；span 组织逻辑在 `AgentTraceListener` 中。

## 9. 内置 Exporter 怎么选

### 9.1 `ConsoleTraceExporter`

适合：

- 本地开发
- 快速查看执行链

### 9.2 `InMemoryTraceExporter`

适合：

- 单元测试
- 断言 span 结构
- 临时调试

### 9.3 `JsonlTraceExporter`

适合：

- 本地归档
- 文件回放
- 离线分析

它的实现是每个完成的 span 以 JSONL 追加写入文件。

### 9.4 `OpenTelemetryTraceExporter`

适合：

- 已有 OTel pipeline
- 统一接入现有 observability 基础设施

### 9.5 `LangfuseTraceExporter`

适合：

- AI 应用观测
- 需要在 Langfuse 视图中查看运行链

## 10. 一个重要的实现细节：Exporter 在 span 完成时同步输出

`AgentTraceListener.finishSpan(...)` 会在 span 结束时直接调用：

```java
exporter.export(span)
```

这意味着 exporter 应被视为运行时关键路径的一部分。如果 exporter 很慢、很不稳定，可能会影响主执行链的收口体验。

因此生产环境里通常应考虑：

- IO 成本
- 批量化或异步包装
- 故障隔离

## 11. `MEMORY_COMPRESS` 事件的现状

`AgentEventType` 中已经定义了 `MEMORY_COMPRESS`，`AgentTraceListener` 也能消费它并创建 memory span。

但默认 memory 实现并不会主动发这个事件。

这意味着如果你想观测压缩行为，通常需要在自定义 compressor / memory 层主动上报，否则 trace 里不会自动出现完整的压缩过程。

## 12. 安全与脱敏

`TraceConfig.masker` 和 `maxFieldLength` 的存在不是装饰项，而是生产环境的必要控制：

- 输入输出可能包含敏感数据
- tool 参数可能包含路径、密钥、用户输入
- 原始模型返回可能非常大

因此生产 trace 一般不应无条件记录全部原文。

## 13. Trace 不等于所有观测

Trace 解决的是“运行时链路如何发生”。

它不自动替代：

- 业务指标
- 服务级日志
- 基础设施监控
- UI 层任务可视化

特别是在 Flowgram 场景下，要区分：

- `Agent trace`：runtime 语义
- `Flowgram trace/report`：节点工作流展示语义

## 14. 最小启用示例

```java
Agent agent = Agents.react()
        .modelClient(modelClient)
        .model("gpt-4.1")
        .traceExporter(new JsonlTraceExporter("tmp/agent-trace.jsonl"))
        .traceConfig(TraceConfig.builder()
                .recordModelInput(true)
                .recordModelOutput(true)
                .recordToolArgs(true)
                .recordToolOutput(true)
                .maxFieldLength(4000)
                .build())
        .build();
```

这个例子对应的真实效果是：

- Builder 自动挂接 `AgentTraceListener`
- 每个完成的 span 都会被写入 JSONL 文件
- 输入输出和 tool 参数记录受 `TraceConfig` 控制

## 15. 继续阅读

1. [Architecture](/docs/agent/architecture)
2. [Memory and State](/docs/agent/memory-and-state)
3. [Agent Teams](/docs/agent/agent-teams)
4. [Reference Core Classes](/docs/agent/reference-core-classes)
