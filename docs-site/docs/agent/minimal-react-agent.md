---
sidebar_position: 2
---

# 最小 ReAct Agent

这一页讲的不是“最短 demo”，而是 AI4J 中最小且正确的 Agent 构造路径。

所谓“最小”，不是把所有能力都塞进一个例子，而是只保留真正构成 Agent loop 的几项要素：

- 一个 `AgentModelClient`
- 一个明确的 `model`
- 一个可替换的 `AgentRuntime`
- 一个可持续累积状态的 `AgentMemory`
- 可选的工具白名单

如果你还没有把这一层跑通，不应该先进入 CodeAct、StateGraph、SubAgent 或 Agent Teams。

## 1. 这页解决什么问题

很多工程第一次接 Agent 时，会把下面几类概念混在一起：

- “一次模型调用” 和 “多步 Agent loop”
- “模型能看到哪些工具” 和 “工具真正怎么执行”
- “单轮输出” 和 “带记忆的持续会话”
- “ReAct 风格工具循环” 和 “代码执行型 runtime”

最小 ReAct Agent 的意义，就是先把默认主线跑通，再决定要不要往上叠加更复杂的编排层。

## 2. 为什么先从 ReAct 开始

`Agents.react()` 是当前 AI4J Agent 层的默认入口。它最终仍然走 `AgentBuilder.build()`，只是把 runtime 预设为 ReAct 主线。

在 `ai4j-agent/src/main/java/io/github/lnyocly/ai4j/agent/AgentBuilder.java` 中，`build()` 的默认装配顺序是：

1. `runtime == null` 时使用 `ReActRuntime`
2. `memorySupplier == null` 时使用 `InMemoryAgentMemory`
3. `toolRegistry == null` 时使用 `StaticToolRegistry.empty()`
4. 未显式提供 `ToolExecutor` 时，按当前工具白名单创建默认执行器
5. 配置了 subagent 时，再用 `SubAgentToolExecutor` 包装工具执行链

也就是说，ReAct 不是额外模式，而是 AgentBuilder 的默认执行语义。

它适合下面这些任务：

- 模型自行判断是否需要调用工具
- 工具结果回灌后继续下一轮推理
- 会话状态保存在 memory 中
- 不需要执行模型生成的代码
- 不需要显式状态图或多人协作

## 3. AI4J 里的 ReAct 是什么

AI4J 的 `ReActRuntime` 是现代 tool-calling 语义下的 ReAct 风格循环，不是论文式显式 `Thought -> Action -> Observation` 文本协议。

它的主循环更接近下面这条链路：

```text
Agent.run(request)
  -> ReActRuntime.run(...)
  -> BaseAgentRuntime.runInternal(...)
  -> buildPrompt(memory + tools + prompts)
  -> modelClient.create(...)
  -> normalizeToolCalls(...)
  -> toolExecutor.execute(...)
  -> memory.addToolOutput(...)
  -> next step or final output
```

这样设计的原因很明确：

- 可以直接复用 provider 原生 `tool_calls / function_call_output`
- 工具参数校验、并行调用、事件流、trace 更容易统一
- 同一套基础设施可以继续承载 SubAgent、MCP、Agent Teams

因此，“最小 ReAct Agent” 的重点不是复刻论文提示词，而是建立一个稳定的运行时闭环。

## 4. 最小对象关系

最小 ReAct Agent 涉及的核心对象如下：

| 对象 | 角色 | 你什么时候要关心它 |
| --- | --- | --- |
| `Agents.react()` | 快速入口 | 需要默认 ReAct runtime 时 |
| `AgentBuilder` | 装配器 | 需要理解默认值和扩展点时 |
| `Agent` | 执行入口 | 直接 `run(...)`、`runStream(...)`、`newSession()` |
| `ReActRuntime` | 默认 loop 策略 | 需要理解工具循环何时继续、何时停止时 |
| `AgentModelClient` | 模型适配层 | Chat / Responses / 自定义协议接入 |
| `AgentToolRegistry` | 工具声明面 | 控制模型“看得到什么工具” |
| `ToolExecutor` | 工具执行面 | 权限审批、拦截、审计、真正执行 |
| `AgentMemory` | 状态源 | 历史输入、模型输出、工具结果都在这里 |

