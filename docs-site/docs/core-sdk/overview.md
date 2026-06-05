# Core SDK 总览

`Core SDK` 对应仓库里的 `ai4j/` 模块，是 AI4J 的基础能力层。你可以只使用这一层完成模型调用、Tool、Skill、MCP、Memory、Search/RAG 和 provider 扩展，也可以在它稳定后再接 Spring Boot、Agent、Coding Agent 或 FlowGram。

这页先回答三个问题：

- Core SDK 到底解决什么。
- 你应该从哪条能力线开始。
- 哪些能力是主线，哪些属于进阶或 provider 相关能力。

## 一句话定位

Core SDK 解决的是：

> 在 Java 8+ 项目里，用一套连续的工程模型接入模型、工具、协议能力、上下文、检索增强和扩展点。

它不是单独的 `Chat` wrapper，也不是把几个 provider API 简单包一层。它更像 AI4J 的能力底座：上层 starter、Agent、Coding Agent、FlowGram 都应复用这层能力，而不是重新定义模型、工具或 RAG。

## 你应该从哪里开始

| 目标 | 入口 | 你会先学到 |
| --- | --- | --- |
| 只想发第一条消息 | [Model Access](/docs/core-sdk/model-access/overview) | Chat、Responses、streaming、多模态怎么选 |
| 想让模型调用本地能力 | [Tools](/docs/core-sdk/tools/overview) | Function Tool、schema、执行模型和安全边界 |
| 想给模型可复用说明和流程 | [Skills](/docs/core-sdk/skills/overview) | Skill 文件、发现、加载和与 Tool/MCP 的边界 |
| 想接外部工具或发布 Java 能力 | [MCP](/docs/mcp/overview) | client、transport、gateway、server publish |
| 想做会话上下文 | [Memory](/docs/core-sdk/memory/overview) | chat memory、session、与 tool 的边界 |
| 想做知识库或检索增强 | [Search & RAG](/docs/core-sdk/search-and-rag/overview) | ingestion、chunk、embedding、vector、rerank、citation |
| 想扩展 provider 或服务 | [Extension](/docs/core-sdk/extension/overview) | provider、model、service、HTTP stack 扩展方式 |

如果你是第一次使用，先看 [Quickstart for Java](/docs/start-here/quickstart-java)，再回到本页选择能力线。

## Core SDK 包含哪些能力

| 能力 | 当前定位 | 适合场景 |
| --- | --- | --- |
| Model Access | 主线 | 调用 Chat、Responses、streaming、多模态等模型能力 |
| Tools | 主线 | 把本地 Java 函数或受控能力暴露给模型 |
| Skills | 进阶主线 | 让模型按需读取说明、模板、任务流程和经验资产 |
| MCP | 进阶主线 | 通过协议接入外部工具、服务、资源或 prompt |
| Memory | 主线 | 保留会话状态、历史消息和上下文边界 |
| Search & RAG | 进阶主线 | 文档入库、检索增强、向量库、rerank、引用追踪 |
| Extension | 进阶参考 | 新 provider、新模型、新服务实现或网络栈扩展 |
| Image / Audio / Realtime | provider 相关能力 | 依赖具体 provider 的能力覆盖 |

统一入口不等于所有 provider 能力完全一致。不同平台对 Chat、Responses、Embedding、Rerank、Image、Audio、Realtime 的支持不同，使用前应查看 [Platform and Service Matrix](/docs/core-sdk/platform-service-matrix)。

## 三个概念边界

### Tool

Tool 是可被模型调用的结构化能力。它通常有名称、描述、参数 schema 和执行器。第一条主线是本地 Function Tool。

### Skill

Skill 是模型可读取的说明资产，通常包含 `SKILL.md`、模板、流程和经验。它帮助模型“知道怎么做”，但它本身不是可执行工具。

### MCP

MCP 是协议化能力连接层。它既可以连接第三方 MCP server，也可以把 Java 能力发布成 MCP server，还可以通过 gateway 管理多服务工具面。

这三者可以组合，但不能混成一个概念。Tool 负责调用，Skill 负责说明，MCP 负责协议连接。

## 与上层模块的关系

| 上层模块 | 复用 Core SDK 的方式 |
| --- | --- |
| Spring Boot starter | 把 Core SDK 配置、服务和 Bean 装进 Spring 容器 |
| Agent | 在模型、工具、memory 之上增加 runtime、workflow、trace、team |
| Coding Agent | 在 Agent 和 Core SDK 之上增加 workspace、session、approval、CLI/TUI/ACP |
| FlowGram | 把 Core/Agent 能力嵌进显式工作流节点和 task API |

因此，Core SDK 不是“读完就跳过”的基础章节，而是后续所有专题的共同前提。

## 生产接入要先确认什么

- provider、model、baseUrl、key 来源是否清楚。
- 使用的 service 面是否被目标 provider 支持。
- Tool 和 MCP 是否默认最小暴露。
- RAG 是否继承业务权限和数据来源元信息。
- streaming、超时、失败重试和日志脱敏是否有边界。
- 多模块使用时是否通过 BOM 对齐版本。

上线前建议看：

- [Version Compatibility](/docs/reference/version-compatibility)
- [Security Overview](/docs/security/overview)
- [Production Checklist](/docs/operations/production-checklist)
- [Troubleshooting](/docs/troubleshooting/overview)

## 推荐阅读顺序

1. [Service Entry and Registry](/docs/core-sdk/service-entry-and-registry)
2. [Platform and Service Matrix](/docs/core-sdk/platform-service-matrix)
3. [Model Access](/docs/core-sdk/model-access/overview)
4. [Tools](/docs/core-sdk/tools/overview)
5. [Skills](/docs/core-sdk/skills/overview)
6. [MCP](/docs/mcp/overview)
7. [Memory](/docs/core-sdk/memory/overview)
8. [Search & RAG](/docs/core-sdk/search-and-rag/overview)
9. [Extension](/docs/core-sdk/extension/overview)

旧的 `ai-basics/`、`core-sdk/chat/`、`core-sdk/responses/`、`core-sdk/mcp/` 中仍有历史细节，但当前正式阅读路径以 sidebar 和 [Documentation Map](/docs/start-here/documentation-map) 为准。
