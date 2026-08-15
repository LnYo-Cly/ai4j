---
sidebar_position: 4
title: 功能地图
description: AI4J 功能地图：用成熟度标记（stable/advanced/preview/experimental）列出每个能力的状态、所属模块、适合场景与继续阅读入口，帮助按需取用最小模块。
tags: [reference]
---

# 功能地图
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
| Java 首次调用 | `stable` | `ai4j` | 第一次接入，想用最短路径跑通一条模型请求 | [Java 快速开始](/docs/getting-started/quickstart-java) |
| 普通 Java 快速开始 | `stable` | `ai4j` | 想先验证依赖、配置和一次模型调用 | [Java 快速开始](/docs/getting-started/quickstart-java) |
| Spring Boot 快速开始 | `stable` | `ai4j-spring-boot-starter` | 已有 Spring Boot 项目，希望用配置和 Bean 接入 | [Spring Boot 快速开始](/docs/getting-started/quickstart-spring-boot) |
| Chat 调用语义 | `stable` | `ai4j` | 已跑通首次调用，想理解消息模型和调用细节 | [Model Access / Chat](/docs/capabilities/models/chat) |
| 第一次工具调用 | `stable` | `ai4j` | 想让模型调用本地函数或工具 | [第一次工具调用](/docs/getting-started/first-tool-call) |
| 路径选择 | `stable` | docs | 不确定该走 SDK、Spring、Agent 还是 FlowGram | [入门路径选择](/docs/getting-started/choose-your-path) |
| 文档地图 | `stable` | docs | 想确认 canonical 主线和旧路径去向 | [文档地图](/docs/reference/maps/documentation-map) |

## 按模块取用

AI4J 的模块关系是从底座向上叠加，而不是一个必须全量采用的平台。你可以按当前目标选择最小模块。

| 当前目标 | 最小取用模块 | 依赖关系 | 适合场景 |
| --- | --- | --- | --- |
| 只做模型调用、工具、RAG、MCP | `ai4j` | 无 AI4J 内部依赖 | 普通 Java 项目先把 AI 能力跑起来 |
| Spring Boot 应用接入 AI | `ai4j-spring-boot-starter` | 依赖 `ai4j` | 需要配置属性、自动装配、Bean 扩展 |
| 嵌入 Agent runtime | `ai4j-agent` | 依赖 `ai4j` | 需要 memory、state、workflow、trace、team orchestration |
| 做本地 Coding Agent runtime | `ai4j-coding` | 依赖 `ai4j`、`ai4j-agent` | 需要 workspace 工具、会话、outer loop、compaction |
| 提供 CLI / TUI / ACP 入口 | `ai4j-cli` | 依赖 `ai4j`、`ai4j-coding` | 需要终端产品壳层和本地会话入口 |
| 接 FlowGram 后端 | `ai4j-flowgram-spring-boot-starter` | 依赖 `ai4j-agent`、`ai4j-spring-boot-starter` | 需要可视化工作流、任务 API、trace bridge |
| 跑 FlowGram demo | `ai4j-flowgram-demo` | 依赖 FlowGram starter | 需要示例后端验证集成 |
| 统一版本 | `ai4j-bom` | 管理多个 artifact 版本 | 同时引入多个 AI4J 模块时减少版本漂移 |

判断原则很简单：先引入能解决当前问题的最小模块；只有当需求自然上升时，再叠加下一层。

## Core SDK

| 能力 | 状态 | 模块 | 解决什么问题 | 深入阅读 |
| --- | --- | --- | --- | --- |
| Model Access | `stable` | `ai4j` | 统一模型接入主线 | [Overview](/docs/capabilities/models/overview) |
| Chat | `stable` | `ai4j` | 对话式模型调用 | [Chat 主线](/docs/capabilities/models/chat) |
| Responses | `stable` | `ai4j` | 面向 Responses 风格的统一调用 | [Responses 主线](/docs/capabilities/models/responses) |
| Streaming | `stable` | `ai4j` | 流式输出、增量结果和前端展示 | [流式语义](/docs/capabilities/models/streaming) |
| Multimodal | `advanced` | `ai4j` | 文本、图像等多模态输入输出 | [多模态](/docs/capabilities/models/multimodal) |
| Tools / Function Call | `stable` | `ai4j` | 本地函数声明、执行和安全边界 | [Tools](/docs/capabilities/tools/overview) |
| Skills | `advanced` | `ai4j` | 给模型按需读取说明、模板和工作流资产 | [Skills](/docs/capabilities/skills/overview) |

## RAG、检索和 MCP

| 能力 | 状态 | 模块 | 解决什么问题 | 深入阅读 |
| --- | --- | --- | --- | --- |
| Search & RAG | `advanced` | `ai4j` | 从外部知识中检索、增强回答和保留引用线索 | [Overview](/docs/capabilities/rag/overview) |
| Ingestion Pipeline | `advanced` | `ai4j` | 文档入库、切分和索引前处理 | [摄取管线](/docs/capabilities/rag/ingestion-pipeline) |
| Hybrid Retrieval | `advanced` | `ai4j` | 组合关键词、向量和其他召回策略 | [混合检索](/docs/capabilities/rag/hybrid-retrieval) |
| Rerank | `advanced` | `ai4j` | 对候选结果重排，提高检索质量 | [重排](/docs/capabilities/rag/rerank) |
| MCP | `advanced` | `ai4j` | 通过协议接入外部工具、服务和能力网关 | [MCP Overview](/docs/capabilities/mcp/overview) |
| MCP Client Integration | `advanced` | `ai4j` | 在客户端侧连接和使用 MCP 能力 | [Client Integration](/docs/capabilities/mcp/client-integration) |

