---
sidebar_position: 9
title: "Trace and Observability"
description: "Explains trace and observability in ai4j-agent: traceExporter is the actual switch, the default records everything and needs masking/truncation, how events collapse into spans, the applicability boundary of each exporter, and the real relationship with OTel/Langfuse/FlowGram."
tags: [concept]
---

# Trace and Observability

This layer is not about "you can plug in an observability platform later." It is about what events `ai4j-agent` already emits today, how those events collapse into spans, which defaults affect what gets recorded, and the real boundary between it and OTel / Langfuse / FlowGram.

If you only understand trace as "print a request log," you will miss the most important parts of this design:

- The agent runtime already has a unified event surface.
- Trace is not hard-coded inside the runtime; it is a listener projection.
- Span types already cover model, tool, handoff, and team task.
- But it is still a lightweight implementation, not a full APM / distributed tracing platform.

## 1. Six key design decisions to grasp first

### 1.1 Trace is an event projection, not a data structure embedded in the runtime

`BaseAgentRuntime`, `CodeActRuntime`, `SubAgentToolExecutor`, and `AgentTeamEventHook` all emit `AgentEvent`.

What actually turns these events into `TraceSpan` is:

- `AgentTraceListener`

This means the trace layer is fundamentally:

- runtime event bus
  -> listener
  -> exporter

Rather than the runtime assembling an OTel span directly on every step.

### 1.2 Setting `traceConfig(...)` alone does not enable trace

This is one of the most easily misunderstood default behaviors.

The actual logic inside `AgentBuilder.build()` is:

```java
AgentEventPublisher resolvedEventPublisher = eventPublisher == null ? new AgentEventPublisher() : eventPublisher;
if (traceExporter != null) {
    resolvedEventPublisher.addListener(new AgentTraceListener(traceExporter, traceConfig));
}
```

In other words:

- Only when `traceExporter != null` does the Builder automatically attach `AgentTraceListener`.
- Passing only `traceConfig(...)` does not take effect automatically.

So the minimum condition to enable trace is not `traceConfig`, but `traceExporter`.

### 1.3 The default is "record everything," not "mask by default"

The defaults of `TraceConfig.builder().build()` are:

- `recordModelInput = true`
- `recordModelOutput = true`
- `recordToolArgs = true`
- `recordToolOutput = true`
- `recordMetrics = true`
- `maxFieldLength = 0`
- `masker = null`
- `pricingResolver = null`

So the default behavior leans toward R&D debugging rather than minimal production exposure.

:::warning Default records everything — you must mask before going live
If you turn this on in production without adding a masker / truncate, the prompt, tool args, and tool output may all be recorded in full.
:::

### 1.4 Trace is best-effort by default and does not block the main flow

`AgentEventPublisher.publish(...)` swallows exceptions while iterating listeners:

```java
try {
    listener.onEvent(event);
} catch (Exception ignored) {
    // Listener errors should not break agent execution.
}
```

So even if:

- the exporter fails to write a file,
- the trace listener throws internally,
- the OTel downstream errors out,

the main Agent run still continues.

This is good for production stability, but the trade-off is that you cannot treat trace success as a business success condition.

### 1.5 The current trace is a single-machine lightweight model, not a concurrency-isolated run-recording system

`AgentTraceListener` internally maintains a set of mutable fields:

- `traceId`
- `rootSpan`
- `stepSpans`
- `modelSpans`
- `toolSpans`

These fields are not bucketed by "runId / sessionId," and `AgentEvent` carries no run identifier, only:

- `type`
- `step`
- `message`
- `payload`

This means the same trace listener on the same Agent instance is better suited for:

- a single run
- sequential execution

If multiple runs concurrently reuse the same listener, the traces may cross-contaminate.

### 1.6 OTel and Langfuse are wired as exporter bridges, not as native instrumentation

`OpenTelemetryTraceExporter` and `LangfuseTraceExporter` are both positioned to:

- First keep AI4J's own `TraceSpan`
- Then project to OTel span at the export stage

They do not rewrite `BaseAgentRuntime` into native OpenTelemetry instrumentation, nor do they handle cross-service context propagation.

## 2. The current trace object graph

The core objects on this line can first be compressed into one diagram:

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

