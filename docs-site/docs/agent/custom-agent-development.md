# Custom Agent Development

“自定义 Agent” 在 `ai4j-agent` 里不是一个单独框架，而是沿着现有执行链替换某一层能力。

真正需要先回答的问题不是“我要不要自己写一个 Agent”，而是：

- 你要替换的是模型协议适配
- 还是工具暴露与执行
- 还是记忆与压缩策略
- 还是整条 runtime loop 的推进语义

这页按源码把这些扩展面拆开，避免把所有定制都堆进一个大而杂的“自定义 Agent”类里。

## 1. 先看真实装配链

最值得先读的源码入口：

- `ai4j-agent/src/main/java/io/github/lnyocly/ai4j/agent/Agents.java`
- `ai4j-agent/src/main/java/io/github/lnyocly/ai4j/agent/AgentBuilder.java`
- `ai4j-agent/src/main/java/io/github/lnyocly/ai4j/agent/AgentContext.java`
- `ai4j-agent/src/main/java/io/github/lnyocly/ai4j/agent/runtime/BaseAgentRuntime.java`

默认装配链可以压缩成：

```text
Agents.react() / codeAct() / deepResearch()
  -> AgentBuilder
  -> 解析默认值
  -> AgentContext
  -> AgentRuntime
  -> Agent.run(...)
  -> BaseAgentRuntime.runInternal(...)
```

理解这条链后，扩展点其实很清楚：

- `AgentModelClient` 决定模型协议怎么接入
- `AgentToolRegistry` / `ToolExecutor` 决定工具面和执行面
- `AgentMemory` / `MemoryCompressor` 决定状态策略
- `AgentRuntime` 决定一次 run 如何推进
- `TraceExporter` / `AgentEventPublisher` 决定可观测与事件出口

## 2. `build()` 的默认值决定了“最小 Agent”长什么样

`AgentBuilder.build()` 当前会补这些默认值：

| 配置项 | 默认值 | 直接后果 |
| --- | --- | --- |
| `runtime` | `ReActRuntime` | 普通 Agent 默认就是 ReAct loop |
| `memorySupplier` | `InMemoryAgentMemory::new` | 新 session 默认只换 memory，不换 runtime |
| `toolRegistry` | `StaticToolRegistry.empty()` | 最小 Agent 可以完全没有工具 |
| `toolExecutor` | 基于基础 registry 名称构造 `ToolUtilExecutor` | 默认执行器只对默认工具体系友好 |
| `codeExecutor` | Java 8 -> `NashornCodeExecutor`；更高版本 -> `GraalVmCodeExecutor` | CodeAct 行为受 JDK 版本影响 |
| `options` | `AgentOptions.builder().build()` | step/stream 等参数都有默认对象 |
| `codeActOptions` | `CodeActOptions.builder().build()` | CodeAct 专属参数也有默认对象 |
| `eventPublisher` | `new AgentEventPublisher()` | 默认有事件总线，即使没有 trace exporter |

真正硬性要求只有一个：

- `modelClient` 不能为空

`model` 虽然不是在 Builder 阶段立即校验，但 `BaseAgentRuntime.buildPrompt(...)` 会在运行时要求它存在。

## 3. 定制前先选对扩展层

很多项目之所以越改越乱，是因为本来只需要替换一层，却重写了整条链。

### 3.1 只想接一个新模型协议

优先自定义：

- `AgentModelClient`

不要先改：

- `AgentRuntime`
- `Agent`

因为大多数模型接入问题，本质上只是把 `AgentPrompt` 映射到底层 API，再把响应映射回 `AgentModelResult`。

### 3.2 只想做审批、审计、远程执行、限流

优先自定义：

- `ToolExecutor`

不要把权限逻辑塞进：

- `AgentToolCallSanitizer`
- 每一个工具函数本体

因为执行器是统一执行边界，既能保留 loop 一致性，也能集中治理错误、审计和拦截。

### 3.3 只想改变上下文保留策略

优先自定义：

- `memorySupplier`
- `AgentMemory`
- `MemoryCompressor`

不要先去改：

- `BaseAgentRuntime`

因为 memory 层本来就负责上下文保留与压缩，runtime 只负责在正确时机读写它。

### 3.4 真的要改变执行语义

这时才考虑自定义：

