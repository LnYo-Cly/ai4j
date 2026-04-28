---
sidebar_position: 3
---

# Model Client Selection

`AgentModelClient` 是 Agent runtime 与具体模型协议之间的适配层。

当前最重要的两个实现是：

- `ChatModelClient`
- `ResponsesModelClient`

选型问题的本质不是“哪个好”，而是：

- 你的 provider 当前更稳定支持哪种协议
- 你的 Agent 是否需要更强的结构化 output 语义
- 你的流式消费方需要什么粒度的事件

## 1. 代码上的真实边界

源码路径：

- `ai4j-agent/src/main/java/io/github/lnyocly/ai4j/agent/model/AgentModelClient.java`
- `model/ChatModelClient.java`
- `model/ResponsesModelClient.java`

`AgentRuntime` 并不直接依赖 `IChatService` 或 `IResponsesService`，它只依赖 `AgentModelClient`。因此：

- runtime 不关心底层是 Chat 还是 Responses
- 协议差异被收敛在 model client 中
- 选型是协议层决策，不是 runtime 层决策

## 2. `ChatModelClient` 如何映射 prompt

`ChatModelClient.toChatCompletion(...)` 的行为可以概括为：

- `systemPrompt` 变成一条 system message
- `instructions` 再变成一条 system message
- `items` 被逐条转换成 chat messages
- 如果存在 tools，会注入 `tools`，并开启 `passThroughToolCalls`

因此在 Chat 路径下：

- `systemPrompt` 和 `instructions` 最终都会落到消息序列里
- 两者都以 system message 形式出现
- 二者的语义区分存在，但它们在协议层并不是两个独立顶级字段

## 3. `ResponsesModelClient` 如何映射 prompt

`ResponsesModelClient.toResponseRequest(...)` 的映射方式与 Chat 不完全对称：

- `systemPrompt` 映射到 `ResponseRequest.instructions`
- `instructions` 被插入为一条 `systemMessage(...)`，放到 `input` items 前面
- `items` 原样进入 `input`
- `tools`、`toolChoice`、`parallelToolCalls` 等作为顶级字段传入

这意味着在 Responses 路径下：

- `systemPrompt` 和 `instructions` 的协议位置是分离的
- `systemPrompt` 更像顶层全局指令
- `instructions` 更像作为输入上下文的一部分被前置注入

如果文档不把这点讲清楚，用户很容易误以为两个 client 的 prompt 映射完全一样。

## 4. Tool calling 在两条路径上的差异

### 4.1 `ChatModelClient`

当 prompt 中带 tools 时，`ChatModelClient` 会显式设置：

- `builder.tools(...)`
- `builder.passThroughToolCalls(true)`

流式模式下也会继续设置 `passThroughToolCalls(true)`。

这表示：

- 底层 chat provider 不应自己吞掉工具调用并继续闭环
- tool calls 会回到 Agent runtime
- 真正执行工具的是 `ToolExecutor`

### 4.2 `ResponsesModelClient`

Responses 路径下，tool calls 来自 response output 结构，再由：

- `ResponseUtil.extractToolCalls(response)`

提取为 `AgentToolCall`。

Agent runtime 拿到这些 tool calls 后，后续治理逻辑和 Chat 路径一致，都会进入统一的 `ToolExecutor` 执行面。

## 5. 流式语义的真实差异

这部分最容易被泛化描述误导，所以这里按当前实现写清楚。

### 5.1 底层协议差异

- Chat 协议更接近消息增量流
- Responses 协议底层是事件流，output 可以包含更结构化的内容

### 5.2 当前 Agent 适配层差异

在当前 `ai4j-agent` 实现里：

- `ChatModelClient` 在流式过程中会分别向上发：
  - `onReasoningDelta(...)`
  - `onDeltaText(...)`
  - `onToolCall(...)`
- `ResponsesModelClient` 当前流式适配主要向上发：
  - `onDeltaText(...)`
  - `onRetry(...)`
  - `onError(...)`

也就是说：

> Responses 底层协议更结构化，但当前 `ResponsesModelClient` 暴露给 Agent 级 listener 的流式信息，主要仍是文本增量；完整结构化 output 与 tool calls 会在流结束后统一进入最终 `AgentModelResult`。

这一点非常重要，因为它直接影响你如何设计实时 UI 和 trace 面板。

## 6. 什么时候优先选 `ResponsesModelClient`

- 你已经主要使用 Responses 协议
- 你希望工具、output、reasoning 等最终结果更贴近结构化 response 语义
- 你更重视 top-level `instructions` / `tools` / `reasoning` 这类字段表达
- 你愿意接受当前 Agent 级流式回调并不会把所有底层事件都一一向上透出

## 7. 什么时候优先选 `ChatModelClient`

- 你已有大量 Chat Completion 兼容链路
- 你更依赖经典消息序列语义
- 你在当前 Agent 实现里更需要流式 reasoning / tool call 的直接回调
- 你的 provider 在 Chat 协议上的兼容性更成熟

## 8. 一个实用选择表

| 需求 | 更适合的选择 |
| --- | --- |
| 最小迁移成本接入已有 chat provider | `ChatModelClient` |
| 更贴近 responses 结构化语义 | `ResponsesModelClient` |
| 需要在 Agent 级流式监听里更早拿到 reasoning/tool-call 回调 | `ChatModelClient` |
| 更强调最终 response output 的统一结构 | `ResponsesModelClient` |

## 9. 是否可以在同一系统里混用

可以，而且在复杂系统里很常见。

例如：

- 简单问答 Agent 用 `ChatModelClient`
- 更强调结构化结果或后续处理的 Agent 用 `ResponsesModelClient`

只要你理解它们的 prompt 映射和流式回调差异，混用没有问题。

## 10. 示例

### 10.1 Chat 路径

```java
Agent weatherAgent = Agents.react()
        .modelClient(new ChatModelClient(chatService))
        .model("your-chat-model")
        .toolRegistry(java.util.Arrays.asList("queryWeather"), null)
        .build();
```

### 10.2 Responses 路径

```java
Agent formatAgent = Agents.react()
        .modelClient(new ResponsesModelClient(responsesService))
        .model("gpt-4.1")
        .instructions("Return strict JSON.")
        .build();
```

## 11. 最常见的误解

### 11.1 “Responses 一定比 Chat 更高级”

不成立。它们是不同协议，不是简单上下级关系。

### 11.2 “Responses 流式等于当前 Agent 级 listener 会完整透出所有底层事件”

不成立。底层协议更丰富，但当前 `ResponsesModelClient` 的 Agent 级流式适配主要向上抛文本增量。

### 11.3 “Chat 进入 Agent 后 provider 会自己把工具执行完”

不成立。带 tools 时，`ChatModelClient` 会开启 `passThroughToolCalls`，把工具调用交回 Agent runtime。

## 12. 继续阅读

1. [System Prompt vs Instructions](/docs/agent/system-prompt-vs-instructions)
2. [Quickstart](/docs/agent/quickstart)
3. [Architecture](/docs/agent/architecture)
4. [Tools and Registry](/docs/agent/tools-and-registry)
