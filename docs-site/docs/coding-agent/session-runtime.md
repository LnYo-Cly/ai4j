---
sidebar_position: 4
---

# 会话、流式与进程

这一页如果只写成“支持 save / resume / fork / process”，信息量其实不够。  
从源码看，Coding Agent 的 session runtime 真正解决的是一个更具体的问题：

**怎样把一次本地代码任务做成可持续、可中断、可恢复、可继续推进的工作会话。**

这件事在 AI4J 里不是单个类完成的，而是几层一起协作：

- `CodingSession`
- `CodingAgentLoopController`
- `DefaultCodingSessionManager`
- `FileSessionEventStore`
- `HeadlessCodingSessionRuntime`
- `SessionProcessRegistry`

## 1. `CodingSession` 不是普通聊天 session

如果从 Java API 看，最核心的对象仍然是：

- `ai4j-coding/.../CodingSession.java`

但它承担的职责明显比普通 `AgentSession` 更重。

它除了 `run(...) / runStream(...)`，还直接提供：

- `snapshot()`
- `exportState()`
- `restore(...)`
- `compact()`
- `listProcesses()`
- `processStatus(...)`
- `processLogs(...)`
- `writeProcess(...)`
- `stopProcess(...)`
- `delegate(...)`

这说明 Coding Agent 的 session 不是“模型上下文容器”，而是一个带：

- memory
- 进程状态
- compact 状态
- loop 决策
- delegation 入口

的工作会话对象。

## 2. 一次 `run(...)` 真实不一定只跑一轮模型调用

`CodingSession.run(...)` 最终不是直接调用底层 `AgentSession.run()`，而是进入：

- `CodingAgentLoopController`

这条 outer loop 会：

1. 跑一轮 agent turn
2. 聚合 tool calls / tool results
3. 检查是否需要 auto-continue
4. 遇到问题时决定 stop reason
5. 必要时生成 continuation prompt 再继续

所以 Coding Agent 的“一个用户请求”与“一个模型回合”并不是一一对应的。

这也是为什么 coding 场景需要单独的 session runtime，而不是只复用通用问答 loop。

## 3. outer loop 到底按什么规则继续或停止

`CodingAgentLoopController` 当前会显式判断几类信号：

- approval rejected
- tool error
- explicit question
- completion-like output
- continuation-like output
- max auto follow-ups
- max total turns

对应的停止原因是显式建模的：

- `COMPLETED`
- `NEEDS_USER_INPUT`
- `BLOCKED_BY_APPROVAL`
- `BLOCKED_BY_TOOL_ERROR`
- `MAX_AUTO_FOLLOWUPS_REACHED`
- `MAX_TOTAL_TURNS_REACHED`
- `INTERRUPTED`

这点很重要，因为 Coding Agent 的“结束”不是只有一种语义。  
一个任务停下来，可能是因为：

- 真做完了
- 需要你回答问题
- 被审批挡住了
- 工具出错了
- 自动推进预算耗尽了

宿主想把体验做好，就必须理解这些 stop reason，而不是只看最后一句输出文本。

## 4. continuation prompt 为什么是隐藏的

当前实现里，outer loop 的后续推进不是把“下一步继续做”写成新的用户消息，而是把 continuation prompt 作为隐藏指令继续跑下一轮。

这有两个后果：

- 会话历史不会被大量“系统自我续写”污染
- 但 runtime 仍然能在内部继续推进工作

所以 session runtime 的职责，不只是保存聊天记录，还要维护“哪些回合是用户发起的，哪些回合是 runtime 自己续跑的”这一层语义。

## 5. `snapshot()` 和 `exportState()` 为什么要分开

这两个接口名字很像，但语义并不一样。

`snapshot()` 产出的是：

- `CodingSessionSnapshot`

它更偏展示与诊断，包含：

- memory item count
- process count
- active/restored process count
- estimated context tokens
- last compact mode / tokens before / tokens after
- auto compact failure count
- process info 列表

`exportState()` 产出的是：

- `CodingSessionState`

它更偏恢复对象，包含：

- `MemorySnapshot`
- `CodingSessionCheckpoint`
- `CodingSessionCompactResult`
- process snapshots
- auto-compact circuit breaker 状态

所以最稳的理解是：

- `snapshot()` 给人看
- `exportState()` 给系统恢复

## 6. save / resume / fork 到底由谁负责

真正做会话生命周期管理的不是 `CodingSession` 自己，而是：

- `DefaultCodingSessionManager`

它负责：

- `create(...)`
- `resume(...)`
- `fork(...)`
- `save(...)`
- `load(...)`
- `list()`
- `appendEvent(...)`
- `listEvents(...)`

这说明 AI4J 把“会话执行”和“会话持久化”拆成了两层：

- `CodingSession` 负责跑
- `CodingSessionManager` 负责存、取、分支、记账

这个分层非常重要，因为 CLI、TUI、ACP 都要共享这套生命周期语义。

## 7. `resume` 和 `fork` 不是简单复制对象

`DefaultCodingSessionManager` 当前在恢复和分支时还会做几件关键事情：

- 校验当前 workspace 是否和已存 session 匹配
- 恢复 `CodingSessionState`
- 保留 `rootSessionId / parentSessionId`
- 追加 `SESSION_RESUMED / SESSION_FORKED` 事件

