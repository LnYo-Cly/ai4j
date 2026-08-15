---
title: Agent 概念地图
description: "ai4j 全部 agent 核心概念的导航地图：20 个概念分 7 个能力簇——能力三角(Function Call/MCP/Skill)、记忆与上下文链、执行核心层级、安全边界、可观测性、对外协议、工程化——每簇标出概念间关系，每条一句话定位 + 深链到详细页。"
sidebar_position: 10
tags: [concept]
---

# Agent 概念地图

> 这页是 ai4j 的**概念 GPS**——20 个 agent 核心概念，按 7 个能力簇组织，每簇标出概念间的关系，每条给一句话定位 + 直达详细页。

## 为什么需要这页

ai4j 的文档按子系统组织（Core SDK / Agent Runtime / Coding Agent / MCP / FlowGram…），每个子系统都有自己的概念入口页。但 agent 概念**横跨多个子系统**——Function Call 在 Core SDK、Hooks 在 Agent Runtime、Compaction 在 Agent + Coding Agent——读者容易在多个 section 间迷路。

这页不重复各详细页的内容，只负责：**告诉你 20 个概念分别是什么、在哪个层、彼此怎么关联、从哪页开始读。**

## 概念全景图

```
┌─────────────────────────────────────────────────────────────┐
│                    能力三角（模型怎么做事）                      │
│   Function Calling ←──→ MCP ←──→ Skill                      │
│     写代码执行            连外部工具       给方法论               │
└─────────────────────────────────────────────────────────────┘
┌─────────────────────────────────────────────────────────────┐
│                 记忆与上下文链（状态怎么管）                      │
│   Memory → Context Window → Compaction → Checkpoint         │
│    记事实      管窗口           压缩          存档恢复            │
└─────────────────────────────────────────────────────────────┘
┌─────────────────────────────────────────────────────────────┐
│                  执行核心层级（工作怎么分配）                     │
│   Agent Loop → DAG/Workflow → Subagents → Agent Teams       │
│    单步循环       编排有向图       分派子任务    多 agent 协作     │
└─────────────────────────────────────────────────────────────┘
┌─────────────────────────────────────────────────────────────┐
│                   安全边界（什么能做什么不能）                    │
│   Sandbox + Hooks + Plugin + Workspace Trust                │
│    隔离执行   事件拦截   贡献能力     信任门禁                   │
└─────────────────────────────────────────────────────────────┘
┌─────────────────────────────────────────────────────────────┐
│                   可观测性（发生了什么）                        │
│   Trace → Replay / Audit                                    │
│   实时追踪    重放恢复 + 防篡改                                 │
└─────────────────────────────────────────────────────────────┘
┌─────────────────────────────────────────────────────────────┐
│                 对外协议与知识增强（怎么跟外部交互）               │
│   A2A + ACP + MCP Server + RAG                              │
│  agent互连  IDE协议  暴露工具  知识增强                          │
└─────────────────────────────────────────────────────────────┘
┌─────────────────────────────────────────────────────────────┐
│                   工程化（怎么上生产）                          │
│   Session + Prompt + Harness                                │
│   会话管理   提示组装   coding agent 宿主                       │
└─────────────────────────────────────────────────────────────┘
```

---

## 1. 能力三角：Function Calling ↔ MCP ↔ Skill

**这是 ai4j 最核心的概念关系**——三者都让模型"做事"，但机制完全不同。先理解这个三角，再深入任何一个。

| 概念 | 一句话 | 所在层 | 详细页 |
|---|---|---|---|
| **Function Calling** | 模型调用你声明的 Java 方法（`@FunctionCall` / built-in / SPI），在宿主进程内执行 | Core SDK | [Tools 总览](/docs/capabilities/tools/overview) |
| **MCP** | 标准协议连接外部工具服务器（本地 stdio / 远程 HTTP），工具不在你的进程里 | Core SDK → 顶层 MCP | [MCP 总览](/docs/capabilities/mcp/overview) |
| **Skill** | 不是执行动作，而是给模型按需读取的**方法论指导**（SKILL.md），控制的是"怎么做"的认知 | Core SDK | [Skills 总览](/docs/capabilities/skills/overview) |

:::tip 三者怎么选
三者不互斥，可以同时用。核心区分：
- **Function Calling** = 模型→你的代码（in-process）
- **MCP** = 模型→外部工具服务器（cross-process）
- **Skill** = 模型→方法论文档（no execution, just knowledge）

详细的对比表和决策框架见 [Skill vs Tool vs MCP](/docs/capabilities/skills/skill-vs-tool-vs-mcp)。
:::

---

## 2. 记忆与上下文链

**状态从"记住"到"压缩"到"恢复"的完整链路。** 这 4 个概念是递进的——后面的概念依赖前面的。

