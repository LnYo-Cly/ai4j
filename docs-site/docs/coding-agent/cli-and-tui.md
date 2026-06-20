---
sidebar_position: 3
---

# CLI / TUI 使用指南

这一页如果只讲启动命令和快捷键，信息其实不够。  
从当前实现看，`code` 和 `tui` 的差别并不只是“一个全屏、一个非全屏”，而是：

- 它们共享同一套 coding runtime、session manager、MCP runtime、approval 语义
- 但在宿主层使用不同的输入、渲染和交互路径

所以理解 `CLI / TUI` 最稳的方式，是先看它们怎么共用 runtime，再看它们从哪里分叉。

## 1. 两种入口其实都从 `CodeCommand` 开始

当前 CLI 主入口是：

- `ai4j-cli/.../command/CodeCommand.java`

它会先做几件固定事情：

1. 解析 `CodeCommandOptions`
2. 创建 `CodingSessionManager`
3. 决定 interactive backend
4. 调 `agentFactory.prepare(...)`
5. 根据运行形态选择不同 runner

也就是说，`code` / `tui` 在最外层并不是两套完全独立程序，而是同一个 command 体系下的两种宿主形态。

## 2. 它们共享的底层到底有哪些

从 `CodeCommand.run(...)` 和 `DefaultCodingCliAgentFactory` 看，`code` 与 `tui` 当前共享：

- `PreparedCodingAgent`
- `CodingAgent`
- `CliProtocol`
- `CliMcpRuntimeManager`
- `CodingSessionManager`
- approval decorator
- provider / protocol / model 配置逻辑

这意味着下面这些行为，在 `code` 和 `tui` 中原则上是一致的：

- 模型调用语义
- 会话保存与恢复
- 工具执行与审批
- MCP 注入
- outer loop / compact / checkpoint

所以 UI 不是这套系统的核心分叉点，runtime 才是。

## 3. 真正的第一层分叉：interactive backend

`CodeCommand` 当前不会简单地因为 `--ui tui` 就进入某个固定路径。  
它会先判断：

- 是否 `uiMode == TUI`
- 是否不是 one-shot prompt
- terminal 是否是 `JlineTerminalIO`
- `AI4J_TUI_BACKEND` / `ai4j.tui.backend` 是否要求 legacy

然后才决定：

- `InteractiveBackend.JLINE`
- 或 `InteractiveBackend.LEGACY`

这说明当前 `tui` 不是单一实现，而是至少存在两种宿主后端策略。

## 4. JLINE 路径和 legacy 路径到底怎么分

### JLINE 路径

如果满足条件，`CodeCommand` 会创建：

- `SlashCommandController`
- `JlineShellContext`
- `JlineShellTerminalIO`
- `JlineCodeCommandRunner`

这条路径更偏命令壳增强型交互：

- slash command
- palette
- 补全
- shell 输入控制

### legacy 路径

否则会走：

- `CodingCliSessionRunner`

再结合：

- `CodingCliTuiFactory`
- `TuiInteractionState`

完成运行。

所以当前 `CLI/TUI` 文档里如果只写“tui 是更完整的文本 UI”，会漏掉一个很重要的实现事实：

**TUI 运行形态本身还可能走不同交互后端。**

## 5. `DefaultCodingCliTuiFactory` 真正决定了什么

当前 TUI factory 是：

- `DefaultCodingCliTuiFactory`

它会装配：

- `TuiConfig`
- `TuiTheme`
- `TuiSessionView`
- `TuiRuntime`

并根据配置判断：

- 是否使用 `AppendOnlyTuiRuntime`
- 或 `AnsiTuiRuntime`
- 是否开启 alternate screen

这说明 TUI 在 AI4J 里不是“多了几种颜色”，而是一个独立渲染运行时层。

## 6. `code` 和 `tui` 的差别究竟该落在哪一层理解

最稳的心智模型是：

- `CodingAgent`：执行内核
- `CodingSessionManager`：会话生命周期
- `CodingCliSessionRunner` / `JlineCodeCommandRunner`：交互运行器
- `TuiRuntime` / `TuiSessionView`：渲染层

所以：

- `code` 更偏 shell/repl 交互壳
- `tui` 更偏持续状态视图壳

但两者不是“两个不同产品”，而是共用执行内核的两个宿主面。

## 7. provider / protocol / model 在这里怎么落

`DefaultCodingCliAgentFactory.resolveProtocol(...)` 和 `createModelClient(...)` 决定了当前会话底层怎么调模型。

当前协议只显式对用户暴露：

- `chat`
- `responses`

