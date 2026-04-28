# Agent Architecture

`ai4j-agent` 不是新的模型接入层，而是建立在 `ai4j` Core SDK 之上的运行时编排层。

它的职责不是“再包一层 Chat/Responses API”，而是把一次性模型调用提升为可持续执行的 Agent loop：模型请求、工具调用、结果回灌、状态累积、事件输出、运行时策略切换，都在这一层完成。

如果你只需要一次 `Chat` 或 `Responses` 调用，直接使用 Core SDK 即可；如果你需要多步推理、工具调用、记忆延续、流式事件和可替换 runtime，才进入 `Agent` 层。

## 1. 它解决什么问题

直接使用模型 API 时，应用层通常要自己处理下面这些问题：

- 如何保存多轮输入、工具结果和最终输出
- 模型返回 `tool_calls` 后，谁来校验、执行、回写
- 一轮结束后何时继续下一轮，何时终止
- 流式输出时，文本、推理、工具事件如何统一暴露
- ReAct、CodeAct、Deep Research 这类不同执行策略如何切换

`ai4j-agent` 把这些共性逻辑收敛成稳定的运行时抽象，避免业务层重复拼装“模型 + 工具 + 状态 + 事件”的主循环。

## 2. 设计原则

### 2.1 模型协议与执行控制分离

模型请求怎么发，由 `AgentModelClient` 决定；一轮执行后是否继续、何时调用工具、何时终止，由 `AgentRuntime` 决定。

这样做的好处是：

- 同一个 runtime 可以切换 `ChatModelClient`、`ResponsesModelClient` 或自定义实现
- 模型协议变更不会污染 Agent loop
- ReAct 和 CodeAct 可以共享部分基础设施，但保留不同的执行语义

### 2.2 工具声明面与执行面分离

`AgentToolRegistry` 只负责告诉模型“你能看到哪些工具”，`ToolExecutor` 才负责“工具真正怎么执行”。

这样做的好处是：

- 工具暴露策略和权限治理策略可以分开维护
- 同一批工具 schema 可以对应不同执行器
- 审批、拦截、审计、重试、隔离，应该放在执行侧，而不是只放在 schema 暴露侧

### 2.3 Memory 是运行时唯一状态源

`AgentMemory` 持有输入、模型输出、工具输出等运行时 item，runtime 每一轮都从 memory 重新组 prompt，而不是在 runtime 内部维护额外的隐式上下文。

这样做的好处是：

- session 可持续
- memory 可替换
- prompt 组装逻辑统一
- 上层 workflow / subagent / team 可以围绕 memory 做状态协作

### 2.4 Runtime 策略可替换

`ReActRuntime`、`CodeActRuntime`、`DeepResearchRuntime` 都通过 `AgentRuntime` 抽象接入。

这意味着：

- Builder 负责装配，不负责执行
- Runtime 负责 loop，不负责 provider 接入
- 你可以为特定任务定义自己的循环策略，而不必重写整个 Agent 框架

### 2.5 事件优先，而不是黑盒执行

`BaseAgentRuntime` 在步骤开始、模型请求、模型响应、工具调用、工具结果、最终输出等节点持续发事件，事件先经过 `AgentEventPublisher`，再交给流式 listener 或 trace 导出器。

这样做的好处是：

- CLI、TUI、ACP、Trace 平台可以消费统一事件流
- 可观测性是运行时结构的一部分，不是额外补丁
- listener 出错不会打断主执行链

## 3. 核心抽象与对象关系

可以把 Agent 看成下面这条装配链和执行链。

```text
Agents.react() / Agents.builder()
  -> AgentBuilder
  -> AgentContext
  -> Agent
  -> AgentRuntime
  -> AgentModelClient
  -> AgentToolRegistry + ToolExecutor
  -> AgentMemory
  -> AgentEventPublisher
```

核心对象的职责如下。

