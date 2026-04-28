---
sidebar_position: 2
---

# System Prompt vs Instructions

`systemPrompt` 和 `instructions` 在 Agent 中都很重要，但它们不是同一个字段的两种写法。

如果把这两个概念混掉，会出现三个问题：

- 全局规则和任务规则混在一起
- prompt 越写越长，难以复用
- 切换 `ChatModelClient` 与 `ResponsesModelClient` 时，对实际协议映射产生误判

## 1. 先给出最实用的区分

- `systemPrompt`：定义长期有效的全局角色、边界和固定约束
- `instructions`：定义本次任务的具体目标、格式要求和局部策略

最稳妥的工程习惯是：

> 把“谁在执行、长期必须遵守什么”放进 `systemPrompt`，把“这次要怎么做”放进 `instructions`。

## 2. 为什么要分成两个字段

从工程角度看，这不是语义洁癖，而是为了把两类变化频率不同的信息拆开：

- 全局规则相对稳定
- 任务要求频繁变化

拆开后的直接收益是：

- 系统级模板更容易复用
- 单次任务不必重写整套角色设定
- 更容易在 trace 中定位是“全局规则问题”还是“任务指令问题”

## 3. 在 `AgentPrompt` 中的建模

源码：

- `ai4j-agent/src/main/java/io/github/lnyocly/ai4j/agent/model/AgentPrompt.java`

`AgentPrompt` 里这两个字段是明确分开的：

- `systemPrompt`
- `instructions`

这说明 runtime 在组 prompt 时已经预设了两种不同作用域的信息，而不是把所有文字都塞进一个大字符串。

## 4. 在 `BaseAgentRuntime.buildPrompt(...)` 中如何组装

`BaseAgentRuntime.buildPrompt(...)` 会：

- 保留 `context.getSystemPrompt()`
- 把 `runtimeInstructions()` 与之合并
- 把 `context.getInstructions()` 单独保留到 `AgentPrompt`

所以在 Agent 内部，最终会有三层不同来源的指令：

- 你的 `systemPrompt`
- runtime 自己的策略指令
- 你的 `instructions`

这三层之后再交给不同的 model client 做协议映射。

## 5. `ChatModelClient` 中的实际映射

`ChatModelClient.toChatCompletion(...)` 当前采用的做法是：

1. 如果存在 `systemPrompt`，先加一条 system message
2. 如果存在 `instructions`，再加一条 system message
3. 再把 `items` 转成后续消息

这意味着在 Chat 路径下：

- `systemPrompt` 和 `instructions` 都会变成 system messages
- 两者在协议层不是两个独立顶层字段
- 顺序上 `systemPrompt` 在前，`instructions` 在后

对 Chat 来说，这种映射仍然有意义，因为它至少保留了两层逻辑上的分离。

## 6. `ResponsesModelClient` 中的实际映射

`ResponsesModelClient.toResponseRequest(...)` 的映射方式与 Chat 不完全一样：

- `systemPrompt` 会映射到 `ResponseRequest.instructions`
- `instructions` 会被插入为一条 `systemMessage(...)`，放到 `input` items 前面

这意味着在 Responses 路径下：

- `systemPrompt` 更像顶层全局指令
- `instructions` 更像前置的任务输入上下文

如果你更重视这两层语义分离，Responses 路径通常更容易表达这种结构。

## 7. 怎么写才更稳

### 7.1 `systemPrompt` 应该放什么

适合放：

- 角色定义
- 长期约束
- 安全边界
- 风格和输出优先级
- 工具使用原则

示例：

```text
You are an enterprise Java assistant.
Never invent unverified facts.
Use tools before answering when external evidence is required.
Prefer concise conclusions followed by supporting evidence.
```

### 7.2 `instructions` 应该放什么

适合放：

- 当前任务目标
- 本轮输出格式
- 当前上下文约束
- 本次步骤要求

示例：

```text
Summarize today's weather for Beijing.
Return JSON with fields city, summary, advice.
If the tool fails, explain the retry action instead of fabricating weather data.
```

## 8. 最常见的错误写法

### 8.1 把动态上下文全塞进 `systemPrompt`

后果：

- prompt 膨胀
- 全局模板难复用
- 每个任务都像在重写系统规则

### 8.2 每轮都重写一整套全局规则

后果：

- token 成本升高
- 规则管理混乱
- 很难判断本次任务到底改了什么

### 8.3 在 `instructions` 里塞长期身份设定

后果：

- 跨轮不稳定
- 模型的“角色层”与“任务层”混在一起

## 9. 一个实用模板

### 9.1 全局模板

```java
String systemPrompt = ""
        + "You are a production-facing assistant.\\n"
        + "Do not invent facts.\\n"
        + "Use tools when external evidence is required.\\n"
        + "Answer with conclusion first, then supporting details.";
```

### 9.2 单任务模板

```java
String instructions = ""
        + "Analyze the latest weather result.\\n"
        + "Return strict JSON with fields city, summary, advice.\\n"
        + "If the tool output is missing, explain the failure clearly.";
```

### 9.3 接入方式

```java
Agent agent = Agents.react()
        .modelClient(modelClient)
        .model("gpt-4.1")
        .systemPrompt(systemPrompt)
        .instructions(instructions)
        .build();
```

## 10. 与 runtime 指令的关系

你传入的 `systemPrompt` 并不是唯一的系统层约束。

例如：

- `ReActRuntime` 会注入自己的 `runtimeInstructions()`
- `CodeActRuntime` 会注入严格的 JSON / code protocol 约束

因此如果你发现某个 runtime 下模型行为与预期不同，不能只检查自己写的 `systemPrompt`，还要看 runtime 自带的策略提示。

## 11. 什么时候应优先选 Responses 路径

如果你特别关心：

- `systemPrompt` 与 `instructions` 的协议层分离
- 更强的结构化请求语义

那么 `ResponsesModelClient` 往往更合适。

如果你更重视传统 chat 兼容性，`ChatModelClient` 仍然是可行路径，只是要明确它最终会把两者都落成 system messages。

## 12. 继续阅读

1. [Model Client Selection](/docs/agent/model-client-selection)
2. [Quickstart](/docs/agent/quickstart)
3. [Architecture](/docs/agent/architecture)
