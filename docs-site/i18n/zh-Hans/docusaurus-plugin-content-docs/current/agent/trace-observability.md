---
sidebar_position: 9
---

# Trace 与可观测性（轻量链路追踪）

这一页讲的是 `ai4j-agent` 当前已经落地的 trace 能力，不是泛泛而谈“以后可以怎么做”。

重点回答四个问题：

- Agent runtime 现在到底会产出哪些 trace 数据
- `reasoning / retry / handoff / team / compact` 这些事件怎么映射到 trace
- 内置 exporter 有哪些，`OpenTelemetry` 是怎么接进来的
- `Agent` trace 和 `FlowGram` 前端调试视图之间是什么关系

## 1. 当前 trace 组件

- `AgentTraceListener`
  - 监听 `AgentEvent`，把 runtime 事件折叠成 `TraceSpan`
- `TraceConfig`
  - 控制记录开关、脱敏、字段裁剪
- `TraceSpan`
  - 一条 span，包含基础字段、attributes、events、metrics
- `TraceSpanEvent`
  - span 内部事件，例如 `model.reasoning`、`model.retry`
- `TraceMetrics`
  - 统一挂载时延、token、cost 这些指标
- `TracePricing` / `TracePricingResolver`
  - 给模型 usage 做成本估算的可选配置
- `TraceExporter`
  - 导出接口
- `AgentFlowTraceBridge`
  - 监听 `AgentFlowTraceListener`，把 Dify / Coze / n8n 调用投影成统一 `TraceSpan`
- `ConsoleTraceExporter`
  - 打印 `TRACE {...}`
- `InMemoryTraceExporter`
  - 测试断言和调试采样
- `CompositeTraceExporter`
  - 一个 span 扇出到多个 exporter
- `JsonlTraceExporter`
  - 追加写入 JSONL 文件
- `OpenTelemetryTraceExporter`
  - 把 AI4J trace 桥接导出到 OTel pipeline
- `LangfuseTraceExporter`
  - 输出 Langfuse 可识别的 OTel span attributes，方便接 Langfuse

## 2. 启用方式

最小接法：

```java
Agent agent = Agents.react()
        .modelClient(modelClient)
        .model("doubao-seed-1-8-251228")
        .traceConfig(TraceConfig.builder().build())
        .traceExporter(new ConsoleTraceExporter())
        .build();
```

`AgentBuilder` 的默认行为很简单：

- 只要你设置了 `traceExporter(...)`
- `build()` 时就会自动挂一个 `AgentTraceListener`
- 不需要再手动注册 listener

如果你要同时打控制台、内存和文件：

```java
TraceExporter exporter = new CompositeTraceExporter(
        new ConsoleTraceExporter(),
        new InMemoryTraceExporter(),
        new JsonlTraceExporter("logs/agent-trace.jsonl")
);

Agent agent = Agents.react()
        .modelClient(modelClient)
        .model("gpt-4o-mini")
        .traceExporter(exporter)
        .build();
```

## 3. `TraceSpan` 结构

`TraceSpan` 当前包含：

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

`events` 里的每一项是 `TraceSpanEvent`：

- `timestamp`
- `name`
- `attributes`

`metrics` 当前包含：

- `durationMillis`
- `promptTokens`
- `completionTokens`
- `totalTokens`
- `inputCost`
- `outputCost`
- `totalCost`
- `currency`

这意味着当前 trace 不是只有“粗粒度 span”，也支持在一个 span 内附加中间事件。

## 4. Span 类型

当前 `TraceSpanType` 已经不只四种：

- `RUN`
  - 一次完整 agent 调用
- `STEP`
  - 一轮 runtime loop
- `MODEL`
  - 一次模型请求
- `TOOL`
  - 一次工具执行
- `HANDOFF`
  - 一次 subagent handoff
- `TEAM_TASK`
  - 一条 team task 的生命周期
- `MEMORY`
  - 一次 memory compact / compress
