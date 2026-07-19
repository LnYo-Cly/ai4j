---
sidebar_position: 2
---

# 最小 ReAct Agent

这页讲的不是“最短 demo”，而是 AI4J Agent 层最小但完整的运行闭环。

所谓“最小”，不是只剩几行代码，而是只保留真正构成 Agent loop 的必要部件：

- 一个 `AgentModelClient`
- 一个 `model`
- 一个 `AgentRuntime`
- 一个 `AgentMemory`
- 可选的工具声明面与执行面

如果这一层还没跑通，不应该先上 CodeAct、SubAgent、StateGraph 或 Agent Teams。

## 1. 先抓住 3 个关键设计决策

### 1.1 ReAct 不是“一个模式”，而是默认 runtime

`Agents.react()` 本质上不是一个完全不同的产品入口，而是对 `AgentBuilder` 的一层快捷封装。

从 `AgentBuilder.build()` 的默认装配看：

- `runtime == null` 时默认使用 `ReActRuntime`
- `memorySupplier == null` 时默认使用 `InMemoryAgentMemory::new`
- `toolRegistry == null` 时默认使用 `StaticToolRegistry.empty()`

也就是说，ReAct 不是额外插件，而是 Agent 层默认主线。

### 1.2 最小 Agent 即使没有工具也仍然成立

很多人第一次接 Agent，会以为“没有 tool call 就不算 Agent”。

这在 AI4J 里不对。

即使 `toolRegistry` 是空的，最小 ReAct Agent 仍然具备：

- 统一的 `AgentPrompt` 组装
- `AgentMemory` 状态累积
- step loop
- streaming / event / trace 接入点

工具只是让 loop 更有外部操作能力，不是 Agent 成立的前提。

### 1.3 Session 隔离只换 memory，不换整套运行环境

`Agent.newSession()` 的实现非常重要：

```java
AgentMemory memory = memorySupplier == null ? baseContext.getMemory() : memorySupplier.get();
AgentContext sessionContext = baseContext.toBuilder().memory(memory).build();
```

这意味着新 session 会复用：

- 同一个 runtime
- 同一个 model client
- 同一个 tool registry
- 同一个 tool executor

只替换 `memory`。

所以 session 的边界是“状态隔离”，不是“运行环境隔离”。

## 2. 这页真正解决什么问题

最小 ReAct Agent 的价值，不是教你拼一段 demo，而是先把下面几组概念拆开：

- 一次模型调用 vs 多步 Agent loop
- 工具暴露面 vs 工具执行面
- 单次运行 vs 会话持续
- runtime 策略 vs 模型协议

把这些边界先理顺，后面的 CodeAct、SubAgent、Teams 才不会越学越乱。

## 3. 最小对象图

最小 ReAct Agent 涉及的核心对象并不多，但关系必须先看清：

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

其中最关键的职责分工是：

| 对象 | 真正职责 |
| --- | --- |
| `AgentBuilder` | 装配默认依赖和执行链 |
| `Agent` | 暴露 `run(...)`、`runStream(...)`、`newSession()` |
| `ReActRuntime` | 决定 loop 如何推进 |
| `AgentModelClient` | 适配底层模型协议 |
| `AgentMemory` | 保存输入、输出、工具结果 |
| `AgentToolRegistry` | 告诉模型“有哪些工具” |
| `ToolExecutor` | 决定工具“真正怎么执行” |

最容易混淆的就是最后两项。AI4J 从设计上就把“声明能力”和“执行能力”拆开了。

## 4. `AgentBuilder` 默认装配链到底做了什么

理解最小 ReAct Agent，最值得直接读的类其实是 `AgentBuilder`。

它在 `build()` 里做的不是简单 `new Agent(...)`，而是一整套默认装配：

1. 解析 runtime
   - 默认 `ReActRuntime`
2. 解析 memory supplier
   - 默认 `InMemoryAgentMemory::new`
