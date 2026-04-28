# Agent Quickstart

这页的目标不是把整个 `ai4j-agent` 章节讲完，而是用最短路径先打通一条真实可运行链路：

- 模型请求能发出去
- Agent runtime 能进入 step loop
- 工具能被暴露给模型
- 工具结果能回灌到下一轮
- 最终结果能从 `AgentResult` 读出来

只要这条主链成立，后面再讨论 runtime 选型、memory 压缩、workflow 编排才有意义。

## 1. 起步前你至少要准备什么

最小可运行 Agent 至少需要四类输入：

- 一个 `AgentModelClient`
- 一个模型名
- 可选的工具暴露面
- 一次 `AgentRequest`

这里最容易混淆的是：

- `modelClient` 不是模型名，它是模型协议适配器
- `toolRegistry(...)` 不是工具执行器，它只决定模型看见什么
- `Agent` 不是直接调用 `IChatService` / `IResponsesService`

## 2. 最小 ReAct Agent

```java
import io.github.lnyocly.ai4j.agent.Agent;
import io.github.lnyocly.ai4j.agent.AgentRequest;
import io.github.lnyocly.ai4j.agent.AgentResult;
import io.github.lnyocly.ai4j.agent.Agents;
import io.github.lnyocly.ai4j.agent.model.ResponsesModelClient;

Agent agent = Agents.react()
        .modelClient(new ResponsesModelClient(responsesService))
        .model("gpt-4.1")
        .systemPrompt("You are a careful assistant.")
        .instructions("Use tools only when necessary.")
        .toolRegistry(java.util.Arrays.asList("queryWeather"), null)
        .build();

AgentResult result = agent.run(AgentRequest.builder()
        .input("请给出北京今天的天气摘要")
        .build());

System.out.println(result.getOutputText());
```

如果你已经有 `Chat` 协议接入，也可以替换为：

```java
Agent agent = Agents.react()
        .modelClient(new ChatModelClient(chatService))
        .model("your-chat-model")
        .toolRegistry(java.util.Arrays.asList("queryWeather"), null)
        .build();
```

## 3. 这段代码背后真实发生了什么

`Agents.react()` 不只是语法糖，它等价于：

- 创建一个 `AgentBuilder`
- 预装 `ReActRuntime`

`build()` 时，`AgentBuilder` 还会继续解析默认依赖：

- `runtime` 默认 `ReActRuntime`
- `memorySupplier` 默认 `InMemoryAgentMemory::new`
- `toolRegistry` 默认 `StaticToolRegistry.empty()`
- 若未显式提供 `toolExecutor`，按当前工具名解析 `ToolUtilExecutor`

因此 quickstart 的真正价值不是“代码短”，而是一次把装配语义、工具语义和主循环语义全部跑通。

## 4. 先跑通什么，再往上加复杂度

最推荐的顺序是：

### 4.1 先验证模型调用本身

确认：

- `modelClient` 能成功请求目标 provider
- `model(...)` 使用的模型名有效
- 不带工具时也能返回正常文本

### 4.2 再验证工具暴露

确认：

- `toolRegistry(...)` 中的工具名真能被 `ToolUtil` 解析
- 模型在需要时会返回 tool call
- `ToolExecutor` 能执行这些工具

### 4.3 再验证闭环

确认：

- 工具结果进入 `AgentMemory`
- Agent 会继续下一轮，而不是在 tool call 后停住
- `AgentResult.getToolCalls()` 与 `getToolResults()` 能反映实际执行链

只有这三层都成立，才说明最小 Agent runtime 真正可用。

## 5. `AgentResult` 里应该看什么

最小 quickstart 跑通后，不要只看 `outputText`。至少还应检查：

- `outputText`
- `toolCalls`
- `toolResults`
- `steps`
- `rawResponse`

对调试最有价值的通常是：

- 是否真的产生了 tool call
- 工具有没有执行
- 最终经历了几步

## 6. 一个更完整的最小验证示例

```java
AgentResult result = agent.run(AgentRequest.builder()
        .input("请给出北京今天的天气摘要")
        .build());

System.out.println("steps = " + result.getSteps());
System.out.println("toolCalls = " + result.getToolCalls());
System.out.println("toolResults = " + result.getToolResults());
System.out.println("answer = " + result.getOutputText());
```

如果模型没有发出工具调用，但你本来预期它会发，那么问题通常不在 runtime 本身，而在：

- prompt 没有驱动模型用工具
- 工具没有正确暴露
- 当前模型或 provider 对 tool calling 支持不一致

## 7. Quickstart 最常见的错误

### 7.1 只写了模型名，没写 `modelClient`

`modelClient` 是必填依赖。没有它，`AgentBuilder.build()` 会直接失败。

### 7.2 把 Core SDK 直接调用误当成 Agent

直接调 `IChatService` 或 `IResponsesService` 还不是 Agent。只有进入 `AgentRuntime` 主循环，才叫真正进入 Agent 层。

### 7.3 工具在 SDK 层存在，但没加入 `toolRegistry`

工具存在于系统里，不等于模型就能看到。模型可见工具面必须显式经过 `toolRegistry(...)`。

### 7.4 一开始就叠加 workflow / subagent / team

先验证单 Agent 再叠复杂度，排障效率会高很多。

## 8. 什么时候该从 quickstart 升级

当下面任何一个条件成立时，就不应继续停留在 quickstart 心智：

- 需要判断 `ChatModelClient` 还是 `ResponsesModelClient`
- 需要长期 session 和记忆压缩
- 需要自定义工具审批、审计、沙箱
- 需要切到 `CodeActRuntime`
- 需要 `StateGraph`、`SubAgent` 或 `Team`

这时你需要进入下一层页面，而不是继续往 quickstart 代码里堆条件。

## 9. 下一步读什么

### 想先判断协议层怎么选

看 [Model Client Selection](/docs/agent/model-client-selection)

### 想理解主循环和装配链

看 [Architecture](/docs/agent/architecture)

### 想理解状态怎样跨步保留

看 [Memory and State](/docs/agent/memory-and-state)

### 想理解工具如何暴露和治理

看 [Tools and Registry](/docs/agent/tools-and-registry)

### 想继续看 runtime 选型

看 [Runtime Implementations](/docs/agent/runtime-implementations)
