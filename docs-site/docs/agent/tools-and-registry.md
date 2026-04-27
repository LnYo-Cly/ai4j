# Tools and Registry

到 `Agent` 层后，问题已经不再是“函数怎么声明”，而是：

- 模型能看到哪些工具
- 工具真正由谁执行
- 工具暴露面如何治理
- tool result 怎样重新进入 runtime

## 1. 先分清两个抽象

最关键的两个接口是：

- `AgentToolRegistry`
- `ToolExecutor`

一句话区分：

- `AgentToolRegistry` 决定“模型看到什么”
- `ToolExecutor` 决定“工具怎么执行”

这两层拆开之后，才可能做真正的治理。

## 2. 真实代码路径

主路径：

- `ai4j-agent/src/main/java/io/github/lnyocly/ai4j/agent/tool`

核心类：

- `AgentToolRegistry`
- `StaticToolRegistry`
- `CompositeToolRegistry`
- `ToolUtilRegistry`
- `ToolExecutor`
- `ToolUtilExecutor`
- `AgentToolCall`
- `AgentToolResult`
- `AgentToolCallSanitizer`

## 3. 最常见的装配方式

### 方式 A：直接传 `AgentToolRegistry`

适合你已经有明确工具面，想自己完全控制暴露列表。

### 方式 B：传函数名和 MCP 服务名

```java
.toolRegistry(Arrays.asList("queryWeather"), Arrays.asList("browser"))
```

这会走 `ToolUtilRegistry`，底层通过 `ToolUtil.getAllTools(functionList, mcpServerIds)` 解析出最终 tool list。

这条路径很适合：

- 基于现有 `ToolUtil`
- 想明确限制当前 Agent 只看到某些函数或 MCP 服务

## 4. 默认执行器怎么来的

如果你没有自己传 `ToolExecutor`，`AgentBuilder` 会根据当前 registry 里的工具名，自动构造一个 `ToolUtilExecutor`。

它的行为是：

- 只允许执行当前 registry 中已经暴露的 tool name
- 真正调用时走 `ToolUtil.invoke(call.getName(), call.getArguments())`

这意味着 agent 运行时默认不是“能调所有工具”，而是“只能调自己当前暴露出来的工具”。

## 5. Runtime 如何消费 tool call

在 `BaseAgentRuntime` 里，工具链路大致是：

1. 模型返回 `tool_calls`
2. 归一化成 `AgentToolCall`
3. `AgentToolCallSanitizer` 做基础校验
4. `ToolExecutor` 执行
5. 结果包装成 `AgentToolResult`
6. tool output 回写 `AgentMemory`
7. 下一轮模型继续读取这些结果

所以工具不是“模型调一下就结束”，而是 runtime 闭环的一部分。

## 6. ToolUtil、Skill、MCP 在这里的关系

从 `Agent` 的视角看：

- `ToolUtil` 是常见工具来源
- `Skill` 是更高层的能力组织方式
- `MCP` 是外部能力接入协议

但到了 `Agent` 层，它们都会被折叠成“当前可见的 tool surface”。

也就是说，`Agent` 不重新定义 MCP 或 Skill，而是在它们之上补一层 runtime 治理。

## 7. SubAgent 和 Team 为什么也会影响工具面

一旦启用 subagent 或 team，工具面不再只来自本地函数和 MCP。

例如：

- `SubAgentDefinition` 会被包装成可调用工具
- `CompositeToolRegistry` 会把基础工具和 subagent tools 合并
- team 模式下会出现专门的 `AgentTeamToolRegistry`

所以 `Tools and Registry` 这页其实是理解 handoff 和 team 的前置页。

## 8. 这一层的优势

- 工具声明和执行解耦，便于治理
- 允许把 `ToolUtil`、MCP、subagent、team 统一折叠成一套工具面
- 允许在执行侧插入 allow-list、fallback、审计、trace
- 为更上层的 Coding Agent approvals 留出了清晰挂点

## 9. 推荐下一步

1. [Minimal ReAct Agent](/docs/agent/runtimes/minimal-react-agent)
2. [CodeAct Runtime](/docs/agent/runtimes/codeact-runtime)
3. [Subagent Handoff](/docs/agent/orchestration/subagent-handoff)
4. [Reference Core Classes](/docs/agent/reference-core-classes)
