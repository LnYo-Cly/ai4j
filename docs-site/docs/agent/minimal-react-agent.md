---
sidebar_position: 2
---

# 最小 ReAct Agent

如果你是第一次在 AI4J 里做 Agent，不要先上 CodeAct、StateGraph 或 Teams。

最稳的起点是一个最小 ReAct Agent：

- 一个模型客户端
- 一个模型名
- 一组明确白名单工具
- 一次可观测的运行结果

这一页只讲这条最短路径。

---

## 1. 最小闭环

当前最小可用 Agent 只需要两项必填：

- `modelClient(...)`
- `model(...)`

最小示例：

```java
Agent agent = Agents.react()
        .modelClient(new ResponsesModelClient(responsesService))
        .model("doubao-seed-1-8-251228")
        .build();

AgentResult result = agent.run(AgentRequest.builder()
        .input("用一句话介绍 AI4J Agent")
        .build());

System.out.println(result.getOutputText());
```

这一步的目标不是“功能很全”，而是先确认：

- 模型请求能发出
- Agent runtime 能跑通
- 最终输出可读

---

## 2. 为什么先选 ReAct

`ReActRuntime` 是当前默认、最稳的通用运行时。

它适合：

- 文本任务
- 多轮思考
- 按需调用工具
- 不依赖代码执行环境

如果你现在的任务还没有明确需要：

- 代码生成后执行
- 复杂状态图分支
- 多成员协作

那就先不要离开 ReAct。

这里补一句定位，避免把当前实现和论文版 ReAct 混为一谈：

- 当前 AI4J 的 `ReActRuntime` 是 **ReAct 风格的 tool loop**
- 它保留了“模型决定是否调用工具 -> 工具执行 -> 结果回给模型 -> 下一轮继续”的闭环
- 但它**不是**严格按论文里的显式 `Thought -> Action -> Observation` 文本轨迹协议来驱动

这样做是有意的：

- 当前主线优先复用 provider 原生 `tool_calls / function_call_output`
- 工程上更容易接流式输出、参数校验、并行工具、MCP 和更高层 Agent 编排
- 所以这里的 `ReAct`，更准确地理解为“现代 tool-calling 版本的 ReAct-style agent”

---

## 3. 把模型客户端接进来

最常见的两种方式是：

### 3.1 Responses 模型客户端

```java
Agent agent = Agents.react()
        .modelClient(new ResponsesModelClient(responsesService))
        .model("doubao-seed-1-8-251228")
        .build();
```

### 3.2 Chat 模型客户端

```java
Agent agent = Agents.react()
        .modelClient(new ChatModelClient(chatService))
        .model("gpt-4o-mini")
        .build();
```

经验上：

- 你已经有稳定 Chat 服务时，先用 `ChatModelClient`
- 你要走更事件化的新路径时，优先 `ResponsesModelClient`

---

## 4. 加上 System Prompt 与 Instructions

最小 Agent 能跑通后，第二步通常是把系统规则和任务指令拆开。

```java
Agent agent = Agents.react()
        .modelClient(new ChatModelClient(chatService))
        .model("gpt-4o-mini")
        .systemPrompt("你是一个严谨的 Java 助手")
        .instructions("不确定时先说明假设，不要编造结论")
        .build();
```

推荐理解：

- `systemPrompt` 负责长期角色和硬规则
- `instructions` 负责当前 Agent 的任务约束

---

## 5. 再加工具白名单

真正进入 Agent 阶段后，最重要的不是“工具越多越好”，而是“只暴露任务真正需要的工具”。

最小示例：

```java
Agent agent = Agents.react()
        .modelClient(new ChatModelClient(chatService))
        .model("gpt-4o-mini")
        .toolRegistry(java.util.Arrays.asList("queryWeather"), null)
        .build();
```

这表示：

- 只暴露 `queryWeather`
- 当前不接入 MCP 服务

如果你一开始就把所有工具全暴露，排障会迅速变得很困难。

---

## 6. 一个真正可落地的最小例子

```java
Agent agent = Agents.react()
        .modelClient(new ResponsesModelClient(responsesService))
        .model("doubao-seed-1-8-251228")
        .systemPrompt("你是一个严谨的天气助手")
        .instructions("必要时再调用工具，最终回答保持简洁")
        .toolRegistry(java.util.Arrays.asList("queryWeather"), null)
        .build();

AgentResult result = agent.run(AgentRequest.builder()
        .input("请给出北京今天的天气摘要")
        .build());

System.out.println(result.getOutputText());
```

这一版已经覆盖：

- 模型调用
- 推理循环
- 工具白名单
- 最终文本输出

---

## 7. 什么时候需要 Session / Memory

当你从“一次调用”升级到“持续对话”时，再考虑：

- `Agent.newSession()`
- 独立 memory
- 历史上下文压缩

也就是说：

- 一次性任务：先用 `agent.run(...)`
- 持续会话：再切 `session`

不要在首个成功调用之前，就把 Session / Memory 一起叠上来。

---

## 8. 什么时候该升级到更复杂的 Runtime

### 升级到 CodeAct

当任务开始要求：

- 让模型产出代码
- 执行代码
- 在代码里多次调工具

### 升级到 Workflow / StateGraph

当任务开始要求：

- 分支
- 循环
- 条件路由

### 升级到 SubAgent / Teams

当任务开始要求：

- 角色分工
- 主从委派
- 多成员协作

在这些需求出现之前，先把 ReAct 打磨稳定。

---

## 9. 建议加的第一层可观测

最小 Agent 一旦跑通，就建议立刻加 Trace：

```java
Agent agent = Agents.react()
        .modelClient(new ChatModelClient(chatService))
        .model("gpt-4o-mini")
        .traceConfig(TraceConfig.builder().build())
        .traceExporter(new ConsoleTraceExporter())
        .build();
```

这样你会更快知道：

- 模型有没有真的请求
- 工具有没有真的调用
- 哪一步最慢

---

## 10. 下一步阅读

1. [自定义 Agent 开发指南](/docs/agent/custom-agent-development)
2. [Runtime 实现详解](/docs/agent/runtime-implementations)
3. [Trace 与可观测性](/docs/agent/trace-observability)