| 对象 | 角色 | 关键职责 |
| --- | --- | --- |
| `Agents` | 工厂入口 | 提供 `builder()`、`react()`、`codeAct()`、`deepResearch()` |
| `AgentBuilder` | 装配器 | 解析 runtime、memory、tools、executor、trace、subagent 等依赖 |
| `AgentContext` | 运行时配置快照 | 封装模型、工具、memory、采样参数、eventPublisher 等 |
| `Agent` | 运行入口 | 暴露 `run`、`runStream`、`newSession` |
| `AgentRuntime` | 执行策略抽象 | 定义一次 Agent run 的主循环语义 |
| `BaseAgentRuntime` | 通用循环骨架 | 负责 prompt 组装、模型调用、工具执行、memory 回写、事件发射 |
| `AgentModelClient` | 模型适配层 | 把 `AgentPrompt` 发送给具体模型协议 |
| `AgentToolRegistry` | 工具声明面 | 暴露模型可见的工具 schema |
| `ToolExecutor` | 工具执行面 | 执行 `AgentToolCall`，返回字符串结果 |
| `AgentMemory` | 状态源 | 保存输入、输出、工具结果和摘要 |
| `AgentEventPublisher` | 事件总线 | 向 trace/listener/宿主转发运行时事件 |

其中最容易被误解的三条边界是：

- `AgentBuilder` 是装配器，不是控制循环的执行器
- `AgentToolRegistry` 是声明面，不是权限执行面
- `AgentMemory` 是状态源，不是上层 workflow 的替代品

## 4. 执行流程

### 4.1 构建阶段

`ai4j-agent/src/main/java/io/github/lnyocly/ai4j/agent/AgentBuilder.java`

`AgentBuilder.build()` 做的不是简单 `new Agent()`，而是完整的依赖解析和运行时装配：

1. 解析 runtime，默认使用 `ReActRuntime`
2. 解析 memory supplier，默认使用 `InMemoryAgentMemory`
3. 解析基础工具注册器，默认使用 `StaticToolRegistry.empty()`
4. 解析 subagent registry，并在需要时把 subagent tools 合并进 `CompositeToolRegistry`
5. 如果没有手动提供 `ToolExecutor`，则按工具名反射创建 `ToolUtilExecutor`
6. 如果配置了 subagent registry，则用 `SubAgentToolExecutor` 包装原始执行器
7. 解析默认 `CodeExecutor`
   - Java 8 默认 `NashornCodeExecutor`
   - 更高版本默认 `GraalVmCodeExecutor`
8. 解析 `AgentOptions`、`CodeActOptions`、`AgentEventPublisher`
9. 如果设置了 `traceExporter`，自动挂接 `AgentTraceListener`
10. 组装 `AgentContext`

这说明 `AgentBuilder` 的本质是运行时依赖编排器。

### 4.2 运行阶段

`ai4j-agent/src/main/java/io/github/lnyocly/ai4j/agent/runtime/BaseAgentRuntime.java`

`BaseAgentRuntime.runInternal()` 是 ReAct 系 runtime 的主循环骨架，核心步骤如下：

1. 从 `AgentRequest` 读取输入，并写入 `AgentMemory`
2. 发出 `STEP_START` 事件
3. 调用 `buildPrompt()`，从 memory、system prompt、instructions、tool schema 组装 `AgentPrompt`
4. 通过 `AgentModelClient.create()` 或 `createStream()` 请求模型
5. 如果模型返回 memory items，则回写到 `AgentMemory`
6. 归一化模型返回的 `tool_calls`
   - 自动补齐 `callId`
   - 清洗空工具名
7. 对每个 tool call 走 `AgentToolCallSanitizer.validationError(...)`
   - 非法调用不会直接抛出，而是转成 `TOOL_ERROR` 风格结果写回 memory
8. 对合法调用执行 `ToolExecutor.execute(call)`
   - 可串行执行
   - 当 `parallelToolCalls == true` 且合法调用数大于 1 时，可并行执行
