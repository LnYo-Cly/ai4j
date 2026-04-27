---
sidebar_position: 999
---

# 术语表

本页用于统一 AI4J 文档中的核心术语，避免同一概念在不同专题里被混淆。

---

## A

### AI 基座

在这套文档里，“AI 基座”不是营销词，而是一个结构化定位。

它表示 AI4J 不只提供单点模型调用，还把这些层面放进统一体系：

- 模型调用
- `Tool / Function Call`
- `Skill`
- `MCP`
- 上层 `Spring Boot / Agent / Coding Agent / Flowgram`

如果你要先看这套分层从哪里开始，优先看：

- [Why AI4J](/docs/start-here/why-ai4j)
- [Architecture at a Glance](/docs/start-here/architecture-at-a-glance)

### ACP

`ACP` 是 `Coding Agent` 的宿主集成协议，用于 IDE、桌面应用或自定义前端与 `ai4j-cli acp` 通过结构化 JSON-RPC 通信。

它不是模型协议，也不是 MCP。

对应文档：

- [MCP 与 ACP](/docs/coding-agent/mcp-and-acp)

### Agent

AI4J 中的 `Agent` 指一套围绕模型、运行时、工具、记忆和编排构建的智能体框架。

对应文档：

- [Agent 架构总览](/docs/agent/overview)

### AiService

AI4J 的统一服务工厂，用于按 `PlatformType` 获取 `Chat`、`Responses`、`Embedding`、`Audio`、`Image`、`Realtime` 等服务接口。

对应文档：

- [Service Entry and Registry](/docs/core-sdk/service-entry-and-registry)

---

## C

### Chat

指 `Chat Completions` 风格的消息式模型接口。

在 AI4J 中通常对应：

- `IChatService`
- `ChatCompletion`
- `ChatCompletionResponse`

### CodeAct

AI4J Agent 中的代码驱动运行时。

适合：

- 先生成代码
- 再通过代码多次调用工具
- 处理复杂结构化任务

对应文档：

- [CodeAct Runtime](/docs/agent/runtimes/codeact-runtime)

### Coding Agent

AI4J 面向本地代码仓交付的一套工程化入口，包含：

- CLI
- TUI
- ACP
- 会话、命令、工具、Skills、MCP 集成

它不是通用 Agent 框架的同义词，而是偏“本地编码交互”的产品层。

对应文档：

- [Coding Agent 总览](/docs/coding-agent/overview)

---

## F

### Function Call

`Function Call` 指模型能够按工具 schema 选择并调用某个本地能力的那条调用语义。

在 AI4J 语境里，它通常是大多数用户理解第一条 Tool 主线的入口。

对应文档：

- [First Tool Call](/docs/start-here/first-tool-call)
- [Core SDK / Tools / Function Calling](/docs/core-sdk/tools/function-calling)

### Flowgram

AI4J 提供的低代码工作流编排接入方向，面向流程图式节点运行、后端任务执行、节点扩展。

对应文档：

- [Flowgram 总览](/docs/flowgram/overview)

### Function Tool

本地 Java 函数工具，通过注解或注册方式暴露给模型调用。

它和 MCP Tool 的区别在于：

- Function Tool 通常直接存在于本地应用内部
- MCP Tool 来自 MCP Server

---

## G

### Gateway

在 MCP 语境里通常指 `McpGateway`，用于统一管理多个 MCP Client、聚合工具并做路由治理。

对应文档：

- [Gateway and Multi-service](/docs/core-sdk/mcp/gateway-and-multi-service)

---

## M

### MCP

`Model Context Protocol`，模型接外部能力的标准协议层。

在 AI4J 中覆盖：

- MCP Client
- MCP Gateway
- MCP Server

对应文档：

- [MCP 总览](/docs/core-sdk/mcp/overview)

### Memory

Agent 或 Coding Agent 在持续会话中的上下文记忆机制。

通常包括：

- 历史消息
- 工具调用记录
- 压缩摘要
- checkpoint

### Model Client

Agent 层的模型适配接口，用于把运行时构造的 `AgentPrompt` 转成具体模型请求。

常见实现：

- `ChatModelClient`
- `ResponsesModelClient`

---

## P

### PlatformType

AI4J 中的平台枚举，用于声明你要调用哪家模型平台。

例如：

- `OPENAI`
- `DOUBAO`
- `DASHSCOPE`
- `OLLAMA`

### Profile

在 `Coding Agent` 中，`provider profile` 指一组可复用的模型配置组合，例如：

- provider
- protocol
- model
- baseUrl
- apiKey 来源

对应文档：

- [Configuration](/docs/coding-agent/configuration)

### Prompt Assembly

在 `Coding Agent` 语境里，指最终送给模型的上下文是如何由：

- `systemPrompt`
- workspace 指令
- `instructions`
- session memory
- 当前输入
- tool schemas

共同组成的。

对应文档：

- [Coding Agent Architecture](/docs/coding-agent/architecture)

---

## R

### ReAct

AI4J Agent 的默认通用运行时，适合：

- 文本任务
- 多轮思考
- 按需调用工具

对应文档：

- [最小 ReAct Agent](/docs/agent/runtimes/minimal-react-agent)

### Responses

指事件化响应模型接口。

在 AI4J 中通常对应：

- `IResponsesService`
- `ResponseRequest`
- `Response`
- `ResponseSseListener`

它和 `Chat` 的差别不只是接口名不同，更在于事件模型更强。

---

## S

### Session

持续会话实例。

在 `Coding Agent` 中，session 通常包含：

- 当前上下文
- 历史事件
- 分支关系
- 内存压缩信息
- 进程状态

### Skill

`Skill` 首先属于 AI4J 基座里的说明资产能力，在 `Coding Agent` 里再进一步产品化。

它通常以 `SKILL.md` 形式存在。

它不是工具协议，而是供模型按需读取和复用的任务说明、模板或工作流指引。

对应文档：

- [Core SDK / Skills 总览](/docs/core-sdk/skills/overview)

### StateGraph

Agent Workflow 中的状态图编排能力，适合分支、循环、条件路由。

对应文档：

- [Workflow StateGraph](/docs/agent/orchestration/stategraph)

### Stream

指模型响应以增量方式到达，而不是一次性整包返回。

要注意：

- 流式事件不等于 token
- 不同平台的 chunk 粒度不同

---

## T

### Tool Registry

用于决定“暴露给模型哪些工具”的注册层。

它和 `ToolExecutor` 不同：

- `ToolRegistry` 决定可见性
- `ToolExecutor` 决定执行方式

### Trace

指 Agent 或 Coding Agent 的过程观测能力。

通常用于记录：

- 模型调用
- 工具调用
- 步骤耗时
- 错误与回退

对应文档：

- [Trace 与可观测性](/docs/agent/observability/trace)