| 概念 | 一句话 | 所在层 | 详细页 |
|---|---|---|---|
| **Memory / Chat Memory** | session 级事实存储（system/user/assistant/tool-call/tool-output/summary），存储与保留策略分离 | Core SDK | [Memory 总览](/docs/capabilities/chat-memory/overview) |
| **Context Window Management** | 管理进入模型的上下文窗口大小（ContextBudget 限制条目/字符/pinned prefix） | Agent Runtime | [Context Window Management](/docs/agent/memory/context-window-management) |
| **Compaction** | 压缩上下文（ContextProjector 按策略裁剪/microcompact 工具结果/auto-compact 熔断） | Agent + Coding Agent | [Memory Compact Context](/docs/agent/memory/memory-compact-context) · [Compact & Checkpoint](/docs/products/coding-agent/compact-and-checkpoint) |
| **Checkpoint / Resume** | 结构化存档 + 崩溃恢复（ResumeCache 跳过已完成副作用 + hash-chained 防篡改审计） | Agent + Coding Agent | [Replay, Recovery & Audit](/docs/agent/observability/replay-recovery-audit) · [Compact & Checkpoint](/docs/products/coding-agent/compact-and-checkpoint) |

:::note 跨层注意
Compaction 和 Checkpoint 在 Agent Runtime 和 Coding Agent 两个层都有实现，各层关注点不同：
- **Agent 层**：ContextProjector + ResumeCache（通用 agent 的上下文/恢复）
- **Coding Agent 层**：CodingSessionCompactor + CodingSessionCheckpoint（coding session 专用管线）

从 Agent 层的概念页开始读，再跳到 Coding Agent 层看工程化实现。
:::

---

## 3. 执行核心层级

**从"单 agent 单步循环"到"多 agent 协作"的 4 级递进。** 每一级是前一级的超集。

| 概念 | 一句话 | 所在层 | 详细页 |
|---|---|---|---|
| **Agent Loop (ReAct / CodeAct)** | 单 agent 的 think→act→observe 循环；ReAct 用 tool-call、CodeAct 用代码执行 | Agent Runtime | [Minimal React Agent](/docs/agent/runtimes/minimal-react-agent) · [CodeAct Runtime](/docs/agent/runtimes/codeact-runtime) |
| **DAG / Workflow Orchestration** | 把多个 agent 步骤编排成有向无环图（StateGraph），声明节点 + 边 + 条件分支 | Agent Runtime | [Workflow StateGraph](/docs/agent/runtimes/workflow-stategraph) |
| **Subagents** | 主 agent 把子任务委派给隔离的子 agent（独立 memory + tool + session） | Agent Runtime | [Subagent Handoff Policy](/docs/agent/orchestration/subagent-handoff-policy) |
| **Agent Teams** | 多个 agent 组成团队，通过 TaskBoard 协调任务分配、并行执行、结果汇总 | Agent Runtime | [Agent Teams](/docs/agent/orchestration/agent-teams) |

---

## 4. 安全边界

**控制 agent 能做什么、不能做什么的 4 道闸门。** 这些概念共同构成 ai4j 的安全防线。

| 概念 | 一句话 | 所在层 | 详细页 |
|---|---|---|---|
| **Sandbox** | 隔离代码执行环境（E2B / Daytona / CubeSandbox），agent 在远程沙箱里跑代码 | Agent Runtime | [Sandbox SPI](/docs/agent/governance/sandbox-spi) · [CubeSandbox](/docs/agent/governance/cubesandbox-provider) |
| **Lifecycle Hooks** | 在 PreToolUse / PostToolUse / Stop 等事件点拦截、审批或观察 agent 行为 | Agent + Coding Agent | [Plugin Lifecycle Hooks](/docs/agent/governance/plugin-lifecycle-hooks) · [Lifecycle Hooks](/docs/products/coding-agent/lifecycle-hooks) |
| **Plugin / Extension** | 第三方打 jar 贡献 tool/command/skill/prompt，经 discover→enable→expose 三段式门禁 | Core SDK (extension-api) | [Extension 总览](/docs/extending/overview) · [扩展 ai4j](/docs/extending/extend-ai4j) |
| **Workspace Trust** | 首次进入未信任目录时暂停问 y/n，`~/.ai4j/trusted-dirs.txt` 管理；`ai4j cli trust` 命令 | Coding Agent | [Lifecycle Hooks & Trust](/docs/products/coding-agent/lifecycle-hooks) |

:::warning 安全模型边界
- **Sandbox** 控制的是**执行隔离**（代码在远端跑，不在你的机器上）
- **Hooks** 控制的是**行为拦截**（在 agent 执行前/后检查 + 审批）
- **Plugin** 控制的是**能力贡献**（不给就不暴露）
- **Workspace Trust** 控制的是**首次信任**（不信任的目录不加载配置）