- `AgentRuntime`
- 或继承 `BaseAgentRuntime`

典型场景：

- 不是标准 ReAct loop
- 不是 CodeAct 的“模型产代码再执行”
- 需要在 run 前插 planning、审批、调度或阶段转换

## 4. 最常见扩展面：自定义 `AgentModelClient`

源码入口：

- `ai4j-agent/src/main/java/io/github/lnyocly/ai4j/agent/model/ChatModelClient.java`
- `ai4j-agent/src/main/java/io/github/lnyocly/ai4j/agent/model/ResponsesModelClient.java`

这两个类最值得看的不是 provider 细节，而是它们如何把：

- `AgentPrompt`

翻译成底层协议，再把底层返回值翻回：

- `AgentModelResult`

最小实现通常长这样：

```java
public class MyModelClient implements AgentModelClient {
    @Override
    public AgentModelResult create(AgentPrompt prompt) {
        // 1. 把 prompt 映射到你的模型 API
        // 2. 取回文本、tool calls、memory items
        return AgentModelResult.builder()
                .outputText("...")
                .toolCalls(java.util.Collections.<AgentToolCall>emptyList())
                .memoryItems(java.util.Collections.<Object>emptyList())
                .rawResponse(null)
                .build();
    }

    @Override
    public AgentModelResult createStream(AgentPrompt prompt, AgentModelStreamListener listener) {
        return create(prompt);
    }
}
```

这里最容易写错的地方有两个：

- 只映射了文本，没把 tool call 或 memory item 映射回来
- 流式实现只推文本 delta，却没补完整的最终 `AgentModelResult`

一旦漏掉这些字段，Agent 看起来“能说话”，但工具链和状态链会悄悄失效。

## 5. 最稳的治理扩展：自定义 `ToolExecutor`

源码入口：

- `ai4j-agent/src/main/java/io/github/lnyocly/ai4j/agent/tool/ToolExecutor.java`
- `ai4j-agent/src/main/java/io/github/lnyocly/ai4j/agent/tool/ToolUtilExecutor.java`
- `ai4j-agent/src/main/java/io/github/lnyocly/ai4j/agent/tool/AgentToolCallSanitizer.java`

如果你要做：

- 权限审批
- 参数检查
- 审计日志
- 外部沙箱
- 网关代理
- 失败重试

执行器是第一优先级扩展点：

```java
ToolExecutor guardedExecutor = call -> {
    approvalService.check(call.getName(), call.getArguments());
    auditService.record(call);
    return delegate.execute(call);
};
```

这里要特别注意一个边界：

- `AgentToolCallSanitizer` 只做结构合法性校验，不做权限控制

如果把授权逻辑写进 sanitizer，最终会把“参数不合法”和“业务不允许执行”混成一类错误。

## 6. 状态策略扩展：自定义 `AgentMemory` 和压缩器

源码入口：

- `ai4j-agent/src/main/java/io/github/lnyocly/ai4j/agent/memory/AgentMemory.java`
- `ai4j-agent/src/main/java/io/github/lnyocly/ai4j/agent/memory/InMemoryAgentMemory.java`
- `ai4j-agent/src/main/java/io/github/lnyocly/ai4j/agent/memory/JdbcAgentMemory.java`

适合自定义 memory 的场景：

- 需要 Redis / MongoDB / 自研会话平台
- 要做长任务摘要
- 要做多租户隔离
- 要把某些工具结果压缩得更激进

最小接入方式通常不是重写 runtime，而是：

```java
Agent agent = Agents.react()
        .modelClient(modelClient)
        .model("gpt-4.1")
        .memorySupplier(() -> new InMemoryAgentMemory(
                new WindowedMemoryCompressor(20)
        ))
        .build();
```

这里的关键原则是：

- state policy 应该放在 memory 层，不应该散落在 runtime 或业务节点里

## 7. 高自由度扩展：继承 `BaseAgentRuntime`

源码入口：

- `ai4j-agent/src/main/java/io/github/lnyocly/ai4j/agent/runtime/BaseAgentRuntime.java`
- `ai4j-agent/src/main/java/io/github/lnyocly/ai4j/agent/runtime/ReActRuntime.java`
- `ai4j-agent/src/main/java/io/github/lnyocly/ai4j/agent/runtime/CodeActRuntime.java`
- `ai4j-agent/src/main/java/io/github/lnyocly/ai4j/agent/runtime/DeepResearchRuntime.java`

