---
sidebar_position: 9
---

# Trace 与可观测性

这一层讲的不是“以后可以接可观测平台”，而是 `ai4j-agent` 现在到底已经把什么事件产出来了、这些事件如何折叠成 span、哪些默认值会影响记录内容，以及它与 OTel / Langfuse / FlowGram 的真实边界。

如果你只把 trace 理解成“打印一下请求日志”，会错过这套设计里最重要的部分：

- Agent runtime 已经有统一事件面
- trace 不是 runtime 内部硬编码，而是 listener projection
- span 类型已经覆盖 model、tool、handoff、team task
- 但它仍然是轻量实现，不等于完整 APM / distributed tracing 平台

## 1. 先抓住 6 个关键设计决策

### 1.1 Trace 是事件投影，不是 runtime 内嵌数据结构

`BaseAgentRuntime`、`CodeActRuntime`、`SubAgentToolExecutor`、`AgentTeamEventHook` 发出的都是 `AgentEvent`。

真正把这些事件变成 `TraceSpan` 的，是：

- `AgentTraceListener`

这意味着 trace 层本质上是：

- runtime event bus
  -> listener
  -> exporter

而不是 runtime 每执行一步都直接拼 OTel span。

### 1.2 `traceConfig(...)` 单独设置并不会启用 trace

这是最容易误解的默认行为之一。

`AgentBuilder.build()` 里真正的逻辑是：

```java
AgentEventPublisher resolvedEventPublisher = eventPublisher == null ? new AgentEventPublisher() : eventPublisher;
if (traceExporter != null) {
    resolvedEventPublisher.addListener(new AgentTraceListener(traceExporter, traceConfig));
}
```

也就是说：

- 只有 `traceExporter != null` 时，Builder 才会自动挂上 `AgentTraceListener`
- 你只传 `traceConfig(...)`，不会自动生效

所以启用 trace 的最小条件不是 `traceConfig`，而是 `traceExporter`。

### 1.3 默认是“全记录”，不是“默认脱敏”

`TraceConfig.builder().build()` 的默认值是：

- `recordModelInput = true`
- `recordModelOutput = true`
- `recordToolArgs = true`
- `recordToolOutput = true`
- `recordMetrics = true`
- `maxFieldLength = 0`
- `masker = null`
- `pricingResolver = null`

因此默认行为更偏向研发调试，而不是生产最小暴露。

如果直接把它开到线上而不加 masker / truncate，prompt、tool args、tool output 都可能被完整记录。

### 1.4 Trace 默认是 best-effort，不会阻断主流程

`AgentEventPublisher.publish(...)` 在遍历 listener 时会吞掉异常：

```java
try {
    listener.onEvent(event);
} catch (Exception ignored) {
    // Listener errors should not break agent execution.
}
```

因此即使：

- exporter 写文件失败
- trace listener 内部抛异常
- OTel downstream 出错

主 Agent run 仍会继续。

这对线上稳定性是好事，但代价是你不能把 trace 成功与否当成业务成功条件。

### 1.5 当前 trace 是单机内轻量模型，不是并发隔离的运行记录系统

`AgentTraceListener` 内部维护的是一组可变字段：

- `traceId`
- `rootSpan`
- `stepSpans`
- `modelSpans`
- `toolSpans`

这些字段没有按“runId / sessionId”做分桶，`AgentEvent` 里也没有 run identifier，只有：

- `type`
- `step`
- `message`
- `payload`

这意味着同一个 Agent 实例上的同一个 trace listener，更适合：

- 单次 run
- 顺序执行

如果多个 run 并发复用同一个 listener，trace 可能互相串扰。

### 1.6 OTel 和 Langfuse 接法是 exporter bridge，不是原生 instrumentation

`OpenTelemetryTraceExporter` 和 `LangfuseTraceExporter` 的定位都是：

- 先保留 AI4J 自己的 `TraceSpan`
- 再在导出阶段投影到 OTel span

它们不是把 `BaseAgentRuntime` 改写成原生 OpenTelemetry instrumentation，也不负责跨服务 context propagation。

## 2. 当前 trace 对象图

这条线的核心对象可以先压缩成一张图：

