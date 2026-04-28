# Agent Architecture

`ai4j-agent` 的架构核心，不是“封装一下模型调用”，而是把多步推理、工具调用、状态延续和可观测性收敛成一套可复用 runtime。

如果你把它当成一个“大一点的 SDK helper”，很多设计都会显得复杂；一旦把它当成“通用 Agent runtime”，这些分层就会合理很多。

## 1. 先抓住 7 个关键设计决策

### 1.1 Builder、Runtime、ModelClient 是三条不同边界

当前架构最重要的第一层拆分就是：

- `AgentBuilder`
- `AgentRuntime`
- `AgentModelClient`

分别回答：

- 这个 Agent 怎么装起来
- 这次 run 怎么推进
- 这个 prompt 怎么发给模型

这三层一旦混写在业务代码里，后面切 runtime、换协议、做治理都会非常痛苦。

### 1.2 `AgentContext` 是 runtime 的唯一配置快照

Builder 最终不是把一堆字段零散传给 runtime，而是构造一个：

- `AgentContext`

runtime 只认这一个上下文对象。

这意味着：

- 运行中的依赖边界是稳定的
- `newSession()` 可以只替换 memory
- runtime 不需要知道 Builder 的所有细节

### 1.3 Memory 是状态源，但不是全部系统状态

`AgentMemory` 当前承载的是：

- 用户输入
- 模型输出
- 工具输出
- summary / snapshot 相关状态

但它不等于全部运行时状态。

例如：

- runtime 类型
- tool registry
- tool executor
- sampling 参数
- event publisher

都不在 memory 里，而是在 `AgentContext`。

### 1.4 工具治理从设计上就不是“藏工具”这么简单

架构里最关键的安全边界之一是：

- `AgentToolRegistry` 只暴露工具面
- `ToolExecutor` 才是真正的执行面

这说明框架作者从一开始就没有把“schema 暴露”与“权限执行”混为一谈。

### 1.5 Runtime 复用基础主循环，而不是各写一套

当前实现里：

- `ReActRuntime` 非常薄
- `DeepResearchRuntime` 也建立在基础循环之上
- 只有 `CodeActRuntime` 在关键位置换了中间表示

这意味着架构并不是 3 套互相平行的 runtime 产品，而是一套基础 runtime + 几个语义分支。

### 1.6 事件流是架构一等对象，不是调试补丁

`AgentEventPublisher`、`AgentEventType`、`AgentTraceListener` 这些都不是外围工具。

它们已经被放进：

- Builder 装配
- Runtime 执行
- Trace exporter

的主链里。

这决定了 AI4J Agent 的可观测性不是“事后 grep 日志”，而是结构化事件投影。

### 1.7 Session 的默认隔离级别比较克制

当前 `Agent.newSession()` 的边界非常明确：

- 只替换 memory

不会替换：

- runtime
- modelClient
- toolRegistry
- toolExecutor
- prompt 模板

所以 session 的默认设计更偏“轻量状态分叉”，不是完整运行环境复制。

## 2. 核心对象图

这层最值得先记住的图，不是包结构，而是对象关系：

```text
Agents
  -> AgentBuilder
  -> AgentContext
  -> Agent
  -> AgentRuntime
  -> AgentModelClient
  -> AgentToolRegistry + ToolExecutor
  -> AgentMemory
  -> AgentEventPublisher
```

其中最关键的职责是：

| 对象 | 真正职责 |
| --- | --- |
| `Agents` | 提供官方装配入口 |
| `AgentBuilder` | 解析默认值、拼装依赖 |
| `AgentContext` | 运行配置快照 |
| `Agent` | 执行入口与 session 派生 |
| `AgentRuntime` | 一次 run 的推进策略 |
| `AgentModelClient` | 模型协议适配 |
| `AgentToolRegistry` | 工具声明面 |
| `ToolExecutor` | 工具执行面 |
| `AgentMemory` | 状态源 |
| `AgentEventPublisher` | 运行事件总线 |

## 3. 构建阶段到底做了什么

理解架构，最值得直接读的第一个类还是 `AgentBuilder`。

### 3.1 默认装配链

`AgentBuilder.build()` 当前会解析这些默认值：

- `runtime` -> `ReActRuntime`
- `memorySupplier` -> `InMemoryAgentMemory::new`
- `toolRegistry` -> `StaticToolRegistry.empty()`
- `codeExecutor` -> Java 8 用 `NashornCodeExecutor`，更高版本用 `GraalVmCodeExecutor`
- `options` -> `AgentOptions.builder().build()`
- `codeActOptions` -> `CodeActOptions.builder().build()`
- `eventPublisher` -> 新建 `AgentEventPublisher`

