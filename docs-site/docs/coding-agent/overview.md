---
sidebar_position: 1
---

# Coding Agent 总览

`Coding Agent` 对应的是 `ai4j-coding/` 和 `ai4j-cli/` 这条产品化路径。

如果 `Agent` 解决的是“通用智能体 runtime 怎么做”，那么 `Coding Agent` 解决的就是“如何把这套能力变成一个能在本地代码仓里稳定交付任务的产品入口”。

## 1. 三分钟理解 Coding Agent

先记住这五句话：

- `Coding Agent` 不是通用 Agent 的别名
- 它建立在 `Core SDK + Agent` 之上，但目标是本地代码仓交付
- 它不仅有 runtime，还有 workspace-aware tools、session、approval、CLI / TUI / ACP 宿主
- `ai4j-coding` 是 runtime，`ai4j-cli` 是直接给人或宿主使用的产品壳层
- 它更像“本地 coding assistant 交付层”，而不是普通业务 Agent 框架

一句话定义可以直接说成：

> 一个建立在 `ai4j` 和 `ai4j-agent` 之上的、本地代码仓交付型 coding runtime 与宿主产品层。

## 2. 它到底解决什么问题

当你把 Agent 用在真实代码仓时，问题会马上从“模型会不会调工具”升级成：

- 工作区里能读什么、改什么、执行什么
- 什么时候应该请求审批
- 会话怎么保存、恢复、分叉、回放
- 长上下文怎么 compact、checkpoint、继续跑
- 终端怎么交互、宿主怎么接协议、进程怎么管理

这些都不是通用 `Agent` runtime 自动替你解决的，所以才需要 `Coding Agent` 这一层。

## 3. 模块路径和能力组成

模块路径：

- `ai4j-coding/`：coding runtime
- `ai4j-cli/`：CLI / TUI / ACP host

可以把它拆成四个能力面来看：

- runtime：outer loop、task policy、tool orchestration、context compaction
- workspace：文件工具、shell/process、patch、任务上下文
- session：save / resume / fork / history / replay / events / compacts
- host：CLI、TUI、ACP、分发与安装

这也是为什么 `Coding Agent` 不是单一模型能力，而是一整套围绕“仓库交付”组织起来的系统。

## 4. 三种主要入口怎么选

### CLI

适合：

- 一次性任务
- 直接在终端里做持续 REPL
- 先验证 provider / model / workspace 是否打通

### TUI

适合：

- 需要更强的交互体验
- 需要 slash command、palette、状态栏、回放等终端 UI
- 希望在终端里长期使用 coding agent

### ACP

适合：

- IDE 插件
- 桌面应用
- 自定义宿主

它的重点不是渲染终端文本，而是传递结构化会话事件和权限交互。

## 5. 和相邻模块的边界

### 5.1 和 Agent 的边界

`Agent` 是通用 runtime。

`Coding Agent` 则是在这个 runtime 之上加入：

- workspace-aware tools
- approvals
- session/process 管理
- CLI / TUI / ACP

如果你在做服务端智能体或业务流程智能体，先看 `Agent`。如果你在做本地代码仓任务交付，先看 `Coding Agent`。

### 5.2 和 Core SDK 的边界

`Core SDK` 负责模型、工具、`Skill`、`MCP`、memory、RAG 等基座能力。

`Coding Agent` 负责把其中一部分能力产品化到“本地仓库交互”场景里。

所以这里的 `Skill`、`MCP`、tools 都不是孤立存在的，而是被重新组织到了 coding workflow 中。

### 5.3 和 Flowgram 的边界

`Coding Agent` 面向“读代码、改文件、跑命令、持续会话”。

`Flowgram` 面向“节点图、后端任务 API、可视化工作流平台”。

一个偏本地仓库任务交互，一个偏流程平台后端，不是同一条线。

## 6. 你会在这一章学到什么

这一组文档主要覆盖：

- 如何最快跑起来
- 如何安装和发布
- 如何理解 architecture / session / compact
- 如何管理配置、审批、Skills、MCP
- 如何在 CLI / TUI / ACP 三种宿主方式下接入

## 7. 推荐阅读顺序

### 直接使用

1. [Why Coding Agent](/docs/coding-agent/why-coding-agent)
2. [Coding Agent Quickstart](/docs/coding-agent/quickstart)
3. [Install and Release](/docs/coding-agent/install-and-release)
4. [CLI / TUI](/docs/coding-agent/cli-and-tui)
5. [Architecture](/docs/coding-agent/architecture)
6. [Session Runtime](/docs/coding-agent/session-runtime)
7. [Compact and Checkpoint](/docs/coding-agent/compact-and-checkpoint)
8. [Configuration](/docs/coding-agent/configuration)
9. [Tools and Approvals](/docs/coding-agent/tools-and-approvals)
10. [Skills](/docs/coding-agent/skills)
11. [MCP and ACP](/docs/coding-agent/mcp-and-acp)
12. [Command Reference](/docs/coding-agent/command-reference)

### 做扩展

1. [Architecture](/docs/coding-agent/architecture)
2. [Session Runtime](/docs/coding-agent/session-runtime)
3. [Compact and Checkpoint](/docs/coding-agent/compact-and-checkpoint)
4. [Configuration](/docs/coding-agent/configuration)
5. [Tools and Approvals](/docs/coding-agent/tools-and-approvals)
6. [Skills](/docs/coding-agent/skills)
7. [MCP and ACP](/docs/coding-agent/mcp-and-acp)
8. [Command Reference](/docs/coding-agent/command-reference)

## 8. 最该先记住的边界

为了避免概念混乱，先记住：

- `Coding Agent` 解决的是本地代码仓交付与交互
- `BaseAgentRuntime` 保持底层单轮 tool-loop 语义，任务级自动继续由 `ai4j-coding` 的 outer loop 负责
- `MCP` 是它可接入的一类工具来源，但不是全部
- `Skill` 不是工具协议，而是供模型按任务读取的说明/模板
- `ACP` 不是模型协议，而是宿主集成协议
- `TUI` 不是另一个 Agent，而是另一个交互壳

如果你是第一次进入这一章，下一页建议先看 [Why Coding Agent](/docs/coding-agent/why-coding-agent)。
