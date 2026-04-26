# Coding Agent Architecture

`Coding Agent` 的架构重点不是“又一个 Agent builder”，而是如何把通用 runtime 变成一个能在本地代码仓里稳定交付任务的系统。

## 1. 先记住四层

可以先把它压成四层：

- `ai4j-coding`：coding runtime、outer loop、workspace-aware task policy
- session layer：save / resume / fork / replay / compact / checkpoint
- tool layer：文件、shell、patch、审批、MCP 注入
- host layer：CLI、TUI、ACP 这些真正面向用户或宿主的入口

也就是说，`Coding Agent` 不是只靠模型就能成立，它一定包含 runtime、会话、工具面和宿主壳层。

## 2. 模块路径怎么理解

当前主要看两块：

- `ai4j-coding/`：负责 coding runtime 本身
- `ai4j-cli/`：负责 CLI / TUI / ACP 这些宿主入口

通常可以这样理解：

- `ai4j-coding` 解决“任务怎么跑”
- `ai4j-cli` 解决“人或宿主怎么用”

## 3. 这页和相邻页面怎么分工

- `overview`：回答这一章在整个 AI4J 里的位置
- `quickstart`：最快跑起来
- `architecture`：分层和模块关系
- `session-runtime`：长期任务、流式、进程、恢复
- `tools-and-approvals`：工作区工具和审批边界
- `mcp-and-acp`：协议接入和宿主边界

## 4. 推荐阅读顺序

如果你是第一次读这一章，建议按当前 canonical 路径进入：

1. [Why Coding Agent](/docs/coding-agent/why-coding-agent)
2. [Coding Agent Quickstart](/docs/coding-agent/quickstart)
3. [CLI / TUI](/docs/coding-agent/cli-and-tui)
4. [Session Runtime](/docs/coding-agent/session-runtime)
5. [Tools and Approvals](/docs/coding-agent/tools-and-approvals)
6. [MCP and ACP](/docs/coding-agent/mcp-and-acp)
7. [Command Reference](/docs/coding-agent/command-reference)