一个最小 Agent 真正跑起来时，依赖关系是：

```text
Agents.react()
  -> AgentBuilder
  -> AgentContext
  -> Agent
  -> ReActRuntime
  -> AgentModelClient
  -> AgentMemory
  -> AgentToolRegistry + ToolExecutor
```

## 5. 最小可用示例

从工程角度看，最小可用组合应该至少显式设置：

- `modelClient(...)`
- `model(...)`

```java
ResponsesService responsesService = aiService.getResponsesService(PlatformType.OPENAI);

Agent agent = Agents.react()
        .modelClient(new ResponsesModelClient(responsesService))
        .model("gpt-4.1")
        .instructions("You are a concise assistant.")
        .build();

AgentResult result = agent.run(AgentRequest.builder()
        .input("用一句话介绍 AI4J Agent")
        .build());

System.out.println(result.getOutputText());
```

这个例子跑通后，你已经验证了下面几件事：

- `AgentModelClient` 能正常请求模型
- `ReActRuntime` 的主循环可以执行
- `AgentMemory` 能承载输入与输出
- 最终结果能从 `AgentResult` 取回

这里有一个需要说清的事实：

- `AgentBuilder.build()` 只强制要求 `modelClient`
- 但从工程实践看，`model(...)` 仍然应当显式传入，避免运行时依赖 provider 默认模型

## 6. 加工具之前，先理解“空工具 Agent”

即使不配置任何工具，ReAct Agent 依然成立。

这时它只是：

- 有 loop 能力的模型运行时
- 有 memory 的多轮执行入口
- 可以接 trace / event / streaming

它和直接调用 Core SDK 的差别在于，后续你随时可以继续叠加：

- 工具白名单
- Session
- 内存压缩
- SubAgent
- Teams

而不需要把调用方式整体推倒重写。

## 7. 最小工具接入应该怎么做

真正需要工具时，建议先从严格白名单开始，而不是把所有可用能力一次性暴露出去。

```java
Agent agent = Agents.react()
        .modelClient(new ChatModelClient(chatService))
        .model("gpt-4o-mini")
        .systemPrompt("你是一个严谨的天气助手")
        .instructions("只在必要时调用工具，最终回答保持简洁。")
        .toolRegistry(
                java.util.Arrays.asList("queryWeather"),
                java.util.Collections.<String>emptyList()
        )
        .options(AgentOptions.builder()
                .maxSteps(6)
                .build())
        .build();
```

然后执行：

```java
AgentResult result = agent.run(AgentRequest.builder()
        .input("给出北京今天的天气摘要")
        .build());
```

这里的设计重点不是“加了一个天气工具”，而是分清两层职责：

- `toolRegistry(...)` 决定模型可见工具面
- `ToolExecutor` 决定工具真正如何执行

这也是 AI4J 权限审批、执行拦截、参数治理可以落地的基础。

## 8. 便捷 `toolRegistry(...)` API 的边界

`AgentBuilder` 提供的便捷写法：

```java
.toolRegistry(Arrays.asList("queryWeather"), Collections.<String>emptyList())
```

本质上不是核心抽象，而是基于反射去创建：

- `ToolUtilRegistry`
- `ToolUtilExecutor`

如果对应集成模块不在 classpath 中，`build()` 会抛出 `IllegalStateException`。

所以要区分两个层次：

- 写 demo、快速接工具，用便捷 API
- 做稳定集成、权限治理、自定义执行链时，显式提供 `AgentToolRegistry` 和 `ToolExecutor`

## 9. 执行链路到底发生了什么

最小 ReAct Agent 运行一轮时，关键流程如下：

1. `Agent.run(...)` 把输入写入 `AgentMemory`
2. `BaseAgentRuntime.buildPrompt(...)` 把 memory、system prompt、instructions、tools 组装成 `AgentPrompt`
3. `AgentModelClient.create(...)` 请求模型
4. 如果模型返回普通文本且没有工具调用，本轮结束
5. 如果模型返回 `tool_calls`，runtime 会先做归一化和参数校验
6. 合法工具调用交给 `ToolExecutor.execute(...)`
7. 工具结果以 output item 的形式回写 memory
8. 下一轮重新组 prompt，直到没有新的工具调用或达到停止条件

