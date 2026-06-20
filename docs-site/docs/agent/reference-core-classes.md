---
sidebar_position: 14
---

# Agent 核心类参考

这页不是类表，也不是 API 罗列，而是一张“源码导航图”。

如果你第一次进入 `ai4j-agent`，最容易发生的事就是在：

- `agent`
- `runtime`
- `tool`
- `memory`
- `subagent`
- `team`
- `trace`

这些包之间来回跳，最后只记住零散类名，却没建立真正的对象关系。

这页的目标，是把核心类重新按“装配 -> 执行 -> 扩展 -> 观测”排序，让你知道该先看哪条线。

## 1. 先建立一张总图

AI4J Agent 层的大致结构可以先压缩成一张图：

```text
Agents / AgentBuilder
  -> Agent / AgentSession
  -> AgentContext
  -> AgentRuntime
  -> AgentModelClient
  -> AgentToolRegistry + ToolExecutor
  -> AgentMemory
  -> trace / event
  -> subagent / workflow / team
```

如果把这张图里的每个对象都看成独立功能点，会很难学；正确方式是顺着依赖方向读。

## 2. 第一层：装配入口

这一层决定“一个 Agent 是怎么被拼起来的”。

### `Agents`

这是工厂入口。

你应该从这里认识官方提供的几条主线：

- `builder()`
- `react()`
- `codeAct()`
- `deepResearch()`
- `team()`

它回答的是“框架公开提供了哪些装配入口”。

### `AgentBuilder`

这是整个 Agent 层最关键的装配器。

如果你只读一个类来理解默认行为，先读它。

它定义了当前系统最重要的默认值：

- runtime 默认 `ReActRuntime`
- memory supplier 默认 `InMemoryAgentMemory::new`
- tool registry 默认 `StaticToolRegistry.empty()`
- 未显式提供 `ToolExecutor` 时会尝试初始化默认执行器
- 配置 subagent 时会合并 subagent tools，并包一层 `SubAgentToolExecutor`
- 未显式提供 `CodeExecutor` 时按 Java 版本选择 Nashorn 或 GraalVM

所以很多“为什么系统会这么跑”的问题，最终都能回到 `AgentBuilder`。

### `AgentContext`

这是 runtime 真正消费的配置快照。

它把执行相关依赖都压在一个对象里：

- `modelClient`
- `toolRegistry`
- `toolExecutor`
- `codeExecutor`
- `memory`
- `options`
- `codeActOptions`
- `eventPublisher`
- `model`
- prompt / sampling / reasoning / extraBody 等配置

它的意义不在于“字段多”，而在于：

- runtime 可以只认一个上下文对象
- session 可以在保留大部分上下文的前提下只替换 `memory`

## 3. 第二层：运行入口和会话语义

这一层决定“同一套装配好的 Agent，怎么被真正调用”。

### `Agent`

`Agent` 是执行入口，不是执行逻辑本身。

它主要暴露：

- `run(AgentRequest)`
- `runStream(...)`
- `runStreamResult(...)`
- `newSession()`

真正重要的点是：

- 它本身几乎不做复杂逻辑
- 只是把调用路由给 runtime，并持有 `baseContext` 和 `memorySupplier`

### `AgentSession`

`AgentSession` 是同一 `Agent` 装配下的长程运行态容器。它仍然会为每个 session 创建独立 `AgentMemory`，但当前职责已经不止“切换 memory”。

它回答的问题是：

- 同一 Agent 如何开始一个新的状态空间；
- 这个状态空间如何拥有稳定 `sessionId`、metadata、event log 和 snapshot；
- 如何通过 `AgentSessionStore` 保存/恢复；
- 如何记录最近一次 compact 结果；
- 如何绑定非敏感 sandbox 摘要。

所以如果你理解了 `Agent.newSession()` 的实现，就会明白：

- Session 不是完整克隆 Agent 运行环境；
- 它会复用 runtime 和大部分 `AgentContext`；
- 它会替换 memory、sessionId 和 event publisher；
- 它把 metadata/event log/snapshot/store/compact/sandbox binding 收拢在 `AgentSession` 边界内。

详细能力矩阵见 [Agent SDK 真实 API 能力矩阵](/docs/agent/real-api-matrix)，长程会话用法见 [Agent Session Runtime](/docs/agent/session-runtime)。

## 4. 第三层：runtime 线

这一层决定一次 Agent run 的推进语义。

### `AgentRuntime`

这是运行时抽象接口。

它真正定义的是：

- `run(...)`
- `runStream(...)`
- `runStreamResult(...)`

也就是说，runtime 是“单次调用的推进器”。

### `BaseAgentRuntime`

这是默认主循环骨架。

如果你在排查：

- 为什么模型会继续下一轮
- 为什么工具结果会进 memory
- 为什么 tool error 没立刻终止
- 为什么多个工具被并行执行

第一反应都应该回到它。

它负责的核心流程包括：