```text
AgentBuilder
  -> AgentEventPublisher
  -> AgentTraceListener
  -> TraceExporter

AgentEvent
  -> AgentTraceListener
  -> TraceSpan / TraceSpanEvent / TraceMetrics

TraceExporter
  -> ConsoleTraceExporter
  -> InMemoryTraceExporter
  -> JsonlTraceExporter
  -> CompositeTraceExporter
  -> OpenTelemetryTraceExporter
  -> LangfuseTraceExporter
```

职责边界如下：

| 对象 | 真正职责 |
| --- | --- |
| `AgentEventPublisher` | 广播 runtime 事件 |
| `AgentTraceListener` | 把事件折叠成 span |
| `TraceConfig` | 决定记录粒度、脱敏与截断 |
| `TraceSpan` | 统一 span 数据模型 |
| `TraceExporter` | 决定 span 输出到哪里 |

## 3. Trace 是怎么被挂进 Agent 的

最小启用方式是：

```java
Agent agent = Agents.react()
        .modelClient(modelClient)
        .model("gpt-4.1")
        .traceExporter(new ConsoleTraceExporter())
        .build();
```

如果你还要控制记录策略：

```java
TraceConfig traceConfig = TraceConfig.builder()
        .maxFieldLength(4000)
        .masker(text -> text == null ? null : text.replace("SECRET", "***"))
        .build();

Agent agent = Agents.react()
        .modelClient(modelClient)
        .model("gpt-4.1")
        .traceExporter(new JsonlTraceExporter("logs/agent-trace.jsonl"))
        .traceConfig(traceConfig)
        .build();
```

这里有 3 个容易忽略的点：

1. `traceExporter(...)` 是开关
2. `traceConfig(...)` 只是配置，不是开关
3. 如果你传了自定义 `eventPublisher(...)`，Builder 会把 trace listener 加到你这份 publisher 上，而不是另起一套

## 4. `TraceSpan` 里真正有什么

`TraceSpan` 当前字段不多，但已经足够覆盖 Agent 主路径：

- `traceId`
- `spanId`
- `parentSpanId`
- `name`
- `type`
- `status`
- `startTime`
- `endTime`
- `error`
- `attributes`
- `events`
- `metrics`

这三个部分最值得分别理解：

### 4.1 `attributes`

适合保存：

- prompt 元信息
- tool args / output
- provider response metadata
- team / handoff payload 里的结构化字段

### 4.2 `events`

适合保存：

- reasoning 文本
- retry 信息
- stream delta
- team message

也就是说，并不是每个中间过程都值得单开 span。

### 4.3 `metrics`

当前统一挂载：

- `durationMillis`
- `promptTokens`
- `completionTokens`
- `totalTokens`
- `inputCost`
- `outputCost`
- `totalCost`
- `currency`

只要 `recordMetrics = false`，连 `durationMillis` 也不会被计算。

## 5. Span 类型真正代表什么

`TraceSpanType` 当前枚举值包括：

- `RUN`
- `STEP`
- `MODEL`
- `TOOL`
- `HANDOFF`
- `TEAM_TASK`
- `MEMORY`
- `AGENT_FLOW`
- `FLOWGRAM_TASK`
- `FLOWGRAM_NODE`

但要分清“类型存在”和“当前 AgentTraceListener 会不会产出”。

### `AgentTraceListener` 当前主要产出

- `RUN`
- `STEP`
- `MODEL`
- `TOOL`
- `HANDOFF`
- `TEAM_TASK`
- `MEMORY`

### 其它类型的来源

- `AGENT_FLOW`
  - 来自 `AgentFlowTraceBridge`
- `FLOWGRAM_TASK` / `FLOWGRAM_NODE`
  - 用于 FlowGram / AgentFlow 侧的投影体系，不是当前 Agent runtime 直接发出来的 span

## 6. 从事件到 span 的真实映射链

### 6.1 Root run span 何时开始

`AgentTraceListener` 不是在 `Agent.run(...)` 调用瞬间就建 root span。

它是在收到第一条 `STEP_START` 时才：

1. 生成 `traceId`
2. 创建 root `RUN` span
3. 再创建当前 step span

所以当前 run span 的起点定义是：

- 第一个 runtime step 开始

不是：

- Agent 对象创建时
- 用户请求进入 API 边界时

### 6.2 Step span 如何结束

每个 `STEP_START` 创建一个 step span。

对应的 `STEP_END` 会：

- 结束该 step span
- 清理该 step 的 tool span fallback 记录

如果 root span 已经在 `FINAL_OUTPUT` 时结束，且 step spans 也都清空了，listener 会 `reset()`。

