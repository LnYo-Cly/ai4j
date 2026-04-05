---
sidebar_position: 2
---

# 模块架构与包地图

这一页专门回答两个问题：

1. `ai4j-sdk` 这个仓库里到底有哪些模块，它们分别负责什么？
2. 如果我要看源码，应该从哪个包开始，而不是在仓库里盲找？

如果你已经知道怎么调用 `AiService`，但还不清楚 `RAG / MCP / Agent / Coding Agent` 分别落在哪一层，这一页就是总地图。

---

## 1. 先看模块，不要先看类

当前仓库不是一个单体 jar，而是多模块分层：

| 模块 | 主要职责 | 典型使用者 |
| --- | --- | --- |
| `ai4j` | 基础 AI 服务层，统一 `Chat / Responses / Embedding / Rerank / Audio / Image / Realtime` 接口，以及 RAG、向量存储、联网增强、MCP 基础能力 | 直接接大模型的业务代码 |
| `ai4j-spring-boot-starter` | Spring Boot 自动配置层，把 `Configuration / AiConfig / 平台配置` 接入 Spring 容器 | Spring Boot 应用 |
| `ai4j-agent` | 通用 Agent runtime，包括 `runtime / tool / memory / subagent / team / trace / workflow` | 要做推理循环和多智能体编排的开发者 |
| `ai4j-coding` | 面向代码仓交付的 Coding Agent 内核，包括 workspace tools、session、checkpoint、compact、outer loop、delegate | 编码助手或 IDE/CLI 宿主 |
| `ai4j-cli` | CLI / TUI / ACP 宿主层，负责交互、会话管理、事件流、MCP runtime、审批桥接 | 终端和 IDE 宿主 |

一句话理解：

- `ai4j` 解决“怎么统一接模型和基础 AI 能力”
- `ai4j-agent` 解决“怎么做可控推理循环”
- `ai4j-coding` 解决“怎么把 Agent 变成代码仓工作流”
- `ai4j-cli` 解决“怎么把 Coding Agent 变成真正可用的产品入口”

---

## 2. 文档章节和源码模块怎么对应

当前 docs-site 里，最容易混的是“专题章节”和“源码模块”不是 1:1 命名。

推荐按下面映射理解：

| 文档章节 | 主要覆盖模块 |
| --- | --- |
| `AI基础能力接入` | 以 `ai4j` 为主，必要时跨到 `ai4j-agent` / `ai4j-coding` 做边界说明 |
| `MCP` | `ai4j` 的 MCP client/gateway/transport 基础能力，以及上层如何消费 |
| `Agent` | `ai4j-agent` |
| `Coding Agent` | `ai4j-coding` + `ai4j-cli` |
| `Flowgram` | `ai4j-agent` 中的 flowgram 运行时与站点示例 |

所以：

- 想看 `IChatService / AiService / RAG / VectorStore`，先去 `AI基础能力接入`
- 想看 `AgentMemory / Runtime / SubAgent / Team`，去 `Agent`
- 想看 `CodingSession / compact / CLI / ACP`，去 `Coding Agent`

---

## 3. `ai4j` 基础层的包地图

`ai4j/src/main/java/io/github/lnyocly/ai4j`

这是整个 SDK 的基础能力根包。最值得先记住的是下面这些子包。

### 3.1 `service`

这是统一接口层。

关键类包括：

- `IChatService`
- `IResponsesService`
- `IEmbeddingService`
- `IRerankService`
- `IAudioService`
- `IImageService`
- `IRealtimeService`
- `Configuration`
- `PlatformType`

这层只定义“业务代码应该依赖什么接口”，不关心具体平台怎么发 HTTP 请求。

### 3.2 `service.factory`

这是统一工厂与多实例路由层。

关键类包括：

- `AiService`
- `AiServiceFactory`
- `DefaultAiServiceFactory`
- `AiServiceRegistry`
- `DefaultAiServiceRegistry`
- `FreeAiService`

职责分工是：

- `AiService`：给单套 `Configuration` 创建具体服务实例；
- `AiServiceRegistry`：按 id 管理多套 `AiService`；
- `DefaultAiServiceRegistry`：把 `AiConfig.platforms[]` 展开成多实例路由表；
- `FreeAiService`：兼容静态入口。

如果你只有一套平台配置，通常直接用 `AiService`。

如果你有多租户、多环境或多 provider 并存，更适合 `AiServiceRegistry`。

### 3.3 `platform`

这是平台适配层。

当前目录里已经有这些 provider 线：

- `openai`
- `zhipu`
- `doubao`
- `dashscope`
- `deepseek`
- `minimax`
- `ollama`
- `jina`
- `moonshot`
- `lingyi`
- `hunyuan`
- `baichuan`
- `standard`

这一层的职责不是提供统一接口，而是把统一接口翻译成各平台协议。

比如：

- `platform.openai.chat.OpenAiChatService`
- `platform.openai.embedding.OpenAiEmbeddingService`
- `platform.doubao.response.DoubaoResponsesService`
- `platform.standard.rerank.StandardRerankService`

这里的 `standard` 很重要，它代表“标准兼容协议”，而不是某个具体厂商。

