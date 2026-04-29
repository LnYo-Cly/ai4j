---
sidebar_position: 1
---

# Coding Agent 总览

如果只用一句话概括，`Coding Agent` 不是“给通用 Agent 多加几个工具”，而是把模型、工具、workspace、session、审批和宿主交互压成一条可交付的本地开发工作链。

所以它对应的不是单个类，而是一组模块协作：

- `ai4j-coding/` 负责 coding runtime
- `ai4j-cli/` 负责 CLI、TUI、ACP 这些真正面向人和宿主的入口

---

## 1. 先看主执行链

当前最值得记住的入口链路是：

```text
CodeCommand.run(...) / AcpJsonRpcServer.createSession(...)
  -> DefaultCodingCliAgentFactory.prepare(...)
  -> CodingAgentBuilder.build()
  -> new CodingSession(...)
  -> HeadlessCodingSessionRuntime or CodingCliSessionRunner
  -> session.run(...) / runStream(...)
  -> tool calls / approvals / MCP / compaction / delegation
  -> save / resume / fork / events
```

这条链表达的是：

- `Coding Agent` 不是一个静态对象
- 它总是在某个 host 入口下被装配
- 装配时就已经决定了 workspace、可见 tools、skills、MCP、审批策略、session store

所以理解 `Coding Agent`，不能只看 `CodingAgent` 这个类本身，必须把“构建”和“宿主运行”一起看。

---

## 2. 它到底解决了通用 Agent 没解决的什么

通用 `Agent` 运行到真实代码仓时，问题很快就不再是“模型会不会调工具”，而是：

- 哪些路径允许读，哪些路径允许写
- shell 命令是前台执行、后台进程还是要审批
- session 如何保存、恢复、分叉、回放
- 长任务如何自动继续，何时 compact，何时停止
- 多个子代理如何继承上下文，又如何隔离 memory 和工具边界
- CLI、TUI、ACP 三种宿主怎样共享同一套 runtime 语义

这些问题在 AI4J 里分别落在：

- `WorkspaceContext`
- built-in tool executors
- `CodingSession`
- loop / compact / checkpoint 体系
- `DefaultCodingRuntime`
- `DefaultCodingSessionManager`
- `CodeCommand` / `AcpJsonRpcServer`

这就是为什么 `Coding Agent` 需要独立成一条产品线，而不是简单复用 `ai4j-agent` 的默认行为。

---

## 3. `ai4j-coding` 和 `ai4j-cli` 到底怎么分工

可以先用最短的两句话记住：

- `ai4j-coding` 决定“任务怎么跑”
- `ai4j-cli` 决定“人或宿主怎么用”

更展开一点：

### `ai4j-coding`

主要负责：

- workspace 语义
- built-in coding tools
- session 运行容器
- outer loop / auto-continue
- compact / checkpoint
- delegation / child session

### `ai4j-cli`

主要负责：

- provider / model / workspace 配置入口
- CLI / TUI 交互外壳
- ACP JSON-RPC 宿主协议
- session store 与 event store
- MCP runtime 装配
- approval UI / approval callback

这也是为什么你会看到：

- `CodingAgentBuilder` 在 `ai4j-coding`
- `CodeCommand`、`AcpJsonRpcServer`、`CliToolApprovalDecorator`、`CliMcpRuntimeManager` 在 `ai4j-cli`

---

## 4. “它是一个本地代码仓交付系统”具体体现在哪

当前实现里，至少有五个“只有 coding 场景才刚需”的特征。

### 4.1 workspace-aware prompt 不是可选装饰

`CodingAgentBuilder.build()` 会先把 `WorkspaceContext` 交给 `CodingSkillDiscovery.enrich(...)`，然后再通过 `CodingContextPromptAssembler.mergeSystemPrompt(...)` 把 workspace 信息、tool 规则、skill 摘要并进系统提示。

这意味着 workspace 不是旁路配置，而是 agent 身份的一部分。

### 4.2 built-in tools 不是普通 function list

`bash`、`read_file`、`write_file`、`apply_patch` 的价值不只是“能调”，而是它们带着 coding-native 的路径、补丁、进程和审批语义。

### 4.3 session 是长期工作容器

`CodingSession` 不是只保存聊天历史。它还承担：

- 进程注册
- compact 结果
- loop decision
- state export / restore
- child session 关联

### 4.4 runtime 不只管当前 prompt

`DefaultCodingRuntime` 负责的是 delegated work、child session、task tracking、session link，不是简单的“给本轮多传一个参数”。

### 4.5 host 不只是 UI 皮肤

CLI、TUI、ACP 共享核心 runtime，但：

- 交互方式不同
- 审批通道不同
- session 事件的消费方式不同

所以 host 层是真正的产品层，不是纯渲染层。

---

## 5. 三种入口真正差别在哪里

### CLI

入口通常从 `CodeCommand.run(...)` 开始。

适合：

- one-shot 执行
- 持续 REPL
- 先确认 provider、workspace、session、tool routing 是否打通

### TUI

仍然从 `CodeCommand.run(...)` 进入，但可能分叉到 JLINE 或 legacy backend。

适合：

- 需要更强交互密度
- 需要 slash commands、palette、状态视图、team board
- 需要长期终端工作流

### ACP

入口在 `AcpJsonRpcServer`。

适合：

- IDE 插件
- 桌面应用
- 自定义宿主

重点不是渲染文本，而是：

- 结构化 session 事件
- 宿主侧 permission request
- 宿主动态注入 MCP、prompt、mode、config

一句话记忆：

- CLI/TUI 偏人类终端使用
- ACP 偏外部程序托管

---

## 6. 构建一个 Coding Agent 时到底被装进去了什么