而默认解析规则也不是纯静态文案，而是 factory 真实实现：

- `openai` + 官方 OpenAI host：优先 `responses`
- `openai` + 自定义兼容 host：倾向 `chat`
- `doubao` / `dashscope`：支持 `responses`
- 其它 provider：通常走 `chat`

所以 CLI/TUI 页里讲协议选择时，不能只讲“推荐哪个”，还要讲这是运行时工厂的实际默认逻辑。

## 8. slash command 和 palette 为什么是宿主层，不是 agent 层

当前 slash command 主控是：

- `SlashCommandController`

它负责：

- `/provider`
- `/providers`
- `/cmd`
- `/commands`
- `/palette`
- `/stream`
- `/mcp`
- `/team`

等命令的发现、补全、选择和路由。

`Ctrl+P` palette、`/` 打开命令列表、本地补全，这些能力都属于宿主交互层。  
它们不改变 `CodingAgent` 内核本身，只改变：

- 用户如何把意图送进 runtime

所以 slash command 不是模型的一部分，而是 host-side command surface。

## 9. 为什么 `/stream` 不是简单 UI 开关

`/stream` 虽然在交互层暴露为命令，但它最终影响的是后续请求是否使用流式模式。

所以它的意义不是：

- “界面上逐字打印还是一次性显示”

而是：

- 后续请求怎样与模型 runtime 交互

这类命令正好体现了 CLI/TUI 的特征：

- 入口在宿主
- 影响落在 runtime

## 10. session store 为什么和 UI 模式无关

`CodeCommand.createSessionManager(...)` 会根据：

- `--no-session`
- `--session-dir`

创建：

- `InMemoryCodingSessionStore + InMemorySessionEventStore`
- 或 `FileCodingSessionStore + FileSessionEventStore`

这说明 session 持久化与否，是 runtime/storage 维度，不是 `code` / `tui` 维度。  
也就是说，你不能把：

- “我在 TUI 里工作”

等同于：

- “我的会话一定会被持久化”

这两者是分开的配置轴。

## 11. MCP 启动告警为什么在 CLI/TUI 启动阶段就出现

`CodeCommand` 在拿到 `PreparedCodingAgent` 后，会直接读取：

- `CliMcpRuntimeManager.buildStartupWarnings()`

并把 warning 打给终端。

这说明 MCP 在 CLI/TUI 里不是“等模型第一次调工具才发现有问题”，而是启动时就会暴露运行状态。

这对 UX 很关键，因为用户可以立即知道：

- 哪个 server 缺失
- 哪个 server 连接失败

而不是等任务跑一半才发现工具面不完整。

## 12. `code` one-shot、持续 CLI、TUI 三种形态的真正差别

### one-shot

更像：

- 一次 prompt
- 一次结果
- 适合脚本化或 CI

### 持续 CLI

更像：

- shell 式持续会话
- slash command / session / process / replay

### TUI

更像：

- 持续状态面板
- transcript + palette + status + team board 一体化

从架构上说，它们共享底层执行面，但宿主交互复杂度逐级提高。

## 13. 最容易踩坑的 5 个点

### 13.1 把 `code` 和 `tui` 当成两套不同 runtime

当前它们主要共享同一套 coding/session/model/MCP 语义。

### 13.2 以为 `--ui tui` 一定走同一后端实现

当前还有 JLINE 与 legacy 的后端分叉。

### 13.3 把 slash command 当模型能力

它们属于宿主命令面，不属于 agent 内核。

### 13.4 把 `/stream` 当纯显示选项

它影响的是模型请求模式。

### 13.5 把 session 持久化理解成 UI 自带能力

真正决定它的是 session manager/store 配置。

## 14. 这页最该记住的结论

AI4J 当前的 `CLI / TUI`，不是“一个简版、一个美化版”，而是共享同一套 coding runtime 的不同宿主交互面：

- `CodeCommand` 统一入口
- `DefaultCodingCliAgentFactory` 统一组装模型、workspace、MCP、approval
- `CodingSessionManager` 统一会话生命周期
- JLINE / legacy / TUI runtime 分别负责不同交互与渲染路径

所以在分析行为差异时，先分清：

- 这是执行内核差异
- 还是宿主交互差异

会比只盯着界面表现更有效。

## 15. 继续阅读

1. [Runtime 架构](/docs/coding-agent/runtime-architecture)
2. [会话、流式与进程](/docs/coding-agent/session-runtime)
3. [MCP 与 ACP](/docs/coding-agent/mcp-and-acp)
4. [命令参考](/docs/coding-agent/command-reference)
