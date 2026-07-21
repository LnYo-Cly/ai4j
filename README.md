<p align="center">
  <img src="https://capsule-render.vercel.app/api?type=waving&color=0:6A5ACD,100:2E86C1&height=180&section=header&text=ai4j&fontSize=46&fontColor=ffffff&animation=fadeIn&desc=Java%20AI%20Agentic%20SDK%20for%20JDK%208%2B&descAlignY=68" alt="ai4j banner" />
</p>

<p align="center">
  <a href="https://search.maven.org/artifact/io.github.lnyo-cly/ai4j">
    <img src="https://img.shields.io/maven-central/v/io.github.lnyo-cly/ai4j?color=2E86C1&label=Maven%20Central" alt="Maven Central" />
  </a>
  <a href="https://lnyo-cly.github.io/ai4j/">
    <img src="https://img.shields.io/badge/Docs-GitHub%20Pages-0A7EA4" alt="Docs" />
  </a>
  <a href="https://www.apache.org/licenses/LICENSE-2.0.txt">
    <img src="https://img.shields.io/badge/License-Apache%202.0-1F6FEB" alt="License" />
  </a>
  <img src="https://img.shields.io/badge/JDK-8%2B-2EA043" alt="JDK 8+" />
  <img src="https://img.shields.io/badge/Agentic-Enabled-6F42C1" alt="Agentic Enabled" />
  <img src="https://img.shields.io/badge/MCP-Supported-0F766E" alt="MCP Supported" />
  <img src="https://img.shields.io/badge/RAG-Built--in-B45309" alt="RAG Built-in" />
  <img src="https://img.shields.io/badge/CLI%20%2F%20TUI%20%2F%20ACP-Built--in-475569" alt="CLI TUI ACP Built-in" />
</p>

# ai4j
一款面向 JDK8+ 的 Java AI Agentic 开发套件，既提供统一的大模型调用与常用 AI 基座能力，也提供更完善的智能体式 Agent 开发能力。  
覆盖多平台模型接入、统一输入输出、Tool Call、MCP、RAG、统一 `VectorStore`、ChatMemory、Agent Runtime、Coding Agent、CLI / TUI / ACP、FlowGram 集成，以及 Dify / Coze / n8n 等已发布 AgentFlow 端点接入能力，帮助 Java 应用从基础模型接入扩展到更完整的 agentic 应用开发。

当前仓库已经演进为多模块 SDK，除核心 `ai4j` 外，还提供 `ai4j-extension-api`、`ai4j-plugin-ask-user`、`ai4j-agent`、`ai4j-coding`、`ai4j-cli`、`ai4j-spring-boot-starter`、`ai4j-flowgram-spring-boot-starter`、`ai4j-bom`。如果只需要基础大模型调用，优先引入 `ai4j`；如果需要插件包、Agent、Coding Agent、CLI / ACP、Spring Boot 或 FlowGram 集成，再按模块引入对应能力。

## README 导航