9. 将工具结果写回 memory
10. 发出 `TOOL_RESULT`、`STEP_END` 等事件
11. 如果本轮没有 tool call，则认为运行完成，输出最终结果

简化后的执行链如下：

```text
Agent.run()
  -> AgentRuntime.run()
  -> BaseAgentRuntime.runInternal()
  -> memory.addUserInput(...)
  -> buildPrompt(...)
  -> modelClient.create(...) / createStream(...)
  -> memory.addOutputItems(...)
  -> normalizeToolCalls(...)
  -> AgentToolCallSanitizer.validationError(...)
  -> toolExecutor.execute(...)
  -> memory.addToolOutput(...)
  -> next loop or final output
```

### 4.3 流式执行语义

当同时满足下面两个条件时，runtime 进入流式模型路径：

- 调用了 `runStream(...)` 或 `runStreamResult(...)`
- `AgentOptions.stream == true`

流式模式下，`BaseAgentRuntime.executeModel(...)` 会把以下信息拆成统一事件：

- `MODEL_REASONING`
- `MODEL_RESPONSE`
- `TOOL_CALL`
- `MODEL_RETRY`
- `ERROR`

所以流式输出不是“直接把底层 provider 事件原样透传”，而是先转成 Agent 级事件语义。

## 5. 为什么 `ToolRegistry` 和 `ToolExecutor` 必须拆开

这一点是 Agent 设计里最关键的边界之一。

`AgentToolRegistry` 的接口非常窄：

```java
public interface AgentToolRegistry {
    List<Object> getTools();
}
```

它只回答一个问题：模型能看到哪些工具 schema。

`ToolExecutor` 的接口同样很窄：

```java
public interface ToolExecutor {
    String execute(AgentToolCall call) throws Exception;
}
```

它回答的是另一个问题：当模型真的发起调用时，系统如何执行它。

拆分后的直接收益是：

- 允许只暴露白名单工具，不暴露整个宿主能力面
- 允许在执行前做权限审批、调用审计、参数重写、沙箱隔离
- 允许把本地函数工具、MCP 工具、subagent tool surface 混在同一声明面中统一暴露
- 允许在不改变模型可见 schema 的前提下，替换执行策略

如果把两者合成一个对象，文档上会简单一点，但工程上会把“模型可见能力”和“宿主真实执行能力”耦合死，后续做安全治理会非常困难。

## 6. 关键代码入口与模块路径

推荐按下面顺序阅读源码。

### 6.1 入口与装配

- `ai4j-agent/src/main/java/io/github/lnyocly/ai4j/agent/Agents.java`
- `ai4j-agent/src/main/java/io/github/lnyocly/ai4j/agent/AgentBuilder.java`
- `ai4j-agent/src/main/java/io/github/lnyocly/ai4j/agent/AgentContext.java`
- `ai4j-agent/src/main/java/io/github/lnyocly/ai4j/agent/Agent.java`

### 6.2 Runtime

- `ai4j-agent/src/main/java/io/github/lnyocly/ai4j/agent/runtime/BaseAgentRuntime.java`
- `ai4j-agent/src/main/java/io/github/lnyocly/ai4j/agent/runtime/ReActRuntime.java`
- `ai4j-agent/src/main/java/io/github/lnyocly/ai4j/agent/runtime/CodeActRuntime.java`

### 6.3 模型适配

- `ai4j-agent/src/main/java/io/github/lnyocly/ai4j/agent/model/AgentModelClient.java`
- `ChatModelClient`
- `ResponsesModelClient`

### 6.4 工具与执行

