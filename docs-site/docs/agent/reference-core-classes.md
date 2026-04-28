---
sidebar_position: 14
---

# Agent 核心类参考

这一页不是 API dump，而是面向源码阅读和二次开发的“类导航图”。

如果你第一次读 `ai4j-agent` 源码，很容易在 `agent`、`runtime`、`tool`、`memory`、`subagent`、`team`、`trace` 这些包之间来回跳。这个页面的目标，是把核心类按职责重新组织，让你知道：

- 应该先看哪一层
- 每个类解决什么问题
- 哪些是默认实现，哪些是扩展点
- 不同场景下该从哪里切入源码

## 1. 阅读这页前先建立一张总图

AI4J Agent 层的大致结构可以抽象为：

```text
Agents / AgentBuilder
  -> Agent / AgentSession
  -> AgentRuntime
  -> AgentModelClient
  -> AgentToolRegistry + ToolExecutor
  -> AgentMemory
  -> trace / event
  -> subagent / team / workflow
```

建议按“从装配到执行，再到扩展层”的顺序阅读，而不是一上来就钻具体 runtime。

## 2. 顶层入口类

这组类决定 Agent 是如何被构建和启动的。

| 类 | 角色 | 你什么时候应该读它 |
| --- | --- | --- |
| `Agents` | 工厂入口 | 想知道有哪些官方入口：`react()`、`codeAct()`、`deepResearch()`、`team()` |
| `AgentBuilder` | 统一装配器 | 想理解默认值、依赖解析、工具执行链、subagent 装配 |
| `Agent` | 执行入口 | 想看 `run(...)`、`runStream(...)`、`newSession()` 的公共语义 |
| `AgentSession` | 会话包装器 | 想知道“新 session”到底隔离了什么 |
| `AgentContext` | 运行时配置快照 | 想看 runtime 真正拿到哪些依赖和参数 |

### 2.1 先看 `AgentBuilder`

如果你只读一个类来理解整体结构，优先读 `AgentBuilder`。

它定义了最关键的默认行为：

- `runtime` 默认 `ReActRuntime`
- `memorySupplier` 默认 `InMemoryAgentMemory::new`
- `toolRegistry` 默认 `StaticToolRegistry.empty()`
- 未显式提供 `ToolExecutor` 时自动组默认执行器
- 配置了 subagent 后用 `SubAgentToolExecutor` 包装执行器
- 未显式提供 `CodeExecutor` 时按 Java 版本选择 Nashorn 或 GraalVM

换句话说，`AgentBuilder` 决定“系统默认长什么样”。

## 3. Runtime 层

Runtime 层决定“一次 Agent run 是怎么跑起来的”。

| 类 | 角色 | 重点看什么 |
| --- | --- | --- |
| `AgentRuntime` | 运行时抽象接口 | 看扩展点边界 |
| `BaseAgentRuntime` | 通用 loop 骨架 | 看 prompt 构造、模型调用、工具执行、memory 回写 |
| `ReActRuntime` | 默认 runtime | 看标准 tool loop 的执行语义 |
| `CodeActRuntime` | 代码驱动 runtime | 看 JSON 协议、代码执行、`reAct` 分支 |
| `DeepResearchRuntime` | 研究型 runtime | 看规划和证据汇总策略 |

### 3.1 调试工具循环时先看 `BaseAgentRuntime`

无论你最终用的是 ReAct 还是更复杂的 runtime，只要问题涉及：

- 为什么模型继续下一轮
- 为什么某个工具被并行执行
- 为什么 tool error 没有直接终止

都应优先回到 `BaseAgentRuntime`。

### 3.2 调试代码执行时再看 `CodeActRuntime`

只有当问题涉及：

- 模型为什么输出 JSON
- 为什么在执行代码
- 为什么 `CODE_RESULT` 又回到了 memory

才需要进一步下钻 `CodeActRuntime`。

## 4. 模型适配层

模型层的职责是“把 AgentPrompt 送到模型协议，再把返回结果规范化”。

