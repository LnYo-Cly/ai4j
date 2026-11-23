---
title: AiStream 异步流与模型中间件
description: 说明 Pi 风格非阻塞 AiStream 事件流、JDK 21 虚拟线程自适应调度器，以及 Retry、KeyPoolFailover、BudgetGuard、CognitiveTiering、ShadowEval 中间件。
tags: [concept, streaming, middleware]
---

# AiStream 异步流与模型中间件

在底层统一模型协议之上，`2.x` 在 `ai4j` 核心模块新增了两个面向生产工程的关键抽象层：

1. **`io.github.lnyocly.ai4j.stream`（`AiStream<T>`）**：不引入 Reactor / RxJava 依赖的非阻塞推拉一体事件流，支持文本增量、思考增量、工具调用与工具执行进度回调，并在 Java 8 基线下自动利用 JDK 21+ 虚拟线程。
2. **`io.github.lnyocly.ai4j.middleware`（`ModelMiddleware`）**：洋葱模型（Onion Pipeline）模型调用拦截链，在不侵入业务代码的前提下提供重试、多 Key 池故障转移、预算熔断、快慢思考分层路由与影子评估。

---

## 1. Pi 风格非阻塞 `AiStream<T>`

`AiStream<T>` 与默认实现 `DefaultAiStream<T>`（实现 `AutoCloseable`）同时支持**事件推送订阅（Push）**、**异步 Future（CompletableFuture）** 与**同步阻塞拉取（Pull / Iterable）**。

### 1.1 推模式订阅与延迟回放

- `onEvent(Consumer<AiStreamEvent>)`：接收完整的结构化生命周期事件
- `onTextDelta(Consumer<String>)` / `onThinkingDelta(Consumer<String>)`：只订阅答案正文或思考正文增量
- `onComplete(Consumer<AiReply<T>>)`：在流正常完成时接收聚合后的 `AiReply<T>`（包含解析后的 POJO、Token 用量与工具旁路详情）
- `onError(Consumer<Throwable>)`：接收异常终止通知

`DefaultAiStream<T>` 内部维护事件历史与 `BlockTracker`，对后注册的订阅者自动回放历史事件并生成不可变 `Message` 快照（`partial`），避免多线程竞争下丢事件。

### 1.2 控制与拉取消费

- `cancel()` / `isCancelled()` / `close()`：主动取消上游模型流或本地工具循环
- `await()`：阻塞等待流结束并返回 `AiReply<T>`
- `toFuture()`：转换为标准 JDK `CompletableFuture<AiReply<T>>`
- `iterable()`：以 `Iterable<AiStreamEvent>` 方式按顺序消费事件，适合传统 Servlet 或阻塞桥接层

### 1.3 16 种 `AiStreamEventType` 生命周期事件

`AiStreamEvent` 通过 `AiStreamEventType` 覆盖从模型推演到工具执行全过程：

| 阶段 | 事件枚举值 | 携带的核心负载 |
| --- | --- | --- |
| 流启动 | `START` | 初始不可变 `partial` 消息快照 |
| 思考块 | `THINKING_START`、`THINKING_DELTA`、`THINKING_END` | `contentIndex`、`delta`、`content` |
| 文本块 | `TEXT_START`、`TEXT_DELTA`、`TEXT_END` | `contentIndex`、`delta`、`content` |
| 工具调用生成 | `TOOLCALL_START`、`TOOLCALL_DELTA`、`TOOLCALL_END` | `contentIndex`、`callId`、`toolName`、`toolCall` |
| 本地工具执行 | `TOOL_EXEC_START`、`TOOL_EXEC_PROGRESS`、`TOOL_EXEC_END` | `callId`、`toolName`、`toolDetails`、`toolError` |
| 结束态 | `DONE`、`ERROR` | `stopReason`、`finalReply`、`usage`、`error` |

```java
AiStream<String> stream = aiClient.prompt("gemini:gemini-2.5-flash")
        .user("先分析再输出结论")
        .stream();

stream.onThinkingDelta(delta -> System.out.print("[思考] " + delta))
      .onTextDelta(System.out::print)
      .onEvent(event -> {
          if (event.getType() == AiStreamEventType.TOOL_EXEC_PROGRESS) {
              System.out.println("\n[进度] " + event.getToolDetails());
          }
      })
      .onComplete(reply -> System.out.println("\n用量: " + reply.getUsage()))
      .onError(Throwable::printStackTrace);

AiReply<String> reply = stream.await();
```

---

## 2. Java 8 字节码兼容与虚拟线程 `DispatcherProvider`

`ai4j-sdk` 全模块保持 **Java 8 字节码基线**，同时在运行期通过 `io.github.lnyocly.ai4j.stream.DispatcherProvider` 实现运行时自适应并发：

- `DispatcherProvider.isVirtualThreadsSupported()`：在运行时反射检测 `Executors.newVirtualThreadPerTaskExecutor()`（JDK 21+ 可用）；
- `DispatcherProvider.executor()` / `newExecutor(String namePrefix)`：
  - 若宿主运行在 JDK 21+ 且支持虚拟线程，直接返回虚拟线程执行器；
  - 若运行在 JDK 8 / 11 / 17，自动回退为带守护线程前缀的缓存线程池，无任何编译期 JDK 版本强绑或异常开销。

---

## 3. `ModelMiddleware` 洋葱链与 `MiddlewareModelClient`

`ModelMiddleware` 同时拦截阻塞调用与流式调用：

- `intercept(ModelCallContext ctx, Next next)`
- `interceptStream(ModelCallContext ctx, ModelStreamListener listener, StreamNext streamNext)`

