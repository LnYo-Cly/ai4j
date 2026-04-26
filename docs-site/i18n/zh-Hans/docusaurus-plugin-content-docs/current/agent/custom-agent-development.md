---
sidebar_position: 4
---

# 自定义 Agent 开发指南（从 0 到可扩展）

这一页专门回答三个问题：

1. `Agent` 最小可用配置是什么？
2. `AgentBuilder` 每个常用参数怎么选？
3. 我想做“自定义 Agent”（自定义模型适配、运行时、工具执行、记忆）时应该从哪里扩展？

## 1. Agent 构建的最小闭环

在当前实现里，真正必填的只有两项：

- `modelClient(...)`
- `model(...)`

示例：

```java
Agent agent = Agents.react()
        .modelClient(new ResponsesModelClient(responsesService))
        .model("doubao-seed-1-8-251228")
        .build();

AgentResult result = agent.run(AgentRequest.builder()
        .input("用一句话介绍 Responses API")
        .build());
```

> 其他组件（`runtime/toolExecutor/memory/options/codeExecutor`）都有默认值，会在 `AgentBuilder.build()` 阶段自动补齐。

## 2. AgentBuilder 参数全景（按职责分组）

## 2.1 运行时与核心执行

- `runtime(AgentRuntime)`：切换执行策略（ReAct/CodeAct/DeepResearch/你自己的 Runtime）。
- `options(AgentOptions)`：通用运行参数。
  - `maxSteps` 默认 `0`（表示无限）
  - `stream` 默认 `false`
- `codeActOptions(CodeActOptions)`：CodeAct 专属参数。
  - `reAct` 默认 `false`

## 2.2 模型与提示词

- `modelClient(AgentModelClient)`：模型协议适配层（Responses/Chat/自定义）。
- `model(String)`：模型名。
- `systemPrompt(String)`：全局角色/规则。
- `instructions(String)`：当前任务指令。
- 采样与输出：`temperature/topP/maxOutputTokens`
- 扩展字段：`reasoning/toolChoice/parallelToolCalls/store/user/extraBody`

## 2.3 工具与 MCP

- `toolRegistry(List<String> functions, List<String> mcpServices)`
- 或 `toolRegistry(AgentToolRegistry)` 自定义注册器
- `toolExecutor(ToolExecutor)` 自定义执行器

当前语义（你最近关心的点）：

- **传什么，用什么**。
- `ToolUtil.getAllTools(functionList, mcpServerIds)` 只返回你显式传入的 Function/MCP 工具。
- 本地 MCP 全量工具不再自动混入普通 Agent 调用。

## 2.4 CodeAct 执行层

- `codeExecutor(CodeExecutor)`：默认 `GraalVmCodeExecutor`
- 支持 `language=python/js`，并通过工具桥 `callTool(...)` 或工具同名函数调用。

## 2.5 记忆与会话

- `memorySupplier(Supplier<AgentMemory>)`：默认 `InMemoryAgentMemory::new`
- `Agent.newSession()` 会为每个 session 复制 context 并注入独立 memory

## 2.6 SubAgent 与治理策略

- `subAgent(SubAgentDefinition)` / `subAgents(...)`
- `subAgentRegistry(SubAgentRegistry)`
- `handoffPolicy(HandoffPolicy)`

## 2.7 可观测

- `eventPublisher(AgentEventPublisher)`
- `traceExporter(TraceExporter)` + `traceConfig(TraceConfig)`

只要配置了 `traceExporter`，`build()` 会自动注册 `AgentTraceListener`。

## 3. 默认装配行为（build() 时发生了什么）

`AgentBuilder.build()` 的核心默认逻辑：

1. `runtime` 为空 -> `ReActRuntime`
2. `memorySupplier` 为空 -> `InMemoryAgentMemory::new`
3. `toolRegistry` 为空 -> `StaticToolRegistry.empty()`
4. `toolExecutor` 为空 -> `ToolUtilExecutor(allowedToolNames)`
5. 配置了 SubAgent -> 包装成 `SubAgentToolExecutor`
6. `codeExecutor` 为空 -> `GraalVmCodeExecutor`
7. `options/codeActOptions/eventPublisher` 都有默认对象
8. 如果设置 `traceExporter` -> 自动挂 `AgentTraceListener`
9. `modelClient` 为空会直接抛异常

## 4. 四种“自定义 Agent”扩展方式

## 4.1 自定义模型客户端（最常见）

你可以实现 `AgentModelClient`，把任何模型协议接入到 Agent runtime：

```java
public class MyModelClient implements AgentModelClient {
    @Override
    public AgentModelResult create(AgentPrompt prompt) {
        // 1) 把 AgentPrompt 映射到你的模型 API
        // 2) 把返回值映射成 AgentModelResult
        return AgentModelResult.builder()
                .outputText("...")
                .toolCalls(Collections.emptyList())
                .memoryItems(Collections.emptyList())
                .rawResponse(null)
                .build();
    }

    @Override
    public AgentModelResult createStream(AgentPrompt prompt, AgentModelStreamListener listener) {
        // 需要流式就推送 delta，再 onComplete
        return create(prompt);
    }
}
```

## 4.2 自定义工具注册/执行

- 自定义 `AgentToolRegistry`：决定“暴露给模型哪些工具”。
- 自定义 `ToolExecutor`：决定“工具如何执行、鉴权、限流、审计”。

适用场景：

- 接企业内部工具总线
- 工具调用前后统一埋点
- 多租户鉴权

## 4.3 自定义记忆与压缩

你可以注入 `memorySupplier`，例如：

- 基于 Redis 的会话记忆
- 自定义 `MemoryCompressor` 的窗口压缩/摘要压缩

```java
Agent agent = Agents.react()
        .modelClient(modelClient)
        .model("your-model")
        .memorySupplier(() -> new InMemoryAgentMemory(new WindowedMemoryCompressor(20)))
        .build();
```

## 4.4 自定义 Runtime（最高自由度）

如果 ReAct/CodeAct/DeepResearch 都不满足，你可以直接实现 `AgentRuntime`，或继承 `BaseAgentRuntime`。

- 实现 `run(...)` / `runStream(...)`
- 决定每轮如何构造 prompt、何时结束、何时调用工具

详见下一页《Runtime 实现详解》。

## 5. 推荐的工程化分层

生产项目推荐按三层拆分：

1. **模型适配层**（`AgentModelClient`）
2. **运行策略层**（`AgentRuntime`）
3. **业务编排层**（`Workflow + SubAgent`）

这样做的好处：

- 模型迁移不会牵动业务编排
- 策略升级（ReAct -> CodeAct）成本低
- 线上排障可定位到明确层次

## 6. 你最近实现过的“可直接复用模板”

- 双 Agent 天气流：`WeatherAgentWorkflowTest`
- StateGraph 路由/分支/循环：`StateGraphWorkflowTest`
- CodeAct + Tool + Trace：`CodeActRuntimeTest`、`CodeActRuntimeWithTraceTest`
- SubAgent handoff 策略：`SubAgentRuntimeTest`、`SubAgentParallelFallbackTest`、`HandoffPolicyTest`

建议你在业务里按这个顺序落地：

1. 先单 Agent + 明确工具白名单
2. 再接 Workflow
3. 最后再引入 SubAgent 和复杂 handoff policy
