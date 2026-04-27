# Agent Quickstart

`Agent` 的最短起步路径，不是先把所有 runtime 都学完，而是先跑一个最小 `ReAct` Agent，确认这四件事已经打通：

- 模型能发出去
- 工具能暴露
- runtime loop 能闭环
- memory 能承接上下文

## 1. 最短可运行示例

```java
import io.github.lnyocly.ai4j.agent.Agent;
import io.github.lnyocly.ai4j.agent.AgentRequest;
import io.github.lnyocly.ai4j.agent.AgentResult;
import io.github.lnyocly.ai4j.agent.Agents;
import io.github.lnyocly.ai4j.agent.model.ResponsesModelClient;

Agent agent = Agents.react()
        .modelClient(new ResponsesModelClient(responsesService))
        .model("doubao-seed-1-8-251228")
        .systemPrompt("你是一个严谨的助手")
        .instructions("只有需要时才调用工具")
        .toolRegistry(java.util.Arrays.asList("queryWeather"), null)
        .build();

AgentResult result = agent.run(AgentRequest.builder()
        .input("请给出北京今天的天气摘要")
        .build());

System.out.println(result.getOutputText());
```

## 2. 这段代码对应了哪些真实部件

- `Agents.react()`：默认装配 `ReActRuntime`
- `ResponsesModelClient`：把模型访问接到 `AgentModelClient`
- `toolRegistry(...)`：通过 `ToolUtilRegistry` 暴露指定工具
- `build()`：组装 `AgentContext`、`ToolExecutor`、`AgentMemory`
- `agent.run(...)`：进入 `BaseAgentRuntime` 的 step loop

也就是说，quickstart 的重点不是“写最短代码”，而是一次把真实运行链跑通。

## 3. 跑通后你应该验证什么

至少确认这四点：

1. 模型能正确返回普通文本
2. 模型在需要时能发出 tool call
3. tool result 会回灌到下一轮，而不是丢失
4. `AgentResult` 里能看到最终输出、tool calls、tool results、steps

如果这四点没问题，说明最小 runtime 已经成立。

## 4. 常见起步错误

- 只配了 model，没有配 `modelClient`
- 误以为 `IChatService` 直接调用就等于进入 `Agent`
- 工具在基础 SDK 可用，但没有加入 agent 的 `toolRegistry`
- 一上来就想同时启用 workflow、subagent、team，导致排障困难

建议先把单 Agent 跑通，再逐层加复杂度。

## 5. 下一步怎么走

### 如果你主要关心模型协议和流式差异

继续看 [Model Client Selection](/docs/agent/model-client-selection)。

### 如果你主要关心状态如何保留

继续看 [Memory and State](/docs/agent/memory-and-state)。

### 如果你主要关心工具怎么暴露和治理

继续看 [Tools and Registry](/docs/agent/tools-and-registry)。

### 如果你准备继续选 runtime

继续看 [Runtime Implementations](/docs/agent/runtimes/runtime-implementations)。