3. 解析基础工具注册器
   - 默认 `StaticToolRegistry.empty()`
4. 若配置了 SubAgent，再合并 subagent tools
5. 若未显式提供 `ToolExecutor`，尝试基于工具名创建默认执行器
6. 解析默认 `CodeExecutor`
   - Java 8 -> `NashornCodeExecutor`
   - 更高版本 -> `GraalVmCodeExecutor`
7. 构造 `AgentContext`

这条装配链说明了一件很重要的事：

- ReAct Agent 的最小可运行状态，不需要你自己手动拼装一堆对象
- 但一旦你要治理工具、替换 memory、插入 subagent 或切换 runtime，就必须回到 `AgentBuilder` 理解默认行为

## 5. `ReActRuntime` 在当前实现里到底有多“薄”

很多人会以为 ReActRuntime 自己有很多复杂逻辑，但实际上当前实现非常薄：

- `runtimeName() -> "react"`
- `runtimeInstructions() -> "Use tools when necessary. Return concise final answers."`

真正的主循环几乎都在 `BaseAgentRuntime`。

这有两个直接含义：

1. ReAct 是“最贴近框架默认能力”的 runtime
2. 你调 ReAct 行为时，很多问题其实应该回去看 `BaseAgentRuntime`

## 6. 最小执行链是怎么跑起来的

最小 ReAct Agent 的一次 `run(...)`，关键流程如下：

1. `Agent.run(request)`
2. `ReActRuntime.run(...)`
3. `BaseAgentRuntime.runInternal(...)`
4. 把用户输入写进 `AgentMemory`
5. `buildPrompt(...)` 组装 `AgentPrompt`
6. `AgentModelClient.create(...)` 或 `createStream(...)`
7. 若模型返回 `memoryItems`，写回 memory
8. 归一化 `toolCalls`
9. 校验工具调用
10. 调 `ToolExecutor.execute(...)`
11. 把工具结果回写 memory
12. 无工具调用则结束，否则下一轮继续

这条链路最关键的地方在于：

- 工具结果不是直接返回给业务层
- 而是先写进 memory，再决定下一轮 prompt 长什么样

所以 Agent 的本质不是“一问一答”，而是“输出反过来塑造下一轮输入”。

## 7. 最小可用示例到底应该验证什么