然后再根据是否配置：

- `subAgentRegistry` / `subAgent(...)`
- `toolExecutor`
- `traceExporter`

决定是否继续包装：

- `CompositeToolRegistry`
- `SubAgentToolExecutor`
- `AgentTraceListener`

### 3.2 Builder 阶段的几个关键推论

#### 默认最小 Agent 可以完全没有工具

因为默认工具面就是：

- `StaticToolRegistry.empty()`

这说明工具不是 Agent 成立的前提。

#### Trace 不是默认总开关

只有你配置了：

- `traceExporter(...)`

Builder 才会自动挂 `AgentTraceListener`。

#### `toolRegistry(List<String>, List<String>)` 不是底层抽象

它只是便捷 API，本质上依赖反射加载：

- `ToolUtilRegistry`
- `ToolUtilExecutor`

所以它更像快速接线入口，而不是核心架构原语。

## 4. 运行阶段到底怎么推进

### 4.1 `Agent` 本身很薄

`Agent` 主要暴露：

- `run(...)`
- `runStream(...)`
- `runStreamResult(...)`
- `newSession()`

它不是复杂主循环本体，真正复杂的逻辑在 runtime。

### 4.2 `BaseAgentRuntime` 是默认主循环骨架

`BaseAgentRuntime.runInternal(...)` 当前主链大致是：

1. 读取 `AgentOptions`
2. 校验 memory
3. 把用户输入写进 memory
4. 发布 `STEP_START`
5. `buildPrompt(...)`
6. `executeModel(...)`
7. 把模型返回 `memoryItems` 写回 memory
8. 归一化 tool calls
9. 参数校验
10. 执行工具
11. 把工具输出写回 memory
12. 发布 `STEP_END`
13. 如果无 tool call，则发布 `FINAL_OUTPUT` 并收口

这条链定义了当前 ReAct 系 runtime 的默认运行语义。

### 4.3 Prompt 不是每次手工拼，而是每步从 memory 重建

`buildPrompt(...)` 会把：

- `memory.getItems()`
- `systemPrompt`
- `instructions`
- `tools`
- sampling 参数

重新组合成新的 `AgentPrompt`。

这说明 Agent 的主循环核心不是“拼接一个不断增长的大字符串”，而是：

- 把 memory 当作状态源
- 每一步重建 prompt 快照

### 4.4 终止条件不是“模型说完了”，而是“本轮没有 tool call”

在默认 ReAct 主循环里，如果模型这轮没有产出 tool call：

- runtime 认为可以收口

如果有 tool call：

- runtime 会继续下一轮

这也是 Agent 和普通单次模型调用的本质差异之一。

## 5. Prompt 层是怎么分层的

这一层如果不看源码，很容易讲错。

### 5.1 `systemPrompt` 会被 runtime 额外拼接

`BaseAgentRuntime.buildPrompt(...)` 会做：

```java
String systemPrompt = mergeText(context.getSystemPrompt(), runtimeInstructions());
```

所以送进模型的系统层文本，不只是你配置进去的那份字符串，还包括 runtime 自带策略。

### 5.2 `instructions` 保持单独字段

它不会参与这一步 merge，而是保留为：

- `AgentPrompt.instructions`

### 5.3 不同 model client 会再把这两个字段映射成不同协议形状

例如：

- Chat 路径：两者都下沉成 system messages
- Responses 路径：`systemPrompt` 进 request-level instructions，`instructions` 进前置 input item

这说明 prompt layering 已经是架构的一部分，而不是文档上的约定俗成。

## 6. 工具层为什么必须拆成两面

### 6.1 声明面

`AgentToolRegistry` 只回答：

> 模型能看到哪些工具？

### 6.2 执行面

`ToolExecutor` 只回答：

> 调用真正发生时，系统怎么执行？

### 6.3 这带来的架构收益

- 工具白名单稳定
- 执行治理可插拔
- 审批 / 审计 / 沙箱能挂在执行侧
- subagent tool surface 能作为普通 tool 统一暴露

如果没有这层拆分，你后面几乎没法优雅地讲清：

- 为什么模型“看得到”不等于“能执行”
- 为什么审批应落在 executor
- 为什么 subagent 是 tool-like handoff

## 7. 状态层和 session 层的真实边界

### 7.1 `AgentMemory` 承载的是运行态上下文