The responsibility boundaries are as follows:

| Object | Real responsibility |
| --- | --- |
| `AgentEventPublisher` | Broadcasts runtime events |
| `AgentTraceListener` | Folds events into spans |
| `TraceConfig` | Decides recording granularity, masking, and truncation |
| `TraceSpan` | Unified span data model |
| `TraceExporter` | Decides where spans are output |

## 3. How trace is wired into the Agent

The minimum way to enable it is:

```java
Agent agent = Agents.react()
        .modelClient(modelClient)
        .model("gpt-4.1")
        .traceExporter(new ConsoleTraceExporter())
        .build();
```

If you also need to control the recording policy:

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

There are three points here that are easy to overlook:

1. `traceExporter(...)` is the switch.
2. `traceConfig(...)` is only configuration, not a switch.
3. If you pass a custom `eventPublisher(...)`, the Builder adds the trace listener to your publisher rather than spinning up a separate one.

## 4. What is actually inside a `TraceSpan`

`TraceSpan` currently has only a few fields, but they are enough to cover the Agent's main path:

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

These three parts are the most worth understanding separately:

### 4.1 `attributes`

Suited for storing:

- prompt metadata
- tool args / output
- provider response metadata
- structured fields inside team / handoff payloads

### 4.2 `events`

Suited for storing:

- reasoning text
- retry information
- stream deltas
- team messages

In other words, not every intermediate step deserves its own span.

### 4.3 `metrics`

Currently all mounted together:

- `durationMillis`
- `promptTokens`
- `completionTokens`
- `totalTokens`
- `inputCost`
- `outputCost`
- `totalCost`
- `currency`

As long as `recordMetrics = false`, even `durationMillis` is not computed.

## 5. What span types actually represent

The current enum values of `TraceSpanType` include:

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

But you need to distinguish "the type exists" from "the current AgentTraceListener will actually emit it."

### What `AgentTraceListener` currently emits

- `RUN`
- `STEP`
- `MODEL`
- `TOOL`
- `HANDOFF`
- `TEAM_TASK`
- `MEMORY`

### Sources of the other types

- `AGENT_FLOW`
  - comes from `AgentFlowTraceBridge`
- `FLOWGRAM_TASK` / `FLOWGRAM_NODE`
  - used by the projection system on the FlowGram / AgentFlow side, not spans emitted directly by the current Agent runtime

## 6. The real mapping chain from events to spans

### 6.1 When the root run span starts

`AgentTraceListener` does not create the root span at the instant `Agent.run(...)` is called.

It does so only when the first `STEP_START` arrives:

1. Generate `traceId`
2. Create the root `RUN` span
3. Then create the current step span

So the starting point of the current run span is defined as:

- the first runtime step begins

Not:

- when the Agent object is created
- when the user request enters the API boundary

### 6.2 How a step span ends

Each `STEP_START` creates a step span.

The corresponding `STEP_END` will:

- end that step span
- clean up that step's tool span fallback records

If the root span has already ended at `FINAL_OUTPUT`, and the step spans are all cleared, the listener will `reset()`.

### 6.3 The streaming vs. non-streaming difference for model spans

`MODEL_REQUEST` creates a `MODEL` span.

After that there are two paths:

#### Non-streaming

- The runtime sends the final `MODEL_RESPONSE(payload=rawResponse)`
- The listener uses the payload to fill in:
  - `responseId`
  - `responseModel`
  - `finishReason`
  - `usage`
- Accumulates metrics
- Closes the model span

#### Streaming

- Intermediate deltas come through as `MODEL_RESPONSE(message=delta, payload=null)`
- The listener attaches them as `model.response.delta` events
- Until the final raw payload arrives
- Only then does it actually close the model span

So the current stream trace is not "one span per delta," but:

- one `MODEL` span
- several `model.response.delta` events

### 6.4 Why reasoning and retry are not independent spans

Both `MODEL_REASONING` and `MODEL_RETRY` are attached as internal events of the current model span:

- `model.reasoning`
- `model.retry`

This is reasonable, because they are essentially internal processes of the same model call rather than separate external operations.

### 6.5 When a tool span is created and ended

`TOOL_CALL` creates a tool span.

If the payload is an `AgentToolCall` and `recordToolArgs = true`, the attributes will include:

- `tool`
- `callId`
- `arguments`

When `TOOL_RESULT` arrives, the listener will:

- look up the span by `callId`
- write output / result / stdout / error
- determine status from the payload
- end the tool span

### 6.6 Handoff and team task are not auxiliary messages, but independent spans

`HANDOFF_START / HANDOFF_END` are aggregated into one `HANDOFF` span.

`TEAM_TASK_CREATED / TEAM_TASK_UPDATED / TEAM_MESSAGE` are aggregated into a `TEAM_TASK` span plus internal events.

This means that in the observability layer:

- subagent handoff
- team task lifecycle

are no longer treated as ordinary tool messages, but as explicit first-class objects.

## 7. What fields the runtime records

### 7.1 Model input fields

When `recordModelInput = true`, common attributes of a `MODEL` span include:

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

### 7.2 Model output fields

When `recordModelOutput = true`:

- stream increments go into the `model.response.delta` event
- the final payload goes into `output`
- the root run span writes `finalOutput`

In addition, fields that can be parsed out of the provider payload:

- `responseId`
- `responseModel`
- `finishReason`

are also extracted into attributes separately.

### 7.3 Tool fields

When `recordToolArgs = true`, the tool span retains the arguments.

When `recordToolOutput = true`:

- a normal tool writes `output`
- a CodeAct tool result may write:
  - `result`
  - `stdout`
  - `error`

### 7.4 Metrics fields

As long as the response carries `usage` and `recordMetrics = true`, the listener will try to write:

- prompt tokens
- completion tokens
- total tokens
- duration

Only if you additionally configure a `TracePricingResolver` will it then estimate cost.

A minimal configuration example:

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

The price unit here is "per million tokens." The SDK does not ship a default price table, because model prices change frequently; if the resolver returns `null`, only tokens are recorded for that model, with no cost estimate.

## 8. Defaults and easily misjudged semantics

### 8.1 When `pricingResolver = null`, cost is not computed automatically

Even if the provider returns usage, by default you get only tokens, not cost.

### 8.2 `maxFieldLength = 0` means no truncation

Many people mistakenly think 0 means "disable the field."

Here 0 means:

- no length trimming is performed

### 8.3 The order in `safeValue(...)` is: serialize -> mask -> truncate

A non-string object is first JSON-serialized, then handed to the masker, then truncated according to `maxFieldLength`.

This means the masker typically sees the final string, not the Java object structure.

### 8.4 `recordMetrics = false` also turns off duration

`finishSpan(...)` only back-fills `durationMillis` when `recordMetrics = true`.

So if you turn off metrics, not only do tokens / cost disappear, the duration is not written either.

## 9. Boundaries most worth knowing in the current implementation

### 9.1 The trace listener is better suited to sequential execution, not concurrent runs on the same instance

The current listener has no runId bucketing, and its internal state is not "a fresh copy per run."

So if you plan to:

- invoke the same Agent concurrently from multiple threads
- and share the same `AgentTraceListener`

expect the traces to potentially cross wires.

### 9.2 Listener errors are swallowed by default

This is to avoid affecting the business, but it also means:

- the trace file was not written
- the exporter downstream went down

you may only notice missing data on the external platform, and you will not receive an exception inside the Agent run.

### 9.3 The `MEMORY` span is now supported by the listener, but the core memory implementation does not actively emit events

This is the one fact on this page most in need of correction.

Although `AgentEventType` has:

- `MEMORY_COMPRESS`

and `AgentTraceListener` also maps it to a `MEMORY` span, the production code in the current repository does not actively emit such events; current hits come mainly from tests.

This means that by default:

- you will not automatically see memory compress traces in an ordinary Agent run

unless:

- you publish such events yourself
- or a later implementation adds memory compression event emission

### 9.4 The tool span fallback key is not a strongly consistent ID system

If `callId` is missing, the listener falls back to using:

- `step + ":" + toolName`

or records a fallback tool span per step.

Under normal conditions the runtime tries to normalize tool calls, but this fallback is only a compatibility fallback, not a strongly constrained unique-key design.

### 9.5 The OTel exporter does not push parent-child chains in real time span by span

To recover parent-child relationships, `AbstractOpenTelemetryTraceExporter` puts finished spans into `pendingSpans` first.

