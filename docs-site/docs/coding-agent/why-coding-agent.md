# Why Coding Agent

`Coding Agent` 这页最容易写成一句空话：  
“它是面向代码任务的 Agent。”

这句话没错，但技术上几乎没有信息量。  
从 AI4J 源码看，`Coding Agent` 之所以值得单独成章，不是因为它换了一个名称，而是因为它在通用 `Agent` 之上又补了 5 层本地交付语义：

- workspace 语义
- coding tools 与进程治理
- 多回合 coding loop
- session / compact / restore
- CLI / TUI / ACP 宿主集成

这几层叠在一起，才构成它和普通 Agent 的真实边界。

## 1. 先看最关键的装配入口

如果你只想找“Coding Agent 和普通 Agent 到底从哪里分叉”，最应该先看：

- `ai4j-coding/.../CodingAgentBuilder.java`

这个 builder 并不是简单包一下 `AgentBuilder`。它实际会在构建阶段额外完成：

- `WorkspaceContext` 解析
- `CodingSkillDiscovery.enrich(...)`
- 内建 coding tool registry 装配
- 内建 tool executor 路由
- `DefaultCodingRuntime` 装配
- sub-agent / handoff 合并
- workspace system prompt 注入

最后它才把这些结果下沉到：

```java
new AgentBuilder().build()
```

也就是说，Coding Agent 不是“调用 AgentBuilder 时顺手开几个开关”，而是：

**先把本地代码仓运行环境装好，再把通用 Agent 嵌进去。**

## 2. 它解决的不是“多一个 shell 工具”，而是 workspace 语义

普通 Agent 一般只知道：

- 模型
- memory
- tools
- runtime

而 `Coding Agent` 先引入了：

- `WorkspaceContext`

这个对象当前至少承载：

- `rootPath`
- `excludedPaths`
- `allowOutsideWorkspace`
- `skillDirectories`
- `allowedReadRoots`
- `availableSkills`

它不只是“给工具一个 cwd”，而是在定义：

- 哪些路径被视为工作区
- 哪些路径默认不可越界
- 哪些目录只能读不能写
- skill 发现结果怎样进入 prompt

这就是为什么 coding 场景不能只靠通用 tool calling 糊过去。  
代码仓任务首先是一个 **工作区边界问题**。

## 3. prompt 也不是普通 Agent 那种“只塞系统提示”

`CodingContextPromptAssembler.mergeSystemPrompt(...)` 会把 workspace 信息真正拼进系统提示里，包括：

- workspace root
- workspace description
- built-in tools 列表
- bash/read_file/write_file/apply_patch 的调用规则
- shell 使用指导
- workspace 外部访问限制
- 可用 skills 摘要

这说明 Coding Agent 的 prompt 不是纯角色设定，而是一个 **宿主环境声明**。

也正因为有这层注入，模型在 coding 场景里看到的不是抽象“工具能力”，而是：

- 当前在哪个仓库里
- 读写规则是什么
- 工具调用负载该怎样构造

这和普通 Agent 的系统提示职责明显不同。

## 4. 它的 built-in tools 也不是“多暴露几个函数”那么简单

`CodingToolRegistryFactory.createBuiltInRegistry()` 当前直接暴露：

- `bash`
- `read_file`
- `write_file`
- `apply_patch`

但真正重要的不是工具名，而是 `CodingAgentBuilder.createBuiltInToolExecutor(...)` 里的执行路由。

它会把这些工具分别接到：

- `ReadFileToolExecutor`
- `WriteFileToolExecutor`
- `ApplyPatchToolExecutor`
- `BashToolExecutor`

并通过 `RoutingToolExecutor` 路由到具体实现。

也就是说，Coding Agent 里的工具系统不是“模型调用一个函数，Java 执行一下”这么轻，而是已经有了：

- workspace-aware 文件执行器
- patch 专用执行器
- bash 进程生命周期执行器

这正是本地代码仓任务与通用 function calling 的本质区别。

## 5. `bash` 在这里为什么是系统能力，不是普通工具