- `AGENT_FLOW`
  - 一次外部 Dify / Coze / n8n published endpoint 调用
- `FLOWGRAM_TASK`
  - 给 FlowGram runtime 复用的任务级 span 类型
- `FLOWGRAM_NODE`
  - 给 FlowGram runtime 复用的节点级 span 类型

其中：

- `AGENT_FLOW`
  - 来自 `AgentFlowTraceBridge`
- `FLOWGRAM_TASK / FLOWGRAM_NODE`
  - 是 trace 核心模型里的通用类型，当前 `AgentTraceListener` 本身不直接产出；FlowGram 侧走的是独立 runtime event + projection 链路

## 5. 状态模型

`TraceSpanStatus` 当前有三种：

- `OK`
- `ERROR`
- `CANCELED`

也就是说，trace 层现在可以明确区分：

- 正常结束
- 异常失败
- 主动取消

这对 handoff、team task、FlowGram task 都是有意义的。

## 6. Agent 事件如何映射到 trace

### 6.1 运行主链路

- 第一次 `STEP_START`
  - 创建 `RUN`
- 每个 `STEP_START / STEP_END`
  - 创建并结束 `STEP`
- `MODEL_REQUEST`
  - 创建 `MODEL`
- `TOOL_CALL / TOOL_RESULT`
  - 创建并结束 `TOOL`
- `FINAL_OUTPUT`
  - 结束 `RUN`
- `ERROR`
  - 将 `RUN` 标记为 `ERROR`

### 6.2 模型中间事件

`BaseAgentRuntime.executeModel(...)` 现在除了 request/response，还会发：

- `MODEL_REASONING`
- `MODEL_RETRY`

这些不会额外拆成独立 span，而是挂在当前 `MODEL` span 的 `events` 上：

- `model.reasoning`
- `model.retry`
- 流式文本增量会作为 `model.response.delta`

这样做的原因是：

- reasoning / retry 本质上属于同一次模型调用的内部过程
- 单独拆 span 会让层级过碎
- 挂成 span event 更适合做时间线与回放

### 6.3 SubAgent handoff

`SubAgentToolExecutor` 会发：

- `HANDOFF_START`
- `HANDOFF_END`

`AgentTraceListener` 会把它们折叠成一个 `HANDOFF` span。

当前 handoff payload 里常见的字段包括：

- `handoffId`
- `callId`
- `tool`
- `subagent`
- `title`
- `detail`
- `status`
- `depth`
- `sessionMode`
- `attempts`
- `durationMillis`
- `output`
- `error`

所以 handoff trace 既能回答“有没有委派”，也能回答：

- 委派给谁
- 第几层 handoff
- 是完成、失败还是 fallback
- 花了多久

### 6.4 Agent Team

`AgentTeamEventHook` 会发：

- `TEAM_TASK_CREATED`
- `TEAM_TASK_UPDATED`
- `TEAM_MESSAGE`

映射规则是：

- task create / update
  - 聚合成 `TEAM_TASK` span
- team message
  - 写入对应 `TEAM_TASK` span 的 `team.message` event

这和 handoff 的区别是：

- handoff 更像主 agent 把一个 tool 调用委派出去
- team task 更像显式任务板上的任务生命周期

### 6.5 Memory compact

`MEMORY_COMPRESS` 现在映射为一个短生命周期 `MEMORY` span。

它适合挂这些信息：

- 为什么压缩
- summary / checkpoint 标识
- 是否 fallback
- 压缩发生在哪个 step

如果你在 Coding Agent 里看 compact 诊断，这一层语义和 agent trace 是能对齐的。

### 6.6 AgentFlow 外部端点调用

`AgentFlow` 本身在 `ai4j` 层只发中立 lifecycle hook：

- start
- stream event
- complete
- error

如果你引入 `ai4j-agent`，可以通过 `AgentFlowTraceBridge` 把它桥接成 `AGENT_FLOW` span。

