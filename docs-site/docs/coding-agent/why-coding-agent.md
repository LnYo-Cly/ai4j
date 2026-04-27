# Why Coding Agent

`Coding Agent` 不是“通用 Agent 换个名字”，而是把 `Agent runtime` 真正带进本地代码仓任务后的产品化层。

## 1. 它解决的不是普通问答问题

一旦任务落到本地仓库，问题会立刻变成：

- 哪些文件可读、可写、可 patch
- shell 能不能执行，执行到什么程度
- 会话如何 save / resume / fork / replay
- 宿主如何展示 reasoning、tool call、approval、checkpoint
- 外部 MCP 工具如何和内置 workspace tools 共存

这些都不是通用 `Agent` 章节会替你自动解决的。

## 2. 它额外补上的能力面

围绕本地代码仓交付，`Coding Agent` 主要补了五层能力：

- workspace-aware tools
- session runtime
- approvals
- host integration
- MCP / ACP bridge

对应模块边界也很清楚：

- `ai4j-coding/`：coding runtime、workspace、checkpoint、compact
- `ai4j-cli/`：CLI、TUI、ACP、配置、宿主事件流

## 3. 和 Agent 的边界

`Agent` 负责通用 runtime：

- step loop
- tool execution
- memory
- workflow / handoff / team
- trace

`Coding Agent` 在这之上继续增加：

- 本地仓库工具面
- 会话持久化与事件账本
- 宿主侧审批交互
- CLI / TUI / ACP 产品壳

所以它不是“Agent 的案例页”，而是面向 coding workflow 的独立产品层。

## 4. 为什么它值得单独宣传

- 它把通用 agent 能力落成了“可在代码仓里交付任务”的具体系统
- 它明确区分了 runtime、session、tool、host，架构更容易讲清楚
- 它把 approval、checkpoint、compact、replay 这些长任务能力做成了一等概念
- 它能同时服务 CLI、TUI、ACP，不被单一交互壳绑死

## 5. 适合什么场景

- 本地代码仓改动
- review / debug / patch / refactor
- IDE 或桌面端宿主集成
- 长任务会话恢复与分叉

如果你的任务不是“在代码仓里持续交付”，而是普通业务 Agent，回到 `Agent` 章节更合适。

## 6. 推荐阅读顺序

1. [Quickstart](/docs/coding-agent/quickstart)
2. [CLI and TUI](/docs/coding-agent/cli-and-tui)
3. [Architecture](/docs/coding-agent/architecture)
4. [Session Runtime](/docs/coding-agent/session-runtime)
5. [Tools and Approvals](/docs/coding-agent/tools-and-approvals)
6. [MCP and ACP](/docs/coding-agent/mcp-and-acp)

下一页建议直接看 [Coding Agent Quickstart](/docs/coding-agent/quickstart)。