`BashToolExecutor` 不是一次性命令玩具，它背后连着：

- `SessionProcessRegistry`
- `CodingSession`

而 `CodingSession` 本身又支持：

- `listProcesses()`
- `processStatus(...)`
- `processLogs(...)`
- `writeProcess(...)`
- `stopProcess(...)`

这说明在 Coding Agent 语义里，shell 不是“一次 exec 就结束”的调用，而是可持续管理的会话进程面。

这点极其关键，因为本地开发任务经常需要：

- 启动服务
- 看日志
- 持续写 stdin
- 停后台进程

普通 Agent 的工具调用模型通常不会把这些做成一等概念。

## 6. 它为什么需要专用 `CodingSession`

如果只有一个 `AgentSession`，其实不够支撑 coding 场景。

`CodingSession` 额外提供了：

- `snapshot()`
- `exportState()`
- `restore(...)`
- `compact()`
- process snapshots
- auto-compact 状态
- checkpoint
- loop decision 记录

这说明 coding 场景关心的不只是“模型回了什么”，还关心：

- 当前工作进展如何保存
- 进程状态能不能恢复
- 上下文过长时如何压缩
- 下一轮为什么继续或停止

所以 `CodingSession` 是一个 **可恢复的工作会话容器**，不是普通聊天 session。

## 7. 为什么它必须有 compaction，而不是把 max tokens 调大

`CodingSession` 里直接内置了：

- `CodingSessionCompactor`
- `CodingToolResultMicroCompactor`
- 自动 compact circuit breaker

从实现上看，它既支持：

- 手动 compact
- 自动 compact
- tool result micro-compact
- 连续失败后的自动熔断

这说明 AI4J 对 coding 场景的判断很明确：

**长任务一定会遇到上下文膨胀，不能只靠模型窗口硬扛。**

普通 Agent 也会遇到上下文问题，但 coding 场景更严重，因为它会堆积：

- 命令输出
- patch 结果
- 文件差异
- 中间总结
- 进程状态

所以 compact 在这里不是锦上添花，而是基础生存能力。

## 8. 它为什么需要专用 loop controller

`CodingAgentLoopController` 是另一个核心分叉点。

它不是简单地“调一次 Agent.run()”，而是会按策略循环：

- 聚合 tool calls 和 tool results
- 判断是否需要 auto-continue
- 识别 approval reject / tool error / explicit question
- 生成 continuation prompt
- 统计总 turns 和 auto-follow-ups

停止原因也被显式建模为：

- `COMPLETED`
- `NEEDS_USER_INPUT`
- `BLOCKED_BY_APPROVAL`
- `BLOCKED_BY_TOOL_ERROR`
- `MAX_AUTO_FOLLOWUPS_REACHED`
- `MAX_TOTAL_TURNS_REACHED`

这很重要，因为 coding 任务往往不是“一轮问答结束”，而是：

- 做一步
- 看结果
- 再决定下一步

所以 Coding Agent 的 loop 不是聊天 UX 的附属逻辑，而是工作流控制器。

## 9. 它的 delegation 也不是普通 sub-agent 套壳

`DefaultCodingRuntime.delegate(...)` 做的事情，明显比普通 handoff 更重：

- 创建 `CodingTask`
- 持久化 task progress
- 生成 parent/child `CodingSessionLink`
- 根据 `CodingSessionMode` 决定 seed state
- 创建 child coding session
- 按 definition 解析 allowed tools
- 支持 background task

配套的 `CodingToolPolicyResolver` 还会根据 agent definition：

- 过滤 tool registry
- 包一层 executor 白名单检查

这意味着 coding delegation 的重点不是“把一句话扔给另一个 agent”，而是：

**在可控工具面、可追踪任务状态、可选会话继承模式下派生子工作。**

这比通用 handoff 更贴近真实工程协作。

## 10. 为什么 approvals 被放在宿主层，而不是硬写进 runtime

这个点很值得说透。

CLI 里的 `CliToolApprovalDecorator` 会在执行器外面加一层审批装饰，当前默认行为包括：