### 6.3 Model span 的流式和非流式差异

`MODEL_REQUEST` 会创建 `MODEL` span。

之后有两种路径：

#### 非流式

- runtime 发最终 `MODEL_RESPONSE(payload=rawResponse)`
- listener 用 payload 补齐：
  - `responseId`
  - `responseModel`
  - `finishReason`
  - `usage`
- 累加 metrics
- 关闭 model span

#### 流式

- 中间 delta 会作为 `MODEL_RESPONSE(message=delta, payload=null)`
- listener 把它挂成 `model.response.delta` event
- 直到最终原始 payload 到达
- 才真正关闭 model span

所以当前 stream trace 不是“每个 delta 一个 span”，而是：

- 一个 `MODEL` span
- 若干 `model.response.delta` events

### 6.4 Reasoning 和 retry 为什么不是独立 span

`MODEL_REASONING` 与 `MODEL_RETRY` 都会作为当前 model span 的内部 event 挂上去：

- `model.reasoning`
- `model.retry`

这是合理的，因为它们本质上属于同一次模型调用内部过程，而不是单独的外部操作。

### 6.5 Tool span 何时创建、何时结束

`TOOL_CALL` 会创建 tool span。

如果 payload 是 `AgentToolCall`，且 `recordToolArgs = true`，attributes 会包含：

- `tool`
- `callId`
- `arguments`

`TOOL_RESULT` 到来时，listener 会：

- 根据 `callId` 找回 span
- 写入 output / result / stdout / error
- 根据 payload 判断 status
- 结束 tool span

### 6.6 Handoff 和 team task 不是附属消息，而是独立 span

`HANDOFF_START / HANDOFF_END` 会聚合成一个 `HANDOFF` span。

`TEAM_TASK_CREATED / TEAM_TASK_UPDATED / TEAM_MESSAGE` 会聚合成 `TEAM_TASK` span + 内部 events。

这意味着在可观测层里：

- subagent handoff
- team task lifecycle

已经不再被当成普通 tool message，而是明确的一等对象。

## 7. 运行时会记录哪些字段

### 7.1 模型输入字段

当 `recordModelInput = true` 时，`MODEL` span 常见 attributes 包括：

- `model`
- `systemPrompt`
- `instructions`
- `items`
- `tools`
- `toolChoice`
- `parallelToolCalls`
- `temperature`
- `topP`
- `maxOutputTokens`
- `reasoning`
- `store`
- `stream`
- `user`
- `extraBody`

### 7.2 模型输出字段

当 `recordModelOutput = true` 时：

- stream 增量进入 `model.response.delta` event
- 最终 payload 进入 `output`
- root run span 会写 `finalOutput`

此外 provider payload 里可解析出来的：

- `responseId`
- `responseModel`
- `finishReason`

也会单独抽到 attributes。

### 7.3 工具字段

当 `recordToolArgs = true` 时，tool span 会保留参数。

当 `recordToolOutput = true` 时：

- 普通工具写 `output`
- CodeAct 工具结果可能写：
  - `result`
  - `stdout`
  - `error`

### 7.4 Metrics 字段

只要响应里带 `usage`，且 `recordMetrics = true`，listener 就会尝试写：

- prompt tokens
- completion tokens
- total tokens
- duration

如果再配 `TracePricingResolver`，才会继续估算 cost。

一个最小配置例子：

```java
TraceConfig traceConfig = TraceConfig.builder()
        .pricingResolver(model -> {
            if ("gpt-4.1".equals(model)) {
                return TracePricing.builder()
                        .inputCostPerMillionTokens(2.0D)
                        .outputCostPerMillionTokens(8.0D)
                        .currency("USD")
                        .build();
            }
            return null;
        })
        .build();

Agent agent = Agents.react()
        .modelClient(modelClient)
        .model("gpt-4.1")
        .traceExporter(new JsonlTraceExporter("logs/agent-trace.jsonl"))
        .traceConfig(traceConfig)
        .build();
```

这里的价格单位是“每百万 token”。SDK 不内置默认价格表，因为模型价格经常变化；如果 resolver 返回 `null`，对应模型只记录 token，不估算 cost。

## 8. 默认值和容易误判的语义

### 8.1 `pricingResolver = null` 时不会自动算钱

即使 provider 返回了 usage，默认也只会有 token，不会有 cost。

