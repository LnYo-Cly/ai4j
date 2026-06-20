---
sidebar_position: 5
---

# Runtime Implementations

`AgentRuntime` 决定的不是“模型用哪个 provider”，而是“一次 Agent run 如何推进、何时停止、何时发事件、工具结果如何回灌”。

同一个 `AgentBuilder`，之所以可以切 `ReActRuntime`、`CodeActRuntime`、`DeepResearchRuntime`，是因为这三种 runtime 代表了三种不同的执行语义，而不是同一条链路的文案差异。

## 1. 先抓住 4 个关键设计决策

### 1.1 runtime 负责推进语义，不负责 provider 接入

`AgentRuntime` 接口只接收：

- `AgentContext`
- `AgentRequest`

它不关心底层是 OpenAI、Doubao 还是别的 provider，也不关心工具具体是怎么实现的。

所以 runtime 这一层回答的是：

- 本轮有没有继续的必要
- 工具结果何时进入下一轮
- 什么时候触发最终输出

### 1.2 `BaseAgentRuntime` 才是默认主循环核心

ReAct、CodeAct、DeepResearch 不是 3 个互不相干的执行器。

当前实现里：

- `ReActRuntime` 几乎完全复用 `BaseAgentRuntime`
- `DeepResearchRuntime` 只是先插入一段 planning，再回到 `BaseAgentRuntime`
- `CodeActRuntime` 则在关键位置重写了“模型输出 -> 执行 -> 回灌”的中间表示

这意味着你调 runtime 行为，通常应先看 `BaseAgentRuntime`，再看各 runtime 在哪里偏离它。

### 1.3 runtime 差异的本质是“中间表示差异”

三种 runtime 的真正区别不在标题，而在它们要求模型输出什么中间表示：

- ReAct：文本 + native tool calls
- CodeAct：JSON code/final message
- DeepResearch：planning text + 后续 ReAct loop

这决定了：

- 模型如何表达下一步动作
- runtime 如何理解模型输出
- memory 里会沉淀什么类型的中间状态

### 1.4 runtime 不等于 workflow，也不等于 outer loop

runtime 负责的是单个 Agent run 的推进。

它不直接负责：

- 显式节点图
- 长程 auto-continue
- checkpoint / compact
- 宿主级 approval 和 workspace 生命周期

这些能力属于 workflow / coding-agent outer loop 等更上层抽象。

## 2. `AgentRuntime` 抽象到底定义了什么

接口本身非常窄：

```java
public interface AgentRuntime {
    AgentResult run(AgentContext context, AgentRequest request) throws Exception;
    void runStream(AgentContext context, AgentRequest request, AgentListener listener) throws Exception;
    default AgentResult runStreamResult(...)
}
```

它真正留给 runtime 的自由度有三类：

1. 如何组 prompt
2. 如何解释模型输出
3. 如何决定 loop 是否继续

也正因为接口足够窄，`AgentBuilder` 才能把相同的：

- model client
- tool registry
- tool executor
- memory

在不同 runtime 之间复用。

## 3. `BaseAgentRuntime` 是默认语义引擎

理解三种内置 runtime，最该先读的是 `BaseAgentRuntime.runInternal(...)`。

### 3.1 它的主循环做了什么

核心链路如下：

1. 读取 `maxSteps`
2. 判断是否进入 streaming 路径
3. 校验 `memory`
4. 将用户输入写入 memory
5. 发出 `STEP_START`
6. `buildPrompt(...)`
7. `executeModel(...)`
8. 把模型返回的 `memoryItems` 写回 memory
9. `normalizeToolCalls(...)`
10. 用 `AgentToolCallSanitizer` 校验调用
11. 执行工具
12. 把工具结果写回 memory
13. 如果本轮没有 tool call，则输出最终结果；否则继续下一轮

这条链路其实定义了 AI4J 默认 Agent 的完整运行语义。

### 3.2 它已经内置了哪些能力

`BaseAgentRuntime` 已经帮上层处理了：

- `maxSteps`
- stream / non-stream 分支
- tool call id 补全
- 空工具名兜底
- tool 参数结构校验
- 校验失败转 `TOOL_ERROR`
- 并行工具执行
- 标准事件发射

这意味着自定义 runtime 时，很多需求并不需要从零实现整个循环，只要改掉真正不同的那一段就行。

### 3.3 默认失败语义是什么

一个非常关键的事实是：

- 工具异常默认不会立刻中断 Agent run

`executeTool(...)` 会把异常压成：

```text
TOOL_ERROR: {"error":"...","tool":"...","callId":"..."}
```

然后把这段错误文本写回 memory，让模型在下一轮看到它。

因此默认 runtime 倾向于把失败视为“可恢复上下文”，而不是“不可继续的终止条件”。

## 4. `ReActRuntime` 其实是最薄的一层

`ReActRuntime` 当前几乎没有自己的一整套复杂实现。

它只显式定义了两件事：

- `runtimeName() = "react"`
- `runtimeInstructions() = "Use tools when necessary. Return concise final answers."`

也就是说，ReAct runtime 的真实行为几乎完全由 `BaseAgentRuntime` 决定。

这带来两个后果：

1. 它是理解整个 Agent 层最好的入口
2. 你遇到的很多 ReAct 行为，其实是 Base runtime 行为，而不是 ReAct 独有行为

## 5. `CodeActRuntime` 是真正换了中间表示

CodeAct 和 ReAct 的差异，不在“有没有工具”，而在“工具前面加了一层代码消息协议”。

### 5.1 模型输出先被当成 JSON 协议解释

`CodeActRuntime` 要求模型输出：

