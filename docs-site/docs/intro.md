---
sidebar_position: 1
title: AI4J 文档中心
description: AI4J 是面向 Java 8+ 的 AI SDK 入口：按需取用的模型调用、工具、RAG、MCP、Spring Boot、Agent、Coding Agent 与 FlowGram building blocks 的总览、推荐起点与仓库模块地图。
tags: [concept]
---

# AI4J 文档中心

AI4J 是一套面向 `Java 8+` 的 AI SDK。它不是一个要求全量采用的 AI 平台，
而是一组可以按需取用的 Java AI building blocks：先拿 `ai4j` 跑通模型调用，
再按项目需要接入 Spring Boot、RAG、MCP、Agent、Coding Agent 或 FlowGram。

如果你只是想先跑通一个模型请求，可以从普通 Java 或 Spring Boot 快速开始。
如果你已经知道要做工具调用、RAG、MCP、Agent 或 Coding Agent，可以直接从功能地图进入。

## 先从这里开始

| 目标 | 推荐入口 | 你会得到什么 |
| --- | --- | --- |
| 普通 Java 跑通第一条 AI 请求 | [Java 快速开始](/docs/getting-started/quickstart-java) | 最小依赖、密钥配置、验证标准和第一条模型调用 |
| 普通 Java 项目先跑通 | [Java 快速开始](/docs/getting-started/quickstart-java) | 最小依赖、配置和第一段调用代码 |
| Spring Boot 项目接入 | [Spring Boot 快速开始](/docs/getting-started/quickstart-spring-boot) | starter、配置项和 Bean 注入方式 |
| 先看完整能力边界 | [功能地图](/docs/getting-started/feature-map) | AI4J 当前能力、成熟度和继续阅读路径 |
| 查正式文档路径 | [文档地图](/docs/reference/maps/documentation-map) | canonical 主线、legacy 来源和迁移方向 |
| 上线前检查 | [生产检查清单](/docs/production/production-checklist) | 版本、密钥、Tool、MCP、RAG、Agent、FlowGram 的检查项 |
| 理解为什么做 AI4J | [为什么选 AI4J](/docs/getting-started/why-ai4j) | 项目定位、适合场景和与相邻方案的差异 |

## 用多少，取多少

你不需要一开始理解整个 AI4J，也不需要把所有模块都引入项目。更合理的方式是按当前问题取一个模块，
等需求变复杂后再升级到下一层。

| 你现在只想做什么 | 直接取用 | 什么时候再升级 |
| --- | --- | --- |
| 用统一 `AiClient`、`@AiService` 调多模型、流式与结构化输出 | `ai4j` | 需要容器配置、SSE 或 Actuator 时接 Starter / `ai4j-web` |
| 单独接入 MCP、RAG、远端沙箱或 A2A 扩展 | `ai4j-mcp` / `ai4j-rag` / `ai4j-sandbox` / `ai4j-a2a` | 通过 Cordis 微内核按需装配，核心包保持零冗余传递依赖 |
| 在 Spring Boot 2/3/4 应用里接 AI、SSE 与 `/actuator/ai4j` | `ai4j-spring-boot-starter`（+ `ai4j-web`） | 需要复杂状态或长程编排时再接 Agent / Harness |
| 做可嵌入的 Agent runtime、长期任务或常驻代理 | `ai4j-agent` -> `ai4j-harness` / `ai4j-ambient` | 需要代码仓任务或插件热插拔时接 Coding Agent / Cordis 插件 |
| 做本地代码仓任务入口 | `ai4j-coding` + `ai4j-cli` | 需要产品化 CLI / TUI / ACP 时扩展命令与边缘 Fleet |
| 接 FlowGram 可视化工作流 | `ai4j-flowgram-spring-boot-starter` | 需要完整 demo 时再看 FlowGram demo |
| 多模块版本统一 | `ai4j-bom` | 项目同时引入多个 AI4J `2.x` artifact 时使用 |

## AI4J 覆盖哪些能力

AI4J 的能力分为清晰的分层。阅读时建议先把 Core SDK 跑通，再按项目需要向上叠加。