## 应用集成和上层运行时

| 能力 | 状态 | 模块 | 适合场景 | 从这里开始 |
| --- | --- | --- | --- | --- |
| Spring Boot Starter | `stable` | `ai4j-spring-boot-starter` | Spring 应用配置化接入、自动装配和 Bean 扩展 | [Spring Boot Overview](/docs/integrations/spring-boot/overview) |
| Agent Runtime | `preview` | `ai4j-agent` | 需要 memory、state、tool registry、workflow 或 team orchestration | [Agent 总览](/docs/agent/overview) |
| Agent Quickstart | `preview` | `ai4j-agent` | 想先跑一个最小 Agent | [Agent 快速开始](/docs/agent/quickstart) |
| Agent Teams | `preview` | `ai4j-agent` | 多 agent 协作和分工编排 | [Agent 团队](/docs/agent/orchestration/agent-teams) |
| Coding Agent | `preview` | `ai4j-coding`、`ai4j-cli` | 面向本地代码仓的任务执行、workspace 工具和 CLI/TUI | [Coding Agent Overview](/docs/products/coding-agent/overview) |
| Coding Agent Quickstart | `preview` | `ai4j-coding`、`ai4j-cli` | 想体验本地 Coding Agent 产品入口 | [Coding Agent Quickstart](/docs/products/coding-agent/quickstart) |
| FlowGram | `preview` | `ai4j-flowgram-spring-boot-starter` | 可视化工作流平台后端、节点运行和 trace bridge | [FlowGram Overview](/docs/products/flowgram/overview) |
| FlowGram Quickstart | `preview` | `ai4j-flowgram-demo` | 想跑通 FlowGram demo 或 starter 集成 | [FlowGram Quickstart](/docs/products/flowgram/quickstart) |
| Solutions | `advanced` | multiple | 按业务场景复用组合方案 | [Solutions Overview](/docs/integrations/solutions/overview) |

## 生产准备和维护

| 能力 | 状态 | 模块 | 解决什么问题 | 深入阅读 |
| --- | --- | --- | --- | --- |
| Version Compatibility | `stable` | docs | Java、Maven、模块和 provider 能力边界 | [版本兼容性](/docs/reference/version-compatibility) |
| Release and Artifacts | `stable` | docs | Maven artifact、BOM、模块引入顺序 | [发布与制品](/docs/reference/release-and-artifacts) |
| Security | `stable` | docs | 密钥、Tool、MCP、RAG、Agent、FlowGram 安全边界 | [安全总览](/docs/production/security) |
| Production Checklist | `stable` | docs | 上线前配置、权限、观测和回归检查 | [生产检查清单](/docs/production/production-checklist) |
| Migration | `stable` | docs | 旧路径、旧示例和旧 API 心智迁移 | [迁移指南](/docs/reference/migration) |
| Troubleshooting | `stable` | docs | provider、Tool、MCP、RAG、Agent、FlowGram 排障入口 | [排障指南](/docs/production/troubleshooting) |
| Comparison | `stable` | docs | 与 Spring AI、LangChain4j、AgentScope Java、Pi Agent 的选型边界 | [Comparison](/docs/reference/about/comparison) |

## 还没有独立页的集成

部分生态集成或平台连接目前可能还没有稳定专题页。文档中如果提到 Dify、Coze、n8n、
AgentFlow 或其他外部平台，应先按能力归类阅读：

| 你要接什么 | 先看哪条主线 |
| --- | --- |
| 外部工具或服务网关 | [MCP](/docs/capabilities/mcp/overview) |
| 本地 Java 函数或业务服务 | [Tools](/docs/capabilities/tools/overview) |
| 结构化提示、流程说明和可复用任务资产 | [Skills](/docs/capabilities/skills/overview) |
| 知识库、检索增强或文档问答 | [Search & RAG](/docs/capabilities/rag/overview) |
| 可视化工作流后端 | [FlowGram](/docs/products/flowgram/overview) |

:::note 集成成熟度
这些集成不应该在入口页里被包装成已经完全稳定的能力。等对应专题页补齐后，再从这里添加深链。
:::

## 推荐阅读顺序

第一次接入建议走：

1. [为什么选 AI4J](/docs/getting-started/why-ai4j)
2. [Java 快速开始](/docs/getting-started/quickstart-java)
3. [Java 快速开始](/docs/getting-started/quickstart-java) 或 [Spring Boot 快速开始](/docs/getting-started/quickstart-spring-boot)
4. [Core SDK / Model Access / Chat](/docs/capabilities/models/chat)
5. [第一次工具调用](/docs/getting-started/first-tool-call)
6. 按需进入 [Core SDK](/docs/capabilities/overview)、[Spring Boot](/docs/integrations/spring-boot/overview)、[Agent](/docs/agent/overview) 或 [FlowGram](/docs/products/flowgram/overview)
7. 上线前检查 [生产检查清单](/docs/production/production-checklist) 和 [Security](/docs/production/security)