### 3.4 `memory`

这是基础会话上下文层，不是 Agent memory。

关键类包括：

- `ChatMemory`
- `InMemoryChatMemory`
- `JdbcChatMemory`
- `ChatMemoryPolicy`
- `MessageWindowChatMemoryPolicy`
- `ChatMemorySnapshot`

它服务的是：

- 直接使用 `IChatService`
- 直接使用 `IResponsesService`

而不是完整 Agent runtime。

### 3.5 `tool`

这是基础 Tool / Function / MCP 暴露与执行层。

关键类包括：

- `ToolUtil`
- `ResponseRequestToolResolver`

职责分工：

- `ToolUtil`：统一把 Function 工具、本地 MCP 工具和远程 MCP 服务暴露成模型可调用的 `tools`，并提供统一执行入口；
- `ResponseRequestToolResolver`：把 `ResponseRequest.functions / mcpServices` 自动解析成 `tools`，供 `Responses` 请求使用。

### 3.6 `mcp`

这是 MCP 基础设施层。

关键内容包括：

- client
- gateway
- transport
- config
- server

它解决的是：

- 怎么连接 MCP server
- 怎么聚合工具
- 怎么把 MCP 暴露回 SDK 或 Agent

而不是直接解决 Agent 推理循环。

### 3.7 `rag` 与 `vector`

这是知识库增强层。

`rag` 里是 RAG 语义抽象：

- `RagService`
- `Retriever`
- `Reranker`
- `DefaultRagService`
- `IngestionPipeline`

`vector` 里是底层向量存储抽象和实现：

- `VectorStore`
- `VectorRecord`
- `VectorSearchRequest`
- `PineconeVectorStore`
- `QdrantVectorStore`
- `MilvusVectorStore`
- `PgVectorStore`

也就是：

- `rag` 更偏“检索增强语义工作流”
- `vector` 更偏“向量数据库接线与统一抽象”

### 3.8 `websearch`

这是联网增强层。

当前核心入口是：

- `ChatWithWebSearchEnhance`
- `websearch.searxng.*`

它本质上是“给基础 `IChatService` 增加检索增强包装”，而不是完整 Agent 搜索系统。

---

## 4. 一次基础调用会经过哪些层

下面以最常见的 `Chat` 请求为例：

```text
业务代码
  -> AiService / AiServiceRegistry
  -> IChatService
  -> platform.<provider>.<service>
  -> listener / tool / memory / websearch 等增强层
  -> HTTP / SSE / WebSocket
```

如果是 `Responses`，多一层工具解析：

```text
ResponseRequest
  -> ResponseRequestToolResolver
  -> ToolUtil.getAllTools(...)
  -> IResponsesService
  -> provider adapter
```

如果是 `RAG`：

```text
AiService
  -> IEmbeddingService
  -> VectorStore
  -> Retriever / Reranker / RagService
```

---

## 5. 什么时候该进入 `ai4j-agent`

下面这些场景，说明你已经不只是“基础服务调用”了：

- 需要模型自己决定是否继续下一轮工具调用
- 需要统一的 step loop
- 需要 Agent memory，而不是简单 `ChatMemory`
- 需要 SubAgent / Agent Teams / Workflow / Trace

这时就该从 `ai4j` 切到 `ai4j-agent`。

`ai4j-agent` 的根包：

`ai4j-agent/src/main/java/io/github/lnyocly/ai4j/agent`

最核心的子包有：

- `runtime`
- `model`
- `tool`
- `memory`
- `subagent`
- `team`
- `trace`
- `workflow`

---

## 6. 什么时候该进入 `ai4j-coding`

下面这些场景，说明你已经不只是“通用 Agent”了：

- 任务目标是本地代码仓交付
- 需要 `bash / read_file / write_file / apply_patch`
- 需要 workspace-aware prompt
- 需要 session save / resume / fork / tree / replay
- 需要 compact / checkpoint / outer loop continuation

这时就该从 `ai4j-agent` 继续进入 `ai4j-coding` 和 `ai4j-cli`。

---

## 7. 一张决策表

| 需求 | 入口层 |
| --- | --- |
| 直接调大模型问答、生成、Embedding、Audio、Image | `ai4j` |
| 需要基础多轮上下文，但不想上 Agent | `ai4j.memory.ChatMemory` |
| 需要统一 step loop、工具推理、多 Agent 编排 | `ai4j-agent` |
| 需要本地代码仓交付、会话树、compact、CLI/TUI/ACP | `ai4j-coding` + `ai4j-cli` |

---

## 8. 推荐连读

1. [统一服务入口与调用方式](/docs/ai-basics/unified-service-entry)
2. [Memory 与 Tool 分层边界](/docs/ai-basics/memory-and-tool-boundaries)
3. [平台适配与统一接口](/docs/ai-basics/platform-adaptation)
4. [RAG 与知识库增强总览](/docs/ai-basics/rag/overview)
5. [MCP 总览](/docs/mcp/overview)
6. [Agent 架构总览](/docs/agent/overview)
7. [Coding Agent 总览](/docs/coding-agent/overview)
