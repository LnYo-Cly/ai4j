---
sidebar_position: 1
---

# Coding Agent 总览

`Coding Agent` 是 AI4J 面向本地代码仓交付的一套工程化入口。

它不是单独一个模型能力，而是一组围绕“读代码、改文件、跑命令、接工具、持续会话”的组合能力，当前有三种主要接入方式：

- `CLI`：面向直接使用者；
- `TUI`：面向更完整的交互体验；
- `ACP`：面向 IDE、桌面应用和自定义宿主集成。

---

## 1. 这部分文档解决什么问题

这一类文档优先回答两类问题：

1. 我怎么把 Coding Agent 跑起来并稳定使用？
2. 我怎么扩展它，让它适配自己的工具链、技能目录、MCP 服务和宿主界面？

所以这一组文档会按“先会用，再扩展”的顺序组织。

---

## 2. 能力地图

当前 Coding Agent 专题覆盖这些内容：

- 启动与交互：CLI、TUI、ACP；
- 发布与分发：fat jar、平台压缩包、GitHub Release、一键安装；
- Runtime 架构：agent runtime、session runtime、host runtime、MCP runtime；
- 会话能力：session、resume、fork、history、tree、events、replay、auto-continue、compact-after-continue；
- 上下文压缩：tool-result microcompact、checkpoint compact、aggressive compact、session-memory fallback；
- Prompt 组装：system、instructions、workspace 约束、skills、commands、memory；
- 模型与配置：provider、protocol、profile、workspace override、stream、approval；
- Provider 运营：全局 profile、workspace 绑定、模型切换、默认协议规则；
- 内置工具：`bash`、`read_file`、`write_file`、`apply_patch`；
- 可发现技能：workspace/global/custom roots 下的 `SKILL.md`；
- MCP：全局注册、工作区启用、当前会话暂停/恢复/重连；
- 扩展点：自定义 `toolRegistry`、`toolExecutor`、`ToolExecutorDecorator`、`CodingCliTuiFactory`。

---

## 3. 三种入口怎么选

### 3.1 CLI

适合：

- 直接在终端里使用；
- 一次性任务或持续 REPL；
- 想最快验证 provider / model / workspace 能否打通。

入口文档：

- [CLI / TUI 使用指南](/docs/coding-agent/cli-and-tui)

### 3.2 TUI

适合：

- 需要更强的主缓冲区交互体验；
- 需要 slash command、palette、状态栏、主题切换；
- 希望在终端里长期使用 coding agent。

入口文档：

- [CLI / TUI 使用指南](/docs/coding-agent/cli-and-tui)
- [TUI 定制与主题](/docs/coding-agent/tui-customization)

### 3.3 ACP

适合：

- 要接 IDE 插件、桌面端或自定义宿主；
- 要消费结构化会话事件，而不是只渲染终端文本；
- 要在宿主侧接管权限审批、历史恢复和流式渲染。

入口文档：

- [ACP 集成](/docs/coding-agent/acp-integration)

---

## 4. 建议阅读顺序

### 4.1 直接使用

1. [Coding Agent 快速开始](/docs/coding-agent/quickstart)
2. [发布、安装与 GitHub Release](/docs/coding-agent/release-and-installation)
3. [CLI / TUI 使用指南](/docs/coding-agent/cli-and-tui)
4. [Runtime 架构](/docs/coding-agent/runtime-architecture)
5. [会话、流式与进程](/docs/coding-agent/session-runtime)
6. [Compact 与 Checkpoint 机制](/docs/coding-agent/compact-and-checkpoint)
7. [Prompt 组装与上下文来源](/docs/coding-agent/prompt-assembly)
8. [配置体系](/docs/coding-agent/configuration)
9. [Provider Profile 与模型切换](/docs/coding-agent/provider-profiles)
10. [命令参考](/docs/coding-agent/command-reference)

### 4.2 做扩展

1. [Tools 与审批机制](/docs/coding-agent/tools-and-approvals)
2. [Runtime 架构](/docs/coding-agent/runtime-architecture)
3. [Compact 与 Checkpoint 机制](/docs/coding-agent/compact-and-checkpoint)
4. [Prompt 组装与上下文来源](/docs/coding-agent/prompt-assembly)
5. [Skills 使用与组织](/docs/coding-agent/skills)
6. [Provider Profile 与模型切换](/docs/coding-agent/provider-profiles)
7. [MCP 对接](/docs/coding-agent/mcp-integration)
8. [ACP 集成](/docs/coding-agent/acp-integration)
9. [TUI 定制与主题](/docs/coding-agent/tui-customization)

---

## 5. 设计边界

为了避免读文档时概念混乱，可以先记住这几个边界：

- `Coding Agent` 解决的是“本地代码仓上的交付与交互”；
- `BaseAgentRuntime` 保持底层单轮 tool-loop 语义，任务级自动继续在 `ai4j-coding` 的 outer loop 中实现；
- `MCP` 是它可以接入的一类工具来源，但不是全部；
- `Skill` 不是工具调用协议，而是供模型按任务读取和复用的说明/模板；
- `ACP` 不是模型协议，而是 Coding Agent 的宿主集成协议；
- `TUI` 不是另一个 Agent，只是另一层交互壳。

---

## 6. 下一步

建议先从 [Coding Agent 快速开始](/docs/coding-agent/quickstart) 开始。
