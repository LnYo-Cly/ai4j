---
sidebar_position: 4
---

# Feature Map

这页是 AI4J 的功能地图。它不替代每个专题页，只负责告诉你：

- 当前有哪些能力。
- 每个能力适合解决什么问题。
- 应该从哪一页开始读。
- 哪些能力是稳定主线，哪些更适合进阶探索。

## 成熟度标记

| 标记 | 含义 |
| --- | --- |
| `stable` | 推荐作为日常接入主线，文档和 API 语义相对稳定 |
| `advanced` | 能力已经成体系，但更适合有明确工程目标后再使用 |
| `preview` | 已有实现和文档入口，但接口、行为或最佳实践仍可能调整 |
| `experimental` | 更偏探索或特定集成，使用前应确认源码、示例和限制 |

## 入门路径

| 能力 | 状态 | 模块 | 适合你在什么时候用 | 从这里开始 |
| --- | --- | --- | --- | --- |
| 普通 Java 快速开始 | `stable` | `ai4j` | 想先验证依赖、配置和一次模型调用 | [Quickstart for Java](/docs/start-here/quickstart-java) |
| Spring Boot 快速开始 | `stable` | `ai4j-spring-boot-starter` | 已有 Spring Boot 项目，希望用配置和 Bean 接入 | [Quickstart for Spring Boot](/docs/start-here/quickstart-spring-boot) |
| 第一次对话 | `stable` | `ai4j` | 想先发出一条消息，理解最小调用路径 | [First Chat](/docs/start-here/first-chat) |
| 第一次工具调用 | `stable` | `ai4j` | 想让模型调用本地函数或工具 | [First Tool Call](/docs/start-here/first-tool-call) |
| 路径选择 | `stable` | docs | 不确定该走 SDK、Spring、Agent 还是 FlowGram | [Choose Your Path](/docs/start-here/choose-your-path) |

## Core SDK

| 能力 | 状态 | 模块 | 解决什么问题 | 深入阅读 |
| --- | --- | --- | --- | --- |
| Model Access | `stable` | `ai4j` | 统一模型接入主线 | [Overview](/docs/core-sdk/model-access/overview) |
| Chat | `stable` | `ai4j` | 对话式模型调用 | [Chat](/docs/core-sdk/model-access/chat) |
| Responses | `stable` | `ai4j` | 面向 Responses 风格的统一调用 | [Responses](/docs/core-sdk/model-access/responses) |
| Streaming | `stable` | `ai4j` | 流式输出、增量结果和前端展示 | [Streaming](/docs/core-sdk/model-access/streaming) |
| Multimodal | `advanced` | `ai4j` | 文本、图像等多模态输入输出 | [Multimodal](/docs/core-sdk/model-access/multimodal) |
| Tools / Function Call | `stable` | `ai4j` | 本地函数声明、执行和安全边界 | [Tools](/docs/core-sdk/tools/overview) |
| Skills | `advanced` | `ai4j` | 给模型按需读取说明、模板和工作流资产 | [Skills](/docs/core-sdk/skills/overview) |

## RAG、检索和 MCP

| 能力 | 状态 | 模块 | 解决什么问题 | 深入阅读 |
| --- | --- | --- | --- | --- |
| Search & RAG | `advanced` | `ai4j` | 从外部知识中检索、增强回答和保留引用线索 | [Overview](/docs/core-sdk/search-and-rag/overview) |
| Ingestion Pipeline | `advanced` | `ai4j` | 文档入库、切分和索引前处理 | [Ingestion Pipeline](/docs/core-sdk/search-and-rag/ingestion-pipeline) |
| Hybrid Retrieval | `advanced` | `ai4j` | 组合关键词、向量和其他召回策略 | [Hybrid Retrieval](/docs/core-sdk/search-and-rag/hybrid-retrieval) |
| Rerank | `advanced` | `ai4j` | 对候选结果重排，提高检索质量 | [Rerank](/docs/core-sdk/search-and-rag/rerank) |
| MCP | `advanced` | `ai4j` | 通过协议接入外部工具、服务和能力网关 | [MCP Overview](/docs/mcp/overview) |
| MCP Client Integration | `advanced` | `ai4j` | 在客户端侧连接和使用 MCP 能力 | [Client Integration](/docs/mcp/client-integration) |

## 应用集成和上层运行时

| 能力 | 状态 | 模块 | 适合场景 | 从这里开始 |
| --- | --- | --- | --- | --- |
| Spring Boot Starter | `stable` | `ai4j-spring-boot-starter` | Spring 应用配置化接入、自动装配和 Bean 扩展 | [Spring Boot Overview](/docs/spring-boot/overview) |
| Agent Runtime | `preview` | `ai4j-agent` | 需要 memory、state、tool registry、workflow 或 team orchestration | [Agent Overview](/docs/agent/overview) |
| Agent Quickstart | `preview` | `ai4j-agent` | 想先跑一个最小 Agent | [Agent Quickstart](/docs/agent/quickstart) |
| Agent Teams | `preview` | `ai4j-agent` | 多 agent 协作和分工编排 | [Agent Teams](/docs/agent/agent-teams) |
| Coding Agent | `preview` | `ai4j-coding`、`ai4j-cli` | 面向本地代码仓的任务执行、workspace 工具和 CLI/TUI | [Coding Agent Overview](/docs/coding-agent/overview) |
| Coding Agent Quickstart | `preview` | `ai4j-coding`、`ai4j-cli` | 想体验本地 Coding Agent 产品入口 | [Coding Agent Quickstart](/docs/coding-agent/quickstart) |
| FlowGram | `preview` | `ai4j-flowgram-spring-boot-starter` | 可视化工作流平台后端、节点运行和 trace bridge | [FlowGram Overview](/docs/flowgram/overview) |
| FlowGram Quickstart | `preview` | `ai4j-flowgram-demo` | 想跑通 FlowGram demo 或 starter 集成 | [FlowGram Quickstart](/docs/flowgram/quickstart) |
| Solutions | `advanced` | multiple | 按业务场景复用组合方案 | [Solutions Overview](/docs/solutions/overview) |

## 还没有独立页的集成

部分生态集成或平台连接目前可能还没有稳定专题页。文档中如果提到 Dify、Coze、n8n、
AgentFlow 或其他外部平台，应先按能力归类阅读：

| 你要接什么 | 先看哪条主线 |
| --- | --- |
| 外部工具或服务网关 | [MCP](/docs/mcp/overview) |
| 本地 Java 函数或业务服务 | [Tools](/docs/core-sdk/tools/overview) |
| 结构化提示、流程说明和可复用任务资产 | [Skills](/docs/core-sdk/skills/overview) |
| 知识库、检索增强或文档问答 | [Search & RAG](/docs/core-sdk/search-and-rag/overview) |
| 可视化工作流后端 | [FlowGram](/docs/flowgram/overview) |

这些集成不应该在入口页里被包装成已经完全稳定的能力。等对应专题页补齐后，再从这里添加深链。

## 推荐阅读顺序

第一次接入建议走：

1. [Why AI4J](/docs/start-here/why-ai4j)
2. [Quickstart for Java](/docs/start-here/quickstart-java) 或 [Quickstart for Spring Boot](/docs/start-here/quickstart-spring-boot)
3. [First Chat](/docs/start-here/first-chat)
4. [First Tool Call](/docs/start-here/first-tool-call)
5. 按需进入 [Core SDK](/docs/core-sdk/overview)、[Spring Boot](/docs/spring-boot/overview)、[Agent](/docs/agent/overview) 或 [FlowGram](/docs/flowgram/overview)