通过 `MiddlewareModelClient.wrap(delegate, middlewares...)` 或 `MiddlewareModelClient.builder(delegate).add(...).build()` 装配中间件链。每一层中间件可以通过 `ctx.withRequest(...)`、`ctx.withClient(...)` 不可变地派生上下文（例如替换目标模型、替换实际执行 `ModelClient`），或通过 `ctx.attribute(key, value)` 在同一条调用链中向后续中间件透传元信息。

```java
ModelClient resilientClient = MiddlewareModelClient.builder(primaryClient)
        .add(budgetGuardMiddleware)
        .add(cognitiveTieringMiddleware)
        .add(keyPoolFailoverMiddleware)
        .add(retryMiddleware)
        .add(shadowEvalMiddleware)
        .build();
```

:::note 推荐的装配顺序
通常建议按**外层 -> 内层**顺序排列：预算守护（先拦截超额）-> 认知分级（路由模型）-> Key 池故障转移（切换备选 Key / 协议）-> 单次重试（指数退避）-> 影子评估（异步镜像最终选定的真实请求）。
:::

---

## 4. 五大内置生产中间件

### 4.1 `RetryMiddleware`：退避重试与流式首包保护

- **适用故障**：`429 Too Many Requests`（支持读取响应头 `Retry-After`，由 `respectRetryAfter` 控制）、`408`、`504`、`5xx` 与可重试网络异常。
- **配置能力**：`initialBackoffMs`、`maxBackoffMs`、`multiplier`、`retryNonReplayable`、`RetryObserver`，以及可替换的 `RetryBackoffSleeper`（提供 `threadSleep()` 与用于单测的 `noop()`）。
- **流式首包安全**：在第一段内容尚未投递给下游之前发生可重试错误时，`RetryMiddleware` 会发送 `ModelEventType.RETRYING` 并安全重建流；一旦已开始向下游吐出正文切片，则不再重复触发以免拼接错乱。

### 4.2 `KeyPoolFailoverMiddleware`：多 Key 轮询与跨协议降级

- **多槽位池**：支持注册多个 `primary(id, client[, model])`（按轮询调度分发流量）与 `fallback(id, client[, model])` 兜底槽位。
- **差异化冷却熔断**：按错误类型区分冷却时间（`failureThreshold`、`errorCooldownMs`、`rateLimitCooldownMs`、`authCooldownMs`），支持注入自定义时钟 `LongSupplier clockMillis` 与查询 `isSlotOpen(slotId)`。
- **跨协议 / 跨模型切换**：内部通过 `applySlot(...)` 动态改写目标 `ModelClient` 与模型名称，支持从主账号或主协议无缝切换到备用厂商与协议（跨协议切换时的 `thoughtSignature` 剥离由协议编解码器自动处理）。

### 4.3 `BudgetGuardMiddleware`：作用域用量与费用熔断

- **拦截策略**：基于 `scopeKeyResolver`（如按租户、会话或用户隔离）统计阻塞与流式调用的 `ModelUsage`。
- **预算上限（`BudgetPolicy`）**：支持 `maxInputTokens`、`maxOutputTokens`、`maxTotalTokens`、`maxCost` 与 `maxRequests`，支持按作用域单独覆盖 `scopePolicy(key, policy)` 及自定义 `CostCalculator`。
- **可观测与重置**：超限时直接抛出 `BudgetExceededException(scopeKey, snapshot, message)`；平时可通过 `snapshot(scopeKey)` 与 `reset(scopeKey)` 查询当前累计快照或按周期重置。

### 4.4 `CognitiveTieringMiddleware`：快慢思考分级路由

借鉴“系统一（快速直觉）/ 系统二（深度推理）”的双系统理念，将请求自动路由至不同 `TierTarget`（`Tier.SYSTEM_1` 或 `Tier.SYSTEM_2`）：

- 内置 `DefaultTierClassifier` 依据以下信号判断复杂度：
  1. 请求属性或元数据中显式指定的 `cognitiveTier` / `tier`
  2. 工具数阈值（默认 `toolThreshold = 3`）
  3. 对话轮次阈值（默认 `turnThreshold = 6`）
  4. 输入文本长度阈值（默认 `textLengthThreshold = 1500`）
  5. 工具调用返回错误，或文本中包含复杂推理关键词（支持中英文）
- 业务也可注入自定义 `TierClassifier` 接管路由策略。

### 4.5 `ShadowEvalMiddleware`：异步影子流量评估

用于新模型、新 Prompt 或低成本替代模型上线前的无侵入灰度对比：

- 按 `sampleRate`（默认 `0.01`，即 1%）或自定义 `sampler` 采样真实请求；
- 通过默认 `redactor` 剥离敏感 `applicationMetadata` 等字段后，提交到独立 `executor` 上异步调用 `candidateClient` / `candidateModel`；
- 生成结构化 `ShadowEvalReport`（含 `primaryLatencyMs`、`candidateLatencyMs`、`textMatch`、`toolNamesMatch`、用量差值、`candidateSuccess` 与 `candidateError`），通过 `reportConsumer` 回调或 `getRecordedReports()` 读取，完全不阻塞或影响主链路响应。

## 下一步建议

- 在统一业务入口中使用这些能力：阅读 [AiClient DX 与声明式服务](/docs/getting-started/ai-client-dx)
- 查看跨协议签名处理细节：阅读 [Gemini 原生协议、Preset 与开放 Platform](/docs/capabilities/models/gemini)
- 在 Spring Boot SSE 输出中消费流事件：阅读 [Web SSE 协议适配与 Boot 2/3/4 Actuator](/docs/integrations/spring-boot/web-sse-and-actuator)