它覆盖的不是内部 agent runtime，而是：

- Dify chat/workflow
- Coze chat/workflow
- n8n webhook workflow

默认映射策略是：

- 一次外部调用 -> 一个 `AGENT_FLOW` span
- stream 中间增量 -> span event
- usage -> `TraceMetrics`
- 最终 output/status/taskId/conversationId/workflowRunId -> attributes

这意味着当系统里同时存在“本地 Agent runtime”与“外部托管 Agent / Workflow 调用”时，最终仍能汇总到同一套 exporter，而不是一半有观测、一半是黑盒。

## 7. 默认记录策略

`TraceConfig.builder().build()` 默认就是：

- `recordModelInput = true`
- `recordModelOutput = true`
- `recordToolArgs = true`
- `recordToolOutput = true`
- `recordMetrics = true`
- `maxFieldLength = 0`
- `masker = null`
- `pricingResolver = null`

也就是默认偏“全记录”，方便本地调试和研发联调。

## 8. 当前会记录哪些字段

### 8.1 模型输入

`MODEL` span attributes 里常见字段：

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

### 8.2 模型输出

- 最终 raw payload -> `output`
- 流式文本增量 -> `model.response.delta` event
- 最终回答 -> `RUN.finalOutput`
- provider 返回的 `usage/model/finishReason` 也会被抽出来单独记录

### 8.3 模型指标

`MODEL` span 在 payload 带 `usage` 时，会自动补齐：

- `metrics.durationMillis`
- `metrics.promptTokens`
- `metrics.completionTokens`
- `metrics.totalTokens`

如果你配置了 `TracePricingResolver`，还会继续估算：

- `metrics.inputCost`
- `metrics.outputCost`
- `metrics.totalCost`
- `metrics.currency`

同时，`RUN` 和 `STEP` span 会聚合同一轮里的 token / cost，总结视角不需要你自己再扫一遍全部 `MODEL` span。

### 8.4 工具调用

- `tool`
- `callId`
- `arguments`

### 8.5 工具返回

- 普通工具：`output`
- CodeAct 工具：`result/stdout/error`

### 8.6 handoff / team / compact

这些数据主要落在它们各自 span 的 attributes 和 events 上，不再强行塞进 `MODEL` 或 `TOOL`。

## 9. 内置 exporter 的使用边界

### 9.1 `ConsoleTraceExporter`

适合：

- 本地开发
- 先快速看有没有请求、有没有工具、有没有 handoff

不适合：

- 正式存档
- 大规模查询

### 9.2 `InMemoryTraceExporter`

适合：

- 单元测试
- 集成测试里断言 span 类型和字段

### 9.3 `JsonlTraceExporter`

适合：

- 本地归档
- 调试时导出文件给别的系统离线分析
- 简单接 ELK / ClickHouse 导入任务

### 9.4 `CompositeTraceExporter`

适合：

- 同时满足调试、留档、平台接入三类需求

### 9.5 `OpenTelemetryTraceExporter`

这是当前推荐的“平台接入桥”。

它的定位不是“用 OTel 完全替代 AI4J trace 模型”，而是：

- 保留 AI4J 自己的 `TraceSpan` 语义
- 导出时把关键字段映射到 OTel span 和 attributes
- 方便接已有的 collector / observability pipeline

接法：

```java
OpenTelemetry openTelemetry = ...;

Agent agent = Agents.react()
        .modelClient(modelClient)
        .model("gpt-4o-mini")
        .traceExporter(new OpenTelemetryTraceExporter(openTelemetry))
        .build();
```

当前导出时会写入这些关键属性：

- `ai4j.trace_id`
- `ai4j.span_id`
- `ai4j.parent_span_id`
- `ai4j.span_type`
- `ai4j.span_status`
- `ai4j.error`
- `ai4j.attr.*`
- `ai4j.event.*`
- `ai4j.metrics.*`
- `gen_ai.usage.input_tokens`
- `gen_ai.usage.output_tokens`