- [快速选择](#快速选择)
- [安装片段](#安装片段)
- [项目定位与对比](#适用场景与常见方案对比)
- [赞助商](#赞助商)
- [官方文档站](#官方文档站)
- [详细文档](#详细文档)
- [English README](README-EN.md)

## 快速选择

| 你要做什么 | 入口 |
| --- | --- |
| 先跑通第一条请求 | [快速开始](docs/readme/zh/quick-start.md) |
| 使用 Chat / streaming / vision / function call / memory | [Chat 服务](docs/readme/zh/chat.md) |
| 使用 Embedding、Rerank、RAG、混合检索 | [Embedding / Rerank / RAG](docs/readme/zh/rag-and-retrieval.md) |
| 使用 Coding Agent CLI / TUI / ACP | [Coding Agent CLI / TUI](docs/readme/zh/coding-agent-cli.md) |
| 使用联网增强或 Spring Boot | [内置联网与 Spring 应用](docs/readme/zh/network-and-spring.md) |
| 查看历史发布变化 | [更新日志](docs/readme/zh/changelog.md) |
| 贡献、赞助、贡献者 | [贡献与支持](docs/readme/zh/contributing-support.md) |

## 安装片段

Gradle：

```gradle
implementation 'io.github.lnyo-cly:ai4j:2.4.1'
```

Maven：

```xml
<dependency>
  <groupId>io.github.lnyo-cly</groupId>
  <artifactId>ai4j</artifactId>
  <version>2.4.1</version>
</dependency>
```

更多模块选型、Spring Boot 配置和调用示例见：[快速开始](docs/readme/zh/quick-start.md)。

## 赞助商

+ [TroveBox AI 中转平台](https://codex.trovebox.online/)：提供 AI API 中转服务，低至 0.1x 倍率。

## 适用场景与常见方案对比

| 方案 | Java 基线 | 应用形态 | 能力侧重点 |
| --- | --- | --- | --- |
| `ai4j` | `JDK8+` | 普通 Java / Spring | 统一大模型接入、Tool / MCP / RAG、Agent Runtime、Coding Agent、CLI / TUI / ACP |
| `Spring AI` | `Java 17+` | `Spring Boot 3.x` | Spring 原生 AI 集成、模型访问、Tool Calling、MCP、RAG |
| `Spring AI Alibaba` | `Java 17+` | `Spring Boot 3.x` | Spring 与阿里云 AI 生态整合 |
| `LangChain4j` | `Java 17+` | 普通 Java / Spring / Quarkus 等 | 通用 Java LLM / Agent / RAG 抽象、AI Services、多框架集成 |

## 支持的平台
+ OpenAI / OpenAI-compatible
+ Anthropic / Anthropic-compatible Messages
+ DashScope（阿里云百炼 / 通义）
+ Doubao（火山方舟 / 豆包）
+ Jina（Rerank / Jina-compatible Rerank）
+ Zhipu（智谱）
+ DeepSeek（深度求索）
+ Moonshot（月之暗面）
+ Hunyuan（腾讯混元）
+ Lingyi（零一万物）
+ Ollama
+ MiniMax
+ Baichuan
+ Suno

## 支持的服务
+ Chat Completions（流式与非流式）
+ Responses
+ Anthropic Messages
+ Embedding
+ Rerank
+ Audio
+ Image
+ Video
+ Realtime
+ Music（Suno 任务式生成）

## 已适配的 AgentFlow / 工作流平台
+ Dify（Chat / Workflow）
+ Coze（Chat / Workflow）
+ n8n（Webhook Workflow）

## 特性
+ 支持MCP服务，内置MCP网关，支持建立动态MCP数据源。
+ 支持Spring以及普通Java应用、支持Java 8以上的应用
+ 多平台、多服务
+ 提供 `AgentFlow` 能力，可直接接入 Dify、Coze、n8n 等已发布 Agent / Workflow 端点
+ 提供 `ai4j-agent` 通用 Agent 运行时，支持 ReAct、subagent、agent teams、memory、trace 与 tool loop
+ 内置 Coding Agent CLI / TUI，支持本地代码仓交互式会话、provider profile、workspace model override、session/process 管理
+ 提供 `ai4j-coding` Coding Agent 运行时，支持 workspace tools、outer loop、checkpoint compaction、subagent 与 team 协作
+ 提供 `ai4j-flowgram-spring-boot-starter`，便于在 Spring Boot 中接入 FlowGram 工作流与 trace
+ 提供 `ai4j-extension-api` 与官方 `ai4j-plugin-ask-user` 样板插件，用于按需扩展 Agent / Coding Agent 工具、命令、Skill 与 Prompt
+ 提供 `ai4j-bom`，便于多模块项目统一版本管理
+ 统一的输入输出
+ 统一的错误处理
+ 支持SPI机制，可自定义Dispatcher和ConnectPool
+ 支持服务增强，例如增加websearch服务
+ 支持流式输出。支持函数调用参数流式输出.
+ 简洁的多模态调用方式，例如vision识图
+ 轻松使用Tool Calls
+ 支持多个函数同时调用（智谱不支持）
+ 支持stream_options，流式输出直接获取统计token usage
+ 内置 `ChatMemory`，支持基础多轮会话上下文维护，可同时适配 Chat / Responses
+ 支持RAG，内置统一 `VectorStore` 抽象，当前支持: Pinecone、Qdrant、pgvector、Milvus
+ 内置 `IngestionPipeline`，统一串联 `DocumentLoader -> Chunker -> MetadataEnricher -> Embedding -> VectorStore.upsert`
+ 内置 `DenseRetriever`、`Bm25Retriever`、`HybridRetriever`，可按语义检索、关键词检索、混合检索方式组合知识库召回
+ `HybridRetriever` 支持 `RrfFusionStrategy`、`RsfFusionStrategy`、`DbsfFusionStrategy`，默认使用 RRF；融合排序与 `Reranker` 语义精排解耦
+ 支持统一 `IRerankService`，当前可接 Jina / Jina-compatible、Ollama、Doubao(方舟知识库重排)；可通过 `ModelReranker` 无缝接入 RAG 精排
+ RAG 运行时可直接拿到 `rank/retrieverSource/retrievalScore/fusionScore/rerankScore/scoreDetails/trace`，并可通过 `RagEvaluator` 计算 `Precision@K/Recall@K/F1@K/MRR/NDCG`
+ 使用Tika读取文件
+ 提供基础 token 估算与用量统计工具

## 官方文档站
+ 在线文档站：`https://lnyo-cly.github.io/ai4j/`
+ 文档站源码位于 `docs-site/`
+ 5 分钟跑通第一条请求：`docs-site/docs/start-here/five-minute-first-chat.md`
+ 普通 Java 接入：`docs-site/docs/start-here/quickstart-java.md`
+ Spring Boot 接入：`docs-site/docs/start-here/quickstart-spring-boot.md`
+ 能力边界与路径选择：`docs-site/docs/start-here/feature-map.md`
+ 插件包生态与第三方扩展：`docs-site/docs/core-sdk/extension/plugin-packages.md`
+ 官方 Ask User 插件：`docs-site/docs/core-sdk/extension/ask-user-plugin.md`
+ Spring Boot 插件配置：`ai.extensions.enabled` + `ai.extensions.tools.expose`
+ CLI 插件骨架生成：`ai4j-cli extension init <directory> --id <extension-id> --package <java-package>`
+ CLI 插件校验：`ai4j-cli extension validate <extension-id>|--all`
+ CLI 插件接入门禁：`ai4j-cli extension check <extension-id> --enable [activation options]`
+ CLI 插件命令执行：`ai4j-cli extension run --enable <extension-id> <command> [arguments...]`
+ CLI 插件资源读取：`ai4j-cli extension resource --enable <extension-id> <skill|prompt> <name>`
+ 插件 Guardrail：已启用插件可在 Agent / Coding Agent 执行 tool call 前拦截内置工具与扩展工具
+ 协议、Agent 与上层集成：`docs-site/docs/mcp/`、`docs-site/docs/agent/`、`docs-site/docs/coding-agent/`、`docs-site/docs/flowgram/`

推荐阅读顺序：

+ `docs-site/docs/intro.md`
+ `docs-site/docs/start-here/five-minute-first-chat.md`
+ `docs-site/docs/start-here/quickstart-java.md` 或 `docs-site/docs/start-here/quickstart-spring-boot.md`
+ `docs-site/docs/start-here/first-chat.md`
+ `docs-site/docs/core-sdk/overview.md`
+ `docs-site/docs/core-sdk/extension/plugin-packages.md`
+ `docs-site/docs/mcp/overview.md`

如果使用支持 Skills 的 agent 工具，可以安装用户侧接入 Skill：

```bash
npx skills add LnYo-Cly/ai4j --skill ai4j-app-builder
```

基础会话上下文新增入口：

+ `docs-site/docs/ai-basics/chat/chat-memory.md`
+ `docs-site/docs/ai-basics/services/rerank.md`
+ `docs-site/docs/ai-basics/rag/ingestion-pipeline.md`

本地运行文档站：

```powershell
cd .\docs-site
npm install
npm run start
```

```powershell
cd .\docs-site
npm run build
```

## 详细文档

- [更新日志](docs/readme/zh/changelog.md)
- [Coding Agent CLI / TUI](docs/readme/zh/coding-agent-cli.md)
- [快速开始](docs/readme/zh/quick-start.md)
- [Chat 服务](docs/readme/zh/chat.md)
- [Embedding / Rerank / RAG](docs/readme/zh/rag-and-retrieval.md)
- [内置联网与 Spring 应用](docs/readme/zh/network-and-spring.md)
- [贡献与支持](docs/readme/zh/contributing-support.md)

## 教程文档

+ [快速接入SpringBoot、接入流式与非流式以及函数调用](http://t.csdnimg.cn/iuIAW)
+ [Java快速接入qwen2.5、llama3.1等Ollama平台开源大模型](https://blog.csdn.net/qq_35650513/article/details/142408092?spm=1001.2014.3001.5501)
+ [Java搭建法律AI助手，快速实现RAG应用](https://blog.csdn.net/qq_35650513/article/details/142568177?fromshare=blogdetail&sharetype=blogdetail&sharerId=142568177&sharerefer=PC&sharesource=qq_35650513&sharefrom=from_link)
+ [大模型不支持联网搜索？为Deepseek、Qwen、llama等本地模型添加网络搜索](https://blog.csdn.net/qq_35650513/article/details/144572824)
+ [java快速接入mcp以及结合mysql动态管理](https://blog.csdn.net/qq_35650513/article/details/150532784?fromshare=blogdetail&sharetype=blogdetail&sharerId=150532784&sharerefer=PC&sharesource=qq_35650513&sharefrom=from_link)
