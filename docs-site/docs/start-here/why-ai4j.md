# Why AI4J

AI4J 是一套面向 `Java 8+` 的 AI SDK。它最核心的目标是降低 Java 项目接入 AI 的成本：
少引概念、少写胶水代码、少被 provider 差异拖住，同时保留向 RAG、MCP、Spring Boot、
Agent 和 Coding Agent 升级的路径。

这页不讲完整 API，只回答一个问题：为什么在 Java 项目里可以考虑 AI4J。

## AI4J 要解决什么问题

Java 项目接入 AI 时，第一步通常只是“调通一个模型”。但真实项目很快会继续遇到：

- 要同时接 OpenAI-compatible、国内模型平台和不同类型的模型能力。
- `Chat`、`Responses`、流式、多模态、Embedding、Rerank 的请求形态不一致。
- 本地工具、Skill、MCP、RAG、Memory 很容易被写成互不相通的几套代码。
- 普通 Java、Spring Boot、Agent runtime、CLI 或工作流平台之间缺少平滑升级路径。
- 大框架能力很全，但为了完成一个简单接入，学习和配置成本偏高。

AI4J 的取舍是：先把 Java AI 接入路径做薄、做直，再把上层能力按需展开。

## 不是全家桶，而是可渐进升级的 Java AI SDK

AI4J 的模块拆分不是为了显得“东西多”，而是为了让使用者可以从一个很小的切口开始。
你可以只引入 `ai4j` 做模型调用和工具接入；如果项目已经是 Spring Boot，再换成 starter 的配置方式；
如果后续需要状态、workflow、team 或代码仓任务，再逐步进入 `ai4j-agent`、`ai4j-coding` 和 `ai4j-cli`。

这条路径的关键是：每一层都能独立解决一个问题，同时尽量不推翻上一层的概念。

| 阶段 | 取用模块 | 主要价值 |
| --- | --- | --- |
| 先跑通 AI 能力 | `ai4j` | 普通 Java 项目也能直接调用模型、工具、RAG 和 MCP |
| 接入 Spring 应用 | `ai4j-spring-boot-starter` | 用配置和 Bean 管理模型服务，不把业务代码绑死在 demo 写法上 |
| 做 Agent runtime | `ai4j-agent` | 在 Core SDK 之上增加 memory、state、workflow、trace 和 team orchestration |
| 做代码仓任务 | `ai4j-coding` + `ai4j-cli` | 把 Agent 能力产品化成本地 Coding Agent、CLI、TUI 和会话入口 |
| 做可视化工作流 | `ai4j-flowgram-spring-boot-starter` | 把 Agent 能力接到 FlowGram 后端和节点运行体系 |
| 统一版本 | `ai4j-bom` | 多模块组合时减少版本漂移 |

所以 AI4J 的模块独立性不是“目录拆得细”，而是“用户可以按阶段拿走需要的那一块”。

## 和 Spring AI、LangChain4j、AgentScope Java 的关系

Spring AI、LangChain4j、AgentScope Java 都有更大的团队、生态和社区积累。
AI4J 不是要在生态规模上和它们硬拼，也不应该把“我什么都比它们强”当作文档卖点。

AI4J 更适合把差异放在这些地方：

| 维度 | AI4J 的取舍 |
| --- | --- |
| 接入门槛 | 面向普通 Java 8+ 和 Maven 项目，先让调用跑起来，再逐步引入高级能力 |
| 概念边界 | 把 Tool、Skill、MCP、RAG、Agent 分开解释，减少“所有东西都是 chain”的混乱 |
| Provider 友好度 | 重视 OpenAI-compatible 和国内模型平台的实际接入体验 |
| 模块取用 | 不强迫全量采用；Core SDK、starter、Agent、Coding、CLI 和 FlowGram 可以按阶段引入 |
| 升级路径 | 从 Core SDK 到 Spring Boot starter、Agent、Coding Agent、FlowGram 保持同一套项目心智 |
| 文档策略 | 不用大而空的框架口号，而是把每个功能的入口、适合场景、限制和下一步写清楚 |

所以，AI4J 的竞争点不是“更大”，而是“更容易在 Java 项目里开始、更容易讲清楚、更容易按需取用和升级”。

## AI4J 的核心特点

### 1. 普通 Java 也能先接入