要注意一件事：

- 当前它是“桥接 exporter”
- 不是把 `AgentRuntime` 全部改造成原生 OTel instrumentation
- exporter 内部会按 `parentSpanId` 做一层缓冲重排，尽量恢复父子链路，不是简单把每个 span 独立平铺出去

所以如果你需要非常严格的 OTel context propagation / 原生父子链路管理，应该在更深层做原生埋点；如果你只是要接 OTel collector、再喂给 Langfuse 之类系统，这一层已经够用。

### 9.6 `LangfuseTraceExporter`

如果你的后端已经走 OTel pipeline，但上层想直接进 Langfuse，这是推荐接法。

```java
OpenTelemetry openTelemetry = ...;

Agent agent = Agents.react()
        .modelClient(modelClient)
        .model("gpt-4o-mini")
        .traceExporter(new LangfuseTraceExporter(openTelemetry, "prod", "2026-04-03"))
        .build();
```

它做的事情不是直连 Langfuse 私有协议，而是：

- 继续输出 OTel span
- 额外写入 Langfuse 识别的 attributes
- 让你可以复用现有 collector / OTLP pipeline

当前会重点映射：

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

`AGENT_FLOW` span 在 Langfuse 里会按 `chain` 语义导出，因此它更适合表现“一个已编排外部流程的调用”，而不是伪装成底层 `generation`。

## 10. 脱敏与裁剪

线上建议至少做两件事：

1. 通过 `masker` 脱敏
2. 通过 `maxFieldLength` 限制超长字段

示例：

```java
TraceConfig config = TraceConfig.builder()
        .maxFieldLength(4000)
        .masker(text -> text == null
                ? null
                : text.replaceAll("(?i)api[_-]?key\\s*[:=]\\s*[^,\\s]+", "apiKey=***"))
        .build();
```

## 11. 与 FlowGram trace 的关系

Agent trace 和 FlowGram trace 不应该混成一层。

当前推荐边界是：

- `Agent`
  - 输出 `TraceSpan`
  - 可接 `OpenTelemetryTraceExporter`
- `FlowGram`
  - 先产出 runtime event
  - 再由后端投影成前端可消费的 `FlowGramTraceView`

`FlowGramTraceView` 当前不只是时间线快照。

在新版 starter 里，后端在返回 `report/result` 前还会补齐：

- `trace.summary.metrics`
- `trace.nodes[nodeId].metrics`
- `workflow.nodes[nodeId].outputs.metrics`

也就是说，FlowGram 前端现在可以直接拿后端 projection 看 node duration、LLM tokens 和 cost，不需要默认自己再从 `rawResponse.usage` 做一遍 client-side 解析。

也就是说：

- 后端平台侧可以 OTel-first
- 但给 `FlowGram.ai` 这类前端画布时，不建议直接让前端读原始 OTel span
- 应该读后端整理好的 trace projection

## 12. 一段 trace 怎么看

建议按这个顺序读：

1. 先看 `RUN`
   - 整体耗时、整体状态、最终输出
2. 再看 `STEP`
   - 有没有循环过多
3. 再看 `MODEL`
   - prompt 是否正确、reasoning/retry 发生在哪
4. 再看 `TOOL`
   - 调了什么工具、参数和输出是否异常
5. 再看 `HANDOFF / TEAM_TASK / MEMORY`
   - 问题是在委派、协作，还是在压缩点发生的

## 13. 参考测试

- `AgentTraceListenerTest`
- `AgentTraceUsageTest`
- `CodeActRuntimeWithTraceTest`
- `AgentFlowTraceBridgeTest`

## 14. 继续阅读

- [Agent 核心类参考手册](/docs/agent/reference-core-classes)
- [Flowgram API 与运行时](/docs/flowgram/api-and-runtime)
- [引用、Trace 与前端展示](/docs/ai-basics/rag/citations-trace-and-ui-integration)
