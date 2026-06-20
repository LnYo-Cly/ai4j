# Agent Quickstart

这页的目标不是展示“最短 demo”，而是帮你先跑通一条最小但真实的 Agent 主链。

所谓真实，是指这条链里至少要经过：

- `AgentBuilder` 默认装配
- `AgentRuntime` step loop
- `AgentModelClient` 协议适配
- `AgentMemory` 写入与回灌
- `AgentResult` 收口

如果这条线还没跑通，就不应该先上：

- workflow
- subagent
- team
- 自定义审批
- trace 平台接入

## 1. 先抓住 4 个关键设计决策

### 1.1 Quickstart 先验证的是“运行链成立”，不是“功能堆满”

最小 quickstart 的任务只有一个：

> 证明一次 Agent run 能从输入走到最终输出。

因此第一版示例不应该一上来就依赖：

- 多个工具
- 外部审批
- 复杂 memory
- workflow 编排

否则你根本分不清问题出在哪一层。

### 1.2 `modelClient(...)` 是必填依赖，模型名不是

`AgentBuilder.build()` 真正硬性要求的是：

- `modelClient != null`

如果没传，它会直接抛：

```text
IllegalStateException: modelClient is required
```

而 `model(...)` 虽然在 Builder 阶段不会立即拦住你，但 `BaseAgentRuntime.buildPrompt(...)` 会在运行时检查：

```text
IllegalStateException: model is required
```

也就是说：

- `modelClient` 决定怎么发请求
- `model` 决定请求发给谁

两者缺一都不行。

### 1.3 默认 `maxSteps = 0`，不是安全默认值

`AgentOptions.builder().build()` 的默认值里：

- `maxSteps = 0`

而 `BaseAgentRuntime.runInternal(...)` 的语义是：

- `maxSteps > 0` 才认为有硬上限
- 否则 loop 不设步数上限

所以 quickstart 示例里最好显式带上：

```java
.options(AgentOptions.builder().maxSteps(1).build())
```

或：

```java
.options(AgentOptions.builder().maxSteps(2).build())
```

不要把“实验方便”误当成“生产安全默认值”。

### 1.4 `toolRegistry(List<String>, List<String>)` 是便利入口，不是底层唯一入口

你最容易在 quickstart 里看到的是：

```java
.toolRegistry(Arrays.asList("queryWeather"), null)
```

但这个 API 本质上是一个反射装配入口，会尝试创建：

- `ToolUtilRegistry`
- `ToolUtilExecutor`

如果相关 integration 模块不在 classpath 中，`build()` 会失败。

所以真正更稳的理解方式是：

- 这是 demo / 快速接线入口
- 不是唯一的正式工程接法

## 2. 第一条正确验证路径：先跑无工具 Agent

最小 quickstart 最推荐先做“无工具验证”。

原因很简单：

- 不依赖 ToolUtil
- 不依赖工具白名单
- 不依赖工具执行
- 只验证模型协议 + runtime loop + memory 回写

示例：

```java
import io.github.lnyocly.ai4j.agent.Agent;
import io.github.lnyocly.ai4j.agent.AgentOptions;
import io.github.lnyocly.ai4j.agent.AgentRequest;
import io.github.lnyocly.ai4j.agent.AgentResult;
import io.github.lnyocly.ai4j.agent.Agents;
import io.github.lnyocly.ai4j.agent.model.ResponsesModelClient;

Agent agent = Agents.react()
        .modelClient(new ResponsesModelClient(responsesService))
        .model("gpt-4.1")
        .systemPrompt("You are a concise assistant.")
        .options(AgentOptions.builder().maxSteps(1).build())
        .build();

AgentResult result = agent.run(AgentRequest.builder()
        .input("用一句话介绍 AI4J Agent")
        .build());

System.out.println(result.getOutputText());
System.out.println(result.getSteps());
```

这一步如果没过，问题通常只会落在：

- provider 凭证 / baseUrl
- `modelClient` 协议接法
- 模型名
- 最基础 runtime 运行

## 3. 第二条验证路径：再加最小工具白名单

无工具 Agent 跑通以后，再加工具才有意义。

示例：

```java
Agent agent = Agents.react()
        .modelClient(new ResponsesModelClient(responsesService))
        .model("gpt-4.1")
        .systemPrompt("You are a weather assistant.")
        .instructions("Use queryWeather when weather information is needed.")
        .toolRegistry(java.util.Arrays.asList("queryWeather"), null)
        .options(AgentOptions.builder().maxSteps(2).build())
        .build();

AgentResult result = agent.run(AgentRequest.builder()
        .input("请给出北京今天的天气摘要")
        .build());
```

这里验证的不是“系统里有没有天气工具”，而是下面 3 件事是否同时成立：

1. 工具 schema 能被暴露给模型
2. 模型真的会返回 tool call
3. `ToolExecutor` 能执行，并把结果写回 memory

## 4. 这段代码背后真实发生了什么

quickstart 最有价值的地方，不在代码行数，而在于它刚好覆盖了默认装配链。

### 4.1 `Agents.react()` 不是神秘入口

它本质上只是进入 `AgentBuilder` 并默认使用 `ReActRuntime`。

### 4.2 `AgentBuilder.build()` 会补哪些默认值

当前默认装配包括：