### 8.2 `maxFieldLength = 0` 代表不截断

很多人会误以为 0 是“禁用字段”。

这里的 0 表示：

- 不做长度裁剪

### 8.3 `safeValue(...)` 的顺序是：序列化 -> 脱敏 -> 截断

非字符串对象会先被 JSON 序列化，再交给 masker，再根据 `maxFieldLength` 截断。

这意味着 masker 看到的通常是最终字符串，而不是 Java 对象结构。

### 8.4 `recordMetrics = false` 会连 duration 一起关掉

`finishSpan(...)` 里只有在 `recordMetrics = true` 时才会补 `durationMillis`。

所以如果你关 metrics，不只是 token / cost 没了，连时长也不会写。

## 9. 当前实现里最值得知道的边界

### 9.1 Trace listener 更适合顺序执行，不适合同实例并发混跑

当前 listener 没有 runId 分桶，内部状态也不是“每次 run 独立一份”。

因此如果你打算：

- 多线程并发调用同一个 Agent
- 并共享同一个 `AgentTraceListener`

就要预期 trace 可能串线。

### 9.2 Listener 错误默认被吞掉

这是为了不影响业务，但也意味着：

- trace 文件没写进去
- exporter downstream 挂了

你可能只会在外部平台缺数据，而不会在 Agent run 里收到异常。

### 9.3 `MEMORY` span 当前 listener 已支持，但核心 memory 实现并未主动发事件

这是这页最需要纠正的一个事实。

虽然 `AgentEventType` 里有：

- `MEMORY_COMPRESS`

而且 `AgentTraceListener` 也会把它映射成 `MEMORY` span，但当前仓库里的生产代码并没有主动发这类事件；现有命中主要来自测试。

这意味着默认情况下：

- 你不会自动在普通 Agent run 里看到 memory compress trace

除非：

- 你自己额外发布这类事件
- 或后续实现补上了内存压缩事件发射

### 9.4 Tool span fallback key 不是强一致 ID 体系

如果 `callId` 缺失，listener 会退回用：

- `step + ":" + toolName`

或按 step 记录一个 fallback tool span。

正常情况下 runtime 会尽量规范化 tool call，但这套 fallback 只能算兼容兜底，不是强约束的唯一键设计。

### 9.5 OTel exporter 不是实时逐 span 推送父子链

`AbstractOpenTelemetryTraceExporter` 为了恢复 parent-child 关系，会把已完成 span 先放进 `pendingSpans`。

只有当父 span 也能导出时，才真正发出去。

由于 root `RUN` span 通常最后才结束，这意味着：

- 很多 child spans 会先缓存
- 等 root span 导出后再一起 flush

所以它更像“按 trace 完整性导出”，不是“每个 span 一结束就立刻实时上报”。

## 10. Exporter 的真实适用边界

### 10.1 `ConsoleTraceExporter`

适合：

- 本地开发
- 快速看有没有 step / model / tool / handoff

不适合：

- 留档
- 查询
- 多 run 聚合分析

### 10.2 `InMemoryTraceExporter`

适合：

- 单元测试
- 集成测试断言
- 本地内存采样

它只是把 spans 放进一个 list，没有索引、没有淘汰、没有聚合。

### 10.3 `JsonlTraceExporter`

适合：

- 本地归档
- 离线分析
- 调试后导入别的系统

当前行为是：

- 追加写文件
- 自动创建父目录
- 不做 rotation
- 每个 span 一行 JSON

### 10.4 `CompositeTraceExporter`

适合：

- 同时打控制台、写文件、喂平台

它只是扇出，不做失败隔离策略以外的高级治理。

### 10.5 `OpenTelemetryTraceExporter`

适合：

- 已有 OTel collector / OTLP pipeline
- 想把 AI4J trace 纳入现有观测体系

它会写入：

- `ai4j.trace_id`
- `ai4j.span_id`
- `ai4j.parent_span_id`
- `ai4j.span_type`
- `ai4j.span_status`
- `ai4j.error`
- `ai4j.attr.*`
- `ai4j.metrics.*`
- `ai4j.event.*`
- `gen_ai.usage.input_tokens`
- `gen_ai.usage.output_tokens`

但要记住，它是 projection bridge，不负责完整的分布式 tracing 语义。

### 10.6 `LangfuseTraceExporter`

适合：

- 你已经走 OTel pipeline
- 但上游观测平台想直接接 Langfuse

