---
sidebar_position: 1
---

# AI基础能力接入总览

这一章是 AI4J 官网里最基础、也最关键的一层。

它回答的是这些问题：

- 我怎么用统一 SDK 接不同模型平台
- 我该用 Chat 还是 Responses
- Embedding、Rerank、Audio、Image、Realtime 分别从哪里进
- Tool、多模态、联网增强、RAG、Rerank、网络栈扩展分别落在哪一层

如果这一层没有打通，后面的 MCP、Agent、Coding Agent 和 Flowgram 都会变成“堆能力”，而不是稳定工程体系。

---

## 1. 这一章的边界

这一章只讲“基础能力接入层”，不讲高级编排层。

也就是说：

- 这里讲 `AiService`、`PlatformType`、`IChatService`、`IResponsesService`
- 这里讲 `IEmbeddingService`、`IRerankService`、`IAudioService`、`IImageService`
- 这里讲请求对象、返回对象、流式监听器和平台差异
- 这里讲多模态、Tool、联网增强、RAG、Rerank 和网络栈扩展这类“基础扩展能力”
- 这里不把 MCP、Agent、Workflow 混成同一层

这样分层后，文档路径会更稳定：

- 基础接入先在这一章解决
- 协议与外部工具系统再看 `MCP`
- 推理循环与编排再看 `Agent`
- 工程化宿主交互再看 `Coding Agent`

---

## 2. 你应该先从哪一页开始

如果你是第一次用 AI4J，建议按下面顺序读：

1. [模块架构与包地图](/docs/ai-basics/architecture-and-package-map)
2. [服务工厂与多实例注册表](/docs/ai-basics/service-factory-and-registry)
3. [统一服务入口与调用方式](/docs/ai-basics/unified-service-entry)
4. [Memory 与 Tool 分层边界](/docs/ai-basics/memory-and-tool-boundaries)
5. [Skill 主题](/docs/ai-basics/skills)
6. [平台适配与统一接口](/docs/ai-basics/platform-adaptation)
7. [统一请求与返回读取约定](/docs/ai-basics/request-and-response-conventions)
8. `Chat` 或 `Responses` 其中一条主线
9. 需要图片、音频、Embedding、Rerank 时再看对应服务页
10. 需要联网增强、RAG 或网络栈扩展时再看对应专题

如果你已经知道自己要做什么，可以直接跳到对应专题。

---

## 3. 这一章的结构

### 3.1 统一入口

- 模块架构与包地图
- 服务工厂与多实例注册表
- `AiService`
- `AiServiceRegistry`
- `Configuration`
- `PlatformType`
- Memory 与 Tool 分层边界
- Skill 主题
- 各服务接口如何选择
- 各类返回结果怎么读取
- 统一请求/返回约定
- 如何扩展 provider 与服务实现

### 3.2 Chat

- 非流式
- 流式
- Tool / Function
- 多模态

### 3.3 Responses

- 非流式
- 流式事件模型
- 与 Chat 的选型差异

### 3.4 其它服务

- Embedding
- Rerank
- Audio
- Image
- Realtime

### 3.5 联网增强

- 联网增强总览
- SearXNG 联网增强

### 3.6 RAG / 知识库增强

- RAG 与知识库增强总览
- RAG 架构、分块与索引设计
- Ingestion Pipeline 文档入库流水线
- Chunking 策略详解
- 混合检索与 Rerank 实战工作流
- 引用、Trace 与前端展示
- Pinecone RAG 工作流

### 3.7 网络栈扩展

- SPI 网络扩展

---

## 4. 最常见的用户路径

### 4.1 只想先打通一个大模型请求

先看：

- [模块架构与包地图](/docs/ai-basics/architecture-and-package-map)
- [服务工厂与多实例注册表](/docs/ai-basics/service-factory-and-registry)
- [统一服务入口与调用方式](/docs/ai-basics/unified-service-entry)
- [统一请求与返回读取约定](/docs/ai-basics/request-and-response-conventions)
- [Chat（非流式）](/docs/ai-basics/chat/non-stream)

### 4.2 想做结构化事件流

先看：

- [Responses（非流式）](/docs/ai-basics/responses/non-stream)
- [Responses（流式事件模型）](/docs/ai-basics/responses/stream-events)

### 4.3 想做图片理解或多模态

先看：

- [Chat（多模态）](/docs/ai-basics/chat/multimodal)

### 4.4 想做工具调用

先看：

- [Memory 与 Tool 分层边界](/docs/ai-basics/memory-and-tool-boundaries)
- [Skill 主题](/docs/ai-basics/skills)
- [Chat（Tool / Function 调用）](/docs/ai-basics/chat/tool-calling)

### 4.5 想做联网增强

先看：

- [联网增强总览](/docs/ai-basics/online-search/overview)
- [SearXNG 联网增强](/docs/ai-basics/online-search/searxng)

### 4.6 想做 RAG 或知识库问答

先看：

- [Embedding 接口](/docs/ai-basics/services/embedding)
- [Rerank 接口](/docs/ai-basics/services/rerank)
- [RAG 与知识库增强总览](/docs/ai-basics/rag/overview)
- [RAG 架构、分块与索引设计](/docs/ai-basics/rag/architecture-and-indexing)
- [Ingestion Pipeline 文档入库流水线](/docs/ai-basics/rag/ingestion-pipeline)
- [Chunking 策略详解](/docs/ai-basics/rag/chunking-strategies)
- [混合检索与 Rerank 实战工作流](/docs/ai-basics/rag/hybrid-retrieval-and-rerank-workflow)
- [引用、Trace 与前端展示](/docs/ai-basics/rag/citations-trace-and-ui-integration)
- [Pinecone RAG 工作流](/docs/ai-basics/rag/pinecone-workflow)

### 4.7 想做候选重排或把 rerank 接到 RAG

先看：

- [Rerank 接口](/docs/ai-basics/services/rerank)
- [RAG 与知识库增强总览](/docs/ai-basics/rag/overview)

### 4.8 想新增模型提供商或扩展服务

先看：

- [模块架构与包地图](/docs/ai-basics/architecture-and-package-map)
- [服务工厂与多实例注册表](/docs/ai-basics/service-factory-and-registry)
- [平台适配与统一接口](/docs/ai-basics/platform-adaptation)
- [新增 Provider 与模型适配](/docs/ai-basics/provider-and-model-extension)

---

## 5. 对应的核心代码入口

- 服务工厂：`ai4j/src/main/java/io/github/lnyocly/ai4j/service/factory/AiService.java`
- 多实例注册表：`ai4j/src/main/java/io/github/lnyocly/ai4j/service/factory/AiServiceRegistry.java`
- 统一配置：`ai4j/src/main/java/io/github/lnyocly/ai4j/service/Configuration.java`
- 平台枚举：`ai4j/src/main/java/io/github/lnyocly/ai4j/service/PlatformType.java`

后续各页都围绕这三个入口展开，不再引入第二套主入口。

补充两条总图页，适合在进入具体 API 前先建立边界：

- [模块架构与包地图](/docs/ai-basics/architecture-and-package-map)
- [Memory 与 Tool 分层边界](/docs/ai-basics/memory-and-tool-boundaries)
- [Skill 主题](/docs/ai-basics/skills)
