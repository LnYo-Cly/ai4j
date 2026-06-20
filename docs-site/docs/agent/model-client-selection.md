---
sidebar_position: 3
---

# Model Client Selection

`AgentModelClient` 决定的不是“你用哪个 provider”，而是：

> Agent runtime 组装好的 `AgentPrompt`，最终以什么协议形态发给模型。

当前最核心的两个实现是：

- `ChatModelClient`
- `ResponsesModelClient`

它们不是“老协议”和“新协议”这么简单，也不是无损等价的两个壳。真正的差异落在：

- prompt 字段如何映射
- tools 如何透传
- stream 时能吐出哪些中间信号
- 最终 `memoryItems` 长什么样
- 哪些高级字段在某条路径上其实根本没被下推

## 1. 先抓住 6 个关键设计决策

### 1.1 这是协议适配选择，不是 runtime 选择

`BaseAgentRuntime` 只依赖：

- `AgentModelClient`

它并不直接依赖：

- `IChatService`
- `IResponsesService`

因此：

- ReAct / CodeAct / DeepResearch 是 runtime 语义选择
- Chat / Responses 是模型协议选择

这两层不要混。

### 1.2 `systemPrompt` 和 `instructions` 在两条路径上不是等价映射

这几乎是整页最重要的事实。

#### Chat 路径

- `systemPrompt` -> 一条 system message
- `instructions` -> 再追加一条 system message

#### Responses 路径

- `systemPrompt` -> `ResponseRequest.instructions`
- `instructions` -> 被包装成 `systemMessage(...)` 并插到 input items 最前面

也就是说：

- Chat 路径里两者都落在 message 序列中
- Responses 路径里两者落在两个不同协议位置

### 1.3 Responses 路径会下推更多顶层字段

`ResponsesModelClient.toResponseRequest(...)` 会直接把这些字段推下去：

- `tools`
- `toolChoice`
- `parallelToolCalls`
- `temperature`
- `topP`
- `maxOutputTokens`
- `reasoning`
- `store`
- `user`
- `extraBody`

而 `ChatModelClient.toChatCompletion(...)` 只下推其中一部分。

这意味着某些 Agent 级配置，在 Chat 路径下其实是“写进 prompt 了，但没变成协议级顶层参数”。

### 1.4 Chat 路径的 toolChoice 约束更窄

`ChatModelClient` 只有在：

```java
prompt.getToolChoice() instanceof String
```

时才会设置 `builder.toolChoice(...)`。

而 `ResponsesModelClient` 是把 `toolChoice` 原样传下去。

如果你在用更复杂的 tool choice 结构，Responses 路径表达力更完整。

### 1.5 两条路径的 `memoryItems` 形状不一样

`ChatModelClient.toModelResult(...)` 会自己构造 memory items：

- 纯文本回答 -> `assistant` message
- 带 tool calls -> `assistantToolCallsMessage(...)`

`ResponsesModelClient.toModelResult(...)` 则直接把：

- `response.getOutput()`

原样塞进 `memoryItems`。

所以同样一轮调用：

- Chat 路径得到的是转换后的统一消息形态
- Responses 路径保留的是更贴近原始 response output 的 item 列表

### 1.6 Stream 能看到什么，不取决于底层协议强不强，而取决于当前适配器吐出了什么

很多人会先入为主地以为：

- Responses 协议更结构化
- 所以 Agent 级 stream 一定更丰富

当前实现并不是这样。

要看当前 client 的 `createStream(...)` 真正回调了什么。

## 2. `ChatModelClient` 到底做了什么

### 2.1 Prompt 如何被翻译成 chat completion

`toChatCompletion(...)` 的核心映射是：

1. `systemPrompt` 变第一条 system message
2. `instructions` 变第二条 system message
3. `items` 逐条转成 `ChatMessage`
4. `tools` 转成 `List<Tool>`
5. 非空 tools 时设置 `passThroughToolCalls(true)`

它真正下推的 top-level 字段包括：

- `model`
- `messages`
- `stream`
- `streamExecution`
- `temperature`
- `topP`
- `maxCompletionTokens`
- `user`
- `parallelToolCalls`
- `toolChoice`（仅字符串）
- `tools`
- `extraBody`

### 2.2 哪些 Agent 字段在 Chat 路径没有被协议级下推