- `apply_patch` 总是需要审批（非 auto 模式）
- `bash` 的 `exec/start/stop/write` 等动作可要求审批
- 审批拒绝会返回 `[approval-rejected]` 语义

然后 `CodingAgentLoopController` 再把这种结果识别成：

- `BLOCKED_BY_APPROVAL`

这个分层非常好，因为它说明：

- “是否审批” 是宿主交互策略
- “审批被拒后 loop 如何停” 是 runtime 策略

也就是说，AI4J 没把 approval 和 tool 执行硬耦合在一起，而是把它拆成：

- tool executor decorator
- loop-level stop reason

这让 CLI、TUI、ACP 可以共用 runtime，但各自实现不同审批体验。

## 11. 它为什么又需要 CLI 侧的 session event store

`FileSessionEventStore` 当前会把会话事件写成：

- 按 sessionId 分文件的 `jsonl`

这说明 CLI/TUI 这一层不是“终端打印完就算了”，而是把 coding session 当成可回放、可列举、可恢复的事件流来处理。

对代码仓任务来说，这种 event log 很有价值，因为你经常需要：

- 回看某轮是怎么做的
- 重放关键操作
- 追踪 approval / tool call / 输出链

普通 Agent 如果只做短会话聊天，通常不需要把这层做这么重。

## 12. skill 在这里为什么也是一等能力

`CodingSkillDiscovery.enrich(...)` 会在构建阶段做两件事：

- 发现默认 skills
- 把 `allowedReadRoots` 和 `availableSkills` 写回 `WorkspaceContext`

然后 `CodingContextPromptAssembler` 再把 skill 摘要并入系统提示。

这意味着 Coding Agent 对 skill 的用法，不只是“可选文档附件”，而是：

- 影响可读根目录
- 影响系统提示中的能力面

这比普通 Agent 里“临时塞一段说明”更体系化。

## 13. 所以它为什么值得单独叫 `Coding Agent`

如果把上面这些拼起来，你会发现它已经不是“Agent + shell”：

- 它有 workspace 边界
- 它有专用文件/patch/bash 执行器
- 它有进程注册与 IO 管理
- 它有多回合 coding loop
- 它有 session snapshot / restore / compact
- 它有 delegation task/link/runtime
- 它有宿主审批与事件存储
- 它有 skill 发现和工作区 prompt 注入

这实际上已经是一套 **本地代码交付 runtime**，而不只是一个模型调用包装层。

## 14. 什么时候该用它，什么时候不该

适合用 `Coding Agent` 的场景：

- 本地仓库改动
- review / debug / patch / refactor
- 需要 shell、文件、patch 和长任务会话的交互
- 需要审批、恢复、压缩、派生子任务

不必强行上 `Coding Agent` 的场景：

- 单轮问答
- 普通业务 Agent 编排
- 不涉及本地工作区和代码资产的任务

这不是因为 Coding Agent 更“高级”，而是因为它的运行面明显更重。

## 15. 这页最该记住的结论

AI4J 当前的 `Coding Agent` 之所以不是通用 Agent 的别名，是因为它在通用 Agent 之上增加了一整套本地代码交付语义：

- 工作区约束
- 专用 coding tools
- 持续进程面
- 多回合 loop
- session compaction / restore
- 任务化 delegation
- 宿主审批与事件账本

把它理解成“面向代码仓任务的 runtime 产品层”，比理解成“一个更会写代码的 Agent”更准确。

## 16. 推荐阅读顺序

1. [Runtime Architecture](/docs/coding-agent/runtime-architecture)
2. [Session Runtime](/docs/coding-agent/session-runtime)
3. [Tools and Approvals](/docs/coding-agent/tools-and-approvals)
4. [Compact and Checkpoint](/docs/coding-agent/compact-and-checkpoint)
5. [CLI and TUI](/docs/coding-agent/cli-and-tui)
6. [MCP and ACP](/docs/coding-agent/mcp-and-acp)

下一页如果要继续沿着这条主线往下读，建议直接看 [Runtime Architecture](/docs/coding-agent/runtime-architecture)。