- `{"type":"code","language":"...","code":"..."}`
- `{"type":"final","output":"..."}`

这意味着模型不是直接发 native tool call，而是先决定：

- 我要不要产出代码
- 这段代码用什么语言
- 我现在是不是该进入 final answer 阶段

### 5.2 工具不再直接进入模型协议

和 `BaseAgentRuntime.buildPrompt(...)` 不同，`CodeActRuntime.buildPrompt(...)` 不把 `tools` 放进 `AgentPrompt`。

它选择的是另一条路径：

- 把工具说明转成文本 guide
- 让代码执行器在运行时桥接工具调用

所以 CodeAct 是：

- prompt protocol 驱动
- code executor 桥接

而不是 provider 原生 schema 驱动。

### 5.3 `reAct` 决定的是执行后是否再回模型

`CodeActOptions.reAct` 的差异，不是“开关某个细枝末节”，而是决定最终答案属于哪一层：

- `false`：尽可能直接用执行结果收口
- `true`：执行成功后再回模型做总结

这直接影响：

- token 成本
- 延迟
- 输出是否更自然语言化

## 6. `DeepResearchRuntime` 没有很多人想象得那么重

从源码看，DeepResearch 当前实现非常克制。

### 6.1 它只是先插入 plan，再走默认循环

`DeepResearchRuntime.run(...)` 并没有实现一个完全独立的新主循环。

它真正做的是：

1. `preparePlan(...)`
2. 如果 request input 是字符串，则调用 `Planner.plan(goal)`
3. 把 plan 写成：

```text
Plan:
1. ...
2. ...
```

4. 用 `systemMessage` 写进 memory
5. 再调用 `super.run(...)`

因此 DeepResearch 的本质更接近：

- planning-enhanced ReAct

而不是完整研究平台。

### 6.2 默认 planner 非常轻

`Planner.simple()` 的实现只是：

- 如果 goal 不为空，就返回只包含 `goal` 本身的单元素列表

所以默认 DeepResearch 并不会自动产生复杂任务拆解。

这页如果不把这一点写清楚，读者很容易误以为它默认就具备复杂 research orchestration。

## 7. 三种 runtime 的真正差异应该怎么理解

### 7.1 ReAct

最接近框架默认语义。

适合：

- 通用问答
- 少量或中等复杂度工具调用
- 想尽量少加约束地进入 Agent loop

### 7.2 CodeAct

换了中间表示，把“模型直接调工具”变成“模型先产代码，再让代码调工具”。

适合：

- 多工具批处理
- 聚合、转换、排序
- 需要显式可执行过程的任务

### 7.3 DeepResearch

不是更复杂的 loop，而是在默认 loop 前插入 planning。

适合：

- 希望执行前先显式给出任务分解
- 希望模型带着结构化计划再进入工具循环

## 8. 选型的正确问题不是“哪个更强”

真正应该问的是：

### 8.1 模型当前要表达的下一步动作是什么

- 直接调工具 -> ReAct
- 先写代码再调工具 -> CodeAct
- 先拆计划再推进 -> DeepResearch

### 8.2 中间状态应该沉淀到 memory 里的是什么

- 工具结果 -> ReAct
- `CODE_RESULT / CODE_ERROR` -> CodeAct
- plan text + tool results -> DeepResearch

### 8.3 你要优化的是哪一类稳定性

- 普通 tool loop 稳定性 -> ReAct
- 工具编排与中间计算稳定性 -> CodeAct
- 先规划再执行的路径可解释性 -> DeepResearch

## 9. 自定义 runtime 时最应该从哪里切

如果你要做自己的 runtime，通常有两条路线。

### 9.1 只改一小段语义

优先继承 `BaseAgentRuntime`，覆写：

- `runtimeName()`
- `runtimeInstructions()`
- `buildPrompt(...)`
- 局部 `runInternal(...)`

### 9.2 真正换主循环

只有当你要改变：

- loop 终止条件
- 模型输出解释协议
- memory 回灌方式

这些主链路逻辑时，才值得重写更大块的 `runInternal(...)`。

否则复用 base runtime 更稳。

## 10. runtime、workflow、outer loop 的边界一定要分开

### runtime 回答什么

- 一次 Agent run 如何推进
- 模型输出怎么解释
- 工具结果何时进入下一轮

### workflow 回答什么

- 多节点之间怎么流转
- 状态图条件怎么组织

### coding-agent outer loop 回答什么

- 自动继续
- checkpoint / compact
- 宿主级 blocked / approval
- workspace 生命周期

这三层一旦混在一起，就很容易把 runtime 该做的事情写得过重，或者把上层需求错塞进 runtime。

## 11. 推荐阅读源码顺序

- `ai4j-agent/src/main/java/io/github/lnyocly/ai4j/agent/runtime/BaseAgentRuntime.java`
- `ai4j-agent/src/main/java/io/github/lnyocly/ai4j/agent/runtime/ReActRuntime.java`
- `ai4j-agent/src/main/java/io/github/lnyocly/ai4j/agent/runtime/CodeActRuntime.java`
- `ai4j-agent/src/main/java/io/github/lnyocly/ai4j/agent/runtime/DeepResearchRuntime.java`
- `ai4j-agent/src/main/java/io/github/lnyocly/ai4j/agent/runtime/Planner.java`

## 12. 继续阅读

1. [Agent Architecture](/docs/agent/architecture)
2. [Tools and Registry](/docs/agent/tools-and-registry)
3. [Memory and State](/docs/agent/memory-and-state)
4. [CodeAct Runtime](/docs/agent/codeact-runtime)
5. [CodeAct Custom Sandbox](/docs/agent/codeact-custom-sandbox)