当前 `ChatModelClient` 没有显式下推：

- `reasoning`
- `store`

因此如果你把这两项当成强协议参数使用，Responses 路径会更符合直觉。

### 2.3 `items` 并不是简单字符串拼接

`ChatModelClient.convertToMessage(...)` 支持的输入形态比“用户文本”更复杂。

它会处理：

- 普通 `ChatMessage`
- `type=message` 的 map
- `type=function_call_output` 的 map
- 多模态 `input_text`
- 多模态 `input_image`

这说明 Chat 路径并不只会吃纯文本消息；它内部已经在做一层 item -> chat message 的协议归一化。

### 2.4 Chat 流式路径会保留 reasoning delta

`StreamingSseListener.send()` 里会区分：

- `isReasoning() == true` -> `onReasoningDelta(delta)`
- 否则 -> `onDeltaText(delta)`

因此在当前 Agent 适配层里，Chat 路径能更早把 reasoning token 级增量抛上来。

这对实时调试面板和 trace 时间线很有价值。

## 3. `ResponsesModelClient` 到底做了什么

### 3.1 Prompt 如何被翻译成 response request

`toResponseRequest(...)` 的映射顺序是：

1. `model`
2. `input(buildItems(prompt))`
3. `tools`
4. `toolChoice`
5. `parallelToolCalls`
6. `temperature`
7. `topP`
8. `maxOutputTokens`
9. `reasoning`
10. `store`
11. `user`
12. `stream`
13. `streamExecution`
14. `systemPrompt -> instructions`
15. `extraBody`

这条路径对 `AgentPrompt` 顶层字段的保留明显更完整。

### 3.2 `instructions` 会被前插成 input item，而不是顶层 instructions

`buildItems(prompt)` 的逻辑是：

- 先复制 `prompt.getItems()`
- 如果有 `instructions`
- 在 index `0` 插入 `AgentInputItem.systemMessage(...)`

所以 Responses 路径里：

- `systemPrompt` 是 request-level instruction
- `instructions` 是 input item 里的前置 system message

这和 Chat 路径里“两个 system message 顺排”完全不是一回事。

### 3.3 Responses 流式路径当前更像“文本增量 + 最终统一收口”

`ResponsesModelClient.createStream(...)` 当前直接回调的是：

- `onDeltaText(...)`
- `onRetry(...)`
- `onError(...)`
- `onComplete(...)`

它不会像 Chat 路径那样单独发 reasoning delta。

更重要的是，tool calls 不是在流中间直接通过 stream listener 吐出来的，而是：

- 等流结束
- `ResponseSseListener.getResponse()`
- 再统一走 `toModelResult(response)`
- 最终由 runtime 从 `AgentModelResult.toolCalls` 继续处理

所以在当前实现里，Responses 路径更偏：

- 流中看文本
- 流后看完整结构

## 4. 两条路径最终如何回到统一的 Agent 语义

虽然协议不同，但 runtime 最终只认 `AgentModelResult`。

### Chat 路径

`toModelResult(ChatCompletionResponse)` 会抽出：

- `outputText`
- `reasoningText`
- `toolCalls`
- `memoryItems`
- `rawResponse`

### Responses 路径

`toModelResult(Response)` 会抽出：

- `outputText`
- `toolCalls`
- `memoryItems`
- `rawResponse`

这里最关键的统一动作是：

- 不同 provider response
  -> 统一投影成 `AgentModelResult`

runtime 后续并不关心它来自 Chat 还是 Responses。

## 5. Stream 行为真正差在哪

### 5.1 Chat 路径

当前能较早暴露：

- reasoning delta
- text delta
- retry

最终再在 `onComplete(...)` 给出统一 `AgentModelResult`。

### 5.2 Responses 路径

当前主要暴露：

- text delta
- retry

结构化结果、tool calls、最终 output 是在流结束后统一返回。

### 5.3 两条路径都不是“流中直接执行工具”

虽然 `AgentModelStreamListener` 有：

- `onToolCall(AgentToolCall call)`

但当前这两个 client 并没有在 streaming 过程中真正调用这个回调来提前透出工具。

实际工具执行仍然依赖：

1. `createStream(...)` 完成
2. 生成最终 `AgentModelResult`
3. `BaseAgentRuntime` 从结果里拿 `toolCalls`
4. 再进入统一 tool loop