`DefaultCodingCliAgentFactory.buildAgent(...)` 是理解“最后拿到的 agent 到底包含什么”的最好入口。

它会装进去的关键对象包括：

- `WorkspaceContext`
- `CodingAgentDefinitionRegistry`
- `CodingAgentOptions`
- `AgentOptions`
- provider 对应的 `AgentModelClient`
- 可选的 `CliMcpRuntimeManager`

接下来 `CodingAgentBuilder.build()` 会继续做这些事：

1. enrich workspace skill 信息
2. 准备 built-in registry / executor
3. 合并外部 tools，例如 MCP
4. 合并 subagent tools
5. 视配置决定是否把 workspace prompt prepend 到 system prompt
6. 用通用 `AgentBuilder` 造出底层 `Agent`
7. 最后再包成 `CodingAgent`

所以 `CodingAgent` 不是替代 `Agent`，而是“在 `Agent` 外面多压了一层 coding-specific 装配和运行语义”。

---

## 7. session 为什么是这个产品线的核心

很多人第一次看会把 `Coding Agent` 理解成“带文件工具的聊天机器人”，这会低估 session 层的重要性。

在当前实现里，真正让它像“工作台”而不是“对话框”的，是 session 体系：

- `DefaultCodingSessionManager.create(...)`
- `resume(...)`
- `fork(...)`
- `save(...)`
- `list(...)`

以及 headless 路径里的：

- `HeadlessCodingSessionRuntime.runPrompt(...)`

这些类一起定义了：

- 一个 prompt 不一定只有一轮模型调用
- 一次会话要留下事件账本
- 恢复和分叉都必须带着 state
- 任务级持续运行不能只靠聊天历史回放

如果忽略这层，你会误以为 CLI、TUI、ACP 只是不同皮肤；但实际上它们共享的是同一套 session lifecycle。

---

## 8. 它和相邻几条产品线的边界

### 和 `Agent` 的边界

`Agent` 是通用模型调用和 tool loop 基座。

`Coding Agent` 关心的是：

- 本地代码仓
- 文件与进程
- 长任务生命周期
- 宿主交互与审批

如果你做业务流程型智能体，优先看 `Agent`。

如果你做本地代码仓交付型系统，优先看 `Coding Agent`。

### 和 `Core SDK` 的边界

`Core SDK` 提供的是：

- model client
- tools 抽象
- skills
- MCP transport / client
- memory / RAG 等基础能力

`Coding Agent` 则把其中一部分重新组织成“本地开发工作流”。

### 和 `Flowgram` 的边界

`Coding Agent` 偏：

- 代码仓交互
- 终端或宿主会话
- 文件、命令、审批、session

`Flowgram` 偏：

- 可视化工作流平台
- 后端任务 API
- 流程编排

二者都能用到模型，但目标系统完全不同。

---

## 9. 这一章最适合哪两类读者

### 直接使用者

你最关心的是：

- 怎样跑起来
- 怎样选 CLI / TUI / ACP
- 怎样配置 provider、MCP、skills
- 怎样理解 approvals、session、compact

### 扩展开发者

你最关心的是：

- 改 prompt 应该进哪层
- 改 tool policy 应该进哪层
- 想加 MCP、subagent、approval、session 能力时从哪入手
- 哪些改动会污染 `ai4j-agent` 的通用边界

如果你属于第二类，这一章建议从架构页开始，不要只看 quickstart。

---

## 10. 最容易混淆的 6 个边界

- `Coding Agent` 不是 `Agent` 的同义词，它是更靠近产品交付的 runtime 组合
- `Skill` 不是 tool protocol，而是按需读取的工作流说明
- `MCP` 只是工具来源之一，不等于全部工具系统
- `ACP` 不是模型协议，而是宿主接入协议
- `TUI` 不是另一个 agent，而是另一个 host runtime
- `session` 不是普通聊天历史，而是可保存、可恢复、可分叉的工作状态容器

---

## 11. 推荐阅读顺序

### 如果你要直接使用

1. [Why Coding Agent](/docs/coding-agent/why-coding-agent)
2. [Coding Agent Quickstart](/docs/coding-agent/quickstart)
3. [CLI / TUI](/docs/coding-agent/cli-and-tui)
4. [Session Runtime](/docs/coding-agent/session-runtime)
5. [Tools 与审批机制](/docs/coding-agent/tools-and-approvals)
6. [Skills 使用与组织](/docs/coding-agent/skills)
7. [MCP 对接](/docs/coding-agent/mcp-integration)
8. [Command Reference](/docs/coding-agent/command-reference)

### 如果你要做扩展

1. [Coding Agent Architecture](/docs/coding-agent/architecture)
2. [Runtime Architecture](/docs/coding-agent/runtime-architecture)
3. [Session Runtime](/docs/coding-agent/session-runtime)
4. [Tools 与审批机制](/docs/coding-agent/tools-and-approvals)
5. [MCP 与 ACP](/docs/coding-agent/mcp-and-acp)
6. [Skills 使用与组织](/docs/coding-agent/skills)
7. [ACP 集成](/docs/coding-agent/acp-integration)

---

## 12. 这页最该记住的结论

- `Coding Agent` 是一条“本地代码仓交付链”，不是单个类名
- `ai4j-coding` 处理任务运行语义，`ai4j-cli` 处理真正的产品宿主入口
- workspace、tools、session、approval、MCP、skills 都是在装配期一起决定的
- CLI、TUI、ACP 共享核心 runtime，但不共享同一种交互壳
- 如果你只把它理解成“能调 bash 的 Agent”，会错过它最关键的 session 和 host 设计

下一页建议先看 [Why Coding Agent](/docs/coding-agent/why-coding-agent)。