| 类 | 角色 | 重点看什么 |
| --- | --- | --- |
| `AgentModelClient` | 统一模型接口 | 想适配新 provider 时必读 |
| `AgentPrompt` | 运行时标准输入 | 看 runtime 是如何组织 model、items、tools、prompts 的 |
| `AgentModelResult` | 标准输出 | 看 outputText、toolCalls、memoryItems、rawResponse |
| `ChatModelClient` | Chat 协议适配器 | 用传统 chat-completions 类模型时 |
| `ResponsesModelClient` | Responses 协议适配器 | 用事件化 / 新式 responses 路径时 |

需要明确一个边界：

- `AgentModelClient` 决定模型协议
- `AgentRuntime` 决定执行策略

这两个层次不要混为一谈。

## 5. Tool 层

Tool 层是 AI4J Agent 架构里最重要的分层之一。

| 类 | 角色 | 重点看什么 |
| --- | --- | --- |
| `AgentToolRegistry` | 工具声明面 | 模型能看到哪些工具 |
| `StaticToolRegistry` | 静态工具集合 | 最直接的工具暴露方式 |
| `CompositeToolRegistry` | 聚合注册器 | 合并普通工具、subagent 工具、team 工具 |
| `ToolExecutor` | 执行面接口 | 工具真正如何执行 |
| `ToolUtilExecutor` | 默认执行器 | 反射式接入 ToolUtil 集成 |
| `AgentToolCall` | 工具调用对象 | 看 name、arguments、callId |
| `AgentToolResult` | 工具结果对象 | 看输出如何回到 runtime |
| `AgentToolCallSanitizer` | 参数校验器 | 看非法 tool call 如何被拒绝 |

### 5.1 为什么一定要先分清 Registry 和 Executor

这是二次开发最容易踩坑的地方。

- `AgentToolRegistry` 只负责“声明”
- `ToolExecutor` 只负责“执行”

所以权限审批、执行拦截、参数重写、沙箱治理，都应该优先挂在 `ToolExecutor` 一侧，而不是寄希望于“模型看不到就不会调用”。

## 6. Memory 层

Memory 层回答的问题是：“运行中积累的上下文到底放在哪里，由谁重组给模型？”

| 类 | 角色 | 重点看什么 |
| --- | --- | --- |
| `AgentMemory` | memory 抽象 | 想替换存储后端时必读 |
| `InMemoryAgentMemory` | 默认内存实现 | 看最基础的 item 累积模型 |
| `MemorySnapshot` | 状态快照 | 看 memory 如何序列化或复制 |
| `MemoryCompressor` | 压缩抽象 | 长会话降本与窗口治理 |
| `WindowedMemoryCompressor` | 窗口压缩实现 | 看默认压缩策略如何切片历史 |

一个关键事实是：

- `Agent.newSession()` 只会替换 `AgentMemory`

所以很多“为什么 session 隔离后行为还一样”的问题，本质上是因为 runtime、modelClient、toolExecutor 都没变。

## 7. Workflow / Orchestration 层

当你不再满足于“单个 Agent loop”时，就会进入编排层。

| 类 | 角色 | 重点看什么 |
| --- | --- | --- |
| `SequentialWorkflow` | 顺序编排 | 简单节点串联 |
| `StateGraphWorkflow` | 状态图编排 | 条件路由、节点跳转、显式状态推进 |
| `AgentNode` | 图节点包装 | Agent 如何进入 workflow |
| `WorkflowContext` | 工作流上下文 | 节点间状态共享 |
| `WorkflowAgent` | Agent 形态工作流 | 工作流如何暴露为 Agent |

如果你在调的是节点关系、路由条件、状态推进，而不是工具循环，那就已经离开 runtime 层，进入 workflow 层了。

## 8. SubAgent 层

SubAgent 层让“另一个 Agent”以工具形式进入主执行链。

| 类 | 角色 | 重点看什么 |
| --- | --- | --- |
| `SubAgentDefinition` | 子代理定义 | 看 name、toolName、sessionMode |
| `SubAgentRegistry` | 子代理注册抽象 | 看工具面如何生成 |
| `StaticSubAgentRegistry` | 默认注册器 | 看默认工具名、schema、执行返回值 |
| `SubAgentToolExecutor` | handoff 执行器 | 看 policy、fallback、timeout、retry |
| `SubAgentSessionMode` | 会话模式 | 看 `NEW_SESSION` vs `REUSE_SESSION` |
| `HandoffPolicy` | 治理策略 | 看深度、超时、错误动作 |
| `HandoffFailureAction` | 失败动作枚举 | 看 `FAIL` / `FALLBACK_TO_PRIMARY` |
| `HandoffContext` | 深度上下文 | 看嵌套 handoff 如何限深 |