所以不要误判成“Chat stream 一边吐 token，一边已经在 runtime 内部并行执行工具了”。

## 6. 取消流和线程中断的语义

这两条路径都有一个很相似的设计：

- 维护一个 `ACTIVE_STREAMS`
- key 是当前 `Thread`
- value 是底层 SSE listener

并提供：

- `cancelActiveStream(Thread thread)`

在 `createStream(...)` 里，如果线程被中断：

- 会先 cancel stream
- 再抛 `InterruptedException`

这说明当前流取消语义是：

- 线程级取消
- 不是单独的 request id / run id 取消协议

这在 CLI / session runtime 里很好用，但也意味着你要理解取消边界是“线程”，不是“任意逻辑任务”。

## 7. 什么时候优先选 Chat

更适合 Chat 的典型场景：

- 你已有成熟的 chat-completions 兼容链路
- 你更依赖 message 序列心智
- 你要在 Agent 级流式回调里更早看到 reasoning delta
- 你当前更看重经典对话和工具循环，而不是 response 顶层字段的完整表达

## 8. 什么时候优先选 Responses

更适合 Responses 的典型场景：

- 你想更完整地下推 `reasoning`、`store`、`toolChoice`
- 你更希望保留 response output item 的结构
- 你后续处理更偏结构化结果，而不是纯消息流
- 你接受“流中主要看文本，结构化信息在结尾统一收口”

## 9. 最容易被说错的几件事

### 9.1 “Responses 一定比 Chat 更高级”

不成立。

它们是两条不同协议线，不是简单的高低级关系。

### 9.2 “Chat 和 Responses 在 prompt 映射上是等价的”

不成立。

尤其是：

- `systemPrompt`
- `instructions`
- `reasoning`
- `store`
- `toolChoice`

这些字段的实际协议位置和保真度都不同。

### 9.3 “Responses 流式天然就能把所有结构化事件实时透给 Agent UI”

不成立。

当前 `ResponsesModelClient` 的 Agent 级 stream 适配主要仍然是文本增量。

### 9.4 “Chat 路径会在 stream 中直接把工具执行完”

不成立。

当前两个 client 的工具执行都还是在 runtime 收到最终 `AgentModelResult` 后统一推进。

## 10. 一个实用的选择表

| 关注点 | 更适合 |
| --- | --- |
| 更成熟的 chat 兼容接入 | `ChatModelClient` |
| 更完整的 response top-level 字段下推 | `ResponsesModelClient` |
| 流式 reasoning 可见性 | `ChatModelClient` |
| 更贴近 response output item 结构 | `ResponsesModelClient` |
| 复杂 `toolChoice` / `reasoning` / `store` 表达 | `ResponsesModelClient` |

## 11. 示例

### Chat 路径

```java
Agent agent = Agents.react()
        .modelClient(new ChatModelClient(chatService))
        .model("your-chat-model")
        .systemPrompt("You are a concise assistant.")
        .options(AgentOptions.builder().maxSteps(1).build())
        .build();
```

### Responses 路径

```java
Agent agent = Agents.react()
        .modelClient(new ResponsesModelClient(responsesService))
        .model("gpt-4.1")
        .systemPrompt("You are a concise assistant.")
        .options(AgentOptions.builder().maxSteps(1).build())
        .build();
```

表面上看只差一行，但协议语义并不一样。

## 12. 推荐阅读源码顺序

- `ai4j-agent/src/main/java/io/github/lnyocly/ai4j/agent/model/AgentModelClient.java`
- `ai4j-agent/src/main/java/io/github/lnyocly/ai4j/agent/model/AgentPrompt.java`
- `ai4j-agent/src/main/java/io/github/lnyocly/ai4j/agent/model/ChatModelClient.java`
- `ai4j-agent/src/main/java/io/github/lnyocly/ai4j/agent/model/ResponsesModelClient.java`
- `ai4j-agent/src/main/java/io/github/lnyocly/ai4j/agent/runtime/BaseAgentRuntime.java`

## 13. 继续阅读

1. [Quickstart](/docs/agent/quickstart)
2. [System Prompt vs Instructions](/docs/agent/system-prompt-vs-instructions)
3. [Tools and Registry](/docs/agent/tools-and-registry)
4. [Runtime Implementations](/docs/agent/runtime-implementations)
