# Agent Architecture

`Agent` 这一层最值得记住的不是某个 builder，而是它把“模型、工具、记忆、编排、观测”拆成了清晰的运行时层次。

## 1. 先看主执行链

一次典型的 Agent 运行，可以先压成这条主链：

```text
AgentBuilder
  -> AgentContext
  -> AgentRuntime
  -> AgentModelClient
  -> AgentToolRegistry / ToolExecutor
  -> AgentMemory
  -> AgentEventPublisher / AgentTraceListener
```

对应代码锚点：

- `AgentBuilder`
- `AgentContext`
- `AgentRuntime`
- `BaseAgentRuntime`
- `AgentModelClient`
- `AgentToolRegistry`
- `ToolExecutor`
- `AgentMemory`
- `AgentEventPublisher`

## 2. 四层结构怎么理解

### 模型层

模型层只回答一件事：请求怎么发给模型。

主要入口：

- `AgentModelClient`
- `ChatModelClient`
- `ResponsesModelClient`
- `AgentPrompt`
- `AgentModelResult`

这一层负责把 runtime 组织好的 prompt、tools、sampling 参数发给模型，并把返回结果转成 agent 统一语义。

### Runtime 层

runtime 层回答：一轮结束后该怎么继续。

主要入口：

- `BaseAgentRuntime`
- `ReActRuntime`
- `CodeActRuntime`
- `DeepResearchRuntime`

`BaseAgentRuntime` 负责最经典的 loop：

1. 从 `AgentMemory` 取 items 组 prompt
2. 请求模型
3. 归一化 tool calls
4. 校验并执行工具
5. 把 tool result 写回 memory
6. 决定是否继续下一步

`CodeActRuntime` 则把“工具循环”改成了“模型产出代码 -> 执行代码 -> 结果再回灌”。

### Tool 层

tool 层回答：模型看到了哪些工具，真正执行工具的是谁。

主要入口：

- `AgentToolRegistry`
- `StaticToolRegistry`
- `CompositeToolRegistry`
- `ToolUtilRegistry`
- `ToolExecutor`
- `ToolUtilExecutor`
- `AgentToolCallSanitizer`

它把“工具声明”和“工具执行”拆成两层，方便做：

- allow-list
- 参数校验
- subagent handoff
- team tool surface
- 审计和 trace

### 状态与编排层

状态和编排层回答：任务如何长期持续，而不是只跑一轮。

主要入口：

- `AgentMemory`
- `workflow/*`
- `subagent/*`
- `team/*`
- `trace/*`

这部分才是 `Agent` 相比普通 SDK 调用最有价值的地方。

## 3. 模块路径怎么读

主路径：

- `ai4j-agent/src/main/java/io/github/lnyocly/ai4j/agent`

建议按这个顺序读源码：

1. `Agents` 和 `AgentBuilder`
2. `model`
3. `runtime`
4. `tool`
5. `memory`
6. `workflow`
7. `subagent`
8. `team`
9. `trace`

这样最不容易把“单轮工具循环”和“更高层的显式编排”混在一起。

## 4. Builder 装配时真正发生了什么

`AgentBuilder.build()` 做的事情比“new 一个 Agent”要多：

- 解析 runtime，默认是 `ReActRuntime`
- 解析 memory supplier，默认是 `InMemoryAgentMemory`
- 解析 tool registry，默认空 registry
- 如果你传了 `toolRegistry(List<String> functions, List<String> mcpServices)`，会走 `ToolUtilRegistry`
- 如果有 subagent，会把 subagent tools 合并进 `CompositeToolRegistry`
- 如果没手传 `ToolExecutor`，会按已暴露工具名构造 `ToolUtilExecutor`
- 如果配了 `traceExporter`，会自动把 `AgentTraceListener` 挂到 `AgentEventPublisher`
- 根据 Java 版本选择默认代码执行器：Java 8 下偏 `NashornCodeExecutor`，更高版本偏 `GraalVmCodeExecutor`

这说明 `AgentBuilder` 的本质不是“参数收集器”，而是 runtime 装配器。

## 5. 运行时事件流

Agent 运行不是黑盒，它会持续发出事件：

- `STEP_START`
- `MODEL_REQUEST`
- `MODEL_RESPONSE`
- `MODEL_REASONING`
- `MODEL_RETRY`
- `TOOL_CALL`
- `TOOL_RESULT`
- `FINAL_OUTPUT`
- `ERROR`

这些事件先服务于 runtime 自己，再被 trace、CLI、TUI、ACP 或上层平台消费。

## 6. 最重要的三道边界

### Builder 和 Runtime 的边界

`AgentBuilder` 负责装配。

`AgentRuntime` 负责执行。

不要把这两层混成“builder 就是 agent 核心逻辑”。

### Tool Registry 和 Tool Executor 的边界

`AgentToolRegistry` 决定模型能看到哪些工具。

`ToolExecutor` 决定工具真正怎么执行。

这也是为什么安全治理通常挂在执行侧，而不是只挂在 schema 侧。

### Memory 和 Workflow 的边界

`AgentMemory` 是运行时状态源。

`Workflow` / `SubAgent` / `Team` 是状态如何被组织和流转的上层编排面。

先有 memory，再谈编排，逻辑会更清楚。

## 7. 这页之后看什么

- 想先跑通最小路径：看 [Quickstart](/docs/agent/quickstart)
- 想判断 `Chat` 还是 `Responses`：看 [Model Client Selection](/docs/agent/model-client-selection)
- 想理解状态如何持续：看 [Memory and State](/docs/agent/memory-and-state)
- 想继续看具体 runtime：看 [Runtime Implementations](/docs/agent/runtimes/runtime-implementations)