它记录的不是抽象“记忆”概念，而是：

- 用户输入
- 模型输出
- 工具输出
- summary / snapshot

### 7.2 `newSession()` 的架构含义

`Agent.newSession()` 的实现只会：

- 基于同一个 `baseContext`
- 替换一份新的 memory

所以：

- 新 session 不是新 Agent 世界
- 只是新状态空间

### 7.3 什么时候应该重新 build 一个 Agent

如果你要换的是：

- runtime
- 工具白名单
- 执行权限
- 模型协议
- prompt 模板

那就不该只开新 session，而应重新装配 Agent。

## 8. 事件与 trace 如何嵌进架构

### 8.1 事件来自 runtime 主链

当前关键事件包括：

- `STEP_START`
- `STEP_END`
- `MODEL_REQUEST`
- `MODEL_REASONING`
- `MODEL_RESPONSE`
- `MODEL_RETRY`
- `TOOL_CALL`
- `TOOL_RESULT`
- `FINAL_OUTPUT`
- `ERROR`

这些不是外围猜出来的，而是 runtime 在关键节点主动发布的。

### 8.2 Trace 是事件投影，不是 runtime 内嵌 span

Builder 如果发现你配置了：

- `traceExporter(...)`

就会在 `eventPublisher` 上挂：

- `AgentTraceListener`

后者再把事件折叠成：

- `RUN`
- `STEP`
- `MODEL`
- `TOOL`
- `HANDOFF`
- `TEAM_TASK`

等 span。

这说明 trace 是架构里的一条正式 side-channel，不是额外日志插件。

## 9. 默认值、限制与失败语义

### 9.1 `modelClient` 是唯一硬性必填依赖

Builder 阶段会直接校验：

- `modelClient`

缺失会抛 `IllegalStateException`。

### 9.2 `maxSteps = 0` 代表不设上限

这是实验友好的默认值，但不是生产安全默认值。

### 9.3 工具异常默认不会中断整轮运行

`BaseAgentRuntime.executeTool(...)` 会捕获异常，并把结果转成：

```text
TOOL_ERROR: {...}
```

然后写回 memory，继续让模型看见这个失败结果。

这意味着默认失败语义更偏：

- 可恢复上下文

而不是：

- 立刻终止

### 9.4 非法 tool call 先校验，再回灌错误

`AgentToolCallSanitizer` 先做结构校验；不合法时也不是直接抛异常，而是转成错误结果回灌。

### 9.5 并行工具调用要求执行器线程安全

只有在：

- `parallelToolCalls == true`
- 合法调用数大于 1

时，runtime 才会开线程池并行跑工具。

所以自定义 executor 必须自己保证线程安全。

## 10. 这个架构不解决什么

它不自动解决：

- 代码仓宿主审批
- 终端 UI / TUI / ACP host
- checkpoint / compact outer loop
- 可视化节点平台

这些都需要更上层模块继续承接。

## 11. 推荐阅读源码顺序

- `ai4j-agent/src/main/java/io/github/lnyocly/ai4j/agent/Agents.java`
- `ai4j-agent/src/main/java/io/github/lnyocly/ai4j/agent/AgentBuilder.java`
- `ai4j-agent/src/main/java/io/github/lnyocly/ai4j/agent/AgentContext.java`
- `ai4j-agent/src/main/java/io/github/lnyocly/ai4j/agent/Agent.java`
- `ai4j-agent/src/main/java/io/github/lnyocly/ai4j/agent/runtime/BaseAgentRuntime.java`
- `ai4j-agent/src/main/java/io/github/lnyocly/ai4j/agent/model/AgentModelClient.java`
- `ai4j-agent/src/main/java/io/github/lnyocly/ai4j/agent/tool/AgentToolRegistry.java`
- `ai4j-agent/src/main/java/io/github/lnyocly/ai4j/agent/tool/ToolExecutor.java`
- `ai4j-agent/src/main/java/io/github/lnyocly/ai4j/agent/memory/AgentMemory.java`
- `ai4j-agent/src/main/java/io/github/lnyocly/ai4j/agent/event/AgentEventPublisher.java`

## 12. 继续阅读

1. [Quickstart](/docs/agent/quickstart)
2. [Tools and Registry](/docs/agent/tools-and-registry)
3. [Memory and State](/docs/agent/memory-and-state)
4. [Runtime Implementations](/docs/agent/runtime-implementations)
5. [Trace 与可观测性](/docs/agent/trace-observability)
