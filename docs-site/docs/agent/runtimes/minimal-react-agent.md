# Minimal ReAct Agent

`Minimal ReAct Agent` 是 `Agent` 章节的推荐起点，因为它代表的是默认 runtime 主线，而不是简化示例。

## 1. 它对应的真实实现是什么

入口：

- `Agents.react()`
- `ReActRuntime`
- `BaseAgentRuntime`

这里的 `ReActRuntime` 很轻，它真正复用的是 `BaseAgentRuntime` 的通用 loop 语义：

- 组 prompt
- 请求模型
- 读取 tool calls
- 执行工具
- 回写 memory
- 决定是否继续

所以学会最小 ReAct，其实是在学 `Agent` 的默认运行心智。

## 2. 什么时候先选它

最适合下面这些场景：

- 文本任务 + 少量工具调用
- 不需要代码执行沙箱
- 不需要显式节点图
- 你想先验证“模型会不会正确地按需调工具”

对大多数业务 Agent 来说，这都是第一站。

## 3. 最小示例

```java
Agent agent = Agents.react()
        .modelClient(new ResponsesModelClient(responsesService))
        .model("doubao-seed-1-8-251228")
        .systemPrompt("你是一个严谨的助手")
        .toolRegistry(java.util.Arrays.asList("queryWeather"), null)
        .build();
```

这段代码已经包含了最关键的四个面：

- model client
- runtime
- tool surface
- memory

## 4. 为什么它值得先跑通

因为后面的大多数复杂能力，都是在这条主线上叠加出来的：

- `CodeAct`：在默认 loop 上换成“产出代码并执行”
- `StateGraph`：在默认 loop 之外再加显式编排
- `SubAgent`：把某些工具调用委派给别的 Agent
- `Teams`：把单 Agent 扩成多成员协作

如果最小 ReAct 都没跑稳，后面所有扩展都会更难排障。

## 5. 跑通后重点看什么

建议验证：

- `AgentResult.getSteps()` 是否符合预期
- `toolCalls` 和 `toolResults` 是否完整
- memory 是否正确保留 tool output
- stream 模式下 reasoning / response / tool 事件是否可观测

## 6. 什么时候该升级

当你出现下面这些信号时，再考虑离开最小 ReAct：

- 工具链很多，文本 loop 不稳定
- 任务更适合先写代码再执行
- 任务天然像流程图
- 需要明确的主从委派或 team 协作

下一页建议看 [Runtime Implementations](/docs/agent/runtimes/runtime-implementations)。