| 层级 | 能力 | 入口 |
| --- | --- | --- |
| Core SDK 与 DX | `AiClient`、`@AiService`、`Chat` / `Responses` / `Protocol.GEMINI`、`ProviderPresets`、`AiStream`、`ModelMiddleware`、`JsonCodec` | [Core SDK / Model Access](/docs/capabilities/models/overview)、[AiClient DX](/docs/getting-started/ai-client-dx)、[Gemini 原生协议](/docs/capabilities/models/gemini)、[AiStream 与中间件](/docs/capabilities/models/ai-stream-and-middleware) |
| 能力接入与微内核 | Function Call、`@AiTool` / `ToolOut`、Skill、MCP、`ai4j-extension-api` Cordis 微内核与官方插件 | [Tools](/docs/capabilities/tools/overview)、[Skills](/docs/capabilities/skills/overview)、[MCP](/docs/capabilities/mcp/overview)、[Cordis 微内核与插件](/docs/extending/plugins/cordis-microkernel) |
| 数据增强 | Memory、Search、RAG、VectorStore、Ingestion、Document Tika、Hybrid Retrieval | [Search & RAG](/docs/capabilities/rag/overview) |
| 应用集成 | Spring Boot 2/3/4 单包双栈、`ai4j-web`（AG-UI / OpenAI SSE）、`/actuator/ai4j` | [Spring Boot](/docs/integrations/spring-boot/overview)、[Web SSE 与 Actuator](/docs/integrations/spring-boot/web-sse-and-actuator) |
| 上层运行时与长程治理 | Agent、`ai4j-testing`、Harness 2.0 治理与多节点 Fleet、Ambient 常驻与门控 RSI、Coding Agent、FlowGram | [Agent](/docs/agent/overview)、[Harness 2.0 与 Fleet](/docs/agent/harness-governance-and-fleet)、[Ambient 与 RSI](/docs/agent/ambient-and-rsi)、[Coding Agent](/docs/products/coding-agent/overview)、[FlowGram](/docs/products/flowgram/overview) |
| 场景方案 | 常见组合方案和可复制集成路径 | [Solutions](/docs/integrations/solutions/overview) |
| 生产准备 | 版本兼容、发布 artifact、安全、迁移、排障 | [版本兼容性](/docs/reference/version-compatibility)、[Security](/docs/production/security)、[排障指南](/docs/production/troubleshooting) |

## 三个概念不要混在一起

AI4J 文档会刻意区分三类能力：

- `Function Call / Tool`：让模型调用本地函数或受控工具。
- `Skill`：给模型按需读取的说明、模板、流程和经验资产。
- `MCP`：通过协议接入外部工具、服务或能力网关。

这三者可以组合，但职责不同。把边界讲清楚，是降低后续使用成本的关键。

## 仓库模块地图

| 模块 | 角色 |
| --- | --- |
| `ai4j-extension-api/` | 轻量公共微内核合同：`ServiceKey`、`Disposable` / 作用域、`ServiceInjector`、manifest 与三段式门禁 |
| `ai4j/` | Core SDK：`AiClient` DX、`@AiService`、`Chat`/`Responses`/`Gemini` 协议、`ProviderPresets`、`AiStream`、`ModelMiddleware`、`JsonCodec` 单栈 |
| `ai4j-mcp/`、`ai4j-rag/`、`ai4j-document-tika/`、`ai4j-sandbox/`、`ai4j-a2a/` | 按需引入的独立能力模块：MCP 桥接、RAG 规划/评判、Tika 文档解析、远端沙箱抽象、A2A 跨 Agent 协作 |
| `ai4j-plugin-ask-user/`、`ai4j-plugin-colab/`、`ai4j-plugin-codeact/`、`ai4j-plugin-sandbox-docker/`、`ai4j-plugin-redis/` | 官方 Cordis 插件包：用户澄清提问、Colab 内核沙箱，以及 Wave 3 新增的 CodeAct 执行、Docker 沙箱与 Redis 存储扩展 |
| `ai4j-agent/` 与 `ai4j-testing/` | Agent runtime（含 `StructuredOutputSelfHealer`）、workflow、trace、memory、team orchestration 与离线回放夹具 |
| `ai4j-harness/` 与 `ai4j-ambient/` | Harness 2.0 CQRS 事件账本 / 治理 / 5 拓扑 Fleet，以及 Ambient 常驻记忆 / 两段式心跳 / 门控 RSI |
| `ai4j-coding/` 与 `ai4j-cli/` | Coding Agent runtime、workspace 工具、outer loop、compaction，以及 CLI / TUI / ACP / Fleet 边缘入口 |
| `ai4j-web/` 与 `ai4j-spring-boot-starter/` | 无侵入 Web SSE（AG-UI / OpenAI 编码）与 Spring Boot 2/3/4 单包双栈自动配置、`/actuator/ai4j` |
| `ai4j-flowgram-spring-boot-starter/` | FlowGram 集成、任务 API、trace bridge |
| `ai4j-flowgram-demo/` 与 `ai4j-flowgram-webapp-demo/` | FlowGram 后端和前端 demo |
| `ai4j-bom/` | Maven `2.x` 多模块版本对齐 BOM |

这些模块不是为了展示目录规模，而是为了让使用者只引入当下需要的能力，并保留向上升级的空间。

下一步建议：如果你还没跑过代码，直接看 [Java 快速开始](/docs/getting-started/quickstart-java)；
如果你想先判断 AI4J 是否适合自己的项目，看 [为什么选 AI4J](/docs/getting-started/why-ai4j)。