如果必须改执行语义，最稳的做法通常不是直接实现 `AgentRuntime`，而是：

- 继承 `BaseAgentRuntime`

原因很简单，`BaseAgentRuntime` 已经帮你处理了：

- input 写入 memory
- prompt 构建
- model 调用
- tool call 归一化
- 校验错误转 `TOOL_ERROR`
- tool result 回写 memory
- 事件发布
- 串行 / 并行工具调用

一个典型自定义 runtime 往往只需要覆写很少的方法：

```java
public class MyRuntime extends BaseAgentRuntime {
    @Override
    protected String runtimeName() {
        return "my-runtime";
    }

    @Override
    protected String runtimeInstructions() {
        return "Always produce a plan before acting.";
    }
}
```

只有在这些骨架本身都不适合时，才建议直接完全实现 `AgentRuntime`。

## 8. 不建议定制的地方

有几处看起来能改，但通常不值得先动。

### 8.1 不要先改 `Agent`

`Agent` 本身很薄，主要负责把 runtime、base context 和 `memorySupplier` 绑定在一起，再为 `newSession()` 创建独立 memory。

如果你想改的是行为，通常应该改：

- builder
- runtime
- memory

而不是 `Agent` 包装器本体。

### 8.2 不要为了接新 provider 重写 runtime

新 provider 问题通常是协议映射问题，不是 loop 语义问题。

### 8.3 不要把业务编排先写进 runtime

固定审批流、多节点状态迁移、更强的条件路由，往往更适合：

- Workflow / StateGraph
- SubAgent
- Team runtime

而不是把所有业务流程直接塞进一个自定义 runtime。

## 9. 默认行为带来的几个后果

### 9.1 默认 session 隔离只依赖 `memorySupplier`

`Agent.newSession()` 不会克隆整个 runtime，只会为新 session 换一份 memory。

所以如果你的 `memorySupplier` 返回共享单例，多个 session 仍会串状态。

### 9.2 自定义执行器异常默认会被 runtime 包装

如果你继承的是 `BaseAgentRuntime`，工具异常最终会被包装成 `TOOL_ERROR`，再回写给模型。

这意味着：

- 执行器抛异常不一定会终止整个 Agent
- 模型有机会自行恢复、重试或换工具

### 9.3 自己完全实现 `AgentRuntime` 会失去很多骨架能力

一旦不再走 `BaseAgentRuntime`，你就要自己负责：

- memory 写回
- 事件发布
- tool error 统一语义
- stream / non-stream 行为一致性

这就是为什么“全自定义 runtime”应该是最后手段，而不是第一选择。

## 10. 一套更稳的工程拆分

生产环境里，最稳的拆分通常是三层：

1. 模型适配层：`AgentModelClient`
2. 执行策略层：`AgentRuntime`
3. 能力治理层：`ToolExecutor` / `AgentMemory` / trace

这样做的直接好处是：

- 模型迁移不必动 loop
- 工具审批不必散落到每个函数
- 长任务状态策略不必绑死在某个 runtime 上

## 11. 调试与测试入口

做自定义 Agent 时，最值得优先阅读或参考的测试通常是：

- `ai4j-agent/src/test/java/io/github/lnyocly/agent/SubAgentRuntimeTest.java`
- `ai4j-agent/src/test/java/io/github/lnyocly/agent/AgentTeamTest.java`
- `ai4j-agent/src/test/java/io/github/lnyocly/agent/AgentTeamTaskBoardTest.java`

而代码入口最值得从这里开始：

- `AgentBuilder.build()`
- `BaseAgentRuntime.runInternal(...)`
- `ChatModelClient`
- `ResponsesModelClient`
- `ToolUtilExecutor`
- `InMemoryAgentMemory` / `JdbcAgentMemory`

## 12. 继续阅读

1. [Architecture](/docs/agent/architecture)
2. [Runtime Implementations](/docs/agent/runtime-implementations)
3. [Tools and Registry](/docs/agent/tools-and-registry)
4. [Memory and State](/docs/agent/memory-and-state)
5. [Trace Observability](/docs/agent/trace-observability)