- 用户输入写 memory
- `AgentPrompt` 组装
- 模型调用
- tool call 归一化
- 参数校验
- 工具执行
- 结果回灌
- 事件发射

### `ReActRuntime`

这是最贴近框架默认语义的 runtime。

当前实现非常薄，主要是在 `BaseAgentRuntime` 之上补了一层：

- runtime name
- runtime instructions

它的价值不是“功能多”，而是“最接近默认行为真相”。

### `CodeActRuntime`

当你看到：

- 模型为什么输出 JSON
- 为什么要先执行代码
- 为什么 `CODE_RESULT` 会回到 memory

就该切换到它。

它的价值在于换掉了中间表示。

### `DeepResearchRuntime`

当前实现不是重型 research framework，而是在默认 loop 前插入 planning。

它值得看的地方在于：

- planning 是怎么进 memory 的
- `Planner.simple()` 到底有多轻

## 5. 第四层：模型适配线

这一层回答的是“runtime 组装好的 prompt，最终怎么发到底层模型协议里”。

### `AgentModelClient`

统一模型适配接口。

当你要接新的 provider，优先看它。

### `AgentPrompt`

这是 runtime 向模型层提交的标准输入。

你在研究 prompt 组装问题时，应该看它，而不是直接猜某个 provider 请求长什么样。

### `AgentModelResult`

这是模型层返回给 runtime 的标准输出。

关键字段通常包括：

- `outputText`
- `toolCalls`
- `memoryItems`
- `rawResponse`

### `ChatModelClient` / `ResponsesModelClient`

它们回答的是协议差异，不回答运行时策略。

这两层一定要分开：

- runtime 决定循环
- model client 决定协议

## 6. 第五层：工具线

这一层是整个 Agent 架构最重要的分层之一。

### `AgentToolRegistry`

它只回答：

> 模型能看到什么工具？

### `ToolExecutor`

它只回答：

> 工具真正怎么执行？

如果你把这两个对象的职责想混了，后面权限治理、审批拦截、参数改写都会越来越乱。

### `StaticToolRegistry`

最直接的工具暴露方式。

### `CompositeToolRegistry`

当你开始合并：

- 普通工具
- subagent tools
- team tools

就会遇到它。

### `AgentToolCall` / `AgentToolResult`

这两个对象是 runtime 和工具执行层交换信息的基本载体。

### `AgentToolCallSanitizer`

如果你在查：

- 为什么一个 tool call 被拒绝
- 为什么 arguments 结构不合法

它是必读类。

## 7. 第六层：memory 线

Agent 的持续性不在 runtime 内部私有字段里，而在 `AgentMemory`。

### `AgentMemory`

抽象接口，回答：

- 运行中积累的输入输出放在哪里
- 下一轮 prompt 从哪里重建

### `InMemoryAgentMemory`

默认实现。

### `MemorySnapshot`

如果你关心复制、持久化、恢复语义，就会需要它。

### `MemoryCompressor` / `WindowedMemoryCompressor`

当问题变成：

- 长会话如何降本
- 历史记录如何裁剪

你才需要进入这一层。

## 8. 第七层：workflow 线

当“单个 Agent loop”已经不够表达你的执行结构时，就会进入 workflow 层。

### `SequentialWorkflow`

最简单的节点串联。

### `StateGraphWorkflow`

适合看：

- 条件路由
- 显式状态推进
- 多节点编排

### `AgentNode`

Agent 如何作为节点被嵌进 workflow。

### `WorkflowContext`

工作流级上下文，而不是单 Agent memory。

### `WorkflowAgent`

把 workflow 再包装成 Agent 形态。

## 9. 第八层：SubAgent 线

这一层解决的是“把另一个 Agent 作为受治理工具接进来”。

### `SubAgentDefinition`

看：

- `name`
- `toolName`
- `sessionMode`

它定义了“这个 subagent 以什么身份被暴露给模型”。

### `StaticSubAgentRegistry`

看：

- 默认工具名生成
- schema 生成
- 输入如何折叠成 subagent task
- 输出如何压平成普通工具结果

### `SubAgentToolExecutor`

这是 handoff 真正发生和被治理的地方。

如果问题涉及：

- timeout
- retry
- deny / fallback
- depth

都应该优先看它。

### `HandoffPolicy` / `HandoffContext`

这一层定义的是：

- handoff 是否允许
- 最大嵌套深度是多少
- 当前线程里的 handoff depth 是多少

## 10. 第九层：Team 线

这一层解决的是“多个成员围绕目标协作”。

### `AgentTeamBuilder`

先看它，因为它决定：

- lead / planner / synthesizer 的回退规则
- options / storage / hooks 的装配方式

### `AgentTeam`

这是团队运行时本体。

它同时也是 `AgentTeamControl`。

如果你想理解：

- run lifecycle
- dispatch loop
- member task execution
- persistence / restore

就应该优先读它。

### `AgentTeamTaskBoard`

真正的状态机核心。

任务是怎么从：

- `PENDING`
- `READY`
- `IN_PROGRESS`
- `COMPLETED`
- `FAILED`
- `BLOCKED`