- `ai4j-agent/src/main/java/io/github/lnyocly/ai4j/agent/tool/AgentToolRegistry.java`
- `ai4j-agent/src/main/java/io/github/lnyocly/ai4j/agent/tool/StaticToolRegistry.java`
- `ai4j-agent/src/main/java/io/github/lnyocly/ai4j/agent/tool/CompositeToolRegistry.java`
- `ai4j-agent/src/main/java/io/github/lnyocly/ai4j/agent/tool/AgentToolCallSanitizer.java`
- `ai4j-agent/src/main/java/io/github/lnyocly/ai4j/agent/tool/ToolExecutor.java`

### 6.5 状态与观测

- `ai4j-agent/src/main/java/io/github/lnyocly/ai4j/agent/memory/AgentMemory.java`
- `InMemoryAgentMemory`
- `ai4j-agent/src/main/java/io/github/lnyocly/ai4j/agent/event/AgentEventPublisher.java`
- `trace/*`

## 7. 典型用法

### 7.1 最小 ReAct Agent

```java
ResponsesService responsesService = aiService.getResponsesService(PlatformType.OPENAI);

Agent agent = Agents.react()
        .modelClient(new ResponsesModelClient(responsesService))
        .model("gpt-4.1")
        .instructions("You are a weather assistant.")
        .toolRegistry(
                java.util.Arrays.asList("queryWeather"),
                java.util.Collections.<String>emptyList()
        )
        .options(AgentOptions.builder()
                .maxSteps(8)
                .stream(false)
                .build())
        .build();

AgentResult result = agent.run(AgentRequest.builder()
        .input("北京今天的天气怎么样？")
        .build());
```

这个例子对应的语义是：

- `ResponsesModelClient` 负责和模型协议打交道
- `ReActRuntime` 负责多步循环
- `toolRegistry(...)` 只暴露 `queryWeather`
- `maxSteps(8)` 为循环设置硬上限，避免无界运行

### 7.2 自定义工具治理

```java
AgentToolRegistry registry = new StaticToolRegistry(myTools);

ToolExecutor executor = call -> {
    permissionChecker.assertAllowed(call);
    auditLogger.record(call);
    return realExecutor.execute(call);
};

Agent agent = Agents.builder()
        .runtime(new ReActRuntime())
        .modelClient(modelClient)
        .toolRegistry(registry)
        .toolExecutor(executor)
        .memorySupplier(MyJdbcAgentMemory::new)
        .build();
```

这个例子说明：

- 工具 schema 可以保持稳定
- 权限、审计、拦截写在执行侧
- memory 可以替换为数据库实现

## 8. 扩展点

`Agent` 架构的可扩展性主要来自下面几个插槽。

### 8.1 `modelClient(AgentModelClient)`

用于适配不同模型协议。只要能消费 `AgentPrompt` 并产出 `AgentModelResult`，就可以被 runtime 复用。

### 8.2 `runtime(AgentRuntime)`

用于替换执行策略。适合实现领域定制循环，而不是把特殊逻辑硬塞进通用 ReAct runtime。

### 8.3 `toolRegistry(...)`

用于控制暴露面。它定义“模型知道哪些工具存在”，是工具白名单和工具发现机制的入口。

### 8.4 `toolExecutor(ToolExecutor)`

用于控制执行面。审批、限流、沙箱、审计、失败注入、代理转发，都应该优先挂在这里。

### 8.5 `memorySupplier(Supplier<AgentMemory>)`

用于替换状态存储后端。`Agent.newSession()` 会用这个 supplier 创建新 memory，因此它直接决定 session 的隔离语义。

### 8.6 `eventPublisher(...)` 与 `traceExporter(...)`

用于接入可观测体系。`traceExporter` 会自动装配 `AgentTraceListener`，而 `eventPublisher` 适合宿主自己挂接更多 listener。

### 8.7 `subAgent(...)` / `subAgentRegistry(...)`

用于把更高层协作能力接入当前 Agent。`AgentBuilder` 会把 subagent tool surface 合并到最终工具暴露面，并用 `SubAgentToolExecutor` 包装执行器。

