---
sidebar_position: 2
---

# System Prompt vs Instructions

这一页讲的不是泛泛的 prompt engineering，而是 AI4J Agent 里这两个字段在源码中的真实语义。

如果这里理解错了，后面会连带把这些事情一起理解错：

- 为什么同一 Agent 在不同 runtime 下行为会变
- 为什么 Chat 和 Responses 的请求形状不一样
- 为什么 trace 里看到的 `systemPrompt` 和你写进去的字符串不完全一样
- 为什么 `newSession()` 后 prompt 规则没有变

## 1. 先抓住 6 个关键设计决策

### 1.1 这两个字段都不是“当前轮临时文本”，而是 AgentContext 的一部分

`AgentBuilder.build()` 会把两者都写进 `AgentContext`：

- `instructions`
- `systemPrompt`

也就是说，这两者都属于“Agent 装配配置”，不是每一步动态重算的临时变量。

这带来的直接后果是：

- 每一轮 `buildPrompt(...)` 都会重新把它们放进 prompt
- 它们不是随着 memory 自动变化的

### 1.2 `systemPrompt` 会被 runtime 隐式扩写，`instructions` 不会

`BaseAgentRuntime.buildPrompt(...)` 的关键逻辑是：

```java
String systemPrompt = mergeText(context.getSystemPrompt(), runtimeInstructions());
```

然后：

```java
.systemPrompt(systemPrompt)
.instructions(context.getInstructions())
```

所以：

- 你的 `systemPrompt` 不是最终送给模型的完整系统文本
- runtime 自己的策略提示会被拼到 `systemPrompt` 后面
- `instructions` 保持独立字段，不参与这一步 merge

### 1.3 这两个字段都会在每一步重复进入模型

很多人会下意识地以为：

- `systemPrompt` 是“初始化时用一次”
- `instructions` 是“本轮只注入一次”

当前实现不是这样。

`BaseAgentRuntime.runInternal(...)` 每轮都会：

1. `buildPrompt(...)`
2. `executeModel(...)`

而 `buildPrompt(...)` 每轮都会把：

- `systemPrompt`
- `instructions`

再次塞进 `AgentPrompt`。

这意味着：

- 写得越长，step 越多，重复 token 成本越高
- 把动态上下文塞进这两个字段会非常浪费

### 1.4 `newSession()` 只换 memory，不换这两者

`Agent.newSession()` 的实现只会替换：

- `memory`

不会替换：

- `systemPrompt`
- `instructions`
- `runtime`
- `modelClient`
- `toolRegistry`

所以 session 隔离的是状态，不是指令模板。

如果你想换 prompt 规则，应该重新 `build()` 一个 Agent，而不是只开新 session。

### 1.5 trace 里记录的 `systemPrompt` 已经是 merge 之后的版本

`AgentTraceListener` 在记录 `MODEL_REQUEST` 时，取的是：

- `prompt.getSystemPrompt()`
- `prompt.getInstructions()`

而这里的 `prompt` 已经是 runtime build 过后的 `AgentPrompt`。

因此 trace 里看到的：

- `systemPrompt`

通常已经包含了 runtime 注入的策略文本，而不是你最初传给 Builder 的那一份原文。

### 1.6 Chat 和 Responses 对这两个字段的协议映射并不等价

这不是实现细节，而是会直接影响你如何设计 prompt 结构。

- Chat 路径：两者最终都会变成 system message
- Responses 路径：`systemPrompt` 进 top-level `instructions`，`instructions` 变成前置 system item

所以“这两个字段都差不多，随便放”是错误结论。

## 2. 这两个字段在对象模型里到底在哪里

源码上，这条链很清楚：

```text
AgentBuilder
  -> AgentContext
  -> AgentPrompt
  -> ChatModelClient / ResponsesModelClient
```

最关键的 3 个对象是：

| 对象 | 这里的职责 |
| --- | --- |
| `AgentContext` | 保存你配置进去的原始 `systemPrompt` / `instructions` |
| `AgentPrompt` | runtime 每一步真正提交给 model client 的 prompt 快照 |
| `ChatModelClient` / `ResponsesModelClient` | 把这两个字段翻译成底层协议 |

`AgentPrompt` 本身把这两个字段分开建模：

- `systemPrompt`
- `instructions`

说明框架作者从抽象层就没把它们视为同一件事。

## 3. `BaseAgentRuntime` 里真正发生了什么