四者正交——一个 agent 可以同时被 sandbox 隔离 + hooks 拦截 + 只暴露白名单工具 + 只在信任目录运行。
:::

---

## 5. 可观测性

**agent 执行过程中发生了什么、能否回溯、能否恢复。**

| 概念 | 一句话 | 所在层 | 详细页 |
|---|---|---|---|
| **Agent Trace / Observability** | runtime 发布统一事件流（MODEL_REQUEST / TOOL_CALL / TOOL_RESULT），trace 消费并折叠成 span 导出到 OTel / Langfuse / JSONL | Agent Runtime | [Trace 与可观测性](/docs/agent/observability/trace-observability) |
| **Replay / Audit** | 节点级 I/O 重放（live/mock）、崩溃续跑（ResumeCache）、防篡改 hash-chained 审计日志 | Agent Runtime | [Replay, Recovery & Audit](/docs/agent/observability/replay-recovery-audit) |

:::note 事件流是基础
Trace 和 Replay 都是 runtime 事件流的**消费者**，不是埋点——事件已经发布了，trace/replay 只是决定怎么消费。这意味着你可以在不改动 agent 代码的前提下，随时加 trace 导出或 replay 恢复。
:::

---

## 6. 对外协议与知识增强

**agent 怎么跟外部世界交互——跟其他 agent、跟 IDE、跟工具服务器、跟知识库。**

| 概念 | 一句话 | 所在层 | 详细页 |
|---|---|---|---|
| **A2A (Agent-to-Agent)** | JSON-RPC + SSE 协议，让 ai4j agent 对外暴露为可被其他 agent 发现和调用的服务 | Agent Runtime | [A2A](/docs/agent/observability/a2a) |
| **ACP (Agent Client Protocol)** | 换行分隔 JSON-RPC（非 LSP framing），让 IDE / 桌面壳驱动 coding session（创建/加载/prompt/权限确认） | Coding Agent | [ACP 集成](/docs/products/coding-agent/acp-integration) · [编程式集成](/docs/getting-started/programmatic-integration) |
| **MCP Server** | 把 ai4j 的工具暴露为 MCP 服务端（streamable-HTTP / SSE / stdio），其他 MCP 客户端可以发现并调用 | MCP (顶层) | [Build Your MCP Server](/docs/capabilities/mcp/build-your-mcp-server) |
| **RAG** | 摄取→分块→嵌入→向量存储→检索→重排→引用，完整的知识增强管线 | Core SDK | [Search and RAG 总览](/docs/capabilities/rag/overview) |

---

## 7. 工程化

**从 SDK 调用走到生产级 agent 应用的 3 个工程概念。**

| 概念 | 一句话 | 所在层 | 详细页 |
|---|---|---|---|
| **Session Management** | AgentSession 作为有状态长运行容器（sessionId + 独立 memory + event log + snapshot/restore） | Agent + Coding Agent | [Session Runtime](/docs/agent/session-runtime) · [Coding Session Runtime](/docs/products/coding-agent/session-runtime) |
| **Prompt / System Prompt** | systemPrompt（运行时指令合并）vs instructions（独立保留）的字段语义 + coding-agent 的 prompt 组装管线 | Agent + Coding Agent | [System Prompt vs Instructions](/docs/agent/system-prompt-vs-instructions) · [Prompt Assembly](/docs/products/coding-agent/prompt-assembly) |
| **Harness / Coding Agent** | 完整的终端 coding agent 宿主（CLI/TUI + ACP + sandbox-routing + tools + approvals + compaction） | Coding Agent | [Coding Agent 总览](/docs/products/coding-agent/overview) · [编程式集成](/docs/getting-started/programmatic-integration) |

---

## 怎么用这页

1. **第一次了解 agent**：从[能力三角](#1-能力三角function-calling--mcp--skill)开始，理解模型怎么做事。
2. **要管状态**：走[记忆与上下文链](#2-记忆与上下文链)，从 Memory 到 Checkpoint。
3. **要编排复杂任务**：走[执行核心层级](#3-执行核心层级)，从 Agent Loop 到 Agent Teams。
4. **要上生产**：检查[安全边界](#4-安全边界) + [可观测性](#5-可观测性) + [工程化](#7-工程化)。
5. **要跟外部对接**：看[对外协议](#6-对外协议与知识增强)。

## 继续阅读

- [Skill vs Tool vs MCP](/docs/capabilities/skills/skill-vs-tool-vs-mcp)——能力三角的详细消歧
- [Agent Runtime 总览](/docs/agent/overview)——agent 子系统的完整入口
- [扩展 ai4j](/docs/extending/extend-ai4j)——插件/Skill/Prompt/自定义 provider 的聚合入口
- [编程式集成](/docs/getting-started/programmatic-integration)——SDK/RPC/事件流/TUI 的聚合入口
- [Feature Map](/docs/getting-started/feature-map)——功能成熟度地图