- `runtime` -> `ReActRuntime`
- `memorySupplier` -> `InMemoryAgentMemory::new`
- `toolRegistry` -> `StaticToolRegistry.empty()`
- `codeExecutor` -> Java 8 用 `NashornCodeExecutor`，更高版本用 `GraalVmCodeExecutor`
- `options` -> `AgentOptions.builder().build()`
- `codeActOptions` -> `CodeActOptions.builder().build()`
- `eventPublisher` -> 新建 `AgentEventPublisher`

如果你还配置了：

- `traceExporter(...)`

Builder 会顺便把 `AgentTraceListener` 挂上去。

### 4.3 `run(...)` 后真正进入哪条链

运行路径大致是：

```text
Agent.run(...)
  -> ReActRuntime.run(...)
  -> BaseAgentRuntime.runInternal(...)
  -> memory.addUserInput(...)
  -> buildPrompt(...)
  -> modelClient.create(...)
  -> memory.addOutputItems(...)
  -> normalizeToolCalls(...)
  -> execute tools if needed
  -> final AgentResult
```

如果这条链你还没看懂，quickstart 最好不要继续加新能力。

## 5. Quickstart 应该先验证什么

### 5.1 先看是不是能跑完一轮

最小要求：

- 不报异常
- `AgentResult.outputText` 有值
- `AgentResult.steps` 是合理值

### 5.2 再看是不是意外进入多轮

如果你明明只想要一轮纯问答，却发现：

- `steps > 1`

通常说明：

- prompt 诱导了工具行为
- 或模型输出被解释成了继续循环的条件

### 5.3 工具版再看有没有完整闭环

工具版不能只看最终答案，还要看：

- `toolCalls`
- `toolResults`
- `steps`

因为最终答案有时候看起来“像是对的”，但实际上根本没触发工具。

## 6. `AgentResult` 里最值得看的字段

`AgentResult` 当前字段很少：

- `outputText`
- `rawResponse`
- `toolCalls`
- `toolResults`
- `steps`

对应的排障价值是：

| 字段 | 主要用途 |
| --- | --- |
| `outputText` | 看最终回答 |
| `rawResponse` | 看 provider 原始响应结构 |
| `toolCalls` | 看模型到底想调什么工具 |
| `toolResults` | 看工具有没有真的执行，以及产出了什么 |
| `steps` | 看 loop 跑了几轮 |

## 7. Quickstart 最常见的错误，其实分别指向哪一层

### 7.1 只传模型名，不传 `modelClient`

这不是模型问题，而是协议适配层没装上。

### 7.2 直接调 Core SDK 服务就以为进入了 Agent

直接调 `IChatService` / `IResponsesService` 还不是 Agent。

只有进入 `AgentRuntime` 主循环，才算真正进入 Agent 层。

### 7.3 工具存在，但模型看不见

这通常不是工具实现没写，而是你没把工具放进：

- `toolRegistry`

### 7.4 `toolRegistry(List<String>, List<String>)` 一调用就抛错

这通常不是 runtime 问题，而是：

- `ToolUtilRegistry`
- `ToolUtilExecutor`

对应模块没有进入 classpath。

### 7.5 一上来就把 workflow / subagent / team 全叠上去

这会把排障面从：

- 一个 Agent

瞬间扩大成：

- runtime
- tool surface
- handoff
- task board
- message bus

这不是快，而是更难定位。

## 8. 什么时候 quickstart 已经不够用了

如果你已经遇到下面这些问题之一，就说明该离开 quickstart 了：

- 想判断 `ChatModelClient` 还是 `ResponsesModelClient` 更适合
- 想理解 `systemPrompt` 和 `instructions` 到底怎么映射
- 想做工具审批、拦截、审计
- 想上更长会话或 memory 压缩
- 想把 Agent 节点编排进 workflow
- 想切到 CodeAct / SubAgent / Team

quickstart 的使命是帮你先把入口打通，不是承载全部复杂度。

## 9. 推荐的最小验证顺序

1. 先跑无工具 Agent
2. 再加一个最小工具白名单
3. 再打开 trace 看 step / model / tool
4. 再考虑 memory / workflow / subagent / team

这个顺序看起来慢，实际上排障速度最快。

## 10. 推荐阅读源码顺序

- `ai4j-agent/src/main/java/io/github/lnyocly/ai4j/agent/Agents.java`
- `ai4j-agent/src/main/java/io/github/lnyocly/ai4j/agent/AgentBuilder.java`
- `ai4j-agent/src/main/java/io/github/lnyocly/ai4j/agent/Agent.java`
- `ai4j-agent/src/main/java/io/github/lnyocly/ai4j/agent/AgentOptions.java`
- `ai4j-agent/src/main/java/io/github/lnyocly/ai4j/agent/runtime/BaseAgentRuntime.java`
- `ai4j-agent/src/main/java/io/github/lnyocly/ai4j/agent/runtime/ReActRuntime.java`
- `ai4j-agent/src/main/java/io/github/lnyocly/ai4j/agent/AgentResult.java`

## 11. 下一步读什么

1. [Agent SDK 真实 API 能力矩阵](/docs/agent/real-api-matrix)
2. [Minimal ReAct Agent](/docs/agent/minimal-react-agent)
3. [Model Client Selection](/docs/agent/model-client-selection)
4. [Tools and Registry](/docs/agent/tools-and-registry)
5. [Memory and State](/docs/agent/memory-and-state)
6. [Runtime Implementations](/docs/agent/runtime-implementations)