从工程角度，最小可用示例至少要显式设置：

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
```

这个例子真正验证的是：

- 模型链路是否打通
- runtime 是否能运行
- `AgentMemory` 是否正常累积输入输出
- `AgentResult` 是否能正确收口

它并不是在验证工具调用，因为这一步还没引入工具。

## 8. “空工具 Agent” 和“带工具 Agent”的边界

### 8.1 不带工具时

你得到的是：

- 一个带 memory 的模型运行时
- 一个可多步推进的 loop
- 一个可插入 trace / stream / event 的执行入口

### 8.2 带工具时

你才真正进入：

- 工具暴露面
- 参数校验
- 工具执行
- 工具结果回灌

因此学习顺序应该是：

1. 先跑通空工具 Agent
2. 再接最小工具白名单
3. 再讨论治理和扩展

否则很容易把问题混在一起。

## 9. 工具接入为什么一定要先理解 `Registry` 和 `Executor`

这几乎是整个 Agent 层最重要的边界之一。

### 9.1 `AgentToolRegistry`

它回答的问题只有一个：

> 模型能看到哪些工具？

### 9.2 `ToolExecutor`

它回答的问题也只有一个：

> 当模型真的发起调用时，系统怎么执行它？

这种拆分带来的工程收益非常大：

- 工具白名单可以稳定存在
- 权限审批不必写在 schema 里
- 审计、限流、沙箱、代理转发可以挂在执行面

所以如果你的目标是做权限治理，重点永远在 `ToolExecutor`，不是在“把工具藏起来”。

## 10. 便捷 `toolRegistry(List<String>, List<String>)` 的真实边界

`AgentBuilder` 提供了一个很顺手的入口：

```java
.toolRegistry(Arrays.asList("queryWeather"), Collections.<String>emptyList())
```

但它本质上是一个反射式 convenience API，会尝试初始化：

- `ToolUtilRegistry`
- `ToolUtilExecutor`

如果对应模块不在 classpath 中，`build()` 会直接抛 `IllegalStateException`。

所以这条 API 更适合：

- 快速 demo
- 已知工具模块完整可用的场景

如果你在做稳定工程集成，应该显式提供：

- `AgentToolRegistry`
- `ToolExecutor`

## 11. 默认值和失败语义里最容易踩的坑

### 11.1 `maxSteps = 0` 不是安全默认值

`BaseAgentRuntime.runInternal(...)` 中：

- `maxSteps > 0` 才算有步数上限
- 否则 loop 没有硬限制

这对实验方便，但对生产通常不合适。

### 11.2 工具错误默认会被写回 memory，而不是直接抛出

`executeTool(...)` 会捕获异常，并构造成：

```text
TOOL_ERROR: {"errorType":"...","error":"...","tool":"...","callId":"..."}
```

然后继续主循环。

这意味着默认语义是：

- 工具失败优先作为“可恢复上下文”
- 而不是“立即终止整轮运行”

### 11.3 并行工具调用依赖执行器线程安全

只有在下面两个条件都成立时才会并行：

- `parallelToolCalls == true`
- 同一轮合法 tool calls 数量大于 1

并行是 runtime 自己开的线程池，所以你自定义的 `ToolExecutor` 必须自己满足线程安全。

## 12. `Agent` 和 `AgentSession` 的真实边界

### `Agent`

更像“共享配置和默认依赖的运行入口”。

### `AgentSession`

更像“在相同运行环境下换了一块新的 memory”。

因此：

- 想换用户上下文或会话状态，用 `newSession()`
- 想换 runtime、工具面、执行权限或模型配置，重新 `build()` 一个 Agent 更清晰

## 13. 什么时候该离开“最小 ReAct”

继续停留在最小 ReAct 不够用，通常是因为你已经遇到下面这些情况之一：

- 需要执行模型生成的代码：进入 [CodeAct Runtime](/docs/agent/codeact-runtime)
- 需要显式节点和状态推进：进入 [Workflow StateGraph](/docs/agent/workflow-stategraph)
- 需要主从委派：进入 [SubAgent 与 Handoff Policy](/docs/agent/subagent-handoff-policy)
- 需要团队协作：进入 [Agent Teams](/docs/agent/agent-teams)

判断标准不是“功能多不多”，而是当前问题是否还属于单一 tool loop。

## 14. 推荐阅读源码顺序

- `ai4j-agent/src/main/java/io/github/lnyocly/ai4j/agent/AgentBuilder.java`
- `ai4j-agent/src/main/java/io/github/lnyocly/ai4j/agent/Agent.java`
- `ai4j-agent/src/main/java/io/github/lnyocly/ai4j/agent/AgentSession.java`
- `ai4j-agent/src/main/java/io/github/lnyocly/ai4j/agent/runtime/BaseAgentRuntime.java`
- `ai4j-agent/src/main/java/io/github/lnyocly/ai4j/agent/runtime/ReActRuntime.java`
- `ai4j-agent/src/main/java/io/github/lnyocly/ai4j/agent/tool/AgentToolRegistry.java`
- `ai4j-agent/src/main/java/io/github/lnyocly/ai4j/agent/tool/ToolExecutor.java`

## 15. 继续阅读

1. [Agent Architecture](/docs/agent/architecture)
2. [Tools and Registry](/docs/agent/tools-and-registry)
3. [Memory and State](/docs/agent/memory-and-state)
4. [CodeAct Runtime](/docs/agent/codeact-runtime)