你应该在下面这些问题时优先看这一层：

- 为什么主 Agent 会把任务委派给子代理
- 为什么子代理工具被拒绝执行
- 为什么 handoff 超时或回退到主执行器

## 9. Team 层

Team 层把多成员协作显式化。

| 类 | 角色 | 重点看什么 |
| --- | --- | --- |
| `AgentTeamBuilder` | 团队装配入口 | 看 leader / planner / synthesizer 的回退规则 |
| `AgentTeam` | 团队运行时 | 看派发、消息、状态持久化、恢复 |
| `AgentTeamOptions` | 团队策略 | 看并发、容错、消息、团队工具默认值 |
| `AgentTeamTaskBoard` | 任务板 | 看任务状态和依赖推进 |
| `AgentTeamTask` | 任务定义 | 看任务输入结构 |
| `AgentTeamTaskState` | 运行态快照 | 看 phase、percent、heartbeat 等 |
| `AgentTeamMessageBus` | 消息总线抽象 | 看消息如何流转与恢复 |
| `InMemoryAgentTeamMessageBus` | 默认消息总线 | 纯内存实现 |
| `FileAgentTeamMessageBus` | 文件邮箱 | `jsonl` 形式保存消息历史 |
| `AgentTeamStateStore` | 状态存储抽象 | 看快照如何保存和恢复 |
| `FileAgentTeamStateStore` | 文件状态存储 | 看 `state/<teamId>.json` 持久化 |
| `AgentTeamToolRegistry` | 团队工具声明面 | 看 `team_*` 协作工具 schema |
| `AgentTeamToolExecutor` | 团队工具执行面 | 看消息、认领、转派、心跳如何执行 |

### 9.1 Team 常见源码切入点

如果你遇到的是以下问题，对应优先阅读点如下：

- 规划器为什么没生效：`AgentTeam` 构造函数 + `AgentTeamBuilder`
- 任务为什么一直 blocked：`AgentTeamTaskBoard`
- 成员为什么能主动协作：`AgentTeamToolRegistry` + `AgentTeamToolExecutor`
- 为什么团队状态能恢复：`snapshotState()` / `loadPersistedState()` / `restoreState(...)`

## 10. Trace 与事件层

观测层不是可选附件，而是运行时的一等公民。

| 类 | 角色 | 重点看什么 |
| --- | --- | --- |
| `AgentEventPublisher` | 事件总线 | 看运行时事件如何广播 |
| `AgentTraceListener` | 事件到 trace 的桥 | 看模型、工具、handoff、team 事件如何映射 |
| `TraceSpan` | trace 基础对象 | 看 span 的统一数据模型 |
| `TraceConfig` | trace 配置 | 看模型输入输出、工具参数、metrics 是否记录 |
| `TraceExporter` | 导出抽象 | 看如何接控制台、JSONL、OTel、Langfuse |
| `ConsoleTraceExporter` | 控制台导出 | 本地调试最直接 |
| `JsonlTraceExporter` | 文件导出 | 离线分析与回放 |
| `OpenTelemetryTraceExporter` | OTel 导出 | 接现有观测平台 |
| `LangfuseTraceExporter` | Langfuse 导出 | 接外部 LLM observability 平台 |

如果你面对的问题是：

- 为什么看不到工具调用链
- 为什么 Team / Handoff 没有落 trace
- 如何把运行信息接入宿主或观测平台

就应当优先看这一层。

## 11. CodeAct 执行层

虽然 `CodeActRuntime` 属于 runtime，但它依赖的执行层值得单独记住。