### 3.1 最关键的一行是 `mergeText(...)`

在 `BaseAgentRuntime.buildPrompt(...)` 中：

```java
String systemPrompt = mergeText(context.getSystemPrompt(), runtimeInstructions());
```

这行的含义非常具体：

- 先取你配置的 `systemPrompt`
- 再把 runtime 自己的系统级策略拼接进去
- 两者之间用换行连接

### 3.2 `instructions` 不参与 merge

后面的 Builder 写法是：

```java
.systemPrompt(systemPrompt)
.instructions(context.getInstructions())
```

所以当前默认 runtime 下，AI4J 保留了一个很明确的层次：

1. 你的系统级设定
2. runtime 的系统级策略
3. 你的任务级说明

### 3.3 为什么这个分层很重要

因为 runtime 的系统策略不是可有可无的装饰。

例如：

- `ReActRuntime` 会补 `Use tools when necessary. Return concise final answers.`
- `CodeActRuntime` 会补一大段 JSON/code protocol 约束

如果你把所有任务要求都堆进 `systemPrompt`，后面看到模型行为异常时，很难分清到底是谁在起作用：

- 你的系统设定
- runtime 的附加策略
- 你的任务说明

## 4. Chat 路径中的真实映射

`ChatModelClient.toChatCompletion(...)` 当前做法很直接：

1. 如果有 `systemPrompt`，先加一条 system message
2. 如果有 `instructions`，再加一条 system message
3. 再把 `items` 转成后续消息

也就是说，在 Chat 协议里：

- 这两个字段最终不是两个顶层属性
- 而是两条顺序相邻的 system messages

它们在协议层的差异主要只剩：

- 顺序
- 文本内容

### 4.1 这对 prompt 设计意味着什么

在 Chat 路径下，最稳的习惯是：

- `systemPrompt` 放长期角色、长期边界、稳定策略
- `instructions` 放本轮目标、格式要求、局部约束

因为虽然协议层最后都是 system messages，但逻辑分层仍然存在，后续 trace 和迁移也更清楚。

### 4.2 不要从“都变成 system message”推出“它们没区别”

区别至少还在 3 个地方：

1. 语义意图不同
2. 代码字段不同
3. Responses 路径映射不同

如果你以后会切 Responses 路径，这种区分尤其重要。

## 5. Responses 路径中的真实映射

`ResponsesModelClient.toResponseRequest(...)` 的处理方式不同：

- `systemPrompt` -> `ResponseRequest.instructions`
- `instructions` -> `buildItems(prompt)` 时插到 input 最前面的 `systemMessage(...)`

这意味着在 Responses 协议里，二者真的落在两个不同层次：

### `systemPrompt`

更像：

- 顶层、全局、请求级指令

### `instructions`

更像：

- 输入序列里的前置任务说明

### 5.1 为什么很多人会在 Responses 路径下写得更稳

因为这条路径能更清楚地保持：

- 全局规则
- 任务规则

的协议级分离，而不是把两者都压成消息序列。

## 6. `CodeActRuntime` 下这件事为什么更关键

在 `CodeActRuntime.buildPrompt(...)` 里，依然会做：

```java
String systemPrompt = mergeText(context.getSystemPrompt(), runtimeInstructions(context));
```

但这里的 `runtimeInstructions(context)` 不是一句短提示，而是一整段协议约束，包括：

- 只能输出单个 JSON 对象
- `type=code` / `type=final` 的格式
- 语言约束
- tool guide
- 某些工具的专门说明

这意味着到了 CodeAct：

- `systemPrompt` 更像“宿主策略 + 用户全局策略”的承载层
- `instructions` 更像“这次任务要达成什么”

如果你把具体任务步骤、数据细节、短期上下文全塞进 `systemPrompt`，很快就会和 runtime protocol 文本混成一团。

## 7. 这两个字段最适合各自承载什么

### 7.1 `systemPrompt` 最适合放什么

适合放：

- 身份设定
- 长期风格
- 稳定优先级
- 风险边界
- 工具使用原则

例如：

```text
You are an enterprise Java assistant.
Do not invent facts.
Prefer tool-backed answers when external evidence is required.
Return concise conclusions first.
```

这些内容的共同特点是：

- 跨任务稳定
- 不依赖当前用户输入
- 每一步重复出现也合理

### 7.2 `instructions` 最适合放什么

适合放：

- 当前任务目标
- 输出格式
- 本轮约束
- 失败时处理策略