这就是 Agent 和一次性 Chat/Responses 调用的根本区别：结果不是“一问一答”，而是“模型输出驱动下一轮执行”。

## 10. 什么时候再引入 Session

`Agent` 和 `AgentSession` 的边界很重要。

如果只是一次任务：

```java
AgentResult result = agent.run(request);
```

如果要维持持续对话：

```java
AgentSession session = agent.newSession();
session.run("先记住我在北京");
session.run("现在根据这个前提继续回答");
```

但要知道 `newSession()` 的语义非常具体：

- 会复用同一个 runtime
- 会复用同一个 model client
- 会复用同一个 tool registry / tool executor
- 只替换 `AgentMemory`

因此它隔离的是会话状态，不是整套运行环境。

## 11. 默认值与失败语义

最小 ReAct Agent 跑通后，通常最容易忽略以下几个默认行为。

### 11.1 `maxSteps` 默认不是生产安全值

`AgentOptions.maxSteps` 默认是 `0`，语义是“不限制步数”。

这对实验方便，但对生产环境通常不合适。建议一开始就显式设置上限，例如 `4`、`6`、`8`。

### 11.2 工具失败默认不会立刻终止整个运行

在 `BaseAgentRuntime` 中，工具异常通常会被转换成错误结果回写 memory，而不是直接把整条链路打断。

这意味着：

- 模型有机会基于错误结果继续修正
- 但如果你希望某些工具错误立即失败，应该在执行层自己实现更严格的策略

### 11.3 并行工具调用要求执行器线程安全

当 `parallelToolCalls == true` 且一轮中存在多个合法工具调用时，runtime 可能并行执行它们。

如果你自定义了 `ToolExecutor`，就要自己保证：

- 线程安全
- 幂等性
- 正确的超时与资源回收

## 12. 什么时候不该再停留在最小 ReAct

当需求升级到下面这些场景时，继续停留在“最小 ReAct”就不够了：

- 需要执行模型生成的代码：进入 [CodeAct Runtime](/docs/agent/codeact-runtime)
- 需要显式节点、条件分支、状态推进：进入 [StateGraph](/docs/agent/orchestration/stategraph)
- 需要主从委派：进入 [SubAgent 与 Handoff Policy](/docs/agent/subagent-handoff-policy)
- 需要团队级协作、任务板、消息总线：进入 [Agent Teams](/docs/agent/agent-teams)

关键判断标准不是“功能多不多”，而是当前任务是否已经超出单一 ReAct loop 的边界。

## 13. 推荐阅读源码入口

如果你要从文档继续下钻源码，建议按下面顺序看：

- `ai4j-agent/src/main/java/io/github/lnyocly/ai4j/agent/Agents.java`
- `ai4j-agent/src/main/java/io/github/lnyocly/ai4j/agent/AgentBuilder.java`
- `ai4j-agent/src/main/java/io/github/lnyocly/ai4j/agent/Agent.java`
- `ai4j-agent/src/main/java/io/github/lnyocly/ai4j/agent/runtime/BaseAgentRuntime.java`
- `ai4j-agent/src/main/java/io/github/lnyocly/ai4j/agent/runtime/ReActRuntime.java`
- `ai4j-agent/src/main/java/io/github/lnyocly/ai4j/agent/tool/AgentToolRegistry.java`
- `ai4j-agent/src/main/java/io/github/lnyocly/ai4j/agent/tool/ToolExecutor.java`

## 14. 推荐验证用例

如果你想确认这一层行为，不要只看 demo，优先看测试：

- `ai4j-agent/src/test/java/io/github/lnyocly/agent/CodeActRuntimeTest.java`
- `ai4j-agent/src/test/java/io/github/lnyocly/agent/SubAgentRuntimeTest.java`
- `ai4j-agent/src/test/java/io/github/lnyocly/agent/AgentTeamTest.java`

虽然这些测试覆盖的是更高层功能，但它们都建立在同一套 `AgentBuilder + Runtime + ToolExecutor + Memory` 主线上。

## 15. 下一步读什么

读完这一页后，建议继续：

1. [Agent Architecture](/docs/agent/architecture)
2. [Tools and Registry](/docs/agent/tools-and-registry)
3. [Memory and State](/docs/agent/memory-and-state)
4. [CodeAct Runtime](/docs/agent/codeact-runtime)