尤其 workspace 校验这一条很关键。  
它说明 session 不是纯 prompt 历史，而是和具体工作区绑定的。

否则把一个仓库里的 session 直接拿到另一个仓库继续跑，很多文件语义都会失真。

## 8. 事件账本不是附属功能，而是 session runtime 的一部分

`FileSessionEventStore` 会把每个 session 的事件按：

- `<sessionId>.jsonl`

写到磁盘。

这层设计很有价值，因为它让会话拥有了独立于 memory 之外的事件视角。

从 `HeadlessCodingSessionRuntime` 和 `DefaultCodingSessionManager` 可以看到，当前会写入的事件至少包括：

- `SESSION_CREATED`
- `SESSION_RESUMED`
- `SESSION_FORKED`
- `SESSION_SAVED`
- `USER_MESSAGE`
- `ASSISTANT_MESSAGE`
- `TOOL_CALL`
- `TOOL_RESULT`
- `AUTO_CONTINUE`
- `AUTO_STOP`
- `BLOCKED`
- `COMPACT`
- `ERROR`

这说明 session runtime 记录的不是“最终摘要”，而是过程账本。

## 9. Headless/ACP 路径为什么要单独做 runtime

CLI/TUI 可以靠终端直接消费流式事件，但 ACP / headless 不行。  
所以 AI4J 单独提供了：

- `HeadlessCodingSessionRuntime`

它会把一次 `runPrompt(...)` 拆成：

1. 生成 `turnId`
2. 记录 `USER_MESSAGE`
3. 流式消费 agent events
4. 转写成 tool / assistant / reasoning / error / loop decision / compact 事件
5. 必要时自动持久化 session
6. 返回 `PromptResult`

所以 headless runtime 的价值不是“没有 UI”，而是把 coding session 变成结构化事件流。

## 10. 流式输出在这里为什么不能只理解成文本 delta

`HeadlessAgentListener` 实际处理的不只是 assistant text，还包括：

- reasoning
- tool call
- tool result
- handoff event
- team event
- team message event
- final output
- error

这意味着 coding session 的流式协议，本质上不是单一文本流，而是一个混合事件流。  
如果宿主只按“token 输出”理解它，就会丢掉很多关键信息：

- 某条工具调用什么时候发生
- 某个 auto-continue 为什么触发
- 某轮为什么 blocked

## 11. 后台进程为什么属于 session runtime，而不是 bash 工具私有状态

`BashToolExecutor` 背后用的是：

- `SessionProcessRegistry`

而 `CodingSession` 直接暴露了 process 管理方法。

这说明长期运行进程不是一次工具调用的瞬时副作用，而是当前 session 状态的一部分。  
也正因为如此：

- `snapshot()` 会统计 process 数量
- `exportState()` 会导出 process snapshots
- `restore(...)` 会恢复 process registry snapshots

所以进程面在 coding 场景里是和 memory 一样重要的 session 组成部分。

## 12. auto-compact 对 session runtime 的意义是什么

`CodingSession` 在每轮结束后会尝试：

- tool-result micro compact
- session compact

并记录：

- 最近自动 compact 结果
- 最近自动 compact 错误
- 连续失败次数
- circuit breaker 是否打开

然后这些状态又会被：

- `snapshot()` 暴露给宿主
- `HeadlessCodingSessionRuntime` 转成 `COMPACT / ERROR` 事件
- `exportState()` 持久化

这说明 compact 不是孤立维护任务，而是 session runtime 的核心稳定性机制。

## 13. 最容易踩坑的 5 个点

### 13.1 把一个用户 prompt 当成一轮模型调用

在 Coding Agent 里，一个用户 prompt 可能对应多轮 outer loop。

### 13.2 只保存 memory，不保存 session state

这样会丢失 process snapshots、compact 诊断和 breaker 状态。

### 13.3 用错 `snapshot()` 和 `exportState()`

前者适合展示，后者才适合恢复。

### 13.4 忽略 workspace 绑定

当前恢复和分支都带 workspace 语义，session 不是跨仓库随意漂移的纯文本历史。

### 13.5 只把流式输出当 assistant 文本

coding runtime 里的流式事件远比文本增量丰富。

## 14. 这页最该记住的结论

AI4J 当前的 session runtime，不是“聊天会话 + 保存按钮”，而是一套工作会话系统：

- 用 `CodingSession` 承载 memory、进程、compact 与 delegation
- 用 `CodingAgentLoopController` 负责多回合推进与 stop reason
- 用 `DefaultCodingSessionManager` 管生命周期与持久化
- 用 `FileSessionEventStore` 保留事件账本
- 用 `HeadlessCodingSessionRuntime` 把整个过程转成宿主可消费的结构化事件流

这正是 Coding Agent 能做长任务、可恢复任务、本地交付任务的基础。

## 15. 继续阅读

1. [Compact 与 Checkpoint 机制](/docs/coding-agent/compact-and-checkpoint)
2. [Tools 与审批机制](/docs/coding-agent/tools-and-approvals)
3. [Runtime 架构](/docs/coding-agent/runtime-architecture)