## 9. 边界、限制与失败语义

### 9.1 必填依赖

- `modelClient` 必填，否则 `AgentBuilder.build()` 直接抛 `IllegalStateException`
- `memory` 在运行时必需；默认由 `memorySupplier` 创建
- 如果模型会触发工具调用，则必须存在可用的 `ToolExecutor`

### 9.2 `maxSteps` 默认不是安全值

`AgentOptions.maxSteps` 默认值是 `0`，其语义是“不限制步数”。这对实验方便，但对生产环境通常不是安全默认值，建议显式配置上限。

### 9.3 工具失败不会中断整个运行

`BaseAgentRuntime.executeTool(...)` 会捕获异常，并把结果转成：

```text
TOOL_ERROR: {"error":"...","tool":"...","callId":"..."}
```

然后把这段错误结果写回 memory，交给后续轮次继续处理。这意味着工具失败默认是“可恢复语义”，不是“立即终止语义”。

### 9.4 非法 tool call 会先校验

`AgentToolCallSanitizer` 会校验：

- 工具名是否为空
- `arguments` 是否为 JSON object
- 特定工具是否满足参数要求，例如 `read_file`、`apply_patch`、`bash`

非法调用不会直接执行，而会转成错误输出回灌模型。

### 9.5 并行工具调用需要执行器线程安全

只有在以下条件成立时才会并行：

- `parallelToolCalls == true`
- 合法工具调用数量大于 1

并行执行由 `BaseAgentRuntime` 内部线程池完成，因此自定义 `ToolExecutor` 必须自行保证线程安全。

### 9.6 `newSession()` 只切换 memory

`Agent.newSession()` 会复制 `baseContext`，但只替换 `memory`。这意味着：

- runtime 复用
- modelClient 复用
- tool registry / executor 复用
- 只有 session 状态隔离

如果你需要连执行策略、用户身份或工具权限一起隔离，应该重新 build 一个 Agent，而不是只开新 session。

### 9.7 `toolRegistry(List<String>, List<String>)` 依赖反射装配

`AgentBuilder` 会通过反射创建 `ToolUtilRegistry` 和 `ToolUtilExecutor`。如果对应集成模块不在 classpath 中，会抛出 `IllegalStateException`。

这意味着这个便捷 API 的本质是“对外简化入口”，不是基础抽象本身。要做稳定集成时，应明确知道它依赖的模块边界。

## 10. 与相邻能力的区别

| 能力 | 解决的问题 | 不负责什么 |
| --- | --- | --- |
| Core SDK `Chat` / `Responses` | 一次模型请求、统一 provider 接入 | 不负责 Agent loop、工具回灌、长期状态 |
| `Agent` | 多步执行、工具调用、memory、流式事件、runtime 策略 | 不负责图级 workflow 编排和完整团队协作建模 |
| `Workflow` / `StateGraph` | 显式节点和状态流转 | 不负责把模型调用自动提升成通用 Agent loop |
| `SubAgent` / `Team` | 多 Agent 协作与任务分派 | 依然建立在 Agent runtime 与工具机制之上 |

最实用的判断方式是：

- 只要一次回答，先用 Core SDK
- 需要模型自己决定是否调工具、何时继续下一步，用 Agent
- 需要显式节点、状态图、团队协作，再往上进入 workflow / subagent / team

## 11. 建议阅读顺序

读完这页后，建议按下面顺序继续：

1. [Quickstart](/docs/agent/quickstart)
2. [Model Client Selection](/docs/agent/model-client-selection)
3. [Tools and Registry](/docs/agent/tools-and-registry)
4. [Memory and State](/docs/agent/memory-and-state)
5. [Runtime Implementations](/docs/agent/runtimes/runtime-implementations)

如果你的重点是治理而不是最小跑通，优先继续看 `Tools and Registry`、`Subagent Handoff Policy`、`Trace Observability`。