例如：

```text
Summarize today's weather for Beijing.
Return strict JSON with fields city, summary, advice.
If the tool fails, explain the failure instead of fabricating data.
```

这些内容的共同特点是：

- 与当前任务强相关
- 可以随任务切换
- 但仍然比单次用户输入更稳定

## 8. 真正不该放进来的东西是什么

### 8.1 不要把实时业务数据塞进 `systemPrompt`

例如：

- 当前查询结果
- 临时表数据
- 每轮变化的上下文摘要

因为它们会在每一步被重复发送，既贵又乱。

### 8.2 不要把原始用户输入改写后再塞进 `instructions`

当前用户真正的动态输入应该走：

- `AgentRequest.input`
- 后续 memory items

而不是每次把用户问题手工再拼成一份 instructions。

### 8.3 不要把 runtime protocol 再手工复制到自己的 `systemPrompt`

尤其是 CodeAct。

runtime 已经会注入自己的协议约束。你再复制一遍，很容易出现：

- 冗余
- 冲突
- trace 中系统文本膨胀

## 9. 最常见的误判和后果

### 9.1 “`systemPrompt` 只会在最开始用一次”

不成立。

它会在每一步 prompt 重建时重复进入模型。

### 9.2 “新 session 就会换一套 prompt 规则”

不成立。

`newSession()` 只换 memory，不换这两个字段。

### 9.3 “trace 里看到的 systemPrompt 就是我原样写进去的那个字符串”

不成立。

trace 里看到的是 runtime merge 之后的 `AgentPrompt.systemPrompt`。

### 9.4 “Chat 路径里两者都会变成 system message，所以随便放都行”

不成立。

你一旦切到 Responses、CodeAct 或做 trace 分析，这种偷懒写法马上会出问题。

## 10. 一个更稳的实用模板

### 全局模板

```java
String systemPrompt = ""
        + "You are a production-facing assistant.\\n"
        + "Do not invent facts.\\n"
        + "Use tools when external evidence is required.\\n"
        + "Keep answers concise and explicit about uncertainty.";
```

### 任务模板

```java
String instructions = ""
        + "Summarize today's weather for Beijing.\\n"
        + "Return JSON with fields city, summary, advice.\\n"
        + "If the tool fails, explain the failure clearly.";
```

### 接入方式

```java
Agent agent = Agents.react()
        .modelClient(modelClient)
        .model("gpt-4.1")
        .systemPrompt(systemPrompt)
        .instructions(instructions)
        .build();
```

这个分层的优点不是“更优雅”，而是：

- 更容易迁移协议
- 更容易看 trace
- 更容易在多任务场景复用

## 11. 什么时候该优先选 Responses 路径

如果你特别在意下面这件事：

> 系统级规则和任务级规则在协议层也要保持分离

那么 `ResponsesModelClient` 往往更贴近你的意图。

如果你更重视 chat 兼容性和传统消息序列心智，`ChatModelClient` 仍然完全可用，但你必须清楚：

- 两者最后都会下沉成 system messages

## 12. 推荐阅读源码顺序

- `ai4j-agent/src/main/java/io/github/lnyocly/ai4j/agent/AgentBuilder.java`
- `ai4j-agent/src/main/java/io/github/lnyocly/ai4j/agent/AgentContext.java`
- `ai4j-agent/src/main/java/io/github/lnyocly/ai4j/agent/model/AgentPrompt.java`
- `ai4j-agent/src/main/java/io/github/lnyocly/ai4j/agent/runtime/BaseAgentRuntime.java`
- `ai4j-agent/src/main/java/io/github/lnyocly/ai4j/agent/runtime/ReActRuntime.java`
- `ai4j-agent/src/main/java/io/github/lnyocly/ai4j/agent/runtime/CodeActRuntime.java`
- `ai4j-agent/src/main/java/io/github/lnyocly/ai4j/agent/model/ChatModelClient.java`
- `ai4j-agent/src/main/java/io/github/lnyocly/ai4j/agent/model/ResponsesModelClient.java`
- `ai4j-agent/src/main/java/io/github/lnyocly/ai4j/agent/trace/AgentTraceListener.java`

## 13. 继续阅读

1. [Model Client Selection](/docs/agent/model-client-selection)
2. [Quickstart](/docs/agent/quickstart)
3. [Architecture](/docs/agent/architecture)
4. [Runtime Implementations](/docs/agent/runtime-implementations)