| 类 | 角色 | 重点看什么 |
| --- | --- | --- |
| `CodeActOptions` | CodeAct 策略开关 | 看 `reAct` 是否开启执行后总结 |
| `CodeExecutor` | 代码执行器接口 | 看你如何替换默认执行环境 |
| `CodeExecutionRequest` | 执行入参 | 看语言、代码、工具注入信息 |
| `CodeExecutionResult` | 执行结果 | 看 result/stdout/error/success |
| `NashornCodeExecutor` | Java 8 执行器 | 看 ES5 兼容边界 |
| `GraalVmCodeExecutor` | 高版本 Java 执行器 | 看 Python/JS 运行能力 |

## 12. 关键默认值速查

下面这些默认值在实际排障时非常重要：

| 位置 | 默认值 | 为什么重要 |
| --- | --- | --- |
| `AgentBuilder.runtime` | `ReActRuntime` | 没显式指定 runtime 时，默认就是 ReAct |
| `AgentBuilder.memorySupplier` | `InMemoryAgentMemory::new` | session 隔离默认只靠内存 |
| `AgentBuilder.codeExecutor` | Java 8 -> Nashorn；更高版本 -> GraalVM | 直接影响 CodeAct 语言语义 |
| `CodeActOptions.reAct` | `false` | 默认执行成功后不会再回模型总结 |
| `SubAgentDefinition.sessionMode` | `NEW_SESSION` | 默认每次 handoff 都是新会话 |
| `HandoffPolicy.maxDepth` | `1` | 默认只允许一层主从委派 |
| `AgentTeamOptions.parallelDispatch` | `true` | Team 默认并发派发 |
| `AgentTeamOptions.enableMemberTeamTools` | `true` | Team 默认让成员能主动协作 |
| `AgentTeamOptions.maxRounds` | `64` | Team 有明确轮次上限 |

## 13. 按场景选源码入口

如果你不确定该从哪里开始，按问题类型选：

### 13.1 我只想跑通最小 Agent

先看：

1. `Agents`
2. `AgentBuilder`
3. `Agent`
4. `ReActRuntime`

### 13.2 我在查工具权限或执行拦截

先看：

1. `AgentToolRegistry`
2. `ToolExecutor`
3. `AgentToolCallSanitizer`
4. 你自己的自定义执行器

### 13.3 我在查 SubAgent 为什么没按预期 handoff

先看：

1. `SubAgentDefinition`
2. `StaticSubAgentRegistry`
3. `SubAgentToolExecutor`
4. `HandoffPolicy`

### 13.4 我在查 Team 为什么没协作起来

先看：

1. `AgentTeamBuilder`
2. `AgentTeam`
3. `AgentTeamTaskBoard`
4. `AgentTeamToolRegistry`
5. `AgentTeamToolExecutor`

### 13.5 我在查 CodeAct 为什么执行不了

先看：

1. `CodeActRuntime`
2. `CodeActOptions`
3. `CodeExecutor`
4. 当前实际注入的执行器

## 14. 推荐测试索引

对源码理解最有帮助的测试，建议优先看：

- `ai4j-agent/src/test/java/io/github/lnyocly/agent/CodeActRuntimeTest.java`
- `ai4j-agent/src/test/java/io/github/lnyocly/agent/CodeActRuntimeWithTraceTest.java`
- `ai4j-agent/src/test/java/io/github/lnyocly/agent/SubAgentRuntimeTest.java`
- `ai4j-agent/src/test/java/io/github/lnyocly/agent/SubAgentParallelFallbackTest.java`
- `ai4j-agent/src/test/java/io/github/lnyocly/agent/HandoffPolicyTest.java`
- `ai4j-agent/src/test/java/io/github/lnyocly/agent/AgentTeamTest.java`
- `ai4j-agent/src/test/java/io/github/lnyocly/agent/AgentTeamTaskBoardTest.java`
- `ai4j-agent/src/test/java/io/github/lnyocly/agent/AgentTeamPersistenceTest.java`

## 15. 关联阅读

如果你读完这页还需要深入某一层，建议继续：

1. [Agent Architecture](/docs/agent/architecture)
2. [Tools and Registry](/docs/agent/tools-and-registry)
3. [CodeAct Runtime](/docs/agent/codeact-runtime)
4. [SubAgent 与 Handoff Policy](/docs/agent/subagent-handoff-policy)
5. [Agent Teams](/docs/agent/agent-teams)