你不需要先把项目改造成某个完整应用框架，也不需要一开始就理解 Agent、workflow 或复杂编排。
从 [5 分钟首聊](/docs/start-here/five-minute-first-chat) 开始，可以先验证模型配置和一次调用。

如果项目已经是 Spring Boot，再走 [Quickstart for Spring Boot](/docs/start-here/quickstart-spring-boot)，
用 starter 管理配置和 Bean。

### 2. 模型能力不是孤立 wrapper

Core SDK 覆盖的不只是 `chat()`：

- [Chat](/docs/core-sdk/model-access/chat)
- [Responses](/docs/core-sdk/model-access/responses)
- [Streaming](/docs/core-sdk/model-access/streaming)
- [Multimodal](/docs/core-sdk/model-access/multimodal)
- Embedding、Rerank、Image、Audio、Realtime 等扩展能力

这些能力应该共享配置、provider 接入和工程约束，而不是每个能力都重新写一套入口。

### 3. Tool、Skill、MCP 分层清楚

AI4J 不把所有外部能力都混成一个概念：

- [Tool](/docs/core-sdk/tools/overview)：本地函数声明、执行模型和安全边界。
- [Skill](/docs/core-sdk/skills/overview)：模型可读取的说明、模板和任务资产。
- [MCP](/docs/mcp/overview)：协议化接入外部工具和服务。

这种分层对小项目和长期项目都重要。小项目能少绕路，长期项目能避免后续重构时边界失控。

### 4. RAG 和检索链路可逐步引入

AI4J 把 [Search & RAG](/docs/core-sdk/search-and-rag/overview) 放在 Core SDK 主线里，
而不是放成一个和模型调用完全割裂的 demo。你可以按需要逐步使用：

- ingestion pipeline
- chunking
- embedding
- vector store
- hybrid retrieval
- rerank
- citations and trace

### 5. 上层能力不强迫你一开始使用

Agent、Coding Agent、FlowGram 是向上升级路径，不是第一次接入 AI4J 的必修课。

当你需要更复杂的 runtime、工作流、代码仓任务或可视化编排时，再进入：

- [Agent](/docs/agent/overview)
- [Coding Agent](/docs/coding-agent/overview)
- [FlowGram](/docs/flowgram/overview)

### 6. 多模块不是负担，而是选择权

如果你只需要模型调用，就停在 `ai4j`。如果你需要 Spring Boot 自动装配，就引入 starter。
如果你要做 Agent runtime、Coding Agent 或 FlowGram，再选择对应模块。AI4J 应该让你逐步加能力，
而不是要求你一开始接受一整套平台流程。

## 适合什么项目

AI4J 更适合：

- 已经在 Java 8+ 或 Maven 体系里，想快速接入 AI 的项目。
- 希望同时兼容普通 Java 和 Spring Boot 的项目。
- 需要 OpenAI-compatible、国内模型平台或多 provider 接入的项目。
- 后续可能从模型调用升级到工具、RAG、MCP 或 Agent 的项目。
- 想按模块取用能力，而不是一开始引入完整平台的项目。
- 希望文档能把功能、边界和成熟度讲清楚，而不是只给 demo 的团队。

AI4J 不太适合：

- 只想绑定一个 provider，且不需要任何扩展能力的极薄 wrapper 场景。
- 已经深度绑定 Spring AI、LangChain4j 或其他框架，并且现有成本很低的项目。
- 需要大型生态、商业支持、海量第三方集成或长期稳定 SLA 的团队。
- 希望所有复杂度都被黑盒隐藏，而不是接受清晰分层和显式配置的项目。

## 下一步读什么

| 你现在想做什么 | 下一页 |
| --- | --- |
| 5 分钟跑通第一条请求 | [5 分钟首聊](/docs/start-here/five-minute-first-chat) |
| 普通 Java 先跑通 | [Quickstart for Java](/docs/start-here/quickstart-java) |
| Spring Boot 接入 | [Quickstart for Spring Boot](/docs/start-here/quickstart-spring-boot) |
| 看完整功能地图 | [Feature Map](/docs/start-here/feature-map) |
| 发出第一条消息 | [First Chat](/docs/start-here/first-chat) |
| 让模型调用本地工具 | [First Tool Call](/docs/start-here/first-tool-call) |

如果你还不确定该走哪条线，先看 [Feature Map](/docs/start-here/feature-map)。
