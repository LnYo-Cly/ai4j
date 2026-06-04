---
sidebar_position: 1
---

# AI4J 文档中心

AI4J 是一套面向 `Java 8+` 的 AI SDK。它的重点不是把所有 AI 框架概念都塞进来，
而是尽量降低 Java 项目接入模型、工具、RAG、MCP 和上层 Agent 能力的成本。

如果你只是想先跑通一个模型请求，可以从普通 Java 或 Spring Boot 快速开始。
如果你已经知道要做工具调用、RAG、MCP、Agent 或 Coding Agent，可以直接从功能地图进入。

## 先从这里开始

| 目标 | 推荐入口 | 你会得到什么 |
| --- | --- | --- |
| 普通 Java 项目先跑通 | [Quickstart for Java](/docs/start-here/quickstart-java) | 最小依赖、配置和第一段调用代码 |
| Spring Boot 项目接入 | [Quickstart for Spring Boot](/docs/start-here/quickstart-spring-boot) | starter、配置项和 Bean 注入方式 |
| 先看完整能力边界 | [Feature Map](/docs/start-here/feature-map) | AI4J 当前能力、成熟度和继续阅读路径 |
| 理解为什么做 AI4J | [Why AI4J](/docs/start-here/why-ai4j) | 项目定位、适合场景和与相邻方案的差异 |

## AI4J 覆盖哪些能力

AI4J 的能力分为三层。阅读时建议先把核心 SDK 跑通，再按项目需要向上升级。

| 层级 | 能力 | 入口 |
| --- | --- | --- |
| Core SDK | `Chat`、`Responses`、流式、多模态、图像、音频、Embedding、Rerank | [Core SDK / Model Access](/docs/core-sdk/model-access/overview) |
| 能力接入 | Function Call、本地工具、Skill、MCP | [Tools](/docs/core-sdk/tools/overview)、[Skills](/docs/core-sdk/skills/overview)、[MCP](/docs/mcp/overview) |
| 数据增强 | Memory、Search、RAG、VectorStore、Ingestion、Hybrid Retrieval | [Search & RAG](/docs/core-sdk/search-and-rag/overview) |
| 应用集成 | Spring Boot starter、配置治理、自动装配 | [Spring Boot](/docs/spring-boot/overview) |
| 上层运行时 | Agent、Coding Agent、FlowGram 工作流 | [Agent](/docs/agent/overview)、[Coding Agent](/docs/coding-agent/overview)、[FlowGram](/docs/flowgram/overview) |
| 场景方案 | 常见组合方案和可复制集成路径 | [Solutions](/docs/solutions/overview) |

## 三个概念不要混在一起

AI4J 文档会刻意区分三类能力：

- `Function Call / Tool`：让模型调用本地函数或受控工具。
- `Skill`：给模型按需读取的说明、模板、流程和经验资产。
- `MCP`：通过协议接入外部工具、服务或能力网关。

这三者可以组合，但职责不同。把边界讲清楚，是降低后续使用成本的关键。

## 仓库模块地图

| 模块 | 角色 |
| --- | --- |
| `ai4j/` | Core SDK：provider 接入、Chat、Responses、RAG、MCP、vector、image、audio、realtime |
| `ai4j-spring-boot-starter/` | Spring Boot 自动配置和应用侧接入 |
| `ai4j-agent/` | Agent runtime、workflow、trace、memory、team orchestration |
| `ai4j-coding/` | Coding Agent runtime、workspace 工具、outer loop、compaction |
| `ai4j-cli/` | CLI、TUI、ACP host 和本地会话入口 |
| `ai4j-flowgram-spring-boot-starter/` | FlowGram 集成、任务 API、trace bridge |
| `ai4j-flowgram-demo/` 与 `ai4j-flowgram-webapp-demo/` | FlowGram 后端和前端 demo |
| `ai4j-bom/` | Maven 版本对齐 |

下一步建议：如果你还没跑过代码，直接看 [Quickstart for Java](/docs/start-here/quickstart-java)；
如果你想先判断 AI4J 是否适合自己的项目，看 [Why AI4J](/docs/start-here/why-ai4j)。
