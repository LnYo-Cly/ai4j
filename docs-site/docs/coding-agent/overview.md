---
sidebar_position: 1
---

# Coding Agent 总览

`Coding Agent` 是 AI4J 面向本地代码仓任务的运行时和宿主入口。它不是“通用 Agent 加几个文件工具”，而是把 workspace、工具、session、审批、MCP、Skill、CLI/TUI/ACP 组织成一条本地开发工作链。

如果你要在业务系统里嵌入一个通用智能体，先看 [Agent](/docs/agent/overview)。如果你要让 AI 在代码仓里读文件、跑命令、写 patch、保留会话并接受宿主审批，再看本章。

## 一句话定位

Coding Agent 解决的是：

> 在本地代码仓上下文里，让模型通过受控工具完成可追踪、可恢复、可审批的开发任务。

它的核心价值不只是“能调 bash”，而是 workspace、tool policy、session lifecycle 和 host protocol 被放在同一个运行模型里。

## 模块分工

| 模块 | 职责 |
| --- | --- |
| `ai4j-coding` | coding runtime、workspace-aware tools、outer loop、compact、child session |
| `ai4j-cli` | CLI、TUI、ACP host、session store、provider profile、approval UI |
| `ai4j-agent` | 底层 Agent runtime |
| `ai4j` | 模型、Tool、Skill、MCP 等基础能力 |

可以简单记住：

- `ai4j-coding` 决定任务怎么跑。
- `ai4j-cli` 决定人或外部宿主怎么用。

## 适合什么场景

| 场景 | 是否适合 |
| --- | --- |
| 在 Java 项目里嵌入通用业务 Agent | 优先看 `ai4j-agent` |
| 本地代码仓问答、修改、验证 | 适合 |
| 需要 CLI 或 TUI 作为开发入口 | 适合 |
| 需要 IDE / 桌面应用通过结构化协议接入 | 适合，走 ACP |
| 需要文件、命令、patch、审批和会话状态 | 适合 |
| 只是一次模型调用或 Tool call | 不需要 Coding Agent |
| 需要可视化工作流画布 | 看 FlowGram |

## 一次运行包含什么

Coding Agent 装配时会同时决定：

- 当前 workspace 和路径边界。
- 可见 built-in tools，例如读文件、写文件、shell、patch。
- 是否接入 MCP tools。
- 可用 Skills 和 workspace 指令。
- provider profile、model、baseUrl、apiKey 来源。
- approval policy。
- session 是否创建、恢复、保存或分叉。

因此，CLI、TUI、ACP 不是三套 Agent，而是三种 host 入口。它们共享核心 runtime，但交互方式和审批通道不同。

## 三种入口

| 入口 | 适合谁 | 重点 |
| --- | --- | --- |
| CLI | 想快速跑 one-shot 或 REPL 的用户 | provider、workspace、session、命令参数 |
| TUI | 长时间在终端里工作的人 | slash command、状态视图、交互密度 |
| ACP | IDE、桌面应用、自定义前端 | JSON-RPC session、permission request、宿主注入能力 |

如果你只是评估功能，先从 [Quickstart](/docs/coding-agent/quickstart) 和 [CLI / TUI](/docs/coding-agent/cli-and-tui) 开始。

## 核心概念

| 概念 | 说明 |
| --- | --- |
| Workspace | 当前代码仓上下文和文件边界 |
| Built-in Tools | 读文件、写文件、shell、patch 等 coding-native 工具 |
| Approval | 对高风险工具调用的确认机制 |
| Session | 可保存、恢复、分叉的工作状态 |
| Compact / Checkpoint | 长任务中的上下文压缩和状态保留 |
| Provider Profile | provider、protocol、model、baseUrl、key 来源组合 |
| Skills | 给模型按需读取的工作流说明和项目经验 |
| MCP / ACP | MCP 接工具能力，ACP 接宿主应用 |

## 安全和限制

Coding Agent 的高风险面比普通 Agent 更大，因为它可能接触文件系统、shell、进程和外部服务。

上线或长期使用前应确认：

- workspace 根目录正确，禁止路径明确。
- 写文件、shell、patch、包管理命令有审批规则。
- session store 不记录真实密钥。
- MCP tools 不默认全量暴露。
- 子代理或 delegation 不越过原始权限边界。
- 运行输出和 trace 不泄漏私有代码或配置。

相关页面：

- [Tools and Approvals](/docs/coding-agent/tools-and-approvals)
- [Session Runtime](/docs/coding-agent/session-runtime)
- [Security Overview](/docs/security/overview)

## 推荐阅读顺序

### 直接使用

1. [Why Coding Agent](/docs/coding-agent/why-coding-agent)
2. [Quickstart](/docs/coding-agent/quickstart)
3. [Install and Release](/docs/coding-agent/install-and-release)
4. [CLI / TUI](/docs/coding-agent/cli-and-tui)
5. [Provider Profiles](/docs/coding-agent/provider-profiles)
6. [Tools and Approvals](/docs/coding-agent/tools-and-approvals)
7. [Session Runtime](/docs/coding-agent/session-runtime)

### 扩展开发

1. [Architecture](/docs/coding-agent/architecture)
2. [Runtime Architecture](/docs/coding-agent/runtime-architecture)
3. [Prompt Assembly](/docs/coding-agent/prompt-assembly)
4. [MCP and ACP](/docs/coding-agent/mcp-and-acp)
5. [Command Reference](/docs/coding-agent/command-reference)

如果你要比较 Coding Agent 和 JS/TS 生态里的 agent SDK，看 [Comparison](/docs/comparison/overview)。