它不是直连 Langfuse 私有 SDK，而是：

- 基于 OTel exporter
- 额外写 Langfuse 可识别 attributes

例如：

- `langfuse.observation.type`
- `langfuse.observation.level`
- `langfuse.observation.input`
- `langfuse.observation.output`
- `langfuse.observation.model`
- `langfuse.observation.model_parameters`
- `langfuse.observation.usage_details`
- `langfuse.observation.cost_details`
- `langfuse.observation.metadata`
- `langfuse.trace.name`
- `langfuse.trace.output`
- `langfuse.trace.metadata`

## 11. 线上最少要做的两件事

如果你把 trace 带到生产，至少应该补这两件事：

### 11.1 脱敏

```java
TraceConfig traceConfig = TraceConfig.builder()
        .masker(text -> text == null
                ? null
                : text.replaceAll("(?i)api[_-]?key\\s*[:=]\\s*[^,\\s]+", "apiKey=***"))
        .build();
```

### 11.2 截断

```java
TraceConfig traceConfig = TraceConfig.builder()
        .maxFieldLength(4000)
        .build();
```

否则 prompt、tool output、extraBody 这些字段很容易把 trace 体积撑爆。

## 12. 与 AgentFlow / FlowGram 的关系

这一层一定要分开。

### Agent runtime 这条线

- 事件来源是 `AgentEvent`
- 投影器是 `AgentTraceListener`
- span 类型以 `RUN / STEP / MODEL / TOOL / HANDOFF / TEAM_TASK` 为主

### AgentFlow / FlowGram 这条线

- 事件来源不是 `AgentEvent`
- 而是 `AgentFlowTraceContext` + chat/workflow events
- 投影器是 `AgentFlowTraceBridge`
- 主 span 类型更偏 `AGENT_FLOW`

所以 FlowGram 前端视图不应该直接等同于 Agent runtime trace。

更准确地说：

- Agent trace 是后端运行视角
- FlowGram trace projection 是画布与节点视角

如果你在看 FlowGram 集成，继续读 [Flowgram Runtime](/docs/flowgram/runtime) 会更合适。

## 13. 排障时应该怎么读 trace

推荐按这条顺序看：

1. `RUN`
   看整体状态、总时长、最终输出。
2. `STEP`
   看有没有异常回环、步数是否异常增长。
3. `MODEL`
   看 prompt、response、reasoning、retry、usage。
4. `TOOL`
   看参数、输出、错误是否合理。
5. `HANDOFF`
   看 subagent 委派是否发生、在哪一层失败。
6. `TEAM_TASK`
   看团队任务板有没有卡死在某状态。

如果第一眼就直接钻 provider raw payload，通常会慢很多。

## 14. 推荐阅读源码顺序

- `ai4j-agent/src/main/java/io/github/lnyocly/ai4j/agent/event/AgentEvent.java`
- `ai4j-agent/src/main/java/io/github/lnyocly/ai4j/agent/event/AgentEventType.java`
- `ai4j-agent/src/main/java/io/github/lnyocly/ai4j/agent/event/AgentEventPublisher.java`
- `ai4j-agent/src/main/java/io/github/lnyocly/ai4j/agent/AgentBuilder.java`
- `ai4j-agent/src/main/java/io/github/lnyocly/ai4j/agent/trace/AgentTraceListener.java`
- `ai4j-agent/src/main/java/io/github/lnyocly/ai4j/agent/trace/TraceConfig.java`
- `ai4j-agent/src/main/java/io/github/lnyocly/ai4j/agent/trace/AbstractOpenTelemetryTraceExporter.java`
- `ai4j-agent/src/main/java/io/github/lnyocly/ai4j/agent/trace/OpenTelemetryTraceSupport.java`
- `ai4j-agent/src/main/java/io/github/lnyocly/ai4j/agent/trace/LangfuseTraceExporter.java`
- `ai4j-agent/src/main/java/io/github/lnyocly/ai4j/agent/trace/AgentFlowTraceBridge.java`

## 15. 继续阅读

1. [Agent Architecture](/docs/agent/architecture)
2. [Tools and Registry](/docs/agent/tools-and-registry)
3. [SubAgent 与 Handoff Policy](/docs/agent/subagent-handoff-policy)
4. [Agent Teams](/docs/agent/agent-teams)
5. [Flowgram Runtime](/docs/flowgram/runtime)