之间推进的，都在这里。

### `AgentTeamToolRegistry` / `AgentTeamToolExecutor`

如果你在查：

- 为什么成员能主动发消息
- 为什么成员能 claim / release / heartbeat task

就该看这两者。

### `AgentTeamMessageBus` / `AgentTeamStateStore`

团队连续性和持久化不是内建在成员 memory 里，而是外移到：

- 消息总线
- 状态存储

这也是 Team 和 SubAgent 的关键区别。

## 11. 第十层：trace 与事件线

这一层决定的是“系统怎么把运行过程暴露给外部”。

### `AgentEventPublisher`

Agent runtime 的统一事件总线。

### `AgentTraceListener`

把事件映射成 trace span。

### `TraceSpan`

统一 span 数据模型。

### `TraceConfig`

决定是否记录：

- 模型输入输出
- 工具参数输出
- metrics

### `TraceExporter`

导出抽象。

### 常见实现

- `ConsoleTraceExporter`
- `JsonlTraceExporter`
- `OpenTelemetryTraceExporter`
- `LangfuseTraceExporter`

如果你的问题是“运行了，但我看不见里面发生了什么”，通常就该顺着这一层读。

## 12. 哪些默认值最值得记住

不是所有默认值都一样重要。最值得记住的是这些：

| 位置 | 默认值 | 为什么重要 |
| --- | --- | --- |
| `AgentBuilder.runtime` | `ReActRuntime` | 默认 runtime 就是 ReAct |
| `AgentBuilder.memorySupplier` | `InMemoryAgentMemory::new` | session 默认只换内存 |
| `AgentBuilder.toolRegistry` | `StaticToolRegistry.empty()` | 最小 Agent 可以没有工具 |
| `AgentBuilder.codeExecutor` | Java 8 -> Nashorn；高版本 -> GraalVM | 直接影响 CodeAct 语言语义 |
| `CodeActOptions.reAct` | `false` | CodeAct 默认不再回模型做总结 |
| `SubAgentDefinition.sessionMode` | `NEW_SESSION` | subagent 默认隔离执行 |
| `HandoffPolicy.maxDepth` | `1` | 默认只允许一层 handoff |
| `AgentTeamOptions.parallelDispatch` | `true` | Team 默认是并发派发模型 |
| `AgentTeamOptions.enableMemberTeamTools` | `true` | 成员默认可主动协作 |

## 13. 按问题类型选源码入口

### 我只想跑通最小 Agent

先看：

1. `Agents`
2. `AgentBuilder`
3. `Agent`
4. `BaseAgentRuntime`
5. `ReActRuntime`

### 我在查工具权限或执行拦截

先看：

1. `AgentToolRegistry`
2. `ToolExecutor`
3. `AgentToolCallSanitizer`
4. 你自己的自定义执行器

### 我在查 CodeAct 为什么没有按预期结束

先看：

1. `CodeActRuntime`
2. `CodeActOptions`
3. `CodeExecutionResult`
4. 当前注入的 `CodeExecutor`

### 我在查 SubAgent 为什么 handoff 失败

先看：

1. `SubAgentDefinition`
2. `StaticSubAgentRegistry`
3. `SubAgentToolExecutor`
4. `HandoffPolicy`

### 我在查 Team 为什么没协作起来

先看：

1. `AgentTeamBuilder`
2. `AgentTeam`
3. `AgentTeamTaskBoard`
4. `AgentTeamToolRegistry`
5. `AgentTeamToolExecutor`

## 14. 推荐测试索引

如果你要用测试反向理解源码，优先看：

- `ai4j-agent/src/test/java/io/github/lnyocly/agent/CodeActRuntimeTest.java`
- `ai4j-agent/src/test/java/io/github/lnyocly/agent/CodeActRuntimeWithTraceTest.java`
- `ai4j-agent/src/test/java/io/github/lnyocly/agent/SubAgentRuntimeTest.java`
- `ai4j-agent/src/test/java/io/github/lnyocly/agent/SubAgentParallelFallbackTest.java`
- `ai4j-agent/src/test/java/io/github/lnyocly/agent/HandoffPolicyTest.java`
- `ai4j-agent/src/test/java/io/github/lnyocly/agent/AgentTeamTest.java`
- `ai4j-agent/src/test/java/io/github/lnyocly/agent/AgentTeamTaskBoardTest.java`
- `ai4j-agent/src/test/java/io/github/lnyocly/agent/AgentTeamPersistenceTest.java`

## 15. 继续阅读

1. [Agent Architecture](/docs/agent/architecture)
2. [Tools and Registry](/docs/agent/tools-and-registry)
3. [Runtime Implementations](/docs/agent/runtime-implementations)
4. [CodeAct Runtime](/docs/agent/codeact-runtime)
5. [SubAgent 与 Handoff Policy](/docs/agent/subagent-handoff-policy)
6. [Agent Teams](/docs/agent/agent-teams)