Only when the parent span can also be exported are they actually sent out.

Since the root `RUN` span usually ends last, this means:

- many child spans are buffered first
- and flushed together after the root span is exported

So it is more like "export by trace completeness," not "report every span immediately the moment it ends."

## 10. The real applicability boundary of each exporter

### 10.1 `ConsoleTraceExporter`

Suited for:

- local development
- quickly seeing whether there are step / model / tool / handoff entries

Not suited for:

- archiving
- querying
- multi-run aggregate analysis

### 10.2 `InMemoryTraceExporter`

Suited for:

- unit tests
- integration test assertions
- local in-memory sampling

It just puts the spans into a list, with no indexing, no eviction, and no aggregation.

### 10.3 `JsonlTraceExporter`

Suited for:

- local archiving
- offline analysis
- importing into another system after debugging

The current behavior is:

- append to a file
- auto-create parent directories
- no rotation
- one JSON line per span

### 10.4 `CompositeTraceExporter`

Suited for:

- writing to the console, to a file, and to a platform at the same time

It only fans out; it does not do advanced governance beyond a failure-isolation policy.

### 10.5 `OpenTelemetryTraceExporter`

Suited for:

- when you already have an OTel collector / OTLP pipeline
- and want to bring AI4J traces into your existing observability system

It writes:

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

But remember, it is a projection bridge and does not handle full distributed tracing semantics.

### 10.6 `LangfuseTraceExporter`

Suited for:

- when you already go through the OTel pipeline
- but want the upstream observability platform to hook directly into Langfuse

It does not directly connect to the Langfuse private SDK; instead it:

- builds on the OTel exporter
- additionally writes Langfuse-recognizable attributes

For example:

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

## 11. The two things you must at least do for production

If you bring trace to production, you should at least add these two:

### 11.1 Masking

```java
TraceConfig traceConfig = TraceConfig.builder()
        .masker(text -> text == null
                ? null
                : text.replaceAll("(?i)api[_-]?key\\s*[:=]\\s*[^,\\s]+", "apiKey=***"))
        .build();
```

### 11.2 Truncation

```java
TraceConfig traceConfig = TraceConfig.builder()
        .maxFieldLength(4000)
        .build();
```

Otherwise, fields like prompt, tool output, and extraBody can easily blow up the trace volume.

## 12. Relationship with AgentFlow / FlowGram

You must keep this layer separate.

### The Agent runtime line

- The event source is `AgentEvent`
- The projector is `AgentTraceListener`
- Span types are mainly `RUN / STEP / MODEL / TOOL / HANDOFF / TEAM_TASK`

### The AgentFlow / FlowGram line

- The event source is not `AgentEvent`
- but rather `AgentFlowTraceContext` + chat/workflow events
- The projector is `AgentFlowTraceBridge`
- The main span type leans toward `AGENT_FLOW`

So the FlowGram front-end view should not be taken as directly equivalent to the Agent runtime trace.

More precisely:

- Agent trace is the backend run view
- FlowGram trace projection is the canvas and node view

If you are looking at FlowGram integration, continue reading [Flowgram Runtime](/docs/products/flowgram/runtime).

## 13. How to read trace when troubleshooting

Recommended order:

1. `RUN`
   Look at the overall status, total duration, and final output.
2. `STEP`
   Look for abnormal loops, or whether the step count grows unexpectedly.
3. `MODEL`
   Look at prompt, response, reasoning, retry, and usage.
4. `TOOL`
   Look at whether arguments, output, and errors are reasonable.
5. `HANDOFF`
   Look at whether a subagent handoff happened, and at which layer it failed.
6. `TEAM_TASK`
   Look at whether the team task board is stuck in some state.

If your first instinct is to drill straight into the provider raw payload, it will usually be much slower.

## 14. Recommended source-reading order

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

## 15. Further reading

1. [Agent Architecture](/docs/agent/architecture)
2. [Tools and Registry](/docs/agent/tools-and-registry)
3. [SubAgent and Handoff Policy](/docs/agent/orchestration/subagent-handoff-policy)
4. [Agent Teams](/docs/agent/orchestration/agent-teams)
5. [Flowgram Runtime](/docs/products/flowgram/runtime)
